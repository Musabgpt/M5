package com.musabgpt.m5;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class MeshFailoverTest {
    private static final class VirtualTime implements MeshFailover.TimeSource,MeshFailover.Delay {
        long clock=1000;
        final List<Long> pauses=new ArrayList<>();
        @Override public long nowMs(){return clock;}
        @Override public void waitMs(long ms){clock+=ms;pauses.add(ms);}
    }
    @Test public void http429SwitchesImmediatelyAndHonorsRetryAfter() throws Exception {
        ProviderMesh health=new ProviderMesh(2);
        VirtualTime time=new VirtualTime();
        MeshFailover engine=new MeshFailover(health);
        ArrayList<String> trace=new ArrayList<>();
        AtomicInteger firstCalls=new AtomicInteger();
        MeshFailover.Result done=engine.execute(
            List.of(new MeshFailover.Route(0,"A"),new MeshFailover.Route(1,"B")),
            (id,ms)->{
                if(id==0){firstCalls.incrementAndGet();throw new MeshFailover.Failure(429,75_000,false);}
                return "unchanged response";},time,time,trace::add);
        assertEquals("B",done.route);
        assertEquals(2,done.calls);
        assertEquals(1,firstCalls.get());
        assertTrue(time.pauses.isEmpty());
        assertTrue(health.blockedFor(0,time.nowMs())>=75_000);
        assertTrue(trace.stream().anyMatch(s->s.contains("switching provider")));
    }
    @Test public void retry503OneAndTwoSecondsThenFailsOver() throws Exception{
        ProviderMesh health=new ProviderMesh(2);
        VirtualTime time=new VirtualTime();
        ArrayList<String> trace=new ArrayList<>();
        AtomicInteger calls=new AtomicInteger();
        MeshFailover.Result result=new MeshFailover(health).execute(
            List.of(new MeshFailover.Route(0,"overloaded"),new MeshFailover.Route(1,"backup")),
            (id,remaining)->{
                if(id==0){calls.incrementAndGet();throw new MeshFailover.Failure(503,0,true);}
                return "OK";},time,time,trace::add);
        assertEquals(3,calls.get());
        assertEquals(List.of(1000L,2000L),time.pauses);
        assertEquals("backup",result.route);
        assertEquals(4,result.calls);
        assertEquals(1,health.failures(0));
    }
    @Test public void socketTimeoutUsesFiniteBudgetThenExhausts()throws Exception{
        ProviderMesh health=new ProviderMesh(1);
        VirtualTime time=new VirtualTime();
        try{
            new MeshFailover(health).execute(List.of(new MeshFailover.Route(0,"timeout")),
                (id,budget)->{time.clock+=30_000;throw new MeshFailover.Failure(0,0,true);},
                time,time,event->{});
            fail("No provider could succeed");
        }catch(MeshFailover.Exhausted expected){
            assertTrue(expected.attempted<=3);
            assertTrue(time.clock<100_000);
        }
    }
    @Test public void hard404IsNotRetriedAndAvailableRoutesAreEligibleOnly()throws Exception{
        ProviderMesh health=new ProviderMesh(2);
        VirtualTime time=new VirtualTime();
        new MeshFailover(health).execute(
            List.of(new MeshFailover.Route(0,"removed"),new MeshFailover.Route(1,"working")),
            (id,remaining)->{if(id==0)throw new MeshFailover.Failure(404,0,false);return "yes";},
            time,time,event->{});
        assertEquals(3_600_000,health.blockedFor(0,time.nowMs()));
        assertEquals(List.of(1),health.order(time.nowMs()));
    }
}
