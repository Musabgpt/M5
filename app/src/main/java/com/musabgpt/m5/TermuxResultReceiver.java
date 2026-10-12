package com.musabgpt.m5;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import java.lang.ref.WeakReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Receives the official Termux RUN_COMMAND PendingIntent command result. */
public final class TermuxResultReceiver extends BroadcastReceiver {
    static WeakReference<M5Activity> activity = new WeakReference<>(null);
    private static final Pattern PAIR = Pattern.compile("M5_PAIR=([a-f0-9]{64})");

    @Override public void onReceive(Context context, Intent intent) {
        Bundle result = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT");
        int exit = result == null ? -999 : result.getInt("exitCode", -999);
        String stdout = result == null ? "" : result.getString("stdout", "");
        String stderr = result == null ? "Termux did not provide a result" : result.getString("stderr", "");
        Matcher found = PAIR.matcher(stdout == null ? "" : stdout);
        M5Activity owner = activity.get();
        if (exit == 0 && found.find()) {
            String key = found.group(1);
            context.getSharedPreferences("m5-private", Context.MODE_PRIVATE).edit().putString("localPairKey", key).apply();
            if (owner != null) owner.onEngineReady(key);
        } else {
            String error = (stderr == null ? "Unknown Termux error" : stderr).trim();
            if (owner != null) owner.onEngineFailure(error.isEmpty() ? "Termux command failed: " + exit : error);
        }
    }
}
