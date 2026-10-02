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
