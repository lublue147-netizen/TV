package com.github.catvod.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.github.catvod.spider.Init;

public class NotifyToast {

    private static volatile String lastMessage = "";
    private static volatile long lastTime = 0;

    public static void show(final String message) {
        if (message == null || message.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        // Prevent duplicate spam within 2 seconds
        if (message.equals(lastMessage) && (now - lastTime < 2000)) {
            return;
        }
        lastMessage = message;
        lastTime = now;

        try {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Context ctx = Init.get();
                        if (ctx != null) {
                            Toast.makeText(ctx.getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        }
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }
}
