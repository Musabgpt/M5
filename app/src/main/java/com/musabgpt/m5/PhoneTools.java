package com.musabgpt.m5;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
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
    public JSONObject openUrl(String requested) throws Exception {
        if(requested==null||requested.length()>1000)throw new IllegalArgumentException("URL too long");
        Uri uri=Uri.parse(requested);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getHost().isEmpty())
            throw new IllegalArgumentException("Only HTTPS websites can be opened");
        activity.startActivity(new Intent(Intent.ACTION_VIEW,uri));
        return new JSONObject().put("executed",true).put("action","browser_open").put("host",uri.getHost());
    }
    public JSONObject dial(String requested) throws Exception {
        if(requested==null||!requested.matches("[+0-9 ()-]{2,35}"))
            throw new IllegalArgumentException("Invalid telephone number");
        String digits=requested.replaceAll("[^+0-9]","");
        activity.startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+digits)));
        // Opens the dialer UI; does NOT place the call.
        return new JSONObject().put("executed",true).put("action","dialer_draft");
    }
    public JSONObject smsDraft(String number,String body) throws Exception {
        if(number==null||!number.matches("[+0-9 ()-]{2,35}")||body==null||body.length()>700)
            throw new IllegalArgumentException("Invalid SMS draft");
        Intent intent=new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:"+number.replaceAll("[^+0-9]","")));
        intent.putExtra("sms_body",body);
        activity.startActivity(intent);
        // Only creates a draft. User must manually confirm Send in their SMS app.
        return new JSONObject().put("executed",true).put("action","sms_draft_created")
            .put("sent",false);
    }
    public JSONObject run(String tool,JSONObject args) throws Exception {
        if("phone_apps".equals(tool))return apps();
        if("phone_open".equals(tool))return open(args.optString("app",""));
        if("phone_open_url".equals(tool))return openUrl(args.optString("url",""));
        if("phone_dial".equals(tool))return dial(args.optString("number",""));
        if("phone_sms_draft".equals(tool))return smsDraft(args.optString("number",""),args.optString("text",""));
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
