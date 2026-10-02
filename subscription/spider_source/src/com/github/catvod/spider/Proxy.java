package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.proxy.GoProxy;
import com.github.catvod.proxy.NetdiskStream;

import java.io.ByteArrayInputStream;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard CatVod / FongMi Proxy spider entrypoint.
 * Automatically delegates to high-performance GoProxy accelerator when available,
 * or gracefully falls back to Java chunk prefetching.
 */
public class Proxy {

    public static Object[] proxy(Map<String, String> params) {
        if (params == null || params.isEmpty()) return null;

        String targetUrl = params.get("url");
        if (TextUtils.isEmpty(targetUrl)) return null;

        try {
            targetUrl = URLDecoder.decode(targetUrl, "UTF-8");
        } catch (Exception ignored) {}

        Map<String, String> headers = extractHeaders(params);

        // 1. If GoProxy is running, redirect ExoPlayer to local GoProxy
        if (GoProxy.isAlive()) {
            String accelerated = GoProxy.getAcceleratedUrl(targetUrl, headers);
            return new Object[]{
                302,
                "text/plain",
                new ByteArrayInputStream(new byte[0]),
                Collections.singletonMap("Location", accelerated)
            };
        }

        // 2. Otherwise serve stream directly via Java streaming engine
        return NetdiskStream.stream(targetUrl, headers);
    }

    private static Map<String, String> extractHeaders(Map<String, String> params) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!"url".equalsIgnoreCase(key) && !"do".equalsIgnoreCase(key) && !"siteKey".equalsIgnoreCase(key)) {
                headers.put(key, entry.getValue());
            }
        }
        return headers;
    }
}
