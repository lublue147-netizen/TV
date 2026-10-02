package com.github.catvod.proxy;

import android.text.TextUtils;
import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure Java fallback streaming engine for TVBox netdisk playback.
 */
public class NetdiskStream {

    private static final String TAG = "NetdiskStream";

    public static Object[] stream(String targetUrl, Map<String, String> requestHeaders) {
        try {
            URL url = new URL(targetUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);

            // Apply netdisk headers
            applyHeaders(conn, targetUrl, requestHeaders);

            int code = conn.getResponseCode();
            if (code >= 300 && code < 400) {
                String redirectUrl = conn.getHeaderField("Location");
                if (!TextUtils.isEmpty(redirectUrl)) {
                    conn.disconnect();
                    return stream(redirectUrl, requestHeaders);
                }
            }

            String contentType = conn.getContentType();
            if (TextUtils.isEmpty(contentType)) {
                contentType = "video/mp4";
            }

            InputStream is = conn.getInputStream();

            Map<String, String> respHeaders = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                    respHeaders.put(entry.getKey(), entry.getValue().get(0));
                }
            }

            // Ensure Range headers
            if (!respHeaders.containsKey("Accept-Ranges")) {
                respHeaders.put("Accept-Ranges", "bytes");
            }
            respHeaders.put("Access-Control-Allow-Origin", "*");

            return new Object[]{ code, contentType, is, respHeaders };
        } catch (Exception e) {
            Log.e(TAG, "NetdiskStream error: " + e.getMessage(), e);
            return null;
        }
    }

    private static void applyHeaders(HttpURLConnection conn, String targetUrl, Map<String, String> customHeaders) {
        String lower = targetUrl.toLowerCase();

        if (lower.contains("quark.cn")) {
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://pan.quark.cn/");
        } else if (lower.contains("aliyundrive") || lower.contains("alipan") || lower.contains("aliyuncs.com")) {
            conn.setRequestProperty("Referer", "https://www.aliyundrive.com/");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        } else if (lower.contains("115.com") || lower.contains("anxia.com")) {
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 115disk/30.1.0");
            conn.setRequestProperty("Referer", "https://115.com/");
        } else if (lower.contains("baidupcs.com") || lower.contains("pan.baidu.com")) {
            conn.setRequestProperty("User-Agent", "pan.baidu.com");
        } else {
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        }

        if (customHeaders != null) {
            for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                if (!TextUtils.isEmpty(entry.getKey()) && !TextUtils.isEmpty(entry.getValue())) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
