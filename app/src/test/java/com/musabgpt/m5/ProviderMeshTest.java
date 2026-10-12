package com.musabgpt.m5;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class ProviderMeshTest {
    @Test public void rateLimitAndUnavailableProvidersAreActuallySkipped(){
        ProviderMesh mesh=new ProviderMesh(3);
        assertEquals(List.of(0,1,2),mesh.order(1000));
        mesh.failure(0,429,1000);
        assertEquals(List.of(1,2),mesh.order(1001));
        assertTrue(mesh.blockedFor(0,1001)>590000);
        mesh.failure(1,503,1000);
        assertEquals(List.of(2),mesh.order(1001));
        mesh.success(2,120);
        assertEquals(List.of(2,0,1),mesh.order(601100));
    }
    @Test public void latencyAndAvailabilityDetermineRanking(){
        ProviderMesh mesh=new ProviderMesh(2);
        mesh.success(0,2200);
        mesh.success(1,90);
        assertEquals(Integer.valueOf(1),mesh.order(100).get(0));
        mesh.failure(1,502,100);
        assertEquals(List.of(0),mesh.order(200));
        assertTrue(mesh.failures(1)==1);
    }
    @Test public void transientFailuresUseFiniteExponentialBackoff(){
        ProviderMesh mesh=new ProviderMesh(1);
        for(int i=0;i<20;i++)mesh.failure(0,0,1000);
        assertTrue(mesh.blockedFor(0,1000)<=300000);
        mesh.success(0,1);
        assertEquals(0,mesh.blockedFor(0,1000));
        assertEquals(1,mesh.successes(0));
    }
}
