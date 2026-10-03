package com.github.catvod.api;

import com.github.catvod.proxy.GoProxy;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.NotifyToast;
import com.github.catvod.utils.OkHttp;
import com.github.catvod.utils.SpiderFirebaseLogger;
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
        public String shareUk;
        public String shareId;

        public FileItem(String name, String fsId, long size, String shareUrl, String pwd, String shareUk, String shareId) {
            this.name = name;
            this.fsId = fsId;
            this.size = size;
            this.shareUrl = shareUrl;
            this.pwd = pwd;
            this.shareUk = shareUk != null ? shareUk : "";
            this.shareId = shareId != null ? shareId : "";
        }

        public FileItem(String name, String fsId, long size, String shareUrl, String pwd) {
            this(name, fsId, size, shareUrl, pwd, "", "");
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
        Map<String, String> cookieJar = new LinkedHashMap<>();
        if (!cookieStr.isEmpty()) {
            for (String part : cookieStr.split(";")) {
                String pair = part.trim();
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    cookieJar.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
                }
            }
        }

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
                        cookieJar.put("BDCLND", vObj.getString("randsk"));
                    }
                }
                if (resp != null) {
                    List<String> setCookies = resp.getHeaders("Set-Cookie");
                    if (setCookies != null) {
                        for (String sc : setCookies) {
                            String pair = sc.split(";")[0].trim();
                            int eq = pair.indexOf('=');
                            if (eq > 0) {
                                cookieJar.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
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
        for (Map.Entry<String, String> entry : cookieJar.entrySet()) {
            if (finalCookie.length() > 0) finalCookie.append("; ");
            finalCookie.append(entry.getKey()).append("=").append(entry.getValue());
        }

        // 2. 获取分享页面解析 shareid 与 uk，并提取根目录 file_list
        String shareUk = "";
        String shareId = "";
        String pageHtml = "";
        Queue<String> dirQueue = new LinkedList<>();

        try {
            Map<String, String> pHeaders = new HashMap<>();
            pHeaders.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");
            if (finalCookie.length() > 0) pHeaders.put("Cookie", finalCookie.toString());
            pageHtml = OkHttp.get("https://pan.baidu.com/s/1" + surl, pHeaders);

            Matcher mUk = Pattern.compile("\"?share_uk\"?\\s*:\\s*\"?(\\d+)\"?").matcher(pageHtml);
            if (mUk.find()) shareUk = mUk.group(1);
            Matcher mId = Pattern.compile("\"?shareid\"?\\s*:\\s*\"?(\\d+)\"?").matcher(pageHtml);
            if (mId.find()) shareId = mId.group(1);

            // 提取网页内联的初始根文件列表
            Matcher mFileList = Pattern.compile("\"file_list\"\\s*:\\s*(\\[\\{.*?\\}\\])").matcher(pageHtml);
            if (mFileList.find()) {
                try {
                    JSONArray arr = new JSONArray(mFileList.group(1));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject f = arr.getJSONObject(i);
                        String name = f.optString("server_filename", "");
                        String fsId = String.valueOf(f.opt("fs_id"));
                        long size = f.optLong("size", 0);
                        int isDir = f.optInt("isdir", 0);
                        String path = f.optString("path", "");

                        if (isDir == 1) {
                            if (!path.isEmpty()) dirQueue.add(path);
                        } else {
                            if (isVideo(name, size)) {
                                results.add(new FileItem(name, fsId, size, shareUrl, pwd, shareUk, shareId));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        if (results.isEmpty() && dirQueue.isEmpty()) {
            dirQueue.add("/");
        }

        // 3. 递归遍历子目录获取所有视频文件
        int depth = 0;
        Set<String> visitedDirs = new HashSet<>();
        while (!dirQueue.isEmpty() && depth < 25) {
            String curDir = dirQueue.poll();
            if (visitedDirs.contains(curDir)) continue;
            visitedDirs.add(curDir);
            depth++;

            try {
                String listUrl;
                if (!shareUk.isEmpty() && !shareId.isEmpty()) {
                    listUrl = "https://pan.baidu.com/share/list?shareid=" + shareId + "&uk=" + shareUk
                            + "&dir=" + URLEncoder.encode(curDir, "UTF-8") + "&page=1&num=100&channel=chunlei&clienttype=0&web=1&app_id=" + APP_ID;
                } else {
                    listUrl = "https://pan.baidu.com/share/list?shorturl=" + surl
                            + "&dir=" + URLEncoder.encode(curDir, "UTF-8") + "&page=1&num=100&channel=chunlei&clienttype=0&web=1&app_id=" + APP_ID;
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
                            String path = f.optString("path", "");

                            if (isDir == 1) {
                                if (!path.isEmpty() && !visitedDirs.contains(path)) {
                                    dirQueue.add(path);
                                }
                            } else {
                                if (isVideo(name, size)) {
                                    results.add(new FileItem(name, fsId, size, shareUrl, pwd, shareUk, shareId));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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
            results.add(new FileItem("4K原画正片", "0", 0, shareUrl, pwd != null ? pwd : "", shareUk, shareId));
        }

        return results;
    }

    private static boolean isVideo(String name, long size) {
        if (name == null) return false;
        return PATTERN_VIDEO_EXT.matcher(name).find() || size > 30 * 1024 * 1024L;
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

        // 格式: baidu_orig::shareUrl::fsId::pwd::shareUk::shareId
        String[] parts = playParam.split("::");
        String mode = parts[0];
        String shareUrl = parts.length > 1 ? parts[1] : "";
        String fsId = parts.length > 2 ? parts[2] : "0";
        String pwd = parts.length > 3 ? parts[3] : "";
        String shareUk = parts.length > 4 ? parts[4] : "";
        String shareId = parts.length > 5 ? parts[5] : "";

        boolean isUnlimited = mode.contains("unlimit") || (flag != null && flag.contains("无限"));

        if (shareUrl.startsWith("search://")) {
            String kw = PanSearchApi.cleanKeyword(shareUrl.substring(9).trim());
            List<PanSearchApi.Item> items = PanSearchApi.searchPan(kw, "baidu");
            if (items.isEmpty()) {
                items = PanSearchApi.search(kw);
            }
            for (PanSearchApi.Item it : items) {
                String[] bInfo = extractShareInfo(it.shareUrl);
                if (bInfo == null) {
                    bInfo = extractShareInfo(it.content);
                }
                if (bInfo != null) {
                    List<FileItem> candFiles = listShareFiles(bInfo[0], bInfo[2]);
                    if (!candFiles.isEmpty()) {
                        shareUrl = bInfo[0];
                        pwd = bInfo[2];
                        fsId = candFiles.get(0).fsId;
                        shareUk = candFiles.get(0).shareUk;
                        shareId = candFiles.get(0).shareId;
                        break;
                    }
                }
            }
        }

        // 如果 fsId 为 "0" 且 shareUrl 有效，尝试遍历获取第一个视频文件的 fsId, shareUk, shareId
        if (("0".equals(fsId) || fsId.isEmpty() || shareUk.isEmpty() || shareId.isEmpty()) && shareUrl.startsWith("http")) {
            List<FileItem> bFiles = listShareFiles(shareUrl, pwd);
            for (FileItem fi : bFiles) {
                if (!"0".equals(fi.fsId)) {
                    fsId = fi.fsId;
                    if (shareUk.isEmpty()) shareUk = fi.shareUk;
                    if (shareId.isEmpty()) shareId = fi.shareId;
                    break;
                }
            }
        }

        // 构建百度直链/流媒体地址
        String directUrl = "";
        String userCookie = getCookie();

        // 如果用户配置了个人百度 Cookie (如 BDUSS)，通过 PCS 直链 API 换取极速直连
        if (!userCookie.isEmpty() && !"0".equals(fsId) && !fsId.isEmpty()) {
            String personalFsId = "";

            // 步骤 1: 调用 share/transfer 转存文件到个人云盘 /TV 目录
            if (!shareUk.isEmpty() && !shareId.isEmpty()) {
                try {
                    String transferUrl = "https://pan.baidu.com/share/transfer?shareid=" + shareId + "&from=" + shareUk + "&ondup=newcopy&async=1&channel=chunlei&web=1&app_id=" + APP_ID + "&clienttype=0";
                    Map<String, String> tHeaders = new HashMap<>();
                    tHeaders.put("User-Agent", "pan.baidu.com");
                    tHeaders.put("Referer", "https://pan.baidu.com/");
                    tHeaders.put("Cookie", userCookie);
                    tHeaders.put("Content-Type", "application/x-www-form-urlencoded");
                    String tBody = "fsidlist=[" + fsId + "]&path=/TV";

                    OkHttp.Response tResp = OkHttp.request("POST", transferUrl, tBody, tHeaders);
                    if (tResp != null && !tResp.body.isEmpty()) {
                        JSONObject tObj = new JSONObject(tResp.body);
                        if (tObj.optInt("errno", -1) == 0) {
                            JSONObject extra = tObj.optJSONObject("extra");
                            if (extra != null && extra.has("list")) {
                                JSONArray eList = extra.optJSONArray("list");
                                if (eList != null && eList.length() > 0) {
                                    String toFsId = String.valueOf(eList.getJSONObject(0).opt("to_fs_id"));
                                    if (!toFsId.isEmpty() && !"null".equals(toFsId) && !"0".equals(toFsId)) {
                                        personalFsId = toFsId;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 步骤 2: 若未直接返回 to_fs_id，列出 /TV 目录获取个人端 fs_id
            if (personalFsId.isEmpty()) {
                try {
                    String tvListUrl = "https://pan.baidu.com/rest/2.0/xpan/file?method=list&dir=%2FTV&web=1&order=time&desc=1";
                    Map<String, String> mHeaders = new HashMap<>();
                    mHeaders.put("User-Agent", "pan.baidu.com");
                    mHeaders.put("Cookie", userCookie);
                    String tvListRes = OkHttp.get(tvListUrl, mHeaders);
                    if (!tvListRes.isEmpty()) {
                        JSONObject tvObj = new JSONObject(tvListRes);
                        if (tvObj.optInt("errno", -1) == 0 && tvObj.has("list")) {
                            JSONArray tvList = tvObj.getJSONArray("list");
                            if (tvList.length() > 0) {
                                personalFsId = String.valueOf(tvList.getJSONObject(0).opt("fs_id"));
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            String targetFsId = !personalFsId.isEmpty() ? personalFsId : fsId;

            // 步骤 3: 使用 filemetas 获取 4K 原画直连 dlink
            try {
                String dlinkUrl = "https://pan.baidu.com/rest/2.0/xpan/multimedia?method=filemetas&dlink=1&fsids=%5B" + targetFsId + "%5D";
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

        SpiderFirebaseLogger.log(String.format("BaiduApi.getPlayerContent: flag=%s, directUrl=%s", flag, directUrl.length() > 50 ? directUrl.substring(0, 50) + "..." : directUrl));
        if (directUrl.isEmpty() || !directUrl.startsWith("http") || directUrl.contains("pan.baidu.com/s/")) {
            String errorMsg;
            if (userCookie.isEmpty()) {
                errorMsg = "请在【配置中心】配置百度网盘Cookie (BDUSS) 后播放";
            } else {
                errorMsg = "百度网盘直链解析失败，该资源可能已被限制";
            }
            NotifyToast.show(errorMsg);
            SpiderFirebaseLogger.recordPlaybackError("BaiduApi", flag, playParam, "baidu_directUrl_empty: " + errorMsg, null);
            result.put("url", "");
            result.put("msg", errorMsg);
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
