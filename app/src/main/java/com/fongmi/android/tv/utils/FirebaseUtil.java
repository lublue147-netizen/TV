package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import com.fongmi.android.tv.BuildConfig;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class FirebaseUtil {

    private static volatile boolean initialized = false;

    public static void init(Context context) {
        if (initialized) return;
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCrashlyticsCollectionEnabled(true);
            crashlytics.sendUnsentReports();
            crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME);
            crashlytics.setCustomKey("version_code", BuildConfig.VERSION_CODE);
            crashlytics.setCustomKey("flavor", BuildConfig.FLAVOR);
            crashlytics.setCustomKey("device_model", Build.MODEL != null ? Build.MODEL : "unknown");
            crashlytics.setCustomKey("device_brand", Build.BRAND != null ? Build.BRAND : "unknown");
            crashlytics.setCustomKey("sdk_int", Build.VERSION.SDK_INT);
            if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                crashlytics.setCustomKey("cpu_abi", Build.SUPPORTED_ABIS[0]);
            }
        } catch (Throwable ignored) {
        }
        initialized = true;
    }

    public static void log(String message) {
        if (message == null) return;
        try {
            FirebaseCrashlytics.getInstance().log(message);
        } catch (Throwable ignored) {
        }
    }

    public static void setCustomKey(String key, String value) {
        if (key == null) return;
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value != null ? value : "");
        } catch (Throwable ignored) {
        }
    }

    public static void setCustomKey(String key, int value) {
        if (key == null) return;
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value);
        } catch (Throwable ignored) {
        }
    }

    public static void setCustomKey(String key, boolean value) {
        if (key == null) return;
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value);
        } catch (Throwable ignored) {
        }
    }

    public static void recordException(Throwable throwable) {
        if (throwable == null) return;
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable);
        } catch (Throwable ignored) {
        }
    }

    public static void recordException(String tag, Throwable throwable) {
        if (throwable == null) return;
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            if (tag != null) crashlytics.setCustomKey("error_tag", tag);
            crashlytics.recordException(throwable);
        } catch (Throwable ignored) {
        }
    }

    public static void recordSpiderError(String siteKey, String method, Throwable throwable) {
        if (throwable == null) return;
        try {
            FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
            crashlytics.setCustomKey("spider_site", siteKey != null ? siteKey : "unknown");
            crashlytics.setCustomKey("spider_method", method != null ? method : "unknown");
            crashlytics.recordException(throwable);
        } catch (Throwable ignored) {
        }
    }

    public static void logEvent(String name, Bundle params) {
        if (name == null) return;
        try {
            FirebaseCrashlytics.getInstance().log("Event: " + name + (params != null ? " " + params.toString() : ""));
        } catch (Throwable ignored) {
        }
    }

    public static void trackScreen(String screenName) {
        if (screenName == null) return;
        try {
            FirebaseCrashlytics.getInstance().log("Screen: " + screenName);
        } catch (Throwable ignored) {
        }
    }
}
