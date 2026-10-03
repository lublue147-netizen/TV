package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.BaiduApi;
import com.github.catvod.api.PanTokenManager;
import com.github.catvod.api.QuarkApi;
import com.github.catvod.api.UcApi;
import com.github.catvod.crawler.Spider;
import com.github.catvod.proxy.GoProxy;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人网盘纯 Java 开源 Spider 实现 (替代 csp_MyPanGuard)
 * 支持：
 * 1. 挂载本地/远程 AList 文件系统 (默认 127.0.0.1:5244)
 * 2. 映射已绑定的阿里云盘与夸克网盘个人媒体库
 */
public class MyPan extends Spider {

    private String serverUrl = "http://127.0.0.1:5244";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        Init.init(context);
        com.github.catvod.api.PanTokenManager.get().load();
        if (extend != null && !extend.trim().isEmpty()) {
            String ext = extend.trim();
            if (ext.startsWith("http://") || ext.startsWith("https://")) {
                this.serverUrl = ext;
            } else if (ext.startsWith("{") && ext.endsWith("}")) {
                try {
                    JSONObject obj = new JSONObject(ext);
                    if (obj.has("url")) this.serverUrl = obj.getString("url");
                } catch (Exception ignored) {}
            }
        }
        if (serverUrl.endsWith("/")) {
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] tags = {
            {"alist", "📁 AList 本地/远程挂载"},
            {"baidu", "🐮 百度网盘 (原画与无限)"},
            {"quark", "🐮 夸克网盘 (个人媒体库)"},
            {"uc", "🐮 UC 网盘 (个人媒体库)"},
            {"aliyun", "🐮 阿里云盘 (个人媒体库)"}
        };

        for (String[] tag : tags) {
            JSONObject item = new JSONObject();
            item.put("type_id", tag[0]);
            item.put("type_name", tag[1]);
            classes.put(item);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();

        // 1. AList 挂载项
        JSONObject alistItem = new JSONObject();
        alistItem.put("vod_id", "alist::/");
        alistItem.put("vod_name", "📁 AList 媒体库根目录");
        alistItem.put("vod_pic", "https://img.icons8.com/color/480/folder-invoices.png");
        alistItem.put("vod_remarks", serverUrl);
        list.put(alistItem);

        // 2. 百度网盘项
        boolean baiduAuthed = PanTokenManager.get().hasBaiduCookie();
        JSONObject bItem = new JSONObject();
        bItem.put("vod_id", "baidu::0");
        bItem.put("vod_name", "🐮 我的百度网盘 (原画与无限)");
        bItem.put("vod_pic", "https://img.icons8.com/color/480/baidu.png");
        bItem.put("vod_remarks", baiduAuthed ? "已绑定授权" : "未授权·请先配置");
        list.put(bItem);

        // 3. 夸克网盘项
        boolean quarkAuthed = QuarkApi.get().hasCookie();
        JSONObject qItem = new JSONObject();
        qItem.put("vod_id", "quark::0");
        qItem.put("vod_name", "🐮 我的夸克网盘");
        qItem.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
        qItem.put("vod_remarks", quarkAuthed ? "已绑定授权" : "未授权·请先扫码");
        list.put(qItem);

        // 4. UC 网盘项
        boolean ucAuthed = PanTokenManager.get().hasUcCookie();
        JSONObject uItem = new JSONObject();
        uItem.put("vod_id", "uc::0");
        uItem.put("vod_name", "🐮 我的UC网盘");
        uItem.put("vod_pic", "https://img.icons8.com/color/480/uc-browser.png");
        uItem.put("vod_remarks", ucAuthed ? "已绑定授权" : "未授权·请先配置");
        list.put(uItem);

        // 5. 阿里云盘项
        boolean aliAuthed = AliYunApi.get().hasToken();
        JSONObject aliItem = new JSONObject();
        aliItem.put("vod_id", "ali::root");
        aliItem.put("vod_name", "🐮 我的阿里云盘");
        aliItem.put("vod_pic", "https://img.icons8.com/color/480/alipay.png");
        aliItem.put("vod_remarks", aliAuthed ? "已绑定授权" : "未授权·请先扫码");
        list.put(aliItem);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if ("alist".equals(tid)) {
            return listAList("/");
        } else if ("aliyun".equals(tid)) {
            return homeContent(filter);
        } else if ("quark".equals(tid)) {
            return homeContent(filter);
        }
        return homeContent(filter);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String id = ids.get(0);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        JSONObject vod = new JSONObject();
        vod.put("vod_id", id);

        if (id.startsWith("alist::")) {
            String path = id.substring("alist::".length());
            // 查询 AList 单文件直链
            JSONObject param = new JSONObject();
            param.put("path", path);
            param.put("password", "");

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            String json = OkHttp.post(serverUrl + "/api/fs/get", param.toString(), headers);
            if (!json.isEmpty()) {
                JSONObject root = new JSONObject(json);
                JSONObject data = root.optJSONObject("data");
                if (data != null) {
                    String rawUrl = data.optString("raw_url");
                    String name = data.optString("name", "媒体文件");
                    vod.put("vod_name", name);
                    vod.put("vod_pic", "https://img.icons8.com/color/480/video-file.png");
                    vod.put("vod_content", "AList 文件路径: " + path);
                    vod.put("vod_play_from", "AList直链");
                    vod.put("vod_play_url", "原画播放$" + rawUrl);
                }
            }
        }

        if (id.startsWith("baidu::")) {
            vod.put("vod_name", "我的百度网盘");
            vod.put("vod_pic", "https://img.icons8.com/color/480/baidu.png");
            vod.put("vod_content", "已连接百度网盘个人媒体库");
            vod.put("vod_play_from", "百度原画$$$百度无限");
            vod.put("vod_play_url", "4K原画正片$baidu_orig::" + id + "::0::#4K无限极速$baidu_unlimit::" + id + "::0::");
        } else if (id.startsWith("uc::")) {
            vod.put("vod_name", "我的UC网盘");
            vod.put("vod_pic", "https://img.icons8.com/color/480/uc-browser.png");
            vod.put("vod_content", "已连接UC网盘个人媒体库");
            vod.put("vod_play_from", "UC原画");
            vod.put("vod_play_url", "4K原画正片$uc::" + id);
        }

        if (vod.optString("vod_play_from").isEmpty()) {
            vod.put("vod_name", "个人网盘媒体库");
            vod.put("vod_pic", "https://img.icons8.com/color/480/folder-invoices.png");
            vod.put("vod_content", "已连接本地与云端媒体资产库。");
            vod.put("vod_play_from", "个人网盘");
            vod.put("vod_play_url", "浏览根目录$http://127.0.0.1:9978");
        }

        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id.startsWith("baidu_") || (flag != null && flag.contains("百度"))) {
            return BaiduApi.get().getPlayerContent(flag, id).toString();
        } else if (id.startsWith("uc::") || (flag != null && flag.contains("UC"))) {
            return UcApi.get().getPlayerContent(id).toString();
        }
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");
        result.put("url", GoProxy.wrap(id));
        return result.toString();
    }

    private String listAList(String path) {
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        try {
            JSONObject param = new JSONObject();
            param.put("path", path);
            param.put("password", "");
            param.put("page", 1);
            param.put("per_page", 100);

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");

            String json = OkHttp.post(serverUrl + "/api/fs/list", param.toString(), headers);
            if (!json.isEmpty()) {
                JSONObject root = new JSONObject(json);
                JSONObject data = root.optJSONObject("data");
                if (data != null) {
                    JSONArray content = data.optJSONArray("content");
                    if (content != null) {
                        for (int i = 0; i < content.length(); i++) {
                            JSONObject item = content.getJSONObject(i);
                            boolean isDir = item.optBoolean("is_dir");
                            String name = item.optString("name");
                            String subPath = (path.endsWith("/") ? path : path + "/") + name;

                            JSONObject vod = new JSONObject();
                            vod.put("vod_id", "alist::" + subPath);
                            vod.put("vod_name", (isDir ? "📁 " : "🎬 ") + name);
                            vod.put("vod_tag", isDir ? "folder" : "file");
                            vod.put("vod_pic", isDir ? "https://img.icons8.com/color/480/folder-invoices.png" : "https://img.icons8.com/color/480/video-file.png");
                            vod.put("vod_remarks", isDir ? "目录" : "文件");
                            list.put(vod);
                        }
                    }
                }
            }
            result.put("list", list);
            return result.toString();
        } catch (Exception ignored) {}

        return "{\"list\":[]}";
    }
}
