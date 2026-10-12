package com.musabgpt.m5;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.os.Build;
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
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final Map<String,Button> tabButtons=new LinkedHashMap<>();
    private boolean showTrace=false;
    private TermuxBridge termux;
    private ProjectExporter exporter;
    private static final int REQUEST_EXPORT_ZIP=940;
    private String pendingExportProject="";
    private FreeModels models;
    private PhoneTools phone;
    private NativeAgent agent;
    private boolean connected=false,agentActive=false;
    private String selectedFile="",originalHash="",lastSavedContent="";
    private String editorFile="";
    private int fileOpenRequest=0;
    private DraftVault draftVault;
    private final Handler autosaveHandler=new Handler(Looper.getMainLooper());
    private final Runnable autosaveTask=()->persistVisibleDraft();
    private final Map<String,String> unsavedDrafts=new LinkedHashMap<>();
    private EditText editor,agentInput;
    private LinearLayout chatLog;
    private ScrollView chatScroll;
    private Spinner modePicker,providerPicker;
    private String activePanel="files";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        draftVault=new DraftVault(this);
        termux=new TermuxBridge(this);
        exporter=new ProjectExporter(this,termux);
        models=new FreeModels(this);
        phone=new PhoneTools(this);
        agent=new NativeAgent(termux,models,phone,new NativeAgent.Screen() {
            @Override public void log(String text){runOnUiThread(()->addFeed(text));}
            @Override public void requestApproval(String title,String preview,NativeAgent.Decision callback) {
                runOnUiThread(()->{
                    if(!hasWindowFocus()){
                        M5AccessibilityService active=M5AccessibilityService.active();
                        if(active!=null){
                            active.askOnScreen(title,preview,approved->callback.decided(approved));
                        }else {
                            addFeed("Approval blocked: foreground application is open and M5 Accessibility is disabled.");
                            callback.decided(false);
                        }
                        return;
                    }
                    new AlertDialog.Builder(M5Activity.this)
                        .setTitle(title).setMessage(preview.length()>2400?preview.substring(0,2400)+"…":preview)
                        .setNegativeButton("Deny",(d,w)->callback.decided(false))
                        .setPositiveButton("Approve once",(d,w)->callback.decided(true))
                        .setOnCancelListener(d->callback.decided(false)).show();
                });
            }
            @Override public void finished(String result){runOnUiThread(()->{agentActive=false;addFeed(result);});}
        });
        buildShell();
        showPanel("files");
        // App renders natively even when Termux is missing; no fake connected state.
    }

    @Override protected void onPause(){
        persistVisibleDraft();
        super.onPause();
    }
    @Override protected void onDestroy(){
        autosaveHandler.removeCallbacks(autosaveTask);
        persistVisibleDraft();
        super.onDestroy();
    }

    private int dp(float n) {return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView label(String value,int size,int color){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,size);
        t.setTextColor(color);t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;
    }
    private GradientDrawable surface(int fill,int stroke,int radius){
        GradientDrawable draw=new GradientDrawable();
        draw.setColor(fill);
        draw.setCornerRadius(dp(radius));
        draw.setStroke(dp(1),stroke);
        return draw;
    }
    private Button button(String text,Runnable action){
        Button b=new Button(this);
        b.setText(text);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setPadding(dp(8),dp(4),dp(8),dp(4));
        b.setBackground(surface(SURFACE,Color.rgb(60,67,82),12));
        b.setOnClickListener(v->action.run());return b;
    }
    private EditText input(String hint,int lines){
        EditText e=new EditText(this);e.setHint(hint);e.setTextColor(Color.WHITE);e.setHintTextColor(MUTED);
        e.setBackground(surface(Color.rgb(26,30,40),Color.rgb(77,89,111),12));
        e.setPadding(dp(14),dp(12),dp(14),dp(12));
        e.setTextSize(14);e.setMinLines(lines);e.setGravity(Gravity.TOP|Gravity.START);
        return e;
    }
    private void buildShell() {
        root=vertical();root.setBackgroundColor(BG);
        // Android 15/16 enforce edge-to-edge for target SDK 35.
        // Respect the status bar, navigation bar, cutout, and on-screen keyboard.
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets bars=insets.getInsets(
                    WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
                left=bars.left;top=bars.top;right=bars.right;
                bottom=Math.max(bars.bottom,keyboard.bottom);
            }else{
                left=insets.getSystemWindowInsetLeft();
                top=insets.getSystemWindowInsetTop();
                right=insets.getSystemWindowInsetRight();
                bottom=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(left,top,right,bottom);
            return insets;
        });
        toolbar=horizontal();toolbar.setPadding(dp(6),dp(5),dp(6),dp(5));toolbar.setBackgroundColor(SURFACE);
        TextView logo=label("M5 STUDIO 0.6",16,ACCENT);logo.setTypeface(null,Typeface.BOLD);
        toolbar.addView(logo,new LinearLayout.LayoutParams(0,dp(48),1));
        connection=label("↻ Connect Termux",13,ACCENT);
        connection.setContentDescription("Connect M5 to Termux tools");
        connection.setOnClickListener(v->connectTermux());
        toolbar.addView(connection);
        root.addView(toolbar);
        HorizontalScrollView scroller=new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs=horizontal();
        for(String name:new String[]{"Files","Agent","Phone","Terminal","Providers"}) {
            Button b=button(name,()->showPanel(name.toLowerCase()));
            tabButtons.put(name.toLowerCase(),b);
            LinearLayout.LayoutParams tabLayout=new LinearLayout.LayoutParams(dp(96),dp(44));
            tabLayout.setMargins(dp(3),dp(3),dp(3),dp(3));
            tabs.addView(b,tabLayout);
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
        status("⌛ Termux…");
        termux.setup(new TermuxBridge.Callback(){
            @Override public void finished(JSONObject data){
                connected=data.optBoolean("ready",false);
                status(connected?"● Termux ready":"↻ Connect Termux");
                if(connected){showPanel(activePanel);toast("Termux ready — no local server");}
            }
            @Override public void failed(String error){connected=false;status("↻ Connect Termux");showError("Termux setup",error);}
        });
    }
    @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){
        super.onRequestPermissionsResult(req,permissions,results);
        if(req==140 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)connectTermux();
    }
    private void showPanel(String name){
        rememberDraft();
        activePanel=name;
        for(Map.Entry<String,Button> entry:tabButtons.entrySet()){
            boolean selected=entry.getKey().equals(name);
            entry.getValue().setBackground(surface(
                selected?Color.rgb(38,65,103):SURFACE,
                selected?ACCENT:Color.rgb(60,67,82),12));
            entry.getValue().setTextColor(selected?Color.rgb(189,219,255):Color.WHITE);
        }
        body.removeAllViews();
        switch(name){
            case "files":showFiles();break;
            case "agent":showAgent();break;
            case "phone":showPhone();break;
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
    private void persistVisibleDraft(){
        if(editor==null||editorFile.isEmpty())return;
        String path=editorFile;
        String contents=editor.getText().toString();
        try {
            if(contents.equals(lastSavedContent)){
                unsavedDrafts.remove(path);
                draftVault.remove(path);
            }else{
                unsavedDrafts.put(path,contents);
                draftVault.put(path,contents,originalHash);
            }
        }catch(Exception error){
            toast("Draft not saved to disk: "+error.getMessage());
        }
    }
    private void rememberDraft(){
        autosaveHandler.removeCallbacks(autosaveTask);
        if(editor==null||editorFile.isEmpty())return;
        persistVisibleDraft();
        editor=null;
        editorFile="";
    }
    private void showSavedDrafts(){
        try{
            ArrayList<String> paths=draftVault.paths();
            if(paths.isEmpty()){showError("Saved drafts","No unsaved drafts stored.");return;}
            new AlertDialog.Builder(this).setTitle("Recover encrypted drafts")
                .setItems(paths.toArray(new String[0]),(dialog,index)->{
                    String path=paths.get(index);
                    if(connected){openFile(path);return;}
                    try{
                        DraftVault.Draft draft=draftVault.get(path);
                        if(draft==null)return;
                        rememberDraft();
                        selectedFile=path;
                        originalHash=draft.savedHash;
                        lastSavedContent="";
                        unsavedDrafts.put(path,draft.contents);
                        showPanel("files");
                    }catch(Exception error){showError("Cannot recover draft",error.getMessage());}
                }).setNegativeButton("Close",null).show();
        }catch(Exception error){showError("Draft recovery",error.getMessage());}
    }
    private void sourceAction(boolean execute){
        if(selectedFile.isEmpty()||editor==null)return;
        String text=editor.getText().toString();
        if(originalHash.isEmpty()||!text.equals(lastSavedContent)){
            showError("Save before "+(execute?"running":"checking"),
                "There are unsaved changes. Save this file first; the tool must check the exact version on disk.");
            return;
        }
        requireTermux(()->{
            Runnable job=()->termux.tool(execute?"run_file":"check_file",
                new String[]{selectedFile},new TermuxBridge.Callback(){
                @Override public void finished(JSONObject observed){
                    boolean success=observed.optBoolean("success",false);
                    String title=(execute?"Run":"Syntax check")+(success?" ✓":" — failed");
                    String output="File: "+observed.optString("file",selectedFile)+
                        "\nExit code: "+observed.optString("exitCode","unknown")+
                        "\nTimed out: "+observed.optBoolean("timedOut")+
                        "\n\n"+observed.optString("stdout","")+
                        "\n"+observed.optString("stderr","");
                    if(output.length()>6000)output=output.substring(0,6000)+"\n[Output clipped]";
                    new AlertDialog.Builder(M5Activity.this)
                        .setTitle(title).setMessage(output)
                        .setPositiveButton("OK",null).show();
                }
                @Override public void failed(String error){showError("Execution not started",error);}
            });
            if(!execute){job.run();return;}
            new AlertDialog.Builder(this).setTitle("Run this source file?")
                .setMessage("Run "+selectedFile+" with your Termux permissions?\n\n"
                +"Source files can access the network and your Termux data. Run only trusted code.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Run once",(d,w)->job.run()).show();
        });
    }

    private void showFiles(){
        caption(selectedFile.isEmpty()?"PROJECT EXPLORER":"EDITOR • "+selectedFile);
        LinearLayout actions=horizontal();
        actions.addView(button("Refresh",()->requireTermux(this::refreshFiles)),new LinearLayout.LayoutParams(0,dp(48),1));
        actions.addView(button("New file",()->requireTermux(this::newFile)),new LinearLayout.LayoutParams(0,dp(48),1));
        if(selectedFile.isEmpty())actions.addView(button("New project",()->requireTermux(this::newProject)),new LinearLayout.LayoutParams(0,dp(48),1));
        if(selectedFile.isEmpty())actions.addView(button("Search",()->requireTermux(this::searchWorkspace)),new LinearLayout.LayoutParams(0,dp(48),1));
        if(selectedFile.isEmpty())actions.addView(button("Drafts",this::showSavedDrafts),new LinearLayout.LayoutParams(0,dp(48),1));
        if(!selectedFile.isEmpty())actions.addView(button("Save",()->requireTermux(this::saveFile)),new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(actions);
        if(!connected){
            info("Offline editing: encrypted drafts are available. Connect Termux to save, run or browse project files.");
            if(selectedFile.isEmpty())return;
        }
        if(!selectedFile.isEmpty()) {
            LinearLayout work=horizontal();
            work.addView(button("Check",()->sourceAction(false)),new LinearLayout.LayoutParams(0,dp(42),1));
            work.addView(button("Run file",()->sourceAction(true)),new LinearLayout.LayoutParams(0,dp(42),1));
            body.addView(work);
            NativeCodeEditor source=new NativeCodeEditor(this);
            source.setHint("Write your source code here");
            source.setFilename(selectedFile);
            editor=source;
            editorFile=selectedFile;
            editor.setMinLines(10);
            editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editor.setText(unsavedDrafts.containsKey(selectedFile)?unsavedDrafts.get(selectedFile):lastSavedContent);
            editor.addTextChangedListener(new TextWatcher(){
                @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
                @Override public void onTextChanged(CharSequence text,int start,int before,int count){}
                @Override public void afterTextChanged(Editable value){
                    autosaveHandler.removeCallbacks(autosaveTask);
                    autosaveHandler.postDelayed(autosaveTask,700);
                }
            });
            body.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
            body.addView(button("Explorer · keep draft",()->{
                rememberDraft();
                selectedFile="";
                showPanel("files");
            }));
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
                    else row.setOnClickListener(v->exportProjectDialog(path));
                    rows.addView(row);
                }
                if(files==null||files.length()==0)rows.addView(label("Workspace is empty. Create a new file.",14,MUTED));
                scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            }
            @Override public void failed(String message){showError("Explorer",message);}
        });
    }
    private void exportProjectDialog(String relative){
        new AlertDialog.Builder(this).setTitle("Folder: "+relative)
            .setMessage("Export source files from this folder to a ZIP file on your phone. No network is used.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Export ZIP",(d,w)->{
                pendingExportProject=relative;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.setType("application/zip");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.putExtra(Intent.EXTRA_TITLE,relative.replace('/','_')+".zip");
                startActivityForResult(intent,REQUEST_EXPORT_ZIP);
            }).show();
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_EXPORT_ZIP)return;
        String name=pendingExportProject;
        pendingExportProject="";
        if(resultCode!=Activity.RESULT_OK||data==null||data.getData()==null)return;
        Uri destination=data.getData();
        status("● Exporting ZIP…");
        exporter.save(name,destination,new ProjectExporter.Result(){
            @Override public void finished(int files,int bytes){
                status("● Termux ready");
                new AlertDialog.Builder(M5Activity.this).setTitle("ZIP exported")
                    .setMessage(files+" source file(s), "+bytes+" bytes saved to your selected Android document.")
                    .setPositiveButton("OK",null).show();
            }
            @Override public void failed(String error){
                status("● Export failed");
                showError("ZIP incomplete",error+" — an incomplete file may remain at the chosen destination.");
            }
        });
    }

    private void openFile(String path){
        int request=++fileOpenRequest;
        termux.tool("read",new String[]{path},new TermuxBridge.Callback(){
            @Override public void finished(JSONObject file){
                if(request!=fileOpenRequest||!"files".equals(activePanel))return;
                loadEditor(path,file.optString("hash",""),file.optString("content",""));
            }
            @Override public void failed(String error){
                if(request!=fileOpenRequest)return;
                try{
                    DraftVault.Draft draft=draftVault.get(path);
                    if(draft!=null&&error.contains("ENOENT")) {
                        loadEditor(path,"","");
                        return;
                    }
                }catch(Exception ignored){}
                showError("Open file",error);
            }
        });
    }
    private void loadEditor(String path,String hash,String diskContent){
        rememberDraft();
        selectedFile=path;
        originalHash=hash;
        lastSavedContent=diskContent;
        try{
            DraftVault.Draft draft=draftVault.get(path);
            if(draft==null||draft.contents.equals(diskContent)){
                unsavedDrafts.remove(path);
                if(draft!=null)draftVault.remove(path);
                showPanel("files");return;
            }
            if(!draft.savedHash.equals(hash)){
                new AlertDialog.Builder(this)
                    .setTitle("File changed since draft was saved")
                    .setMessage("This draft is based on another version of the file. Choose which version to edit. Restoring the draft will NOT save it to the project automatically.")
                    .setNegativeButton("Use disk version",(d,w)->{
                        unsavedDrafts.remove(path);
                        try{draftVault.remove(path);}catch(Exception ignored){}
                        showPanel("files");
                    })
                    .setPositiveButton("Review recovered draft",(d,w)->{
                        unsavedDrafts.put(path,draft.contents);
                        showPanel("files");
                    }).show();
                return;
            }
            unsavedDrafts.put(path,draft.contents);
        }catch(Exception error){
            showError("Draft recovery error","Disk file was not changed: "+error.getMessage());
        }
        showPanel("files");
    }
    private void newFile(){
        EditText name=input("Relative filename: e.g. main.py",1);
        new AlertDialog.Builder(this).setTitle("New source file")
            .setView(name).setNegativeButton("Cancel",null).setPositiveButton("Create",(d,w)->{
                String path=name.getText().toString().trim();
                if(path.isEmpty()){showError("File name","Enter a path.");return;}
                ++fileOpenRequest;
                loadEditor(path,"","");
            }).show();
    }
    private void searchWorkspace(){
        EditText query=input("Search code text (e.g. calculate)",1);
        query.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Find in project files")
            .setView(query).setNegativeButton("Cancel",null)
            .setPositiveButton("Search",(dialog,which)->{
                String text=query.getText().toString().trim();
                if(text.isEmpty()||text.length()>100){
                    showError("Invalid search","Enter 1–100 characters");return;
                }
                termux.tool("search",new String[]{text},new TermuxBridge.Callback(){
                    @Override public void finished(JSONObject result){
                        JSONArray matches=result.optJSONArray("matches");
                        if(matches==null||matches.length()==0){
                            showError("Search", "No matching source lines found in the indexed workspace files.");
                            return;
                        }
                        ArrayList<String> labels=new ArrayList<>();
                        ArrayList<String> paths=new ArrayList<>();
                        for(int i=0;i<matches.length()&&i<80;i++){
                            JSONObject hit=matches.optJSONObject(i);
                            if(hit==null)continue;
                            String path=hit.optString("path","");
                            if(path.isEmpty())continue;
                            paths.add(path);
                            labels.add(path+":"+hit.optInt("line")+"\n"+hit.optString("text",""));
                        }
                        new AlertDialog.Builder(M5Activity.this).setTitle("Search results · "+text)
                            .setItems(labels.toArray(new String[0]),(dlg,index)->{
                                openFile(paths.get(index));
                            }).setNegativeButton("Close",null).show();
                    }
                    @Override public void failed(String error){showError("Code search failed",error);}
                });
            }).show();
    }

    private void newProject(){
        LinearLayout dialog=vertical();
        dialog.setPadding(dp(20),dp(10),dp(20),dp(5));
        EditText project=input("Name e.g. Calculator2026",1);
        project.setSingleLine(true);
        dialog.addView(project);
        final String[] presets={"HTML calculator (offline)","Python starter","Node.js starter"};
        Spinner pick=new Spinner(this);
        pick.setAdapter(spinnerAdapter(presets));
        dialog.addView(pick);
        new AlertDialog.Builder(this).setTitle("Create a real project")
            .setMessage("Writes project files into Termux private workspace. No paid model or cloud required.")
            .setView(dialog).setNegativeButton("Cancel",null)
            .setPositiveButton("Create",(ignored,which)->{
                String name=project.getText().toString().trim();
                if(!name.matches("[A-Za-z][A-Za-z0-9_-]{1,35}")){
                    showError("Invalid name","Use 2–36 letters, numbers, hyphens or underscores; begin with a letter.");
                    return;
                }
                String preset=new String[]{"web-calculator","python","node"}[pick.getSelectedItemPosition()];
                termux.tool("scaffold",new String[]{name,preset},new TermuxBridge.Callback(){
                    @Override public void finished(JSONObject result){
                        String files=result.optJSONArray("files")==null?"":result.optJSONArray("files").toString();
                        new AlertDialog.Builder(M5Activity.this).setTitle("Project created")
                            .setMessage("Project: "+result.optString("project")+"\nValidation: "+result.optString("verification")+"\nFiles: "+files)
                            .setPositiveButton("Open files",(d,w)->{selectedFile="";showPanel("files");})
                            .show();
                    }
                    @Override public void failed(String error){showError("Project not created",error);}
                });
            }).show();
    }

    private void saveFile(){
        if(editor==null||selectedFile.isEmpty())return;
        final String path=editorFile;
        final String contents=editor.getText().toString();
        final String expectedHash=originalHash;
        if(contents.getBytes(StandardCharsets.UTF_8).length>40000){
            showError("Save","Source file exceeds 40 KB. No files were changed.");return;
        }
        String encoded=Base64.encodeToString(contents.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);
        status("● Saving…");
        termux.tool("write",new String[]{path,encoded,expectedHash},new TermuxBridge.Callback(){
            @Override public void finished(JSONObject receipt){
                String newHash=receipt.optString("hash","");
                if(newHash.isEmpty()){
                    showError("Save not verified","Termux did not confirm the content hash");
                    return;
                }
                if(path.equals(selectedFile)){
                    originalHash=newHash;
                    lastSavedContent=contents;
                    if(editor!=null && path.equals(editorFile) &&
                        !contents.equals(editor.getText().toString())){
                        persistVisibleDraft();
                    }else if(!unsavedDrafts.containsKey(path)||
                        contents.equals(unsavedDrafts.get(path))){
                        unsavedDrafts.remove(path);
                        try{draftVault.remove(path);}catch(Exception error){
                            toast("File saved; encrypted draft cleanup failed");
                        }
                    }
                }
                status("● Termux ready");
                toast("Saved "+path);
            }
            @Override public void failed(String error){
                status("● Save blocked");
                showError("File not saved",error);
            }
        });
    }
    private void showAgent(){
        caption("AI CODING AGENT");
        LinearLayout options=horizontal();
        TextView details=label("Native tools  ·  Consent  ·  Free AI",12,MUTED);
        options.addView(details,new LinearLayout.LayoutParams(0,dp(40),1));
        options.addView(button(showTrace?"Hide trace":"Show trace",()->{
            showTrace=!showTrace;showPanel("agent");
        }));
        body.addView(options);
        chatScroll=new ScrollView(this);chatLog=vertical();
        for(String text:feed)if(showTrace||!isTrace(text))chatLog.addView(messageView(text));
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
            addFeed("You: "+goal);agentActive=true;agentInput.setText("");
            // Direct HTTPS chat works without Termux; only tool invocations need the bridge.
            agent.start(goal,providerPicker.getSelectedItemPosition(),modePicker.getSelectedItemPosition()==0?"plan":"agent");
        }),new LinearLayout.LayoutParams(0,dp(48),1));
        row.addView(button("Stop",()->{agent.stop();agentActive=false;}),new LinearLayout.LayoutParams(0,dp(48),1));
        row.addView(button("Clear",()->new AlertDialog.Builder(this)
            .setTitle("Clear current chat?")
            .setMessage("Clear the visible task feed and short-term chat context. Project files are preserved.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Clear",(d,w)->{agent.clearConversation();feed.clear();showPanel("agent");})
            .show()),new LinearLayout.LayoutParams(0,dp(48),1));
        body.addView(row);
    }
    private ArrayAdapter<String> spinnerAdapter(String[] values){
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);
        return a;
    }
    private boolean isTrace(String content){
        return content.startsWith("Reasoning step")||content.startsWith("Provider:")||
            content.startsWith("Tool:")||content.startsWith("Result of ")||
            content.startsWith("M5 native ")||content.startsWith("Opening ");
    }
    private TextView messageView(String content){
        boolean user=content.startsWith("You:");
        boolean blocked=content.startsWith("Blocked:")||content.startsWith("Tool failed:")||content.startsWith("Free model request failed:");
        TextView text=label(content,14,user?Color.rgb(231,241,255):Color.WHITE);
        text.setPadding(dp(15),dp(12),dp(15),dp(12));
        text.setBackground(surface(user?Color.rgb(29,60,104):
            blocked?Color.rgb(89,48,42):Color.rgb(37,43,56),
            blocked?Color.rgb(155,83,70):Color.rgb(59,69,89),14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(user?dp(25):0,dp(2),user?0:dp(12),dp(6));
        text.setLayoutParams(lp);
        return text;
    }
    private void addFeed(String text){
        feed.add(text);
        while(feed.size()>90)feed.remove(0);
        if("agent".equals(activePanel) && chatLog!=null && (showTrace||!isTrace(text))){
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
    private void showPhone() {
        caption("ANDROID PHONE CONTROL");
        info("Launch installed apps directly, without any AI provider. Advanced screen controls require you to enable Accessibility.");
        info("Accessibility enabled: "+phone.accessibilityReady());
        body.addView(button("Open WhatsApp",()->{
            try {
                JSONObject result=phone.open("واتساب");
                toast("Android launch: "+result.optString("package"));
            }catch(Exception error){showError("Open app",error.getMessage());}
        }));
        body.addView(button("Enable Phone Controls in Android Settings",()->{
            new AlertDialog.Builder(this).setTitle("Optional phone automation permission")
                .setMessage("Android Accessibility can see visible labels, and can tap, type and scroll. M5 processes screen data only when you request an action; it must ask before sharing visible UI with an AI provider. Disable it anytime in Android Settings. Sideloaded apps on some Android versions may also require Allow restricted settings.")
                .setNegativeButton("Cancel",null)
                .setPositiveButton("Open Settings",(d,w)->phone.requestAccessibilitySettings())
                .show();
        }));
        body.addView(button("Show installed launchable apps",()->{
            try {
                JSONObject result=phone.apps();
                JSONArray apps=result.optJSONArray("apps");
                StringBuilder names=new StringBuilder();
                if(apps!=null)for(int i=0;i<apps.length()&&i<80;i++){
                    JSONObject app=apps.optJSONObject(i);
                    if(app!=null)names.append(app.optString("app")).append("\n");
                }
                new AlertDialog.Builder(this).setTitle("Launchable apps").setMessage(names.length()==0?"No launchable apps visible":names.toString())
                    .setPositiveButton("OK",null).show();
            }catch(Exception error){showError("Installed apps",error.getMessage());}
        }));
        info("Agent phone tools: phone_apps, phone_open, phone_observe, phone_click, phone_type, phone_scroll, phone_back and phone_home. Interactions require explicit consent. M5 cannot silently enable Accessibility or bypass other apps' security.");
    }
    private void showProviders(){
        caption("FREE AI PROVIDERS");
        info("Direct HTTPS. Public free gateways can be unavailable or rate-limited; optional free-plan keys are encrypted locally.");
        ScrollView settings=new ScrollView(this);
        LinearLayout items=vertical();
        settings.addView(items);
        body.addView(settings,new LinearLayout.LayoutParams(-1,0,1));
        items.addView(label("FreeLLMPool: public endpoints, automatic cooldown after HTTP 429. No unlimited access.",13,MUTED));
        for(int i=1;i<FreeModels.FAMILIES.length;i++){
            final int id=i;
            items.addView(label(FreeModels.FAMILIES[i],16,ACCENT));
            EditText url=input("HTTPS OpenAI-compatible /chat/completions URL",1);
            url.setSingleLine(true);
            url.setText(models.getEndpoint(i));
            url.setEnabled(i<4);
            items.addView(url);
            EditText model=input("Model ID",1);
            model.setSingleLine(true);
            model.setText(models.getModel(i));
            items.addView(model);
            EditText key=input(models.hasKey(i)?"Encrypted key already saved · enter replacement":"Optional free-plan API key",1);
            key.setSingleLine(true);
            key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
            items.addView(key);
            LinearLayout commands=horizontal();
            commands.addView(button("Save",()->{
                try{
                    models.configure(id,url.getText().toString().trim(),model.getText().toString().trim());
                    String secret=key.getText().toString().trim();
                    if(!secret.isEmpty()){models.configureKey(id,secret);key.setText("");}
                    toast("Provider settings saved locally");
                }catch(Exception error){showError("Provider setup failed",error.getMessage());}
            }),new LinearLayout.LayoutParams(0,dp(48),1));
            commands.addView(button("Forget key",()->{
                try{models.configureKey(id,"");key.setText("");toast("Saved API key erased");}
                catch(Exception error){showError("Keystore",error.getMessage());}
            }),new LinearLayout.LayoutParams(0,dp(48),1));
            items.addView(commands);
            items.addView(button("Test this provider (uses free quota)",()->{
                JSONArray message=new JSONArray();
                try{message.put(new JSONObject().put("role","user").put("content","Reply with exactly OK."));}
                catch(Exception ignored){}
                models.complete(message,id,new FreeModels.Callback(){
                    @Override public void success(String answer,String route){
                        runOnUiThread(()->showError("Provider response",route+": "+answer.substring(0,Math.min(350,answer.length()))));
                    }
                    @Override public void error(String message){
                        runOnUiThread(()->showError("Provider unavailable",message));
                    }
                });
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
