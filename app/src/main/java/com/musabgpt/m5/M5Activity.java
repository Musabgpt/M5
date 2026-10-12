package com.musabgpt.m5;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M5's only Android window: first-use Termux consent then one local IDE WebView.
 * No embedded Arabic shell, floating overlay, root, ADB or unauthenticated bridge.
 */
public final class M5Activity extends Activity {
    private static final String PERMISSION = "com.termux.permission.RUN_COMMAND";
    private static final String ENGINE = "http://127.0.0.1:8765/";
    private static final AtomicInteger REQUESTS = new AtomicInteger(100);
    private LinearLayout root;
    private TextView status;
    private Button startButton;
    private WebView web;
    private boolean showingEditor = false;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(25,25,25));
        getWindow().setNavigationBarColor(Color.rgb(25,25,25));
        TermuxResultReceiver.activity = new java.lang.ref.WeakReference<>(this);
        showSetup("M5 uses Termux to execute real project commands locally. No second terminal window is required.");
        String prior = getSharedPreferences("m5-private", MODE_PRIVATE).getString("localPairKey", "");
        if (prior.matches("[a-f0-9]{64}")) openWorkbench(prior);
    }

    private void showSetup(String message) {
        showingEditor = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(34, 50, 34, 30);
        root.setBackgroundColor(Color.rgb(25,25,25));
        TextView heading = new TextView(this);
        heading.setText("M5  ·  AI Coding Workbench");
        heading.setTextSize(23);
        heading.setTextColor(Color.rgb(175,204,255));
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        status = new TextView(this);
        status.setText(message);
        status.setTextColor(Color.rgb(224,224,224));
        status.setTextSize(15);
        status.setPadding(0, 32, 0, 28);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        startButton = new Button(this);
        startButton.setText("Start M5 using Termux");
        startButton.setOnClickListener(v -> launchTermux());
        root.addView(startButton);
        TextView details = new TextView(this);
        details.setText("Requires official Termux, RUN_COMMAND permission and allow-external-apps=true. First setup downloads this repository and Node.js into Termux. Commands run with your explicit consent.");
        details.setTextColor(Color.rgb(170,170,170));
        details.setPadding(0, 30, 0, 0);
        root.addView(details);
        setContentView(root);
    }

    private void launchTermux() {
        if (getPackageManager().getLaunchIntentForPackage("com.termux") == null) {
            status.setText("Termux not detected. Install official Termux first, then reopen M5.");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Grant M5 permission to run commands in Termux, then retry.");
            requestPermissions(new String[]{PERMISSION}, 22);
            return;
        }
        startButton.setEnabled(false);
        status.setText("Preparing the M5 engine in Termux. No terminal window will be opened.");
        String script = "set -eu; "
                + "command -v git >/dev/null || pkg install -y git >/dev/null; "
                + "command -v node >/dev/null || pkg install -y nodejs-lts >/dev/null; "
                + "if [ ! -f \"$HOME/m5/server/index.mjs\" ]; then "
                + "git clone --depth 1 --branch feat/cursor-grade-workbench https://github.com/Musabgpt/M5.git \"$HOME/m5\"; fi; "
                + "cd \"$HOME/m5\"; mkdir -p \"$HOME/m5-workspace\"; "
                + "if ! (echo > /dev/tcp/127.0.0.1/8765) 2>/dev/null; then "
                + "nohup node server/index.mjs > \"$HOME/m5-server.log\" 2>&1 </dev/null & sleep 3; fi; "
                + "test -s \"$HOME/.m5/session-token\"; "
                + "printf 'M5_PAIR=%s\\n' \"$(cat \"$HOME/.m5/session-token\")\"";
        try {
            Intent callback = new Intent(this, TermuxResultReceiver.class);
            int id = REQUESTS.incrementAndGet();
            callback.setAction("com.musabgpt.m5.TERMUX_RESULT." + id);
            PendingIntent pending = PendingIntent.getBroadcast(this, id, callback,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            Intent request = new Intent("com.termux.RUN_COMMAND");
            request.setClassName("com.termux", "com.termux.app.RunCommandService");
            request.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            request.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", script});
            request.putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home");
            request.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            request.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pending);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(request); else startService(request);
        } catch (Exception e) {
            onEngineFailure("Termux launch rejected: " + e.getMessage() + ". Confirm allow-external-apps=true.");
        }
    }

    public void onEngineReady(String key) {
        runOnUiThread(() -> openWorkbench(key));
    }
    public void onEngineFailure(String error) {
        runOnUiThread(() -> {
            if (showingEditor) showSetup(error);
            else { status.setText("Engine blocked: " + error); startButton.setEnabled(true); }
        });
    }
    private void openWorkbench(String key) {
        showingEditor = true;
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        WebView.setWebContentsDebuggingEnabled(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !("http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()) && uri.getPort() == 8765);
            }
            @Override public void onReceivedError(WebView view, android.webkit.WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) runOnUiThread(() -> showSetup("M5 service is not responding. Tap Start M5 to restart its Termux engine."));
            }
        });
        setContentView(web);
        web.loadUrl(ENGINE + "?key=" + key);
    }
    @Override public void onBackPressed() {
        if (web != null && showingEditor && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        TermuxResultReceiver.activity.clear();
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
