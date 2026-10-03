package com.github.catvod.api;

import android.content.Context;
import android.content.SharedPreferences;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 阿里云盘官方 API 纯 Java 开源实现
 * 支持：
 * 1. 官方二维码登录生成与扫码状态轮询授权
 * 2. 匿名免登录与授权全景目录递归提取
 * 3. 阿里云盘原画 / 4K 直连播放地址秒级解析
 */
public class AliYunApi {

    private static final String PREF_NAME = "pan_config";
    private static final String KEY_TOKEN = "ali_token";

    private static volatile AliYunApi instance;
    private String refreshToken = "";
    private String accessToken = "";

    public static AliYunApi get() {
        if (instance == null) {
            synchronized (AliYunApi.class) {
                if (instance == null) {
                    instance = new AliYunApi();
                }
            }
        }
        return instance;
    }

    private AliYunApi() {
        loadToken();
    }

    private void loadToken() {
        try {
            Context ctx = Init.get();
            if (ctx != null) {
                SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                this.refreshToken = sp.getString(KEY_TOKEN, "");
            }
        } catch (Throwable ignored) {}
    }

    public synchronized void setRefreshToken(String token) {
        if (token == null) token = "";
        this.refreshToken = token.trim();
        try {
            Context ctx = Init.get();
            if (ctx != null) {
                SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                sp.edit().putString(KEY_TOKEN, this.refreshToken).apply();
            }
        } catch (Throwable ignored) {}
    }

    public String getRefreshToken() {
        if (refreshToken.isEmpty()) loadToken();
        return refreshToken;
    }

    public boolean hasToken() {
        return !getRefreshToken().isEmpty();
    }

    private Map<String, String> getHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        h.put("Referer", "https://www.aliyundrive.com/");
        if (!accessToken.isEmpty()) {
            h.put("Authorization", "Bearer " + accessToken);
        }
        return h;
    }

    public static class QrResult {
        public String t;
        public String ck;
        public String codeContent;
        public String qrImage;

        public QrResult(String t, String ck, String codeContent) {
            this.t = t;
            this.ck = ck;
            this.codeContent = codeContent;
            try {
                this.qrImage = "https://api.qrserver.com/v1/create-qr-code/?size=450x450&margin=10&data="
                        + URLEncoder.encode(codeContent, "UTF-8");
            } catch (Exception e) {
                this.qrImage = codeContent;
            }
        }
    }

    /**
     * 生成阿里云盘 App 扫码登录凭证与二维码
     */
    public QrResult getQrcode() {
        try {
            String url = "https://passport.aliyundrive.com/newlogin/qrcode/generate.do?appName=aliyun_drive&fromSite=52&appName=aliyun_drive&appEntrance=web&isMobile=false&lang=zh_CN&returnUrl=&bizParams=&_bx-v=2.2.3";
            String res = OkHttp.post(url, "", getHeaders());
            JSONObject obj = new JSONObject(res);
            if (obj.optBoolean("success", false) && obj.has("content")) {
                JSONObject data = obj.getJSONObject("content").getJSONObject("data");
                String t = data.optString("t");
                String ck = data.optString("ck");
                String codeContent = data.optString("codeContent");
                return new QrResult(t, ck, codeContent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 轮询阿里云盘扫码状态
     * 返回：SUCCESS / SCANED / WAITING / EXPIRED
     */
    public String checkQrcode(String t, String ck) {
        try {
            String url = "https://passport.aliyundrive.com/newlogin/qrcode/query.do?appName=aliyun_drive&fromSite=52&_bx-v=2.2.3";
            String body = "t=" + URLEncoder.encode(t, "UTF-8")
                    + "&ck=" + URLEncoder.encode(ck, "UTF-8")
                    + "&appName=aliyun_drive&appEntrance=web&isMobile=false&lang=zh_CN";

            String res = OkHttp.post(url, body, getHeaders());
            JSONObject obj = new JSONObject(res);
            if (obj.optBoolean("success", false) && obj.has("content")) {
                JSONObject data = obj.getJSONObject("content").getJSONObject("data");
                String qrStatus = data.optString("qrCodeStatus", "");

                if ("CONFIRMED".equalsIgnoreCase(qrStatus)) {
                    String token = "";
                    if (data.has("bizExt")) {
                        try {
                            String bizExtStr = new String(java.util.Base64.getDecoder().decode(data.getString("bizExt")), StandardCharsets.UTF_8);
                            JSONObject bizJson = new JSONObject(bizExtStr);
                            if (bizJson.has("pds_login_result")) {
                                token = bizJson.getJSONObject("pds_login_result").optString("refreshToken");
                            }
                        } catch (Exception ignored) {}
                    }
                    if (token.isEmpty() && data.has("pds_login_result")) {
                        token = data.getJSONObject("pds_login_result").optString("refreshToken");
                    }
                    if (!token.isEmpty()) {
                        setRefreshToken(token);
                        refreshAccessToken();
                        return "SUCCESS";
                    }
                    return "SUCCESS";
                } else if ("SCANED".equalsIgnoreCase(qrStatus)) {
                    return "SCANED";
                } else if ("NEW".equalsIgnoreCase(qrStatus)) {
                    return "WAITING";
                } else {
                    return "EXPIRED";
                }
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
        return "WAITING";
    }

    /**
     * 刷新 AccessToken
     */
    public boolean refreshAccessToken() {
        if (!hasToken()) return false;
        try {
            String url = "https://auth.aliyundrive.com/v2/account/token";
            JSONObject body = new JSONObject();
            body.put("refresh_token", getRefreshToken());
            body.put("grant_type", "refresh_token");

            String res = OkHttp.postJson(url, body.toString(), getHeaders());
            JSONObject json = new JSONObject(res);
            if (json.has("access_token")) {
                this.accessToken = json.getString("access_token");
                if (json.has("refresh_token")) {
                    setRefreshToken(json.getString("refresh_token"));
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 获取分享令牌 (支持免登录匿名解析)
     */
    public String getShareToken(String shareId, String pwd) {
        try {
            String url = "https://api.aliyundrive.com/v2/share_link/get_share_token";
            JSONObject body = new JSONObject();
            body.put("share_id", shareId);
            body.put("share_pwd", pwd != null ? pwd : "");

            String res = OkHttp.postJson(url, body.toString(), getHeaders());
            JSONObject json = new JSONObject(res);
            if (json.has("share_token")) {
                return json.getString("share_token");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static class FileItem {
        public String name;
        public String fileId;
        public String category;
        public long size;

        public FileItem(String name, String fileId, String category, long size) {
            this.name = name;
            this.fileId = fileId;
            this.category = category;
            this.size = size;
        }
    }

    /**
     * 递归遍历阿里云盘分享文件夹提取视频剧集
     */
    public List<FileItem> listShareFiles(String shareId, String shareToken) {
        List<FileItem> videos = new ArrayList<>();
        if (shareToken == null || shareToken.isEmpty()) {
            shareToken = getShareToken(shareId, "");
        }
        if (shareToken.isEmpty()) return videos;

        Map<String, String> h = getHeaders();
        h.put("x-share-token", shareToken);

        Queue<String> folderQueue = new LinkedList<>();
        folderQueue.add("root");

        int depth = 0;
        while (!folderQueue.isEmpty() && depth < 50) {
            String folderId = folderQueue.poll();
            depth++;
            try {
                String marker = "";
                boolean hasMore = true;
                while (hasMore) {
                    String url = "https://api.aliyundrive.com/adrive/v3/file/list";
                    JSONObject body = new JSONObject();
                    body.put("share_id", shareId);
                    body.put("parent_file_id", folderId);
                    body.put("limit", 100);
                    body.put("order_by", "name");
                    body.put("order_direction", "ASC");
                    if (!marker.isEmpty()) {
                        body.put("marker", marker);
                    }

                    String res = OkHttp.postJson(url, body.toString(), h);
                    JSONObject json = new JSONObject(res);
                    JSONArray items = json.optJSONArray("items");
                    if (items == null || items.length() == 0) break;

                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        String type = item.optString("type", "");
                        String fId = item.optString("file_id");
                        String name = item.optString("name");

                        if ("folder".equalsIgnoreCase(type)) {
                            folderQueue.add(fId);
                        } else {
                            String cat = item.optString("category", "");
                            long size = item.optLong("size", 0);
                            if ("video".equalsIgnoreCase(cat) || isVideo(name)) {
                                videos.add(new FileItem(name, fId, cat, size));
                            }
                        }
                    }

                    marker = json.optString("next_marker", "");
                    if (marker.isEmpty()) {
                        hasMore = false;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        videos.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return videos;
    }

    /**
     * 获取视频直链播放 URL
     */
    public String getPlayUrl(String shareId, String shareToken, String fileId) {
        if (shareToken == null || shareToken.isEmpty()) {
            shareToken = getShareToken(shareId, "");
        }
        if (shareToken.isEmpty()) return "";

        try {
            Map<String, String> h = getHeaders();
            h.put("x-share-token", shareToken);

            String url = "https://api.aliyundrive.com/v2/file/get_share_link_download_url";
            JSONObject body = new JSONObject();
            body.put("share_id", shareId);
            body.put("file_id", fileId);
            body.put("expire_sec", 900);

            String res = OkHttp.postJson(url, body.toString(), h);
            JSONObject json = new JSONObject(res);
            if (json.has("download_url")) {
                return json.getString("download_url");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static boolean isVideo(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".ts")
                || lower.endsWith(".mov") || lower.endsWith(".flv") || lower.endsWith(".avi")
                || lower.endsWith(".webm") || lower.endsWith(".m2ts") || lower.endsWith(".m3u8");
    }
}
