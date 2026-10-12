package com.musabgpt.m5;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative fallback when free LLMs return code fences rather than tool_calls.
 * Converts only recognized source-code languages into new workspace files.
 * Never executes the result, never overwrites existing files and never auto-approves.
 */
public final class CodeArtifacts {
    private CodeArtifacts(){}
    public static final class Artifact {
        public final String path,content;
        Artifact(String path,String content){this.path=path;this.content=content;}
    }
    private static final Map<String,String> NAMES=new LinkedHashMap<>();
    static{
        NAMES.put("html","index.html");
        NAMES.put("css","style.css");
        NAMES.put("javascript","app.js");
        NAMES.put("js","app.js");
        NAMES.put("typescript","app.ts");
        NAMES.put("ts","app.ts");
        NAMES.put("python","main.py");
        NAMES.put("py","main.py");
        NAMES.put("json","data.json");
        NAMES.put("markdown","README.md");
        NAMES.put("md","README.md");
    }
    public static ArrayList<Artifact> extract(String response){
        ArrayList<Artifact> output=new ArrayList<>();
        if(response==null||response.length()>75000)return output;
        String fence=new String(new char[]{96,96,96});
        Pattern pattern=Pattern.compile("(?s)"+Pattern.quote(fence)
            +"([A-Za-z0-9+#._-]*)[^\\n]*\\n(.*?)\\n"+Pattern.quote(fence));
        Matcher m=pattern.matcher(response);
        LinkedHashMap<String,String> unique=new LinkedHashMap<>();
        while(m.find()&&unique.size()<4){
            String lang=m.group(1).trim().toLowerCase(java.util.Locale.ROOT);
            String path=NAMES.get(lang);
            String content=m.group(2).trim();
            if(path==null||content.length()<16||content.length()>28000)continue;
            if(content.indexOf('\u0000')>=0)continue;
            // Multiple blocks with the same language are ambiguous. Do not merge or overwrite.
            if(unique.containsKey(path))return new ArrayList<>();
            unique.put(path,content+"\n");
        }
        if(unique.isEmpty()){
            String body=response.trim();
            String lower=body.toLowerCase(java.util.Locale.ROOT);
            if(body.length()>=60&&body.length()<28000&&
                (lower.startsWith("<!doctype html")||lower.startsWith("<html"))&&
                lower.contains("</html>")){
                unique.put("index.html",body+"\n");
            }
        }
        for(Map.Entry<String,String> e:unique.entrySet())
            output.add(new Artifact(e.getKey(),e.getValue()));
        return output;
    }
}
