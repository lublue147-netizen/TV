package com.github.catvod.api;

import com.github.catvod.proxy.GoProxy;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UC 网盘官方分享与直连解析 API (纯 Java 开源实现)
 * 基于阿里统一云存储架构 (ucpro)
 */
public class UcApi {

    private static final String BASE_URL = "https://drive-pc.uc.cn/1/clouddrive/";
    private static final String PR = "pr=ucpro&fr=pc";
    private static final Pattern PATTERN_UC_LINK = Pattern.compile("https?://drive\\.uc\\.cn/s/([a-zA-Z0-9]+)");
    private static final Pattern PATTERN_CODE = Pattern.compile("(?:code|pwd|密码)[:：=]?([a-zA-Z0-9]{4,})");
    private static final Pattern PATTERN_VIDEO_EXT = Pattern.compile("\\.(?:mp4|mkv|ts|flv|iso|mov|avi|rmvb|wmv|m4v)$", Pattern.CASE_INSENSITIVE);

    private static volatile UcApi instance;
    private String cookie = "";

    public static class FileItem {
        public String name;
        public String fid;
        public String shareFidToken;
        public long size;

        public FileItem(String name, String fid, String shareFidToken, long size) {
            this.name = name;
            this.fid = fid;
            this.shareFidToken = shareFidToken;
            this.size = size;
        }
    }

    public static UcApi get() {
        if (instance == null) {
            synchronized (UcApi.class) {
                if (instance == null) {
                    instance = new UcApi();
                }
            }
        }
        return instance;
    }

    private UcApi() {
    }

    public synchronized void setCookie(String c) {
        if (c == null) c = "";
        this.cookie = c.trim();
        PanTokenManager.get().setUcCookie(this.cookie);
    }

    public String getCookie() {
        if (cookie.isEmpty()) {
            this.cookie = PanTokenManager.get().getUcCookie();
        }
        return cookie;
    }

    public static String extractShareId(String url) {
        if (url == null) return null;
        Matcher m = PATTERN_UC_LINK.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    public String getShareToken(String shareId, String code) {
        try {
            String url = BASE_URL + "share/sharepage/token?" + PR;
            JSONObject body = new JSONObject();
            body.put("pwd_id", shareId);
            if (code != null && !code.isEmpty()) {
                body.put("passcode", code);
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Referer", "https://drive.uc.cn/");

            String res = OkHttp.post(url, body.toString(), headers);
            if (!res.isEmpty()) {
                JSONObject obj = new JSONObject(res);
                if (obj.optInt("code", -1) == 0 && obj.has("data")) {
                    return obj.getJSONObject("data").optString("stoken", "");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public List<FileItem> listShareFiles(String shareId, String stoken) {
        List<FileItem> results = new ArrayList<>();
        if (shareId == null || shareId.isEmpty()) return results;

        try {
            String url = BASE_URL + "share/sharepage/detail?" + PR + "&pwd_id=" + shareId + "&stoken=" + stoken + "&pdir_fid=0&page=1&size=100";
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://drive.uc.cn/");

            String res = OkHttp.get(url, headers);
            if (!res.isEmpty()) {
                JSONObject obj = new JSONObject(res);
                if (obj.optInt("code", -1) == 0 && obj.has("data")) {
                    JSONObject data = obj.getJSONObject("data");
                    JSONArray list = data.optJSONArray("list");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject f = list.getJSONObject(i);
                            String fid = f.optString("fid", "");
                            String name = f.optString("file_name", "");
                            String shareFidToken = f.optString("share_fid_token", "");
                            long size = f.optLong("size", 0);
                            String formatType = f.optString("format_type", "");

                            if ("video".equalsIgnoreCase(formatType) || PATTERN_VIDEO_EXT.matcher(name).find() || size > 30 * 1024 * 1024L) {
                                results.add(new FileItem(name, fid, shareFidToken, size));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (results.size() > 1) {
            Collections.sort(results, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem o1, FileItem o2) {
                    return o1.name.compareToIgnoreCase(o2.name);
                }
            });
        }

        if (results.isEmpty()) {
            results.add(new FileItem("4K原画正片", "0", "", 0));
        }

        return results;
    }

    public JSONObject getPlayerContent(String playParam) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");

        // 格式: uc::shareId::stoken::fid::shareFidToken
        String[] parts = playParam.split("::");
        String shareId = parts.length > 1 ? parts[1] : "";
        String stoken = parts.length > 2 ? parts[2] : "";
        String fid = parts.length > 3 ? parts[3] : "0";
        String shareFidToken = parts.length > 4 ? parts[4] : "";

        String downloadUrl = "";
        String cookieStr = getCookie();

        if (!stoken.isEmpty() && !"0".equals(fid)) {
            try {
                String url = BASE_URL + "share/download?" + PR;
                JSONObject body = new JSONObject();
                body.put("pwd_id", shareId);
                body.put("stoken", stoken);
                JSONArray fids = new JSONArray();
                fids.put(fid);
                body.put("fids", fids);

                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Referer", "https://drive.uc.cn/");
                if (!cookieStr.isEmpty()) headers.put("Cookie", cookieStr);

                String res = OkHttp.post(url, body.toString(), headers);
                if (!res.isEmpty()) {
                    JSONObject obj = new JSONObject(res);
                    if (obj.optInt("code", -1) == 0 && obj.has("data")) {
                        JSONArray dArr = obj.optJSONArray("data");
                        if (dArr != null && dArr.length() > 0) {
                            downloadUrl = dArr.getJSONObject(0).optString("download_url", "");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (downloadUrl.isEmpty()) {
            downloadUrl = "https://drive.uc.cn/s/" + shareId;
        }

        JSONObject headers = new JSONObject();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
        headers.put("Referer", "https://drive.uc.cn/");

        String proxyUrl = GoProxy.wrapWithHost(downloadUrl, "drive.uc.cn");
        result.put("url", proxyUrl);
        result.put("header", headers);

        return result;
    }
}
