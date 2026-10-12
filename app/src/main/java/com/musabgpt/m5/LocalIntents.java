package com.musabgpt.m5;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic, model-free Android app launch parsing. No action chaining. */
public final class LocalIntents {
    private LocalIntents(){}
    private static final Pattern SIMPLE_OPEN=Pattern.compile(
        "^\\s*(?:افتح|افتحلي|شغل|شغّل|open|launch)\\s+([\\p{L}\\p{N} ._-]{2,90})\\s*$",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    private static final Pattern SIMPLE_URL=Pattern.compile(
        "^\\s*(?:افتح|افتحلي|open|browse|زور|تصفح)\\s+(https://\\S{5,990})\\s*$",
        Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
    public static String urlToOpen(String request){
        if(request==null)return null;
        Matcher m=SIMPLE_URL.matcher(request);
        return m.matches()?m.group(1):null;
    }
    public static String appToOpen(String request){
        if(request==null)return null;
        Matcher m=SIMPLE_OPEN.matcher(request);
        if(!m.matches())return null;
        String app=m.group(1).trim();
        if(app.matches("(?is).*(ثم|بعدها|and then|وابعث|وارسل|و ارسل).*"))return null;
        return app;
    }
}
