package com.musabgpt.m5;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class ContextBudgetTest {
    private JSONObject item(String role,String content)throws Exception{
        return new JSONObject().put("role",role).put("content",content);
    }
    @Test public void limitsObservationsWhilePreservingSystemAndRecentGoal()throws Exception{
        JSONArray full=new JSONArray();
        full.put(item("system","You are an authorized coding assistant."));
        full.put(item("user","Build a project"));
        for(int i=0;i<30;i++){
            full.put(item("user","OBSERVED result of read_file: "+"x".repeat(11000)));
        }
        full.put(item("user","Recent goal must survive"));
        JSONArray packed=ContextBudget.pack(full);
        assertTrue(packed.length()<full.length());
        assertTrue(ContextBudget.estimateTokens(packed)<=15000);
        assertEquals("system",packed.getJSONObject(0).getString("role"));
        assertTrue(packed.getJSONObject(packed.length()-1).getString("content").contains("Recent goal"));
        assertTrue(full.length()>packed.length()); // original agent history remains intact
    }
    @Test public void rejectsLargeOldObservationsWithoutClaimingSemanticCompression()throws Exception{
        JSONArray messages=new JSONArray();
        messages.put(item("system","Rules"));
        messages.put(item("user","OBSERVED result of tool: "+"y".repeat(40000)));
        JSONArray packed=ContextBudget.pack(messages);
        assertTrue(packed.getJSONObject(1).getString("content").length()<=6000);
    }
}
