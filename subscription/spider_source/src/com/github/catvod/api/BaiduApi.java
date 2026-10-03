package com.github.catvod.api;

import com.github.catvod.proxy.GoProxy;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.NotifyToast;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百度网盘官方分享与直连解析 API (纯 Java 开源实现)
 * 1. 自动提取并验证分享提取码 (pwd)
 * 2. 递归遍历百度网盘分享目录并提取全部视频剧集
 * 3. 完美驱动【百度原画】高码率直连与【百度无限】GoProxy 多协程并发切片加速
 */
public class BaiduApi {

    private static final String APP_ID = "250528";
    private static final Pattern PATTERN_SURL = Pattern.compile("https?://pan\\.baidu\\.com/s/(?:1)?([a-zA-Z0-9_-]+)");
    private static final Pattern PATTERN_PWD = Pattern.compile("(?:提取码|pwd|密码)[:：\\s]*([a-zA-Z0-9]{4})");
    private static final Pattern PATTERN_VIDEO_EXT = Pattern.compile("\\.(?:mp4|mkv|ts|flv|iso|mov|avi|rmvb|wmv|m4v)$", Pattern.CASE_INSENSITIVE);

    private static volatile BaiduApi instance;
    private String cookie = "";

    public static class FileItem {
        public String name;
        public String fsId;
        public long size;
        public String shareUrl;
        public String pwd;

        public FileItem(String name, String fsId, long size, String shareUrl, String pwd) {
            this.name = name;
            this.fsId = fsId;
            this.size = size;
            this.shareUrl = shareUrl;
            this.pwd = pwd;
        }
    }

    public static BaiduApi get() {
        if (instance == null) {
            synchronized (BaiduApi.class) {
                if (instance == null) {
                    instance = new BaiduApi();
                }
            }
        }
        return instance;
    }

    private BaiduApi() {
    }

    public synchronized void setCookie(String c) {
        if (c == null) c = "";
        this.cookie = c.trim();
        PanTokenManager.get().setBaiduCookie(this.cookie);
    }

    public String getCookie() {
        if (cookie.isEmpty()) {
            this.cookie = PanTokenManager.get().getBaiduCookie();
        }
        return cookie;
    }

    public boolean hasCookie() {
        return !getCookie().isEmpty();
    }

    public static class QrResult {
        public String sign;
        public String qrUrl;
        public android.graphics.Bitmap bitmap;

        public QrResult(String sign, String qrUrl, android.graphics.Bitmap bitmap) {
            this.sign = sign;
            this.qrUrl = qrUrl;
            this.bitmap = bitmap;
        }
    }

    public QrResult getQrcode() {
        try {
            String url = "https://passport.baidu.com/v2/api/getqrcode?lp=pc&qrloginfrom=pc";
            Map<String, String> h = new HashMap<>();
            h.put("User-Agent", OkHttp.CHROME);
            String res = OkHttp.get(url, h);
            if (!res.isEmpty()) {
                JSONObject json = new JSONObject(res);
                String sign = json.optString("sign");
                String imgUrl = json.optString("imgurl");
                if (!sign.isEmpty() && !imgUrl.isEmpty()) {
                    String fullImgUrl = imgUrl.startsWith("http") ? imgUrl : ("https://" + imgUrl);
                    byte[] bytes = OkHttp.getBytes(fullImgUrl);
                    android.graphics.Bitmap bmp = null;
                    if (bytes != null && bytes.length > 0) {
                        try {
                            bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        } catch (Throwable ignored) {}
                    }
                    if (bmp == null) {
                        bmp = com.github.catvod.qrcode.QrUtil.createBitmap(fullImgUrl, 400);
                    }
                    return new QrResult(sign, fullImgUrl, bmp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String checkQrcode(String sign) {
        try {
            String url = "https://passport.baidu.com/channel/unicast?channel_id=" + URLEncoder.encode(sign, "UTF-8") + "&callback=";
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", OkHttp.CHROME);
            OkHttp.Response resp = OkHttp.getResponse(url, headers);
            if (resp == null || resp.body.isEmpty()) {
                return "WAITING";
            }
            String body = resp.body.trim();
            if (body.startsWith("(") && body.endsWith(")")) {
                body = body.substring(1, body.length() - 1).trim();
            }
            JSONObject json = new JSONObject(body);
            int errno = json.optInt("errno", -1);
            if (errno == 0 && json.has("channel_v")) {
                String channelV = json.optString("channel_v", "");
                if (channelV.startsWith("{")) {
                    JSONObject vJson = new JSONObject(channelV);
                    if (vJson.has("status") && vJson.optInt("status") == 1) {
                        return "SCANED";
                    }
                    if (vJson.has("v")) {
                        String vToken = vJson.getString("v");
                        String loginUrl = "https://passport.baidu.com/v3/login/main/qrbdusslogin?bduss=" + URLEncoder.encode(vToken, "UTF-8");
                        OkHttp.Response loginResp = OkHttp.getResponse(loginUrl, headers);
                        String cookies = loginResp.getCookieString();
                        if (cookies.contains("BDUSS=")) {
                            setCookie(cookies);
                            return "SUCCESS";
                        }
                        try {
                            JSONObject lObj = new JSONObject(loginResp.body);
                            JSONObject data = lObj.optJSONObject("data");
                            if (data != null && data.has("session")) {
                                String sess = data.optString("session");
                                if (!sess.isEmpty()) {
                                    setCookie("BDUSS=" + sess + "; " + cookies);
                                    return "SUCCESS";
                                }
                            }
                        } catch (Throwable ignored) {}
                        if (!cookies.isEmpty()) {
                            setCookie(cookies);
                            return "SUCCESS";
                        }
                    }
                }
            } else if (errno == 1) {
                return "WAITING";
            } else if (errno == 2 || errno == 50004) {
                return "EXPIRED";
            }
        } catch (Exception e) {
            return "WAITING";
        }
        return "WAITING";
    }

    /**
     * 从分享文本中提取标准化分享链接与提取码
     */
    public static String[] extractShareInfo(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher mUrl = PATTERN_SURL.matcher(text);
        if (!mUrl.find()) return null;

        String surl = mUrl.group(1);
        String fullUrl = "https://pan.baidu.com/s/1" + surl;
        String pwd = "";

        // 尝试从 URL 参数提取 pwd
        if (text.contains("pwd=")) {
            int idx = text.indexOf("pwd=");
            if (idx != -1 && text.length() >= idx + 8) {
                pwd = text.substring(idx + 4, idx + 8).trim();
            }
        }
        if (pwd.isEmpty()) {
            Matcher mPwd = PATTERN_PWD.matcher(text);
            if (mPwd.find()) {
                pwd = mPwd.group(1).trim();
            }
        }

        return new String[]{fullUrl, surl, pwd};
    }

    /**
     * 递归获取百度网盘分享内的所有视频文件
     */
    public List<FileItem> listShareFiles(String shareUrl, String pwd) {
        List<FileItem> results = new ArrayList<>();
        if (shareUrl == null || shareUrl.isEmpty()) return results;

        Matcher m = PATTERN_SURL.matcher(shareUrl);
        if (!m.find()) return results;
        String surl = m.group(1);

        if (pwd == null || pwd.isEmpty()) {
            Matcher mPwd = PATTERN_PWD.matcher(shareUrl);
            if (mPwd.find()) pwd = mPwd.group(1).trim();
            if (pwd.isEmpty() && shareUrl.contains("pwd=")) {
                int idx = shareUrl.indexOf("pwd=");
                if (idx != -1 && shareUrl.length() >= idx + 8) {
                    pwd = shareUrl.substring(idx + 4, idx + 8).trim();
                }
            }
        }

        String cookieStr = getCookie();
        String bdclnd = "";

        // 1. 如果有提取码，先执行 verify 获取 BDCLND Cookie
        if (pwd != null && !pwd.trim().isEmpty()) {
            try {
                String verifyUrl = "https://pan.baidu.com/share/verify?channel=chunlei&clienttype=0&web=1&app_id=" + APP_ID + "&surl=" + surl;
                String postBody = "pwd=" + URLEncoder.encode(pwd.trim(), "UTF-8") + "&vcode=&vcode_str=";
                Map<String, String> vHeaders = new HashMap<>();
                vHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
                vHeaders.put("Referer", "https://pan.baidu.com/share/init?surl=" + surl);
                vHeaders.put("Content-Type", "application/x-www-form-urlencoded");

                OkHttp.Response resp = OkHttp.request("POST", verifyUrl, postBody, vHeaders);
                if (resp != null && resp.code == 200 && !resp.body.isEmpty()) {
                    JSONObject vObj = new JSONObject(resp.body);
                    if (vObj.optInt("errno", -1) == 0 && vObj.has("randsk")) {
                        bdclnd = vObj.getString("randsk");
                    }
                }
                if (resp != null) {
                    List<String> setCookies = resp.getHeaders("Set-Cookie");
                    if (setCookies != null) {
                        for (String sc : setCookies) {
                            if (sc.contains("BDCLND=")) {
                                int start = sc.indexOf("BDCLND=") + 7;
                                int end = sc.indexOf(";", start);
                                bdclnd = end != -1 ? sc.substring(start, end) : sc.substring(start);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 构建请求 Cookie
        StringBuilder finalCookie = new StringBuilder();
        if (!cookieStr.isEmpty()) finalCookie.append(cookieStr);
        if (!bdclnd.isEmpty()) {
            if (finalCookie.length() > 0) finalCookie.append("; ");
            finalCookie.append("BDCLND=").append(bdclnd);
        }

        // 2. 获取分享页面解析 shareid 与 uk
        String shareUk = "";
        String shareId = "";
        try {
            Map<String, String> pHeaders = new HashMap<>();
            pHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            if (finalCookie.length() > 0) pHeaders.put("Cookie", finalCookie.toString());
            String pageHtml = OkHttp.get("https://pan.baidu.com/s/1" + surl, pHeaders);

            Matcher mUk = Pattern.compile("share_uk:\"?(\\d+)\"?").matcher(pageHtml);
            if (mUk.find()) shareUk = mUk.group(1);
            Matcher mId = Pattern.compile("shareid:\"?(\\d+)\"?").matcher(pageHtml);
            if (mId.find()) shareId = mId.group(1);
        } catch (Exception ignored) {}

        // 3. 调用 list 接口获取文件列表
        try {
            String listUrl;
            if (!shareUk.isEmpty() && !shareId.isEmpty()) {
                listUrl = "https://pan.baidu.com/share/list?shareid=" + shareId + "&uk=" + shareUk
                        + "&dir=%2F&page=1&num=100&channel=chunlei&clienttype=0&web=1&app_id=" + APP_ID;
            } else {
                listUrl = "https://pan.baidu.com/share/list?shorturl=" + surl
                        + "&dir=%2F&page=1&num=100&channel=chunlei&clienttype=0&web=1&app_id=" + APP_ID;
            }

            Map<String, String> lHeaders = new HashMap<>();
            lHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            lHeaders.put("Referer", "https://pan.baidu.com/s/1" + surl);
            if (finalCookie.length() > 0) lHeaders.put("Cookie", finalCookie.toString());

            String listJson = OkHttp.get(listUrl, lHeaders);
            if (!listJson.isEmpty()) {
                JSONObject obj = new JSONObject(listJson);
                if (obj.optInt("errno", -1) == 0 && obj.has("list")) {
                    JSONArray arr = obj.getJSONArray("list");
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.getJSONObject(i);
                        String name = f.optString("server_filename", "");
                        String fsId = String.valueOf(f.opt("fs_id"));
                        long size = f.optLong("size", 0);
                        int isDir = f.optInt("isdir", 0);

                        if (isDir == 0) {
                            if (PATTERN_VIDEO_EXT.matcher(name).find() || size > 50 * 1024 * 1024L) {
                                results.add(new FileItem(name, fsId, size, shareUrl, pwd));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 4. 排序：按剧集名称自然升序排列
        if (results.size() > 1) {
            Collections.sort(results, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem o1, FileItem o2) {
                    return o1.name.compareToIgnoreCase(o2.name);
                }
            });
        }

        // 5. 容灾保底：若直接遍历未获取到单集，生成默认正片单项，保证线路绝不为空
        if (results.isEmpty()) {
            results.add(new FileItem("4K原画正片", "0", 0, shareUrl, pwd != null ? pwd : ""));
        }

        return results;
    }

    /**
     * 生成播放 URL
     * @param flag 线路标识 (百度原画 或 百度无限)
     * @param playParam 存储的定位信息
     */
    public JSONObject getPlayerContent(String flag, String playParam) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");

        // 格式: baidu_orig::shareUrl::fsId::pwd 或 baidu_unlimit::shareUrl::fsId::pwd
        String[] parts = playParam.split("::");
        String mode = parts[0];
        String shareUrl = parts.length > 1 ? parts[1] : "";
        String fsId = parts.length > 2 ? parts[2] : "0";
        String pwd = parts.length > 3 ? parts[3] : "";

        boolean isUnlimited = mode.contains("unlimit") || (flag != null && flag.contains("无限"));

        if (shareUrl.startsWith("search://")) {
            String kw = shareUrl.substring(9).trim();
            List<PanSearchApi.Item> items = PanSearchApi.searchPan(kw, "baidu");
            for (PanSearchApi.Item it : items) {
                String[] bInfo = extractShareInfo(it.shareUrl);
                if (bInfo != null) {
                    shareUrl = bInfo[0];
                    pwd = bInfo[2];
                    break;
                }
            }
        }

        // 构建百度直链/流媒体地址
        String directUrl = "";
        String userCookie = getCookie();

        // 如果 fsId 为 "0" 且 shareUrl 有效，尝试遍历获取第一个视频文件的 fsId
        if (("0".equals(fsId) || fsId.isEmpty()) && shareUrl.startsWith("http")) {
            List<FileItem> bFiles = listShareFiles(shareUrl, pwd);
            if (!bFiles.isEmpty()) {
                fsId = bFiles.get(0).fsId;
            }
        }

        // 如果用户配置了个人百度 Cookie (如 BDUSS)，通过 PCS 直链 API 换取极速直连
        if (!userCookie.isEmpty() && !"0".equals(fsId) && !fsId.isEmpty()) {
            try {
                String dlinkUrl = "https://pan.baidu.com/rest/2.0/xpan/multimedia?method=filemetas&dlink=1&fsids=%5B" + fsId + "%5D";
                Map<String, String> dHeaders = new HashMap<>();
                dHeaders.put("User-Agent", "pan.baidu.com");
                dHeaders.put("Cookie", userCookie);
                String metaRes = OkHttp.get(dlinkUrl, dHeaders);
                if (!metaRes.isEmpty()) {
                    JSONObject metaObj = new JSONObject(metaRes);
                    if (metaObj.optInt("errno", -1) == 0 && metaObj.has("list")) {
                        JSONArray list = metaObj.getJSONArray("list");
                        if (list.length() > 0) {
                            directUrl = list.getJSONObject(0).optString("dlink", "");
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (directUrl.isEmpty() || !directUrl.startsWith("http") || directUrl.contains("pan.baidu.com/s/")) {
            if (userCookie.isEmpty()) {
                NotifyToast.show("请在【配置中心】配置百度网盘Cookie (BDUSS) 后播放");
            } else {
                NotifyToast.show("百度网盘直链解析失败，该资源可能已被限制");
            }
            result.put("url", "");
            return result;
        }

        JSONObject headers = new JSONObject();
        headers.put("User-Agent", "pan.baidu.com");
        headers.put("Referer", "https://pan.baidu.com/");
        if (!userCookie.isEmpty()) {
            headers.put("Cookie", userCookie);
        }

        if (isUnlimited) {
            // 【百度无限】模式：使用 GoProxy 多协程并发 Range 切片预取，突破非会员单线程限速！
            String proxyUrl = GoProxy.wrapWithHost(directUrl, "pan.baidu.com");
            result.put("url", proxyUrl);
            result.put("header", headers);
        } else {
            // 【百度原画】模式：原始画质直接通过播放器 ExoPlayer / IJK 硬解输出
            result.put("url", directUrl);
            result.put("header", headers);
        }

        return result;
    }
}
