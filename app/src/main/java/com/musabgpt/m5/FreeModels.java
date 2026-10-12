package com.musabgpt.m5;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Direct HTTPS, free-first router. All public routes can be rate-limited or
 * unavailable. Official free-plan gateways are optional and user-keyed.
 */
public final class FreeModels {
    private static final ExecutorService NETWORK=Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private final SecureKeys keys;
    private final long[] publicCooldownUntil=new long[4];
    public interface Callback {
        void success(String text,String route);
        void error(String message);
    }
    private static final String[][] PUBLIC_ROUTES = {
        {"Kilo Free","https://api.kilo.ai/api/gateway/chat/completions","kilo-auto/free"},
        {"LLM7 Free","https://api.llm7.io/v1/chat/completions","default"},
        {"Pollinations Free","https://text.pollinations.ai/openai/chat/completions","openai-fast"},
        {"OVHcloud Free","https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions","Qwen3-Coder-30B-A3B-Instruct"}
    };
    public static final String[] FAMILIES = {
        "FreeLLMPool","GPT4Free","Free LLM Gateway","Free LLM API",
        "Groq Free (API key)","OpenRouter Free (API key)","Google AI Studio Free (API key)"
    };
    private static final String[] OFFICIAL_URLS = {
        "","","","",
        "https://api.groq.com/openai/v1/chat/completions",
        "https://openrouter.ai/api/v1/chat/completions",
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    };
    private static final String[] OFFICIAL_MODELS = {
        "","","","",
        "openai/gpt-oss-120b","openrouter/free","gemini-2.5-flash"
    };
    public FreeModels(Context context) {
        prefs=context.getSharedPreferences("m5-provider",Context.MODE_PRIVATE);
        keys=new SecureKeys(context);
    }
    public String getEndpoint(int family){
        if(family>=4&&family<FAMILIES.length)return OFFICIAL_URLS[family];
        return prefs.getString("url_"+family,"");
    }
    public String getModel(int family){
        String fallback=family>=4&&family<FAMILIES.length?OFFICIAL_MODELS[family]:"";
        return prefs.getString("model_"+family,fallback);
    }
    public boolean hasKey(int family){return keys.has(family);}
    public void configureKey(int family,String value) throws Exception {keys.put(family,value.trim());}
    public void configure(int family,String endpoint,String model) {
        if(family<1||family>=FAMILIES.length)throw new IllegalArgumentException("Invalid provider family");
        if(family>=4) {
            if(!OFFICIAL_URLS[family].equals(endpoint))
                throw new IllegalArgumentException("Official free provider URL cannot be changed; protects API keys");
        } else if(!endpoint.isEmpty() && (!endpoint.startsWith("https://")
            ||!endpoint.endsWith("/chat/completions")||endpoint.contains("@")
            ||endpoint.contains("#")||endpoint.length()>250)) {
            throw new IllegalArgumentException("Custom gateway must use HTTPS /chat/completions");
        }
        if(model.length()>120||model.trim().isEmpty()&&!endpoint.isEmpty())
            throw new IllegalArgumentException("Model identifier required");
        prefs.edit().putString("url_"+family,endpoint).putString("model_"+family,model).apply();
    }
    public void complete(JSONArray messages,int family,Callback callback) {
        NETWORK.execute(()->{
            StringBuilder errors=new StringBuilder();
            try{
                if(family>0&&family<FAMILIES.length&&!getEndpoint(family).isEmpty()){
                    try {
                        String secret=keys.get(family);
                        if(family>=4&&secret.isEmpty())throw new Exception("Add a free-tier API key in Providers");
                        String output=request(getEndpoint(family),getModel(family),messages,secret);
                        callback.success(output,FAMILIES[family]);return;
                    }catch(Exception e){errors.append(FAMILIES[family]).append(": ").append(e.getMessage()).append("; ");}
                }
                int attempted=0;
                for(int i=0;i<PUBLIC_ROUTES.length;i++){
                    if(SystemClock.elapsedRealtime()<publicCooldownUntil[i])continue;
                    attempted++;
                    String[] route=PUBLIC_ROUTES[i];
                    try{
                        String text=request(route[1],route[2],messages,"");
                        callback.success(text,route[0]);return;
                    }catch(ProviderError e){
                        // Honor rate limits instead of hammering dead or throttled free gateways.
                        long cooldown=e.code==429?600_000:e.code==401||e.code==403||e.code==404?1_800_000:60_000;
                        publicCooldownUntil[i]=SystemClock.elapsedRealtime()+cooldown;
                        errors.append(route[0]).append(": HTTP ").append(e.code).append("; ");
                    }catch(Exception e){
                        publicCooldownUntil[i]=SystemClock.elapsedRealtime()+120_000;
                        errors.append(route[0]).append(": ").append(e.getClass().getSimpleName()).append("; ");
                    }
                }
                if(attempted==0)errors.append("All public free routes are cooling down after rate limits or network errors.");
                callback.error("Free providers unavailable. "+errors.substring(0,Math.min(420,errors.length())));
            }catch(Exception e){callback.error("Free provider failure: "+e.getMessage());}
        });
    }
    private static final class ProviderError extends Exception {
        final int code;
        ProviderError(int code){super("HTTP "+code);this.code=code;}
    }
    private String request(String endpoint,String model,JSONArray messages,String key) throws Exception {
        URL url=new URL(endpoint);
        if(!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null)
            throw new IllegalArgumentException("Non-HTTPS gateway rejected");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(16000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Content-Type","application/json");
        if(!key.isEmpty())conn.setRequestProperty("Authorization","Bearer "+key);
        conn.setDoOutput(true);
        JSONObject body=new JSONObject();
        body.put("model",model.isEmpty()?"auto":model);
        body.put("messages",messages);
        body.put("stream",false);
        body.put("temperature",0.1);
        byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
        if(data.length>100000)throw new IllegalStateException("Task context exceeds 100 KB");
        try {
            try(OutputStream out=conn.getOutputStream()){out.write(data);}
            int response=conn.getResponseCode();
            if(response!=200)throw new ProviderError(response);
            if(conn.getContentLength()>250000)throw new IllegalStateException("Response too large");
            byte[] raw;
            try(InputStream input=conn.getInputStream()){
                java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
                byte[] part=new byte[4096];int n;
                while((n=input.read(part))!=-1){
                    buffer.write(part,0,n);
                    if(buffer.size()>250000)throw new IllegalStateException("Response limit reached");
                }
                raw=buffer.toByteArray();
            }
            JSONObject result=new JSONObject(new String(raw,StandardCharsets.UTF_8));
            JSONObject answer=result.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            String content=answer.optString("content","");
            if(!content.isEmpty())return content;
            JSONArray toolCalls=answer.optJSONArray("tool_calls");
            if(toolCalls!=null&&toolCalls.length()==1){
                JSONObject function=toolCalls.getJSONObject(0).optJSONObject("function");
                if(function!=null){
                    JSONObject args=new JSONObject(function.optString("arguments","{}"));
                    JSONObject normalized=new JSONObject()
                        .put("name",function.optString("name","")).put("arguments",args);
                    return new JSONObject().put("tool_calls",new JSONArray().put(normalized)).toString();
                }
            }
            throw new IllegalStateException("Empty model response");
        }finally{conn.disconnect();}
    }
}
