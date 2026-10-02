package com.github.catvod.net.interceptor;

import androidx.annotation.NonNull;

import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RequestInterceptor implements Interceptor {

    private static volatile String defaultUserAgent = Util.CHROME;

    private final ConcurrentHashMap<String, String> authMap;

    public static void setDefaultUserAgent(String ua) {
        defaultUserAgent = (ua == null || ua.trim().isEmpty()) ? Util.CHROME : ua.trim();
    }

    public static String getDefaultUserAgent() {
        return defaultUserAgent;
    }

    public RequestInterceptor() {
        authMap = new ConcurrentHashMap<>();
    }

    public void clear() {
        authMap.clear();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        Request.Builder builder = request.newBuilder();
        HttpUrl url = request.url();
        checkHeaders(url, request, builder);
        checkAuth(url, builder);
        return chain.proceed(builder.build());
    }

    private void checkHeaders(HttpUrl url, Request request, Request.Builder builder) {
        if (request.header(HttpHeaders.USER_AGENT) == null) {
            builder.header(HttpHeaders.USER_AGENT, defaultUserAgent);
        }
        if (request.header(HttpHeaders.REFERER) == null) {
            builder.header(HttpHeaders.REFERER, url.scheme() + "://" + url.host() + "/");
        }
    }

    private void checkAuth(HttpUrl url, Request.Builder builder) {
        String host = url.host();
        String auth = url.queryParameter("auth");
        if (auth != null) authMap.put(host, auth);
        else if (authMap.containsKey(host)) builder.url(url.newBuilder().addQueryParameter("auth", authMap.get(host)).build());
    }
}
