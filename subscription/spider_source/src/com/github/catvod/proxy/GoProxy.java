package com.github.catvod.proxy;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.github.catvod.spider.Init;
import com.github.catvod.utils.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for Go-based Netdisk Acceleration Proxy and JNI .so library.
 */
public class GoProxy {

    private static final String TAG = "GoProxy";
    private static final int DEFAULT_PORT = 9978;
    private static volatile int sPort = 0;
    private static final AtomicBoolean sLoaded = new AtomicBoolean(false);
    private static final AtomicBoolean sStarted = new AtomicBoolean(false);

    // Native JNI functions implemented in libgoproxy.so
    public static native int nativeStart(int port);
    public static native void nativeStop();
    public static native int nativeGetPort();

    /**
     * Attempts to start the Go proxy service via JNI .so or local binary.
     */
    public static synchronized void start() {
        if (sStarted.get()) return;

        // 1. Try JNI .so loading
        if (tryLoadNative()) {
            try {
                int p = nativeStart(DEFAULT_PORT);
                if (p > 0) {
                    sPort = p;
                    sStarted.set(true);
                    Log.i(TAG, "GoProxy SO started successfully on port: " + sPort);
                    return;
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to start native GoProxy: " + t.getMessage());
            }
        }

        // 2. Check if a standalone daemon is already running on 127.0.0.1:9978
        if (checkHealth(DEFAULT_PORT)) {
            sPort = DEFAULT_PORT;
            sStarted.set(true);
            Log.i(TAG, "GoProxy daemon detected running on port: " + sPort);
            return;
        }

        // 3. Try to extract and launch executable binary if packaged in assets
        tryLaunchBinary();
    }

    private static boolean tryLoadNative() {
        if (sLoaded.get()) return true;
        try {
            System.loadLibrary("goproxy");
            sLoaded.set(true);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void tryLaunchBinary() {
        Context context = Init.get();
        if (context == null) return;

        try {
            File binFile = new File(context.getFilesDir(), "goproxy");
            if (binFile.exists() && binFile.canExecute()) {
                Runtime.getRuntime().exec(binFile.getAbsolutePath() + " -port " + DEFAULT_PORT);
                Thread.sleep(500);
                if (checkHealth(DEFAULT_PORT)) {
                    sPort = DEFAULT_PORT;
                    sStarted.set(true);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Cannot launch goproxy binary: " + t.getMessage());
        }
    }

    public static boolean isAlive() {
        int port = sPort > 0 ? sPort : DEFAULT_PORT;
        return checkHealth(port);
    }

    public static int getPort() {
        return sPort > 0 ? sPort : DEFAULT_PORT;
    }

    public static boolean checkHealth(int port) {
        try {
            URL url = new URL("http://127.0.0.1:" + port + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(600);
            conn.setReadTimeout(600);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Builds accelerated playback URL for ExoPlayer.
     */
    public static String getAcceleratedUrl(String rawUrl, Map<String, String> headers) {
        try {
            int port = getPort();
            StringBuilder sb = new StringBuilder();
            sb.append("http://127.0.0.1:").append(port).append("/play?url=")
              .append(URLEncoder.encode(rawUrl, "UTF-8"));
            if (headers != null && !headers.isEmpty()) {
                org.json.JSONObject obj = new org.json.JSONObject();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    obj.put(entry.getKey(), entry.getValue());
                }
                sb.append("&headers=").append(URLEncoder.encode(obj.toString(), "UTF-8"));
            }
            return sb.toString();
        } catch (Exception e) {
            return rawUrl;
        }
    }

    public static String wrap(String rawUrl) {
        return wrap(rawUrl, (Map<String, String>) null);
    }

    public static String wrapWithHost(String rawUrl, String hostOrReferer) {
        Map<String, String> headers = new HashMap<>();
        if (hostOrReferer != null && !hostOrReferer.isEmpty()) {
            if (hostOrReferer.contains("baidu")) {
                headers.put("User-Agent", "pan.baidu.com");
                headers.put("Referer", "https://pan.baidu.com/");
            } else if (hostOrReferer.contains("uc")) {
                headers.put("User-Agent", OkHttp.CHROME);
                headers.put("Referer", "https://drive.uc.cn/");
            } else if (hostOrReferer.contains("quark")) {
                headers.put("User-Agent", OkHttp.CHROME);
                headers.put("Referer", "https://pan.quark.cn/");
            } else {
                headers.put("User-Agent", OkHttp.CHROME);
                headers.put("Referer", hostOrReferer);
            }
        }
        return wrap(rawUrl, headers);
    }

    /**
     * Formats URL with TVBox proxy protocol fallback.
     */
    public static String wrap(String rawUrl, Map<String, String> headers) {
        if (isAlive()) {
            return getAcceleratedUrl(rawUrl, headers);
        }
        // Fallback to CatVod proxy:// schema
        try {
            return "proxy://do=pan&url=" + URLEncoder.encode(rawUrl, "UTF-8");
        } catch (Exception e) {
            return rawUrl;
        }
    }
}
