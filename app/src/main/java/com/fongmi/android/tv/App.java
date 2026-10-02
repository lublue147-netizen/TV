package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.utils.FirebaseUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.google.gson.Gson;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseUtil.init(this);
        installExceptionHandler();
        Init.set(com.fongmi.android.tv.api.loader.SpiderContext.get());
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
    }

    private void installExceptionHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            writeCrashLog(thread, ex);
            recordCrashlytics(thread, ex);
            if (isSpiderOrLoaderException(ex)) {
                ex.printStackTrace();
                return;
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex);
            }
        });
    }

    private static void recordCrashlytics(Thread thread, Throwable ex) {
        try {
            FirebaseUtil.setCustomKey("crash_thread", thread != null ? thread.getName() : "unknown");
            if (isSpiderOrLoaderException(ex)) {
                FirebaseUtil.setCustomKey("crash_type", "spider_loader");
                FirebaseUtil.recordException(ex);
            } else {
                FirebaseUtil.setCustomKey("crash_type", "fatal");
                FirebaseUtil.recordException(ex);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void writeCrashLog(Thread thread, Throwable ex) {
        try {
            java.io.File dir = App.get().getExternalFilesDir("crash");
            if (dir == null) dir = new java.io.File(App.get().getFilesDir(), "crash");
            if (!dir.exists()) dir.mkdirs();
            java.io.File logFile = new java.io.File(dir, "crash.log");
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            pw.println("Time: " + new java.util.Date());
            pw.println("Thread: " + (thread != null ? thread.getName() : "unknown"));
            ex.printStackTrace(pw);
            com.github.catvod.utils.Path.write(logFile, sw.toString().getBytes());
        } catch (Throwable ignored) {
        }
    }

    private static boolean isSpiderOrLoaderException(Throwable t) {
        while (t != null) {
            String msg = String.valueOf(t.getMessage());
            if (msg.contains("catvod") || msg.contains("Spider") || msg.contains("DexClassLoader") || msg.contains("wex")) {
                return true;
            }
            for (StackTraceElement element : t.getStackTrace()) {
                String cls = element.getClassName();
                if (cls.contains("catvod") || cls.contains("Spider") || cls.contains("JarLoader") || cls.contains("BaseLoader") || cls.contains("SiteApi") || cls.contains("SafeSpider")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) {
            this.activity = activity;
            FirebaseUtil.trackScreen(activity.getClass().getSimpleName());
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}