package com.github.catvod.spider;

import android.content.Context;

public class Init {

    private static volatile Context mContext;

    public static void init(Context context) {
        mContext = context;
    }

    public static Context get() {
        return mContext;
    }

    public static Object loader() {
        return new Object();
    }
}
