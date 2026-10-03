package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AList extends Spider {

    private String serverUrl = "";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            this.serverUrl = extend.trim();
            if (serverUrl.endsWith("/")) serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return categoryContent("/", "1", filter, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (serverUrl.isEmpty()) return "";

        JSONObject param = new JSONObject();
        param.put("path", tid);
        param.put("password", "");
        param.put("page", 1);
        param.put("per_page", 100);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String json = OkHttp.post(serverUrl + "/api/fs/list", param.toString(), headers);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

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
                        String path = (tid.endsWith("/") ? tid : tid + "/") + name;

                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", path);
                        vod.put("vod_name", (isDir ? "📁 " : "🎬 ") + name);
                        vod.put("vod_tag", isDir ? "folder" : "file");
                        vod.put("vod_pic", isDir ? "https://img.icons8.com/color/480/folder-invoices.png" : "https://img.icons8.com/color/480/video-file.png");
                        vod.put("vod_remarks", isDir ? "目录" : humanSize(item.optLong("size", 0L)));
                        list.put(vod);
                    }
                }
            }
        }

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (serverUrl.isEmpty() || ids.isEmpty()) return "";
        String path = ids.get(0);

        JSONObject param = new JSONObject();
        param.put("path", path);
        param.put("password", "");

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        String json = OkHttp.post(serverUrl + "/api/fs/get", param.toString(), headers);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        if (!json.isEmpty()) {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                String rawUrl = data.optString("raw_url");
                String name = data.optString("name");

                JSONObject vod = new JSONObject();
                vod.put("vod_id", path);
                vod.put("vod_name", name);
                vod.put("vod_pic", "https://img.icons8.com/color/480/video-file.png");
                vod.put("vod_play_from", "AList直链");
                vod.put("vod_play_url", "原画播放$" + rawUrl);
                list.put(vod);
            }
        }

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");
        result.put("url", com.github.catvod.proxy.GoProxy.wrap(id, null));
        return result.toString();
    }

    private String humanSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
