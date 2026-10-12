package com.musabgpt.m5;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Explicit bounded message envelope. Preserves the original source text
 * stored in the agent; truncates only copies sent to free inference APIs.
 * This is character budgeting, not an AST parser or a semantic embedding.
 */
public final class ContextBudget {
    private ContextBudget(){}
    public static final int MAX_TOTAL=58000;
    private static final int MAX_OBSERVATION=6000;
    public static JSONArray pack(JSONArray original){
        JSONArray result=new JSONArray();
        if(original==null||original.length()==0)return result;
        JSONObject system=original.optJSONObject(0);
        if(system!=null)result.put(message("system",shorten(system.optString("content",""),10000)));
        int remaining=MAX_TOTAL-(system==null?0:Math.min(10000,system.optString("content","").length()));
        java.util.ArrayList<JSONObject> reverse=new java.util.ArrayList<>();
        for(int i=original.length()-1;i>=1;i--){
            JSONObject item=original.optJSONObject(i);
            if(item==null)continue;
            String role=item.optString("role","");
            if(!role.equals("user")&&!role.equals("assistant"))continue;
            String text=item.optString("content","");
            boolean observation=text.startsWith("OBSERVED result");
            int limit=observation?MAX_OBSERVATION:9000;
            if(remaining<1500)break;
            String reduced=shorten(text,Math.min(limit,remaining));
            reverse.add(message(role,reduced));
            remaining-=reduced.length();
        }
        for(int i=reverse.size()-1;i>=0;i--)result.put(reverse.get(i));
        return result;
    }
    public static int estimateTokens(JSONArray messages){
        int chars=0;
        if(messages!=null)for(int i=0;i<messages.length();i++){
            JSONObject m=messages.optJSONObject(i);
            if(m!=null)chars+=m.optString("content","").length();
        }
        return (chars+3)/4; // heuristic only, not provider's real tokenizer.
    }
    private static JSONObject message(String role,String content){
        JSONObject o=new JSONObject();
        try{o.put("role",role).put("content",content);}
        catch(Exception ignored){}
        return o;
    }
    private static String shorten(String text,int limit){
        if(text.length()<=limit)return text;
        if(limit<55)return text.substring(0,Math.max(0,limit));
        String notice="\n[Context truncated; reread file if needed]";
        return text.substring(0,limit-notice.length())+notice;
    }
}
