package com.github.catvod.spider;

import android.content.Context;

public class Init {

    private static volatile Context mContext;

    public static void init(Context context) {
        if (context != null) {
            mContext = context;
        }
    }

    public static Context get() {
        if (mContext == null) {
            try {
                java.lang.reflect.Method m = Class.forName("android.app.ActivityThread").getMethod("currentApplication");
                mContext = (Context) m.invoke(null);
            } catch (Throwable ignored) {}
        }
        return mContext;
    }

    public static Object loader() {
        return new Object();
    }
}
