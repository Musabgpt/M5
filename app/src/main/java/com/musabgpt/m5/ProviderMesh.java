package com.musabgpt.m5;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Health-aware provider scoring and circuit breaker; no claim of unlimited
 * free quota or guaranteed 150ms network failover.
 * The caller owns transport and invokes this policy with monotonic time.
 */
public final class ProviderMesh {
    private static final class Node {
        double latencyMs=2500.0;
        int successes=0;
        int failures=0;
        int consecutiveFailures=0;
        long blockedUntilMs=0;
    }
    private final Node[] nodes;
    public ProviderMesh(int count) {
        if(count<1||count>32)throw new IllegalArgumentException("Unsupported route count");
        nodes=new Node[count];
        for(int i=0;i<count;i++)nodes[i]=new Node();
    }
    public synchronized List<Integer> order(long nowMs){
        ArrayList<Integer> result=new ArrayList<>();
        for(int i=0;i<nodes.length;i++)
            if(nodes[i].blockedUntilMs<=nowMs)result.add(i);
        result.sort(Comparator.comparingDouble((Integer i)->score(i)).reversed()
            .thenComparingInt(Integer::intValue));
        return result;
    }
    public synchronized void success(int index,long latencyMillis){
        Node n=nodes[index];
        long delay=Math.max(1,Math.min(latencyMillis,180_000));
        n.latencyMs=n.successes==0?delay:0.72*n.latencyMs+0.28*delay;
        n.successes++;n.consecutiveFailures=0;n.blockedUntilMs=0;
    }
    public synchronized void failure(int index,int status,long nowMs){
        failure(index,status,nowMs,0L);
    }
    public synchronized void failure(int index,int status,long nowMs,long retryAfterMs){
        Node n=nodes[index];n.failures++;n.consecutiveFailures++;
        long wait;
        if(status==429)wait=Math.max(30_000L,Math.min(3_600_000L,retryAfterMs));
        else if(status==503||status==502||status==504)
            wait=Math.max(30_000L,Math.min(300_000L,retryAfterMs));
        else if(status==401||status==403||status==404)wait=3_600_000L;
        else wait=Math.min(300_000L,20_000L*(1L<<Math.min(n.consecutiveFailures-1,4)));
        n.blockedUntilMs=Math.max(n.blockedUntilMs,nowMs+wait);
    }
    public synchronized double score(int index){
        Node n=nodes[index];
        double reliability=(n.successes+1.0)/(n.successes+n.failures+2.0);
        return reliability*100.0-(Math.log1p(n.latencyMs)/Math.log(10))*5.0
            -n.consecutiveFailures*4.0;
    }
    public synchronized long blockedFor(int index,long nowMs){
        return Math.max(0,nodes[index].blockedUntilMs-nowMs);
    }
    public synchronized int successes(int index){return nodes[index].successes;}
    public synchronized int failures(int index){return nodes[index].failures;}
}
