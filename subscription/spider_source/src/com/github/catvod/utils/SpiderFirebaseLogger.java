package com.github.catvod.utils;

import java.lang.reflect.Method;

/**
 * Bridge that dynamically reports logs and diagnostics to FirebaseCrashlytics / FirebaseUtil
 * in the host Android TV application process via reflection (zero compile-time dependency).
 */
public class SpiderFirebaseLogger {

    public static void log(String message) {
        if (message == null) return;
        try {
            Class<?> clazz = Class.forName("com.fongmi.android.tv.utils.FirebaseUtil");
            Method method = clazz.getMethod("log", String.class);
            method.invoke(null, "[Spider] " + message);
        } catch (Throwable ignored) {
        }
    }

    public static void recordSpiderError(String siteKey, String methodStr, Throwable t) {
        try {
            Class<?> clazz = Class.forName("com.fongmi.android.tv.utils.FirebaseUtil");
            Method method = clazz.getMethod("recordSpiderError", String.class, String.class, Throwable.class);
            method.invoke(null, siteKey != null ? siteKey : "Spider", methodStr != null ? methodStr : "error", t);
        } catch (Throwable ignored) {
        }
    }

    public static void recordPlaybackError(String siteKey, String flag, String urlOrId, String stage, Throwable t) {
        try {
            Class<?> clazz = Class.forName("com.fongmi.android.tv.utils.FirebaseUtil");
            Method method = clazz.getMethod("recordPlaybackError", String.class, String.class, String.class, String.class, Throwable.class);
            method.invoke(null, siteKey != null ? siteKey : "Spider", flag != null ? flag : "", urlOrId != null ? urlOrId : "", stage != null ? stage : "", t);
        } catch (Throwable ignored) {
        }
    }
}
