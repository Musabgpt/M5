package com.musabgpt.m5;

import java.util.Locale;

/** Offline capability routing, deliberately strict to avoid false completion. */
public final class OfflineFallback {
    public static final class Plan {
        public final String preset;
        public final String projectPrefix;
        public final String explanation;
        private Plan(String preset,String prefix,String explanation){
            this.preset=preset;this.projectPrefix=prefix;this.explanation=explanation;
        }
    }
    private OfflineFallback(){}
    public static Plan match(String request){
        if(request==null||request.length()>700)return null;
        String s=request.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ");
        boolean create=s.matches("(?s).*(أنشئ|انشئ|اصنع|ابن|اكتب|سوّي|سوي|إنشاء|عمل|create|build|generate|make|write).*");
        if(!create || s.contains("لا تكتب") || s.contains("لا تنشئ") ||
            s.contains("do not create") || s.contains("don\'t create"))return null;
        boolean calculator=s.matches("(?s).*(حاسبة|آلة حاسبة|اله حاسبه|calculator).*");
        boolean web=s.matches("(?s).*(html|javascript|ويب|web|متصفح|browser).*");
        if(calculator&&web)
            return new Plan("web-calculator","FreeCalculator",
                "Creates a basic offline HTML/CSS/JavaScript calculator; does not implement other requested features.");
        boolean starter=s.matches("(?s).*(مشروع|قالب|starter|project|template).*");
        if(starter&&(s.contains("python")||s.contains("بايثون")))
            return new Plan("python","PythonStarter",
                "Creates a simple Python starter, not a complete custom application.");
        if(starter&&(s.contains("node.js")||s.contains("nodejs")||s.contains("نود")))
            return new Plan("node","NodeStarter",
                "Creates a simple Node.js starter, not a complete custom application.");
        return null;
    }
}
