package com.musabgpt.m5;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Direct HTTPS model client inside native Android. Never uses localhost or an M5 server. */
public final class FreeModels {
    private static final ExecutorService NETWORK = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    public interface Callback {
        void success(String text, String route);
        void error(String message);
    }
    private static final String[][] FREE_ROUTES = {
        {"Kilo Free", "https://api.kilo.ai/api/gateway/chat/completions", "kilo-auto/free"},
        {"LLM7 Free", "https://api.llm7.io/v1/chat/completions", "default"},
        {"Pollinations Free", "https://text.pollinations.ai/openai/chat/completions", "openai-fast"},
        {"OVHcloud Free", "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/chat/completions", "Qwen3-Coder-30B-A3B-Instruct"}
    };
    public static final String[] FAMILIES = {"FreeLLMPool", "GPT4Free", "Free LLM Gateway", "Free LLM API"};

    public FreeModels(Context context) { prefs=context.getSharedPreferences("m5-provider",Context.MODE_PRIVATE); }

    public String getEndpoint(int family) { return prefs.getString("url_"+family, ""); }
    public String getModel(int family) { return prefs.getString("model_"+family, ""); }
    public void configure(int family,String endpoint,String model) {
        if(family<1||family>=FAMILIES.length)throw new IllegalArgumentException("Only optional gateway families can be customized");
        if(!endpoint.isEmpty() && (!endpoint.startsWith("https://") || !endpoint.endsWith("/chat/completions")
            || endpoint.contains("@") || endpoint.contains("#") || endpoint.length()>250))
            throw new IllegalArgumentException("Custom gateway must be an HTTPS /chat/completions endpoint");
        if(model.length()>120)throw new IllegalArgumentException("Model name too long");
        prefs.edit().putString("url_"+family,endpoint).putString("model_"+family,model).apply();
    }
    public void complete(JSONArray messages,int family,Callback callback) {
        NETWORK.execute(() -> {
            StringBuilder errors=new StringBuilder();
            try {
                if(family>0 && family<FAMILIES.length && !getEndpoint(family).isEmpty()) {
                    try { JSONObject data=request(getEndpoint(family),getModel(family),messages);
                        callback.success(data.getString("text"),FAMILIES[family]); return;
                    }catch(Exception e){errors.append(FAMILIES[family]).append(": ").append(e.getMessage()).append("; ");}
                }
                for(String[] route:FREE_ROUTES) {
                    try { JSONObject data=request(route[1],route[2],messages);
                        callback.success(data.getString("text"),route[0]); return;
                    }catch(Exception e){errors.append(route[0]).append(": ").append(e.getMessage()).append("; ");}
                }
                callback.error("No free HTTPS model responded. "+errors.toString().substring(0,Math.min(320,errors.length())));
            }catch(Exception e){callback.error("Model request failed: "+e.getMessage());}
        });
    }

    private JSONObject request(String endpoint,String model,JSONArray messages) throws Exception {
        URL url=new URL(endpoint);
        if(!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo()!=null) throw new Exception("Untrusted URL");
        HttpURLConnection conn=(HttpURLConnection)url.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(18000);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("Content-Type","application/json");
        conn.setDoOutput(true);
        JSONObject request=new JSONObject();
        request.put("model",model.isEmpty()?"auto":model);
        request.put("messages",messages);
        request.put("stream",false);
        request.put("temperature",0.1);
        byte[] data=request.toString().getBytes(StandardCharsets.UTF_8);
        if(data.length>100000)throw new Exception("Model context too large");
        try(OutputStream out=conn.getOutputStream()){out.write(data);}
        int code=conn.getResponseCode();
        if(code!=200)throw new Exception("HTTP "+code);
        int length=conn.getContentLength();
        if(length>250000)throw new Exception("Response exceeds limit");
        byte[] raw;
        try(InputStream input=conn.getInputStream()){
            java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
            byte[] part=new byte[4096];int count;
            while((count=input.read(part))!=-1){buffer.write(part,0,count);if(buffer.size()>250000)throw new Exception("Response too large");}
            raw=buffer.toByteArray();
        }finally{conn.disconnect();}
        JSONObject body=new JSONObject(new String(raw,StandardCharsets.UTF_8));
        JSONObject message=body.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        String content=message.optString("content","");
        // Native text-only route; do not invent tool results if a provider responds with unsupported tool_calls.
        if(content.isEmpty() && message.has("tool_calls"))throw new Exception("Provider returned native tool_calls without text content");
        if(content.isEmpty())throw new Exception("Empty assistant content");
        return new JSONObject().put("text",content);
    }
}
