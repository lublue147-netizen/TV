package com.github.catvod.spider;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import com.github.catvod.proxy.GoProxy;

public class Init {

    private static volatile Context mContext;
    private static volatile Activity mActivity;
    private static volatile boolean mRegistered = false;

    public static void init(Context context) {
        if (context != null) {
            mContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            if (context instanceof Activity) {
                mActivity = (Activity) context;
            }
            if (mContext instanceof Application && !mRegistered) {
                try {
                    ((Application) mContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                            mActivity = activity;
                        }

                        @Override
                        public void onActivityStarted(Activity activity) {
                            mActivity = activity;
                        }

                        @Override
                        public void onActivityResumed(Activity activity) {
                            mActivity = activity;
                        }

                        @Override
                        public void onActivityPaused(Activity activity) {}

                        @Override
                        public void onActivityStopped(Activity activity) {}

                        @Override
                        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                        @Override
                        public void onActivityDestroyed(Activity activity) {
                            if (mActivity == activity) mActivity = null;
                        }
                    });
                    mRegistered = true;
                } catch (Throwable ignored) {}
            }
        }

        // Start the local playback proxy once the application context is available.
        try {
            GoProxy.start();
        } catch (Throwable ignored) {}
    }

    public static Context get() {
        if (mContext == null) {
            try {
                Method m = Class.forName("android.app.ActivityThread").getMethod("currentApplication");
                mContext = (Context) m.invoke(null);
            } catch (Throwable ignored) {}
        }
        return mContext;
    }

    public static Activity getActivity() {
        try {
            Class<?> appClass = Class.forName("com.fongmi.android.tv.App");
            Method method = appClass.getMethod("activity");
            Activity act = (Activity) method.invoke(null);
            if (act != null && !act.isFinishing()) {
                mActivity = act;
                return act;
            }
        } catch (Throwable ignored) {}
        if (mActivity != null && !mActivity.isFinishing()) {
            return mActivity;
        }
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = activityThreadClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            Map<?, ?> activities = (Map<?, ?>) activitiesField.get(activityThread);
            if (activities != null) {
                for (Object activityRecord : activities.values()) {
                    Class<?> activityRecordClass = activityRecord.getClass();
                    Field pausedField = activityRecordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (!pausedField.getBoolean(activityRecord)) {
                        Field activityField = activityRecordClass.getDeclaredField("activity");
                        activityField.setAccessible(true);
                        Activity act = (Activity) activityField.get(activityRecord);
                        if (act != null && !act.isFinishing()) {
                            mActivity = act;
                            return act;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return mActivity;
    }

    public static Object loader() {
        return new Object();
    }
}
