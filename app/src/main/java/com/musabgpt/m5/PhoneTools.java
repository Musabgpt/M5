package com.musabgpt.m5;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

/** Actual Android launcher and opt-in Accessibility tools, executed on UI thread. */
public final class PhoneTools {
    private final Activity activity;
    public PhoneTools(Activity activity){this.activity=activity;}
    public boolean accessibilityReady(){return M5AccessibilityService.active()!=null;}
    public void requestAccessibilitySettings(){
        activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }
    private static String normalize(String s){
        return (s==null?"":s.trim().toLowerCase(Locale.ROOT)
            .replace("أ","ا").replace("إ","ا").replace("آ","ا").replace("ة","ه"));
    }
    public JSONObject apps() throws Exception {
        Intent launcher=new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm=activity.getPackageManager();
        List<ResolveInfo> matches=pm.queryIntentActivities(launcher,0);
        JSONArray out=new JSONArray();
        int count=0;
        for(ResolveInfo app:matches){
            if(count++>=80)break;
            out.put(new JSONObject().put("app",String.valueOf(app.loadLabel(pm)))
                .put("package",app.activityInfo.packageName));
        }
        return new JSONObject().put("executed",true).put("apps",out).put("partial",matches.size()>80);
    }
    public JSONObject open(String requested) throws Exception {
        String query=normalize(requested);
        if(query.length()==0||query.length()>120)throw new IllegalArgumentException("App name required");
        PackageManager pm=activity.getPackageManager();
        String pkg=null;
        if(query.contains("واتساب")||query.contains("واتس")||query.contains("whatsapp")){
            pkg="com.whatsapp";
            if(pm.getLaunchIntentForPackage(pkg)==null)pkg="com.whatsapp.w4b";
        } else if(query.contains("كروم")||query.contains("chrome"))pkg="com.android.chrome";
        else if(query.contains("يوتيوب")||query.contains("youtube"))pkg="com.google.android.youtube";
        else if(query.contains("تليجرام")||query.contains("تيليجرام")||query.contains("telegram"))pkg="org.telegram.messenger";
        else if(query.contains("اعدادات")||query.contains("settings")){
            activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
            return new JSONObject().put("executed",true).put("app","Android Settings");
        }
        if(pkg==null){
            Intent launcher=new Intent(Intent.ACTION_MAIN);
            launcher.addCategory(Intent.CATEGORY_LAUNCHER);
            for(ResolveInfo item:pm.queryIntentActivities(launcher,0)){
                String label=normalize(String.valueOf(item.loadLabel(pm)));
                String found=normalize(item.activityInfo.packageName);
                if(label.equals(query)||found.equals(query)){pkg=item.activityInfo.packageName;break;}
                if(pkg==null&&(label.contains(query)||query.contains(label)))pkg=item.activityInfo.packageName;
            }
        }
        if(pkg==null)throw new IllegalStateException("App not found: "+requested);
        Intent intent=pm.getLaunchIntentForPackage(pkg);
        if(intent==null)throw new IllegalStateException("App is unavailable or hidden: "+pkg);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        return new JSONObject().put("executed",true).put("app",requested).put("package",pkg);
    }
    public JSONObject run(String tool,JSONObject args) throws Exception {
        if("phone_apps".equals(tool))return apps();
        if("phone_open".equals(tool))return open(args.optString("app",""));
        M5AccessibilityService svc=M5AccessibilityService.active();
        if(svc==null)throw new IllegalStateException(
            "Enable M5 Native accessibility service manually in Android Settings; the app cannot grant itself this access.");
        if("phone_observe".equals(tool))return svc.observe();
        if("phone_click".equals(tool))return svc.click(args.getInt("id"));
        if("phone_type".equals(tool))return svc.type(args.getInt("id"),args.getString("text"));
        if("phone_scroll".equals(tool))return svc.scroll(args.getInt("id"),
            !"backward".equals(args.optString("direction","forward")));
        if("phone_back".equals(tool))return svc.global("back");
        if("phone_home".equals(tool))return svc.global("home");
        throw new IllegalArgumentException("Unsupported phone action: "+tool);
    }
}
