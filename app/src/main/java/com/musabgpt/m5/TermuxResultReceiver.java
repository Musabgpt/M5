package com.musabgpt.m5;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives the official Termux RUN_COMMAND result. TermuxConstants defines
 * TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE as "result" (NOT
 * "com.termux.RUN_COMMAND_RESULT"). The inner keys are stdout, stderr,
 * exitCode, err and errmsg. Never equate an intent dispatch with success.
 *
 * https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
 */
public final class TermuxResultReceiver extends BroadcastReceiver {
    static WeakReference<M5Activity> activity = new WeakReference<>(null);
    private static final String RESULT_BUNDLE = "result";
    private static final Pattern PAIR = Pattern.compile("(?:^|\\n)M5_PAIR=([a-f0-9]{64})(?:\\r?\\n|$)");

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        final Bundle result = intent.getBundleExtra(RESULT_BUNDLE);
        final M5Activity owner = activity.get();
        if (result == null) {
            if (owner != null) owner.onEngineFailure("Termux returned no result bundle. Check Termux version >= 0.109 and RUN_COMMAND permissions.");
            return;
        }
        final int error = result.getInt("err", Activity.RESULT_OK);
        final int exit = result.getInt("exitCode", -999);
        final String stdout = result.getString("stdout", "");
        final String stderr = result.getString("stderr", "");
        final String message = result.getString("errmsg", "");
        final int originalStdoutLength = parseLength(result.get("stdout_original_length"));
        if (error == Activity.RESULT_OK && exit == 0
                && originalStdoutLength <= stdout.length()) {
            Matcher found = PAIR.matcher(stdout);
            if (found.find()) {
                final String key = found.group(1);
                context.getSharedPreferences("m5-private", Context.MODE_PRIVATE)
                    .edit().putString("localPairKey", key).apply();
                if (owner != null) owner.onEngineReady(key);
                return;
            }
        }
        if (owner != null) {
            String diagnostic = error != Activity.RESULT_OK ? message
                : (exit != 0 ? stderr : "Missing or truncated M5 pairing receipt.");
            if (diagnostic == null || diagnostic.trim().isEmpty()) {
                diagnostic = "Termux startup did not complete (exit " + exit + ", err " + error + ").";
            }
            owner.onEngineFailure(diagnostic.substring(0, Math.min(400, diagnostic.length())));
        }
    }

    private static int parseLength(Object original) {
        if (original == null) return 0;
        try { return Integer.parseInt(String.valueOf(original)); }
        catch (NumberFormatException e) { return Integer.MAX_VALUE; }
    }
}
