package com.musabgpt.m5;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Model text -> one validated M5 tool call, supporting both free-provider styles
 * {"tool_calls":[{"name":"run_command","arguments":{"command":"..."}}]}
 * {"action":"run_command","command":"..."}
 * and markdown fenced JSON. Parsing never executes commands.
 */
public final class AgentProtocol {
    private AgentProtocol() {}

    public static final class Result {
        public final JSONObject call;
        public final String error;
        Result(JSONObject call, String error) { this.call=call; this.error=error; }
    }
    public static Result parse(String response) {
        if (response == null || response.length() > 40000) return new Result(null,"Model output is missing or oversized");
        String raw=response.trim();
        final String fence=new String(new char[]{96,96,96});
        if (raw.startsWith(fence)) {
            int end=raw.lastIndexOf(fence);
            int newline=raw.indexOf('\n');
            if (newline<0 || end<=newline) return new Result(null,"Incomplete fenced JSON tool call");
            raw=raw.substring(newline+1,end).trim();
        }
        if (!raw.startsWith("{")) return new Result(null,null);
        JSONObject body;
        try { body=new JSONObject(raw); }
        catch (Exception e) {
            return raw.contains("tool_calls") || raw.contains("\"action\"")
                    ? new Result(null,"Malformed model tool JSON; nothing was executed") : new Result(null,null);
        }
        try {
            JSONObject tool=null,arguments;
            if(body.has("tool_calls")) {
                JSONArray calls=body.optJSONArray("tool_calls");
                if(calls==null || calls.length()!=1) return new Result(null,"Expected exactly one tool call");
                tool=calls.optJSONObject(0);
                if(tool==null)return new Result(null,"Tool call is not an object");
            } else if(body.has("action") || body.has("tool") || body.has("name")) {
                tool=body;
            } else return new Result(null,null);
            String name=tool.optString("name",tool.optString("action",tool.optString("tool",""))).trim();
            if(tool.optJSONObject("function")!=null) {
                JSONObject fn=tool.getJSONObject("function");
                name=fn.optString("name",name);
                Object value=fn.opt("arguments");
                if(value instanceof JSONObject) arguments=(JSONObject)value;
                else if(value instanceof String) arguments=new JSONObject((String)value);
                else arguments=new JSONObject();
            } else {
                Object a=tool.has("arguments")?tool.opt("arguments"):tool.opt("args");
                if(a instanceof JSONObject) arguments=(JSONObject)a;
                else if(a instanceof String) arguments=new JSONObject((String)a);
                else {
                    arguments=new JSONObject();
                    for(String k:new String[]{"path","query","command","content","expectedHash","summary"}) {
                        if(tool.has(k))arguments.put(k,tool.get(k));
                    }
                }
            }
            if(!name.matches("list_files|read_file|search_code|write_file|run_command|git_status|git_diff|finish"))
                return new Result(null,"Unsupported model tool: "+name);
            JSONObject normalized=new JSONObject();
            normalized.put("name",name);
            normalized.put("arguments",arguments);
            return new Result(normalized,null);
        } catch(Exception e) {
            return new Result(null,"Unrecognized model tool arguments; no command was executed");
        }
    }
}
