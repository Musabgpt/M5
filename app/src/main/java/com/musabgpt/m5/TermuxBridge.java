package com.musabgpt.m5;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

/** Runs one-off Termux commands via the official intent API. No local HTTP service. */
public final class TermuxBridge {
    private static final String PREFIX = "/data/data/com.termux/files/usr/bin/";
    private static final String HOME = "/data/data/com.termux/files/home";
    private static final AtomicInteger NEXT = new AtomicInteger(1000);
    private static final ConcurrentHashMap<Integer, Callback> REQUESTS = new ConcurrentHashMap<>();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private final Activity activity;
    private volatile boolean sessionReady=false;

    public interface Callback {
        void finished(JSONObject data);
        void failed(String message);
    }

    TermuxBridge(Activity activity) { this.activity = activity; }
    public boolean isConnected() { return sessionReady && readyForUse(); }

    public boolean readyForUse() {
        return activity.getPackageManager().getLaunchIntentForPackage("com.termux") != null
                && (Build.VERSION.SDK_INT < 23
                    || activity.checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED);
    }

    public void requestPermission() {
        if (Build.VERSION.SDK_INT >= 23
                && activity.checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{"com.termux.permission.RUN_COMMAND"}, 140);
        }
    }

    public void setup(Callback callback) {
        // The one-shot Node bridge ships INSIDE the APK. No GitHub checkout,
        // HTTP server, or remote script download is needed to use Termux tools.
        final String encoded;
        try (InputStream input=activity.getAssets().open("native-bridge.mjs");
             ByteArrayOutputStream buffer=new ByteArrayOutputStream()) {
            byte[] part=new byte[4096];
            int n;
            while((n=input.read(part))!=-1) {
                buffer.write(part,0,n);
                if(buffer.size()>80000) throw new IllegalStateException("Bundled Termux bridge too large");
            }
            encoded=Base64.encodeToString(buffer.toByteArray(),Base64.NO_WRAP);
        } catch (Exception e) {
            callback.failed("Bundled native tools unavailable: "+e.getMessage());
            return;
        }
        String sessionCode;
        try(InputStream input=activity.getAssets().open("termux-sessions.mjs");
            ByteArrayOutputStream buf=new ByteArrayOutputStream()){
            byte[] block=new byte[4096];int n;
            while((n=input.read(block))!=-1){buf.write(block,0,n);if(buf.size()>80000)throw new IllegalStateException("Session engine too large");}
            sessionCode=Base64.encodeToString(buf.toByteArray(),Base64.NO_WRAP);
        }catch(Exception e){callback.failed("Session engine missing: "+e.getMessage());return;}
        String script="set -eu; "
            + "command -v node >/dev/null 2>&1 || pkg install -y nodejs-lts 1>&2; "
            + "mkdir -p \"$HOME/m5-native/scripts\" \"$HOME/m5-workspace\"; "
            + "printf '%s' '"+encoded+"' | base64 -d > \"$HOME/m5-native/scripts/native-bridge.mjs.m5new\"; "
            + "mv \"$HOME/m5-native/scripts/native-bridge.mjs.m5new\" \"$HOME/m5-native/scripts/native-bridge.mjs\"; "
            + "printf '%s' '"+sessionCode+"' | base64 -d > \"$HOME/m5-native/scripts/termux-sessions.mjs.m5new\"; "
            + "mv \"$HOME/m5-native/scripts/termux-sessions.mjs.m5new\" \"$HOME/m5-native/scripts/termux-sessions.mjs\"; "
            + "node \"$HOME/m5-native/scripts/native-bridge.mjs\" ping";
        sessionReady=false;
        execute(PREFIX + "bash", new String[]{"-lc",script}, new Callback(){
            @Override public void finished(JSONObject data) {
                sessionReady=data.optBoolean("ready",false);
                if(sessionReady) callback.finished(data);
                else callback.failed("Termux did not confirm the native workspace");
            }
            @Override public void failed(String reason) {
                sessionReady=false;
                callback.failed(reason);
            }
        },120000);
    }

    /** Launch a durable Termux command session. Requires approval at the caller. */
    public void session(String operation,String[] args,Callback callback){
        if(!isConnected()){callback.failed("Connect Termux first");return;}
        if(!operation.matches("start|read|terminate")){callback.failed("Unsupported session operation");return;}
        String[] params=new String[args.length+2];
        params[0]=HOME+"/m5-native/scripts/termux-sessions.mjs";
        params[1]=operation;
        System.arraycopy(args,0,params,2,args.length);
        execute(PREFIX+"node",params,callback,75000);
    }

    public void tool(String action, String[] arguments, Callback callback) {
        if (!isConnected()) { callback.failed("Termux is offline. Use Connect Termux in the app header."); return; }
        if (!action.matches("ping|list|read|write|search|git_status|git_diff|shell|scaffold|check_file|run_file|audit_project|history_list|history_compare|history_restore|git_project_status|git_project_diff|git_project_init|patch_apply|browser_observe")) {
            callback.failed("Unknown Termux action");
            return;
        }
        String[] full = new String[arguments.length + 2];
        full[0] = HOME + "/m5-native/scripts/native-bridge.mjs";
        full[1] = action;
        System.arraycopy(arguments, 0, full, 2, arguments.length);
        // Node is launched once per action; it is not a web server or daemon.
        execute(PREFIX + "node", full, callback, 75000);
    }

    private void execute(String program, String[] arguments, Callback callback, int timeoutMs) {
        if (!readyForUse()) {
            callback.failed("Termux is missing or the RUN_COMMAND permission has not been granted.");
            return;
        }
        int requestId = NEXT.incrementAndGet();
        REQUESTS.put(requestId, callback);
        MAIN.postDelayed(() -> {
            Callback waiting = REQUESTS.remove(requestId);
            if (waiting != null) waiting.failed("Termux response timed out. Check battery restrictions and Termux status.");
        }, timeoutMs);
        try {
            Intent receiver = new Intent(activity, TermuxResultReceiver.class);
            receiver.setAction("com.musabgpt.m5.NATIVE_RESULT." + requestId);
            receiver.putExtra("m5_request_id", requestId);
            PendingIntent pending = PendingIntent.getBroadcast(activity, requestId, receiver,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
            Intent command = new Intent("com.termux.RUN_COMMAND");
            command.setClassName("com.termux", "com.termux.app.RunCommandService");
            command.putExtra("com.termux.RUN_COMMAND_PATH", program);
            command.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments);
            command.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME);
            command.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            command.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
            if (Build.VERSION.SDK_INT >= 26) activity.startForegroundService(command);
            else activity.startService(command);
        } catch (Exception e) {
            Callback pending = REQUESTS.remove(requestId);
            if (pending != null) pending.failed("Termux invocation failed: " + e.getMessage());
        }
    }

    static void completed(Context context, Intent intent) {
        if (intent == null) return;
        int requestId = intent.getIntExtra("m5_request_id", -1);
        Callback callback = REQUESTS.remove(requestId);
        if (callback == null) return;
        Bundle result = intent.getBundleExtra("result");
        if (result == null) { callback.failed("Termux result bundle is missing"); return; }
        int err = result.getInt("err", Activity.RESULT_OK);
        int exit = result.getInt("exitCode", -1);
        String stdout = result.getString("stdout", "");
        String stderr = result.getString("stderr", "");
        String original = result.getString("stdout_original_length", "");
        if (original.isEmpty()) {
            Object value = result.get("stdout_original_length");
            if (value != null) original = String.valueOf(value);
        }
        try {
            if (!original.isEmpty() && Integer.parseInt(original) > Math.max(stdout.length(), stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)) {
                callback.failed("Termux output was truncated; no data was accepted.");
                return;
            }
        } catch (NumberFormatException error) { callback.failed("Malformed Termux output length"); return; }
        if (err != Activity.RESULT_OK || exit != 0) {
            String reason = result.getString("errmsg", "");
            if (reason.isEmpty() && !stdout.trim().isEmpty()) {
                try {
                    JSONObject toolFailure = new JSONObject(stdout.trim());
                    if (!toolFailure.optBoolean("ok", true))
                        reason = toolFailure.optString("error", "");
                } catch (Exception ignored) { /* keep Termux error text */ }
            }
            if (reason.isEmpty()) reason = stderr;
            if (reason.isEmpty()) reason = "Termux failed (exit=" + exit + ", err=" + err + ")";
            callback.failed(reason.length() > 500 ? reason.substring(0, 500) : reason);
            return;
        }
        try {
            JSONObject value = new JSONObject(stdout.trim());
            if (!value.optBoolean("ok", false)) {
                callback.failed(value.optString("error", "Termux command failed"));
                return;
            }
            callback.finished(value);
        } catch (Exception e) {
            callback.failed("Termux did not return valid one-shot JSON: " + e.getMessage());
        }
    }
}
