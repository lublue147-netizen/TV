package com.fongmi.android.tv.api.loader;

import android.app.Application;
import android.content.Context;

import com.fongmi.android.tv.App;

public class SpiderContext extends Application {

    private static volatile SpiderContext instance;

    public static SpiderContext get() {
        if (instance == null) {
            synchronized (SpiderContext.class) {
                if (instance == null) {
                    instance = new SpiderContext();
                }
            }
        }
        return instance;
    }

    public SpiderContext() {
        super();
        attachBaseContext(App.get());
    }

    private String getRealPackageName() {
        Context base = getBaseContext();
        if (base != null) return base.getPackageName();
        App app = App.get();
        return app != null ? app.getPackageName() : "com.lublue.android.tv";
    }

    @Override
    public String getPackageName() {
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (int i = 2; i < Math.min(stack.length, 12); i++) {
                String cls = stack[i].getClassName();
                if (cls.startsWith("android.") || cls.startsWith("com.android.") || cls.startsWith("androidx.")) {
                    return getRealPackageName();
                }
            }
        } catch (Throwable ignored) {
        }
        return "com.fongmi.android.tv";
    }

    @Override
    public String getOpPackageName() {
        return getRealPackageName();
    }

    @Override
    public Object getSystemService(String name) {
        Context base = getBaseContext();
        if (base != null) return base.getSystemService(name);
        App app = App.get();
        return app != null ? app.getSystemService(name) : super.getSystemService(name);
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }
}
