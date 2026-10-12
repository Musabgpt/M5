package com.musabgpt.m5;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Opt-in, on-demand Android UI automation. No background text harvesting.
 * Android accessibility must be enabled by the user in system Settings.
 */
public final class M5AccessibilityService extends AccessibilityService {
    private static volatile M5AccessibilityService active;
    private final Map<Integer,AccessibilityNodeInfo> nodes = new LinkedHashMap<>();
    private String observedPackage="";
    private long observedAt=0;

    public static M5AccessibilityService active() { return active; }
    @Override public void onServiceConnected() { active=this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally no event logging, clipboard monitoring, or remote uploads.
    }
    @Override public void onInterrupt() {}
    @Override public void onDestroy() {
        if(active==this)active=null;
        nodes.clear();super.onDestroy();
    }

    public JSONObject observe() throws Exception {
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)throw new IllegalStateException("No accessible foreground window. Open the app first.");
        String pkg=String.valueOf(root.getPackageName());
        if(pkg.startsWith("com.musabgpt.m5"))throw new IllegalStateException("M5 cannot recursively control itself");
        JSONArray array=new JSONArray();
        nodes.clear();
        observedPackage=pkg;
        observedAt=android.os.SystemClock.uptimeMillis();
        visit(root,array,0);
        return new JSONObject().put("executed",true).put("package",pkg).put("elements",array)
            .put("partial",nodes.size()>=75).put("note","Visible accessibility elements only, no screenshot or hidden data");
    }
    private void visit(AccessibilityNodeInfo node,JSONArray out,int depth) throws Exception {
        if(node==null||depth>13||nodes.size()>=75)return;
        final boolean useful=node.isClickable()||node.isEditable()||node.isScrollable()
            ||(node.getText()!=null&&node.getText().length()>0)
            ||(node.getContentDescription()!=null&&node.getContentDescription().length()>0);
        if(useful){
            int id=nodes.size()+1;
            nodes.put(id,node);
            JSONObject item=new JSONObject();
            item.put("id",id);
            item.put("class",shortText(node.getClassName(),45));
            // Password fields, input values and OTP-like content are never shared.
            item.put("label",node.isPassword()?"[REDACTED PASSWORD]":shortText(node.getText(),110));
            item.put("description",shortText(node.getContentDescription(),75));
            item.put("clickable",node.isClickable()).put("editable",node.isEditable()&&!node.isPassword())
                .put("scrollable",node.isScrollable());
            Rect bounds=new Rect();node.getBoundsInScreen(bounds);
            item.put("bounds",bounds.left+","+bounds.top+","+bounds.right+","+bounds.bottom);
            out.put(item);
        }
        for(int i=0;i<node.getChildCount()&&nodes.size()<75;i++){
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null)visit(child,out,depth+1);
        }
    }
    private static String shortText(CharSequence value,int max){
        if(value==null)return "";
        String text=value.toString().replaceAll("[\\r\\n\\t]+"," ").trim();
        if(text.length()>max)text=text.substring(0,max)+"…";
        return text;
    }
    private AccessibilityNodeInfo recentNode(int id) {
        if(android.os.SystemClock.uptimeMillis()-observedAt>15000)
            throw new IllegalStateException("Screen observation is stale. Observe again before acting.");
        AccessibilityNodeInfo current=getRootInActiveWindow();
        if(current==null||!observedPackage.equals(String.valueOf(current.getPackageName())))
            throw new IllegalStateException("Foreground app changed. Observe again before acting.");
        AccessibilityNodeInfo node=nodes.get(id);
        if(node==null||!node.refresh())throw new IllegalStateException("Element changed; observe again.");
        if(node.isPassword())throw new IllegalStateException("Password fields cannot be controlled by the agent.");
        return node;
    }
    public JSONObject click(int id) throws Exception {
        AccessibilityNodeInfo node=recentNode(id);
        if(!node.isClickable())throw new IllegalStateException("Selected element is not clickable.");
        boolean ok=node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        return new JSONObject().put("executed",ok).put("action","click").put("id",id);
    }
    public JSONObject type(int id,String value) throws Exception {
        if(value==null||value.length()>800)throw new IllegalArgumentException("Text is too long");
        AccessibilityNodeInfo node=recentNode(id);
        if(!node.isEditable())throw new IllegalStateException("Selected element is not editable.");
        Bundle args=new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
        boolean ok=node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        // Never echo the typed text to the model after execution.
        return new JSONObject().put("executed",ok).put("action","type").put("characters",value.length());
    }
    public JSONObject scroll(int id,boolean forward) throws Exception {
        AccessibilityNodeInfo node=recentNode(id);
        if(!node.isScrollable())throw new IllegalStateException("Selected element is not scrollable");
        boolean ok=node.performAction(forward?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
        return new JSONObject().put("executed",ok).put("action","scroll").put("forward",forward);
    }
    public JSONObject global(String direction) throws Exception {
        int action;
        if("back".equals(direction))action=GLOBAL_ACTION_BACK;
        else if("home".equals(direction))action=GLOBAL_ACTION_HOME;
        else throw new IllegalArgumentException("Unsupported global action");
        boolean ok=performGlobalAction(action);
        return new JSONObject().put("executed",ok).put("action",direction);
    }
}
