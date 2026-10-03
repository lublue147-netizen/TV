package com.github.catvod.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class OkHttp {

    public static final String CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    public static final String MOBILE = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    public static class Response {
        public int code;
        public String body;
        public Map<String, List<String>> headers;

        public Response(int code, String body, Map<String, List<String>> headers) {
            this.code = code;
            this.body = body != null ? body : "";
            this.headers = headers != null ? headers : new HashMap<>();
        }

        public String getHeader(String name) {
            if (headers == null) return "";
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    List<String> list = entry.getValue();
                    if (list != null && !list.isEmpty()) return list.get(0);
                }
            }
            return "";
        }

        public List<String> getHeaders(String name) {
            List<String> result = new ArrayList<>();
            if (headers == null) return result;
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    if (entry.getValue() != null) result.addAll(entry.getValue());
                }
            }
            return result;
        }

        public String getCookieString() {
            List<String> setCookies = getHeaders("Set-Cookie");
            if (setCookies.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (String sc : setCookies) {
                String pair = sc.split(";")[0].trim();
                if (!pair.isEmpty()) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(pair);
                }
            }
            return sb.toString();
        }
    }

    public static String get(String urlStr) {
        return get(urlStr, null);
    }

    public static String get(String urlStr, Map<String, String> headers) {
        Response resp = request("GET", urlStr, null, headers);
        return resp.body;
    }

    public static Response getResponse(String urlStr, Map<String, String> headers) {
        return request("GET", urlStr, null, headers);
    }

    public static String post(String urlStr, String body, Map<String, String> headers) {
        Response resp = request("POST", urlStr, body, headers);
        return resp.body;
    }

    public static String postJson(String urlStr, String jsonBody, Map<String, String> headers) {
        Map<String, String> h = headers != null ? new HashMap<>(headers) : new HashMap<>();
        h.put("Content-Type", "application/json; charset=UTF-8");
        Response resp = request("POST", urlStr, jsonBody, h);
        return resp.body;
    }

    public static Response request(String method, String urlStr, String body, Map<String, String> headers) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", CHROME);
            conn.setRequestProperty("Referer", url.getProtocol() + "://" + url.getHost() + "/");

            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }

            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                conn.setDoOutput(true);
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                }
                if (body != null) {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                    OutputStream os = conn.getOutputStream();
                    os.write(bytes);
                    os.flush();
                    os.close();
                }
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            String resBody = "";
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                resBody = sb.toString().trim();
            }

            Map<String, List<String>> resHeaders = conn.getHeaderFields();
            conn.disconnect();
            return new Response(code, resBody, resHeaders);
        } catch (Exception e) {
            return new Response(500, "", null);
        }
    }

    public static byte[] getBytes(String urlStr) {
        return getBytes(urlStr, null);
    }

    public static byte[] getBytes(String urlStr, Map<String, String> headers) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", CHROME);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    conn.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            int code = conn.getResponseCode();
            if (code >= 200 && code < 400) {
                InputStream is = conn.getInputStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                is.close();
                conn.disconnect();
                return baos.toByteArray();
            }
            conn.disconnect();
        } catch (Exception ignored) {}
        return null;
    }
}
