package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.content.ContextWrapper;

import com.fongmi.android.tv.App;

public class SpiderContext extends ContextWrapper {

    private static volatile SpiderContext instance;

    public static SpiderContext get() {
        if (instance == null) {
            synchronized (SpiderContext.class) {
                if (instance == null) {
                    instance = new SpiderContext(App.get());
                }
            }
        }
        return instance;
    }

    public SpiderContext(Context base) {
        super(base);
    }

    @Override
    public String getPackageName() {
        return "com.fongmi.android.tv";
    }

    @Override
    public String getOpPackageName() {
        return "com.fongmi.android.tv";
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }
}
