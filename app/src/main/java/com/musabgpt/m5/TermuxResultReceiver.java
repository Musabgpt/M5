package com.musabgpt.m5;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Private one-shot Termux result callback; never starts a local server. */
public final class TermuxResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        TermuxBridge.completed(context, intent);
    }
}
