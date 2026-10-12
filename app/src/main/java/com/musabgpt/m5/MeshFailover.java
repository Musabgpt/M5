package com.musabgpt.m5;

import java.util.ArrayList;
import java.util.List;

/**
 * Failover policy for opt-in free-tier HTTP LLM routes.
 *
 * Immutable requests are resent only after transport failure. No successful
 * agent tool is replayed by this class. Network handoff latency is not bounded
 * by this policy; only the number of attempts and total time are bounded.
 *
 * Pure Java (no Android dependency) to enable deterministic fault injection.
 */
public final class MeshFailover {
    public static final long MAX_TASK_MS=60_000L;
    public static final class Route {
        public final int id;
        public final String name;
        public Route(int id,String name){this.id=id;this.name=name;}
    }
    public static final class Failure extends Exception {
        public final int status;
        public final long retryAfterMs;
        public final boolean temporary;
        public Failure(int status,long retryAfterMs,boolean temporary){
            super(status>0?"HTTP "+status:temporary?"Transient network error":"Provider failure");
            this.status=status;
            this.retryAfterMs=Math.max(0,retryAfterMs);
            this.temporary=temporary;
        }
    }
    public static final class Result {
        public final String text;
        public final String route;
        public final int calls;
        Result(String text,String route,int calls){this.text=text;this.route=route;this.calls=calls;}
    }
    public interface Transport {
        String call(int routeId,long remainingMs) throws Failure;
    }
    public interface TimeSource { long nowMs(); }
    public interface Delay { void waitMs(long delayMs) throws InterruptedException; }
    public interface Events { void update(String safeMessage); }
    public static final class Exhausted extends Exception {
        public final int attempted;
        Exhausted(int calls){super("No available free routes responded within the bounded task budget");attempted=calls;}
    }
    private final ProviderMesh health;
    public MeshFailover(ProviderMesh health){this.health=health;}

    public Result execute(List<Route> configured,Transport transport,TimeSource time,Delay delay,
                          Events events) throws Exhausted,InterruptedException {
        final long started=time.nowMs();
        final long deadline=started+MAX_TASK_MS;
        int calls=0;
        List<Integer> order=health.order(started);
        List<Route> ready=new ArrayList<>();
        // The caller lists its explicitly configured choice first, then
        // public free routes. A route is never enabled without caller opt-in.
        for(Route route:configured)if(order.contains(route.id))ready.add(route);
        for(int index=0;index<ready.size();index++){
            Route route=ready.get(index);
            for(int attempt=1;attempt<=3;attempt++){
                long remaining=deadline-time.nowMs();
                if(remaining<=1200)throw new Exhausted(calls);
                calls++;
                long begin=time.nowMs();
                try{
                    events.update("[Mesh] Trying "+route.name+" (attempt "+attempt+"/3)");
                    String answer=transport.call(route.id,remaining);
                    if(answer==null||answer.isEmpty())throw new Failure(0,0,false);
                    health.success(route.id,Math.max(1,time.nowMs()-begin));
                    events.update("[Mesh] "+route.name+" responded · "+calls+" request(s)");
                    return new Result(answer,route.name,calls);
                }catch(Failure failure){
                    boolean canRetry=failure.temporary &&
                        (failure.status==0||failure.status==502||
                         failure.status==503||failure.status==504) && attempt<3;
                    if(canRetry){
                        long wait=attempt==1?1000L:2000L;
                        if(time.nowMs()+wait+1200<deadline){
                            events.update("[Mesh] "+route.name+" unavailable; retry in "+(wait/1000)+"s");
                            delay.waitMs(wait);
                            continue;
                        }
                    }
                    health.failure(route.id,failure.status,time.nowMs(),failure.retryAfterMs);
                    long cool=health.blockedFor(route.id,time.nowMs());
                    events.update("[Mesh] "+route.name+" "+failure.getMessage()+
                        " · cooldown "+(cool/1000)+"s"+
                        (index+1<ready.size()?" · switching provider":""));
                    break;
                }catch(RuntimeException bug){
                    // Never reveal endpoint, key, stack trace or user prompt in telemetry.
                    health.failure(route.id,0,time.nowMs(),0);
                    events.update("[Mesh] "+route.name+" failed internally · switching provider");
                    break;
                }
            }
        }
        throw new Exhausted(calls);
    }
}
