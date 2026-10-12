package com.musabgpt.m5;

import android.app.Activity;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Exports real Termux project source through Android's user-selected document provider. */
public final class ProjectExporter {
    public interface Result {
        void finished(int files,int bytes);
        void failed(String message);
    }
    private static final int MAX_FILES=30, MAX_BYTES=700_000;
    private final Activity activity;
    private final TermuxBridge termux;

    ProjectExporter(Activity activity,TermuxBridge termux){
        this.activity=activity;this.termux=termux;
    }
    public void save(String prefix,Uri document,Result result){
        if(prefix==null||!prefix.matches("[A-Za-z][A-Za-z0-9_/-]{1,80}")||prefix.contains("..")){
            result.failed("Invalid workspace project name");return;
        }
        termux.tool("list",new String[]{prefix},new TermuxBridge.Callback(){
            @Override public void failed(String message){result.failed("Cannot list project: "+message);}
            @Override public void finished(JSONObject response){
                JSONArray files=response.optJSONArray("files");
                if(files==null){result.failed("No project files were returned");return;}
                List<String> names=new ArrayList<>();
                for(int i=0;i<files.length();i++){
                    JSONObject item=files.optJSONObject(i);
                    if(item==null||!"file".equals(item.optString("type")))continue;
                    String path=item.optString("path","");
                    if(!path.startsWith(prefix+"/"))continue;
                    String entry=path.substring(prefix.length()+1);
                    if(entry.isEmpty()||entry.startsWith("/")||entry.contains("..")||entry.contains("\\")
                        ||!entry.matches("[A-Za-z0-9_./-]{1,180}"))continue;
                    names.add(path);
                }
                if(names.isEmpty()){result.failed("No shareable source files in this project");return;}
                if(names.size()>MAX_FILES){result.failed("Project exceeds ZIP export limit of 30 source files");return;}
                try{
                    OutputStream stream=activity.getContentResolver().openOutputStream(document,"w");
                    if(stream==null)throw new IllegalStateException("Document provider refused writing");
                    ZipOutputStream zip=new ZipOutputStream(stream);
                    next(prefix,names,0,zip,0,result);
                }catch(Exception e){result.failed("Cannot open ZIP destination: "+e.getMessage());}
            }
        });
    }
    private void next(String prefix,List<String> paths,int index,ZipOutputStream out,int total,Result callback){
        if(index>=paths.size()){
            try{out.close();callback.finished(paths.size(),total);}
            catch(Exception e){callback.failed("Unable to finish ZIP: "+e.getMessage());}
            return;
        }
        final String full=paths.get(index);
        termux.tool("read",new String[]{full},new TermuxBridge.Callback(){
            @Override public void failed(String message){
                closeQuietly(out);
                callback.failed("Cannot export "+full+": "+message);
            }
            @Override public void finished(JSONObject data){
                try {
                    byte[] bytes=data.getString("content").getBytes(StandardCharsets.UTF_8);
                    if(bytes.length>40_000||total+bytes.length>MAX_BYTES)
                        throw new IllegalStateException("Project exceeds ZIP export size limit");
                    String entry=full.substring(prefix.length()+1);
                    ZipEntry zipEntry=new ZipEntry(entry);
                    out.putNextEntry(zipEntry);
                    out.write(bytes);
                    out.closeEntry();
                    next(prefix,paths,index+1,out,total+bytes.length,callback);
                }catch(Exception error){
                    closeQuietly(out);
                    callback.failed("ZIP export failed: "+error.getMessage());
                }
            }
        });
    }
    private void closeQuietly(ZipOutputStream out){
        try{out.close();}catch(Exception ignored){}
    }
}
