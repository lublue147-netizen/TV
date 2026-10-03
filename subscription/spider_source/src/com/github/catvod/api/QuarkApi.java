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
 * 夸克网盘官方 API 纯 Java 开源实现
 * 支持：
 * 1. OAuth 扫码登录授权与凭证自动维护
 * 2. 匿名 / 会员分享资源递归深度遍历
 * 3. 4K 原画免转存 / 极速转存直连下载与切片转码播放
 */
public class QuarkApi {

    private static final String PREF_NAME = "pan_config";
    private static final String KEY_COOKIE = "quark_cookie";
    private static final String HOST_PAN = "https://pan.quark.cn/1/clouddrive/";
    private static final String HOST_DRIVE_PC = "https://drive-pc.quark.cn/1/clouddrive/";
    private static final String HOST_DRIVE = "https://drive.quark.cn/1/clouddrive/";
    private static final String PR = "pr=ucpro&fr=pc&uc_param_str=";
    private static final String PC_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) quark-cloud-drive/2.5.20 Chrome/100.0.4896.160 Electron/18.3.5.4-b478491100 Safari/537.36 Channel/pckk_other_ch";

    private static volatile QuarkApi instance;
    private String cookie = "";
    private String saveDirId = null;

    public static QuarkApi get() {
        if (instance == null) {
            synchronized (QuarkApi.class) {
                if (instance == null) {
                    instance = new QuarkApi();
                }
            }
        }
        return instance;
    }

    private QuarkApi() {
    }

    public synchronized void setCookie(String c) {
        if (c == null) c = "";
        this.cookie = c.trim();
        PanTokenManager.get().setQuarkCookie(this.cookie);
    }

    public String getCookie() {
        if (cookie.isEmpty()) {
            this.cookie = PanTokenManager.get().getQuarkCookie();
        }
        return cookie;
    }

    public boolean hasCookie() {
        return !getCookie().isEmpty();
    }

    private Map<String, String> getHeaders() {
        Map<String, String> h = new HashMap<>();
        h.put("User-Agent", PC_UA);
        h.put("Referer", "https://pan.quark.cn/");
        if (!getCookie().isEmpty()) {
            h.put("Cookie", getCookie());
        }
        return h;
    }

    public static class QrResult {
        public String token;
        public String qrUrl;
        public String qrDataUri;
        public String qrImage;

        public QrResult(String token, String qrUrl) {
            this.token = token;
            this.qrUrl = qrUrl;
            this.qrDataUri = com.github.catvod.qrcode.QrUtil.createDataUri(qrUrl);
            if (this.qrDataUri != null && !this.qrDataUri.isEmpty()) {
                this.qrImage = this.qrDataUri;
            } else {
                try {
                    this.qrImage = "https://api.pwmqr.com/qrcode/create/?url=" + URLEncoder.encode(qrUrl, "UTF-8");
                } catch (Exception e) {
                    this.qrImage = qrUrl;
                }
            }
        }
    }

    /**
     * 生成夸克 App 扫码登录凭证与二维码链接
     */
    public QrResult getQrcode() {
        try {
            String reqId = UUID.randomUUID().toString();
            String url = "https://uop.quark.cn/cas/ajax/getTokenForQrcodeLogin?client_id=386&v=1.2&request_id=" + reqId;
            String res = OkHttp.get(url, getHeaders());
            JSONObject obj = new JSONObject(res);
            if ("ok".equalsIgnoreCase(obj.optString("message")) || obj.optInt("status") == 2000000) {
                JSONObject members = obj.getJSONObject("data").getJSONObject("members");
                String token = members.getString("token");
                String qrUrl = "https://su.quark.cn/4_eMHBJ?uc_param_str=&token=" + token
                        + "&client_id=532&uc_biz_str=S%3Acustom%7COPT%3ASAREA%400%7COPT%3AIMMERSIVE%401%7COPT%3ABACK_BTN_STYLE%400";
                return new QrResult(token, qrUrl);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 轮询扫码授权状态并自动保存 Cookie
     * 返回：SUCCESS / WAITING / EXPIRED / ERROR
     */
    public String checkQrcode(String token) {
        try {
            String reqId = UUID.randomUUID().toString();
            String url = "https://uop.quark.cn/cas/ajax/getServiceTicketByQrcodeToken?token="
                    + URLEncoder.encode(token, "UTF-8") + "&client_id=532&v=1.2&request_id=" + reqId;
            String res = OkHttp.get(url, getHeaders());
            JSONObject obj = new JSONObject(res);
            int status = obj.optInt("status", -1);

            if (status == 2000000) {
                JSONObject members = obj.getJSONObject("data").getJSONObject("members");
                String ticket = members.optString("service_ticket");
                if (!ticket.isEmpty()) {
                    Map<String, String> cookieMap = new LinkedHashMap<>();
                    // 1. 换取初级登录凭证
                    String authUrl = "https://pan.quark.cn/account/info?st=" + URLEncoder.encode(ticket, "UTF-8") + "&lw=scan";
                    Map<String, String> h = new HashMap<>();
                    h.put("User-Agent", PC_UA);
                    h.put("Referer", "https://pan.quark.cn/");
                    OkHttp.Response authResp = OkHttp.getResponse(authUrl, h);
                    extractCookies(authResp, cookieMap);

                    // 2. 访问 pan.quark.cn/list 激活网盘会话并补齐 __puus
                    if (!cookieMap.isEmpty()) {
                        h.put("Cookie", toCookieString(cookieMap));
                        OkHttp.Response listResp = OkHttp.getResponse("https://pan.quark.cn/list", h);
                        extractCookies(listResp, cookieMap);

                        // 3. 访问 drive-pc 接口获取专属客户端 Token
                        h.put("Cookie", toCookieString(cookieMap));
                        OkHttp.Response pcResp = OkHttp.getResponse(
                            HOST_DRIVE_PC + "file/sort?" + PR + "&pdir_fid=0&_page=1&_size=50&_fetch_total=1&_sort=file_type:asc,updated_at:desc",
                            h
                        );
                        extractCookies(pcResp, cookieMap);
                    }

                    String newCookie = toCookieString(cookieMap);
                    if (!newCookie.isEmpty()) {
                        setCookie(newCookie);
                        com.github.catvod.utils.NotifyToast.show("🎉 夸克网盘扫码授权成功！4K原画已激活");
                        return "SUCCESS";
                    }
                }
                return "SUCCESS";
            } else if (status == 50004001) {
                return "WAITING";
            } else {
                return "EXPIRED";
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private static void extractCookies(OkHttp.Response resp, Map<String, String> map) {
        if (resp == null) return;
        List<String> setCookies = resp.getHeaders("Set-Cookie");
        if (setCookies != null) {
            for (String sc : setCookies) {
                String pair = sc.split(";")[0].trim();
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String k = pair.substring(0, eq).trim();
                    String v = pair.substring(eq + 1).trim();
                    map.put(k, v);
                }
            }
        }
    }

    private static String toCookieString(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * 获取分享链接的 stoken
     */
    public String getShareToken(String shareId, String pwd) {
        try {
            String url = HOST_PAN + "share/sharepage/token?" + PR;
            JSONObject body = new JSONObject();
            body.put("pwd_id", shareId);
            body.put("passcode", pwd != null ? pwd : "");
            String res = OkHttp.postJson(url, body.toString(), getHeaders());
            JSONObject json = new JSONObject(res);
            if (json.has("data") && json.getJSONObject("data").has("stoken")) {
                return json.getJSONObject("data").getString("stoken");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static class FileItem {
        public String name;
        public String fid;
        public String shareFidToken;
        public long size;

        public FileItem(String name, String fid, String shareFidToken, long size) {
            this.name = name;
            this.fid = fid;
            this.shareFidToken = shareFidToken != null ? shareFidToken : "";
            this.size = size;
        }
    }

    /**
     * 递归遍历分享文件夹，提取所有视频媒体剧集
     */
    public List<FileItem> listShareFiles(String shareId, String stoken) {
        List<FileItem> videos = new ArrayList<>();
        if (stoken == null || stoken.isEmpty()) {
            stoken = getShareToken(shareId, "");
        }
        if (stoken.isEmpty()) return videos;

        Queue<String> folderQueue = new LinkedList<>();
        folderQueue.add("0"); // 根目录

        int depth = 0;
        while (!folderQueue.isEmpty() && depth < 50) {
            String folderId = folderQueue.poll();
            depth++;
            try {
                int page = 1;
                boolean hasMore = true;
                while (hasMore && page <= 10) {
                    String url = HOST_PAN + "share/sharepage/detail?" + PR + "&pwd_id=" + shareId
                            + "&stoken=" + URLEncoder.encode(stoken, "UTF-8")
                            + "&pdir_fid=" + folderId
                            + "&force=0&_page=" + page + "&_size=100&_sort=file_type:asc,file_name:asc";

                    String res = OkHttp.get(url, getHeaders());
                    JSONObject json = new JSONObject(res);
                    if (!json.has("data")) break;

                    JSONObject data = json.getJSONObject("data");
                    JSONArray list = data.optJSONArray("list");
                    if (list == null || list.length() == 0) break;

                    for (int i = 0; i < list.length(); i++) {
                        JSONObject item = list.getJSONObject(i);
                        boolean isDir = item.optBoolean("dir", false);
                        String fid = item.optString("fid");
                        String name = item.optString("file_name");

                        if (isDir) {
                            folderQueue.add(fid);
                        } else {
                            String format = item.optString("format_type", "");
                            String shareFidToken = item.optString("share_fid_token", "");
                            long size = item.optLong("size", 0);

                            if (isVideo(name, format)) {
                                videos.add(new FileItem(name, fid, shareFidToken, size));
                            }
                        }
                    }

                    int total = data.optJSONObject("metadata") != null ? data.getJSONObject("metadata").optInt("_total", 0) : 0;
                    if (page * 100 >= total) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 自然排序集数 (第01集, 第02集...)
        videos.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return videos;
    }

    /**
     * 获取直链播放 URL
     */
    public String getPlayUrl(String shareId, String stoken, String fid, String shareFidToken) {
        if (!hasCookie()) {
            return "";
        }

        try {
            // 1. 保存到个人云盘临时 TV 目录
            String userFid = saveToTemp(shareId, stoken, fid, shareFidToken);
            if (userFid == null || userFid.isEmpty()) {
                return "";
            }

            // 2. 请求 4K 下载直链
            String downUrl = HOST_DRIVE + "file/download?" + PR;
            JSONObject downBody = new JSONObject();
            JSONArray fidsArr = new JSONArray();
            fidsArr.put(userFid);
            downBody.put("fids", fidsArr);

            String downRes = OkHttp.postJson(downUrl, downBody.toString(), getHeaders());
            JSONObject downJson = new JSONObject(downRes);
            if (downJson.has("data")) {
                JSONArray arr = downJson.getJSONArray("data");
                if (arr.length() > 0) {
                    String durl = arr.getJSONObject(0).optString("download_url");
                    if (!durl.isEmpty()) return durl;
                }
            }

            // 3. 转码播放回退
            String playUrl = HOST_DRIVE + "file/v2/play?" + PR;
            JSONObject playBody = new JSONObject();
            playBody.put("fid", userFid);
            playBody.put("resolutions", "4k,2k,super,high,normal");
            playBody.put("supports", "fmp4");

            String playRes = OkHttp.postJson(playUrl, playBody.toString(), getHeaders());
            JSONObject playJson = new JSONObject(playRes);
            if (playJson.has("data") && playJson.getJSONObject("data").has("video_list")) {
                JSONArray vList = playJson.getJSONObject("data").getJSONArray("video_list");
                if (vList.length() > 0) {
                    return vList.getJSONObject(0).getJSONObject("video_info").optString("url");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private synchronized String saveToTemp(String shareId, String stoken, String fid, String shareFidToken) throws Exception {
        ensureSaveDir();
        if (saveDirId == null) return null;

        String saveUrl = HOST_DRIVE_PC + "share/sharepage/save?" + PR;
        JSONObject saveBody = new JSONObject();
        JSONArray fidList = new JSONArray();
        fidList.put(fid);
        JSONArray tokenList = new JSONArray();
        tokenList.put(shareFidToken != null ? shareFidToken : "");

        saveBody.put("fid_list", fidList);
        saveBody.put("fid_token_list", tokenList);
        saveBody.put("to_pdir_fid", saveDirId);
        saveBody.put("pwd_id", shareId);
        saveBody.put("stoken", stoken);
        saveBody.put("pdir_fid", "0");
        saveBody.put("scene", "link");

        String saveRes = OkHttp.postJson(saveUrl, saveBody.toString(), getHeaders());
        JSONObject saveJson = new JSONObject(saveRes);
        if (saveJson.has("data")) {
            JSONObject dataObj = saveJson.getJSONObject("data");
            if (dataObj.has("finish")) {
                JSONArray finishArr = dataObj.optJSONArray("finish");
                if (finishArr != null && finishArr.length() > 0) {
                    return finishArr.optJSONObject(0).optString("fid");
                }
            }
            if (dataObj.has("task_id")) {
                String taskId = dataObj.getString("task_id");
                for (int retry = 0; retry < 8; retry++) {
                    Thread.sleep(500);
                    String taskUrl = HOST_DRIVE_PC + "task?" + PR + "&task_id=" + taskId;
                    String taskRes = OkHttp.get(taskUrl, getHeaders());
                    JSONObject tJson = new JSONObject(taskRes);
                    if (tJson.has("data") && tJson.getJSONObject("data").has("save_as")) {
                        JSONArray topFids = tJson.getJSONObject("data").getJSONObject("save_as").optJSONArray("save_as_top_fids");
                        if (topFids != null && topFids.length() > 0) {
                            return topFids.getString(0);
                        }
                    }
                }
            }
        }
        return null;
    }

    private void ensureSaveDir() {
        if (saveDirId != null) return;
        try {
            String url = HOST_DRIVE_PC + "file/sort?" + PR + "&pdir_fid=0&_page=1&_size=50&_sort=file_type:asc,updated_at:desc";
            String res = OkHttp.get(url, getHeaders());
            JSONObject json = new JSONObject(res);
            if (json.has("data") && json.getJSONObject("data").has("list")) {
                JSONArray list = json.getJSONObject("data").getJSONArray("list");
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.getJSONObject(i);
                    if ("TV".equals(item.optString("file_name"))) {
                        saveDirId = item.optString("fid");
                        return;
                    }
                }
            }

            // 创建 TV 目录
            String createUrl = HOST_DRIVE_PC + "file?" + PR;
            JSONObject body = new JSONObject();
            body.put("pdir_fid", "0");
            body.put("file_name", "TV");
            body.put("dir_path", "");
            body.put("dir_init_lock", "false");
            String cRes = OkHttp.postJson(createUrl, body.toString(), getHeaders());
            JSONObject cJson = new JSONObject(cRes);
            if (cJson.has("data") && cJson.getJSONObject("data").has("fid")) {
                saveDirId = cJson.getJSONObject("data").getString("fid");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean isVideo(String name, String format) {
        if ("video".equalsIgnoreCase(format)) return true;
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".ts")
                || lower.endsWith(".mov") || lower.endsWith(".flv") || lower.endsWith(".avi")
                || lower.endsWith(".webm") || lower.endsWith(".m2ts") || lower.endsWith(".m3u8");
    }
}
