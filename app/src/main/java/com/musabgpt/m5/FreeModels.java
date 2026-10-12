package com.musabgpt.m5;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.io.InterruptedIOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayDeque;
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
    // 0–3 anonymous routes; 5–10 optional, explicitly configured families.
    private final ProviderMesh mesh=new ProviderMesh(12);
    private final MeshFailover orchestrator=new MeshFailover(mesh);
    private final ArrayDeque<String> recentEvents=new ArrayDeque<>();
    private volatile String lastActiveRoute="none";
    public interface Callback {
        void success(String text,String route);
        void error(String message);
        default void status(String event){}
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
    private static final class Endpoint {
        final String label,url,model,key;
        Endpoint(String label,String url,String model,String key){
            this.label=label;this.url=url;this.model=model;this.key=key;
        }
    }
    private synchronized void record(String event){
        if(event==null)return;
        recentEvents.addLast(event.substring(0,Math.min(180,event.length())));
        while(recentEvents.size()>18)recentEvents.removeFirst();
    }
    public void complete(JSONArray messages,int family,Callback callback) {
        // Context is serialized ONCE. No failover transition can alter this payload.
        final String payload;
        try{
            JSONObject requestBody=new JSONObject()
                .put("model","placeholder") // overwritten per provider, context never changes
                .put("messages",messages)
                .put("stream",false)
                .put("temperature",0.1);
            payload=requestBody.toString();
        }catch(Exception error){callback.error("Could not prepare request");return;}
        NETWORK.execute(()->{
            final long started=SystemClock.elapsedRealtime();
            List<MeshFailover.Route> enabled=new ArrayList<>();
            Map<Integer,Endpoint> endpoints=new LinkedHashMap<>();
            if(family>0&&family<FAMILIES.length&&!getEndpoint(family).isEmpty()){
                try {
                    String token=keys.get(family);
                    if(family<4||!token.isEmpty()){
                        int id=4+family;
                        enabled.add(new MeshFailover.Route(id,FAMILIES[family]));
                        endpoints.put(id,new Endpoint(FAMILIES[family],getEndpoint(family),getModel(family),token));
                    }else record("[Mesh] Selected free route requires a user-supplied free API key");
                }catch(Exception ignored){record("[Mesh] Encrypted key unavailable; using public alternatives");}
            }
            List<Integer> order=mesh.order(started);
            for(int id:order)if(id>=0&&id<PUBLIC_ROUTES.length){
                String[] route=PUBLIC_ROUTES[id];
                enabled.add(new MeshFailover.Route(id,route[0]));
                endpoints.put(id,new Endpoint(route[0],route[1],route[2],""));
            }
            final MeshFailover.Events events=event->{
                record(event);
                try{callback.status(event);}catch(RuntimeException ignored){}
            };
            try{
                MeshFailover.Result result=orchestrator.execute(enabled,
                    (id,remaining)->{
                        Endpoint endpoint=endpoints.get(id);
                        if(endpoint==null)throw new MeshFailover.Failure(0,0,false);
                        try{return request(endpoint.url,endpoint.model,payload,endpoint.key,remaining);}
                        catch(ProviderError problem){
                            throw new MeshFailover.Failure(problem.code,problem.retryAfterMs,
                                problem.code==502||problem.code==503||problem.code==504);
                        }catch(SocketTimeoutException|ConnectException|UnknownHostException|SocketException exception){
                            throw new MeshFailover.Failure(0,0,true);
                        }catch(InterruptedIOException exception){
                            throw new MeshFailover.Failure(0,0,true);
                        }catch(Exception exception){throw new MeshFailover.Failure(0,0,false);}
                    },SystemClock::elapsedRealtime,Thread::sleep,events);
                lastActiveRoute=result.route;
                callback.success(result.text,result.route);
            }catch(MeshFailover.Exhausted unavailable){
                events.update("[Mesh] All eligible free routes exhausted or cooling down");
                callback.error("Free-tier providers unavailable (tried "+unavailable.attempted+
                    " request(s)). Local deterministic fallback may be available.");
            }catch(InterruptedException stopped){
                Thread.currentThread().interrupt();
                callback.error("Model request interrupted; no result was accepted");
            }catch(RuntimeException unexpected){
                record("[Mesh] Inference engine failed safely");
                callback.error("Free inference could not complete; no project action was executed.");
            }
        });
    }
    public synchronized String meshStatus(){
        long now=SystemClock.elapsedRealtime();
        StringBuilder sb=new StringBuilder("Free provider mesh (observed only)\n");
        sb.append("Last successful route: ").append(lastActiveRoute).append('\n');
        for(int i=0;i<PUBLIC_ROUTES.length;i++){
            sb.append(PUBLIC_ROUTES[i][0]).append(": ")
              .append(mesh.blockedFor(i,now)>0 ?
                  "cooldown "+(mesh.blockedFor(i,now)/1000)+"s" : "eligible")
              .append(" · success ").append(mesh.successes(i))
              .append(" · failures ").append(mesh.failures(i))
              .append(" · score ").append(String.format(java.util.Locale.ROOT,"%.1f",mesh.score(i)))
              .append('\n');
        }
        for(int family=1;family<FAMILIES.length;family++){
            int index=4+family;
            if(!getEndpoint(family).isEmpty()){
                sb.append(FAMILIES[family]).append(": ")
                    .append(mesh.blockedFor(index,now)>0 ?
                        "cooldown "+mesh.blockedFor(index,now)/1000+"s":"eligible")
                    .append(" · successes ").append(mesh.successes(index)).append('\n');
            }
        }
        sb.append("\nRecent routing events:\n");
        for(String event:recentEvents)sb.append(event).append('\n');
        return sb.toString();
    }
    private static final class ProviderError extends Exception {
        final int code;
        final long retryAfterMs;
        ProviderError(int code,long retryAfterMs){
            super("HTTP "+code);this.code=code;this.retryAfterMs=retryAfterMs;
        }
    }
    private static long retryAfter(String header){
        if(header==null||header.isEmpty())return 0;
        try{
            long seconds=Long.parseLong(header.trim());
            return Math.max(0,Math.min(3_600_000L,seconds*1000L));
        }catch(NumberFormatException ignored){}
        try{
            long delay=java.time.Duration.between(java.time.ZonedDateTime.now(),
                java.time.ZonedDateTime.parse(header,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME))
                .toMillis();
            return Math.max(0,Math.min(3_600_000L,delay));
        }catch(Exception ignored){return 0;}
    }
    private String request(String endpoint,String model,String serialized,String key,long remainingMs) throws Exception {
        URL url=new URL(endpoint);
        if(!"https".equalsIgnoreCase(url.getProtocol())||url.getUserInfo()!=null)
            throw new IllegalArgumentException("Non-HTTPS gateway rejected");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout((int)Math.min(4500,Math.max(1000,remainingMs)));
        conn.setReadTimeout((int)Math.min(8500,Math.max(1000,remainingMs)));
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Content-Type","application/json");
        if(!key.isEmpty())conn.setRequestProperty("Authorization","Bearer "+key);
        conn.setDoOutput(true);
        // Create a provider-specific envelope without changing the frozen conversation.
        JSONObject body=new JSONObject(serialized);
        body.put("model",model.isEmpty()?"auto":model);
        byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
        if(data.length>100000)throw new IllegalStateException("Task context exceeds 100 KB");
        try {
            try(OutputStream out=conn.getOutputStream()){out.write(data);}
            int response=conn.getResponseCode();
            if(response!=200)throw new ProviderError(response,retryAfter(conn.getHeaderField("Retry-After")));
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
