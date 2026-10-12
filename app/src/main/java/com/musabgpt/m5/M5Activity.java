package com.musabgpt.m5;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;

/**
 * 100% native Android activity: no WebView, no local server, no localhost.
 * Termux tools are one-off PendingIntent requests and model calls are direct HTTPS.
 */
public final class M5Activity extends Activity {
    private static final int BG=Color.rgb(23,24,28), SURFACE=Color.rgb(34,36,42);
    private static final int ACCENT=Color.rgb(114,162,245), MUTED=Color.rgb(165,170,181);
    private static final String EMPTY_HASH="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private LinearLayout root,body,toolbar;
    private TextView connection;
    private final ArrayList<String> feed=new ArrayList<>();
    private TermuxBridge termux;
    private FreeModels models;
    private NativeAgent agent;
    private boolean connected=false,agentActive=false;
    private String selectedFile="",originalHash=EMPTY_HASH;
    private EditText editor,agentInput;
    private LinearLayout chatLog;
    private ScrollView chatScroll;
    private Spinner modePicker,providerPicker;
    private String activePanel="files";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        termux=new TermuxBridge(this);
        models=new FreeModels(this);
        agent=new NativeAgent(termux,models,new NativeAgent.Screen() {
            @Override public void log(String text){runOnUiThread(()->addFeed(text));}
            @Override public void requestApproval(String title,String preview,NativeAgent.Decision callback) {
                runOnUiThread(()->new AlertDialog.Builder(M5Activity.this)
                    .setTitle(title).setMessage(preview.length()>2400?preview.substring(0,2400)+"…":preview)
                    .setNegativeButton("Deny",(d,w)->callback.decided(false))
                    .setPositiveButton("Approve once",(d,w)->callback.decided(true))
                    .setOnCancelListener(d->callback.decided(false)).show());
            }
            @Override public void finished(String result){runOnUiThread(()->{agentActive=false;addFeed(result);});}
        });
        buildShell();
        showPanel("files");
        // App renders natively even when Termux is missing; no fake connected state.
    }

    private int dp(float n) {return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);
        t.setTextColor(color);t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;
    }
    private Button button(String text,Runnable action){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE));
        b.setOnClickListener(v->action.run());return b;
    }
    private EditText input(String hint,int lines){
        EditText e=new EditText(this);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(MUTED);
        e.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        e.setTextSize(14);e.setMinLines(lines);e.setGravity(Gravity.TOP|Gravity.START);
        return e;
    }
    private void buildShell() {
        root=vertical();root.setBackgroundColor(BG);
        toolbar=horizontal();toolbar.setPadding(dp(6),dp(5),dp(6),dp(5));toolbar.setBackgroundColor(SURFACE);
        TextView logo=label("M5  /  NATIVE",18,ACCENT);logo.setTypeface(null,Typeface.BOLD);
        toolbar.addView(logo,new LinearLayout.LayoutParams(0,dp(48),1));
        connection=label("● Offline",12,MUTED);
        toolbar.addView(connection);
        root.addView(toolbar);
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=horizontal();
        for(String name:new String[]{"Files","Agent","Terminal","Providers"}) {
            Button b=button(name,()->showPanel(name.toLowerCase()));
            tabs.addView(b,new LinearLayout.LayoutParams(dp(98),dp(48)));
        }
        Button connect=button("↻ Termux",this::connectTermux);
        tabs.addView(connect,new LinearLayout.LayoutParams(dp(124),dp(48)));
        scroller.addView(tabs);root.addView(scroller);
        View line=new View(this);line.setBackgroundColor(Color.rgb(54,56,63));root.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
        body=vertical();body.setPadding(dp(11),dp(7),dp(11),dp(8));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    private void status(String text){connection.setText(text);}

    private void connectTermux(){
        if(!termux.readyForUse()){
            new AlertDialog.Builder(this).setTitle("Termux permission")
              .setMessage("M5 needs official Termux and permission to run commands. Install Termux if needed, enable allow-external-apps=true in its properties, and grant 'Run commands in Termux' under Android app permissions.")
              .setPositiveButton("Request permission",(d,w)->termux.requestPermission())
              .setNegativeButton("Cancel",null).show();
            return;
        }
        status("● Connecting…");
        termux.setup(new TermuxBridge.Callback(){
            @Override public void finished(JSONObject data){
                connected=data.optBoolean("ready",false);
                status(connected?"● Termux ready":"● Termux error");
                if(connected){showPanel(activePanel);toast("Termux ready — no local server");}
            }
            @Override public void failed(String error){connected=false;status("● Disconnected");showError("Termux setup",error);}
        });
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){
        super.onRequestPermissionsResult(req,permissions,results);
        if(req==140 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)connectTermux();
    }
    private void showPanel(String name){
        activePanel=name;body.removeAllViews();
        switch(name){
            case "files":showFiles();break;
            case "agent":showAgent();break;
            case "terminal":showTerminal();break;
            case "providers":showProviders();break;
            default:showFiles();
        }
    }
    private void caption(String text){body.addView(label(text,19,Color.WHITE));}
    private void info(String text){body.addView(label(text,13,MUTED));}
    private void requireTermux(Runnable action){
        if(connected)action.run();
        else showError("Termux not connected","Tap ↻ Termux in the top bar to start direct command access. The editor is native and does not depend on localhost.");
    }
    private void showFiles(){
        caption(selectedFile.isEmpty()?"PROJECT EXPLORER":"EDITOR • "+selectedFile);
        LinearLayout actions=horizontal();
        actions.addView(button("Refresh",()->requireTermux(this::refreshFiles)),new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(button("New file",()->requireTermux(this::newFile)),new LinearLayout.LayoutParams(0,dp(48),1));
        if(!selectedFile.isEmpty())actions.addView(button("Save",()->requireTermux(this::saveFile)),new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(actions);
        if(!connected){info("No server required. Connect Termux to list and edit files in ~/m5-workspace.");return;}
        if(!selectedFile.isEmpty()) {
            editor=input("Code editor",10);editor.setTypeface(Typeface.MONOSPACE);
            editor.setTextSize(13);editor.setHorizontallyScrolling(true);
            editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            body.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
            body.addView(button("Back to Explorer",()->{selectedFile="";showPanel("files");}));
        } else refreshFiles();
    }
    private void refreshFiles(){
        if(!connected)return;
        termux.tool("list",new String[]{""},new TermuxBridge.Callback(){
            @Override public void finished(JSONObject value){
                if(!"files".equals(activePanel)||!selectedFile.isEmpty())return;
                body.removeViews(2,Math.max(0,body.getChildCount()-2));
                ScrollView scroll=new ScrollView(M5Activity.this);LinearLayout rows=vertical();
                JSONArray files=value.optJSONArray("files");
                if(files!=null)for(int i=0;i<files.length();i++){
                    JSONObject file=files.optJSONObject(i);if(file==null)continue;
                    String path=file.optString("path","");
                    boolean folder="directory".equals(file.optString("type"));
                    TextView row=label((folder?"▸  ":"📄  ")+path,14,folder?ACCENT:Color.WHITE);
                    if(!folder)row.setOnClickListener(v->openFile(path));
                    rows.addView(row);
                }
                if(files==null||files.length()==0)rows.addView(label("Workspace is empty. Create a new file.",14,MUTED));
                scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            }
            @Override public void failed(String message){showError("Explorer",message);}
        });
    }
    private void openFile(String path) {
        termux.tool("read",new String[]{path},new TermuxBridge.Callback(){
            @Override public void finished(JSONObject file){
                selectedFile=path;originalHash=file.optString("hash","");
                showPanel("files");if(editor!=null)editor.setText(file.optString("content",""));
            }
            @Override public void failed(String message){showError("Open file",message);}
        });
    }
    private void newFile(){
        EditText name=input("Relative filename: e.g. main.py",1);
        new AlertDialog.Builder(this).setTitle("New source file")
            .setView(name).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{
                String path=name.getText().toString().trim();
                if(path.isEmpty()){showError("File name","Enter a path.");return;}
                selectedFile=path;originalHash=EMPTY_HASH;showPanel("files");
                if(editor!=null)editor.setText("");
            }).show();
    }
    private void saveFile(){
        if(editor==null||selectedFile.isEmpty())return;
        String data=editor.getText().toString();
        if(data.getBytes(StandardCharsets.UTF_8).length>40000){showError("Save","The direct bridge supports source files up to 40KB.");return;}
        String encoded=Base64.encodeToString(data.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        status("● Saving…");
        termux.tool("write",new String[]{selectedFile,encoded,originalHash},new TermuxBridge.Callback(){
            @Override public void finished(JSONObject result){originalHash=result.optString("hash",originalHash);status("● Termux ready");toast("Saved "+selectedFile);}
            @Override public void failed(String error){status("● Save blocked");showError("File not saved",error);}
        });
    }
    private void showAgent(){
        caption("AI CODING AGENT");
        info("Direct HTTPS free models · local files through Termux intents · no localhost.");
        chatScroll=new ScrollView(this);chatLog=vertical();
        for(String text:feed)chatLog.addView(messageView(text));
        chatScroll.addView(chatLog);
        body.addView(chatScroll,new LinearLayout.LayoutParams(-1,0,1));
        providerPicker=new Spinner(this);providerPicker.setAdapter(spinnerAdapter(FreeModels.FAMILIES));body.addView(providerPicker);
        modePicker=new Spinner(this);modePicker.setAdapter(spinnerAdapter(new String[]{"Plan · read only","Agent · approve edits"}));
        modePicker.setSelection(1);body.addView(modePicker);
        agentInput=input("Ask M5 to inspect, write, or test real code…",2);
        body.addView(agentInput);
        LinearLayout row=horizontal();
        row.addView(button("Send",()->{
            String goal=agentInput.getText().toString().trim();
            if(goal.isEmpty())return;
            requireTermux(()->{addFeed("You: "+goal);agentActive=true;agentInput.setText("");
                agent.start(goal,providerPicker.getSelectedItemPosition(),modePicker.getSelectedItemPosition()==0?"plan":"agent");});
        }),new LinearLayout.LayoutParams(0,dp(48),1));
        row.addView(button("Stop",()->{agent.stop();agentActive=false;}),new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(row);
    }
    private ArrayAdapter<String> spinnerAdapter(String[] values){
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);
        return a;
    }
    private TextView messageView(String content){
        TextView text=label(content,14,Color.WHITE);text.setBackgroundColor(SURFACE);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(7);
        text.setLayoutParams(lp);return text;
    }
    private void addFeed(String text){
        feed.add(text);
        while(feed.size()>90)feed.remove(0);
        if("agent".equals(activePanel) && chatLog!=null){
            chatLog.addView(messageView(text));
            chatScroll.post(()->chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }
    private void showTerminal(){
        caption("TERMUX · NATIVE COMMANDS");
        info("Direct RUN_COMMAND interface. No terminal server. Commands require your explicit confirmation.");
        EditText command=input("e.g. pwd, ls, python --version",2);
        body.addView(command);
        ScrollView scroll=new ScrollView(this);
        TextView output=label("Output will appear here after command completes.",13,MUTED);
        output.setTypeface(Typeface.MONOSPACE);scroll.addView(output);
        body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(button("Run command",()->{
            String value=command.getText().toString().trim();
            if(value.isEmpty())return;
            requireTermux(()->new AlertDialog.Builder(this).setTitle("Execute in Termux?")
                .setMessage(value).setNegativeButton("Cancel",null)
                .setPositiveButton("Run",(d,w)->{
                    output.setText("Running…");
                    termux.tool("shell",new String[]{value},new TermuxBridge.Callback(){
                        @Override public void finished(JSONObject r){
                            output.setText("exit "+r.optInt("code")+" / success="+r.optBoolean("success")+
                                "\n\n"+r.optString("stdout","")+"\n"+r.optString("stderr",""));
                        }
                        @Override public void failed(String error){output.setText(error);}
                    });
                }).show());
        }));
    }
    private void showProviders(){
        caption("FREE AI PROVIDERS");
        info("These models connect directly from the Android app over HTTPS. No M5 server is needed. Free quotas and availability may change.");
        for(String family:FreeModels.FAMILIES)info("• "+family);
        info("FreeLLMPool tries multiple public free routes. Other gateway families remain configurable via a remote HTTPS endpoint (no local proxy).");
        for(int i=1;i<FreeModels.FAMILIES.length;i++){
            final int id=i;body.addView(label(FreeModels.FAMILIES[i],16,ACCENT));
            EditText url=input("HTTPS OpenAI-compatible /chat/completions URL",1);
            url.setSingleLine(true);url.setText(models.getEndpoint(i));body.addView(url);
            EditText model=input("Model ID",1);
            model.setSingleLine(true);model.setText(models.getModel(i));body.addView(model);
            body.addView(button("Save "+FreeModels.FAMILIES[i],()->{
                try{models.configure(id,url.getText().toString().trim(),model.getText().toString().trim());toast("Saved provider configuration");}
                catch(Exception e){showError("Invalid gateway",e.getMessage());}
            }));
        }
    }
    private void showError(String title,String message) {
        if(isFinishing())return;
        new AlertDialog.Builder(this).setTitle(title)
            .setMessage(message).setPositiveButton("OK",null).show();
    }
    private void toast(String text){android.widget.Toast.makeText(this,text,android.widget.Toast.LENGTH_SHORT).show();}
}
