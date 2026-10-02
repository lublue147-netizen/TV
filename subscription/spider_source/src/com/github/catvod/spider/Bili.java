package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class Bili extends Spider {

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] tags = {
            {"高考", "高考精选课堂"},
            {"中考", "中考同步课堂"},
            {"名名师", "名师讲堂精选"},
            {"纪录片", "B站高分纪录片"}
        };

        for (String[] tag : tags) {
            JSONObject item = new JSONObject();
            item.put("type_id", tag[0]);
            item.put("type_name", tag[1]);
            classes.put(item);
        }
        result.put("class", classes);
        result.put("list", new JSONArray());
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return searchContent(tid, false, pg);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids.isEmpty()) return "";
        String bvid = ids.get(0);

        String viewUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
        String json = OkHttp.get(viewUrl);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        if (!json.isEmpty()) {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONObject vod = new JSONObject();
                vod.put("vod_id", bvid);
                vod.put("vod_name", data.optString("title"));
                vod.put("vod_pic", data.optString("pic"));
                vod.put("vod_content", data.optString("desc"));
                vod.put("vod_remarks", "UP: " + data.optJSONObject("owner").optString("name"));

                JSONArray pages = data.optJSONArray("pages");
                StringBuilder playUrls = new StringBuilder();
                if (pages != null) {
                    for (int i = 0; i < pages.length(); i++) {
                        JSONObject p = pages.getJSONObject(i);
                        if (i > 0) playUrls.append("#");
                        playUrls.append(p.optString("part")).append("$").append(bvid).append("?cid=").append(p.optLong("cid"));
                    }
                }
                vod.put("vod_play_from", "B站高清");
                vod.put("vod_play_url", playUrls.toString());
                list.put(vod);
            }
        }

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}

        String url = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&keyword=" +
                     java.net.URLEncoder.encode(key, "UTF-8") + "&page=" + page;
        String json = OkHttp.get(url);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        if (!json.isEmpty()) {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray items = data.optJSONArray("result");
                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", item.optString("bvid"));
                        vod.put("vod_name", item.optString("title").replaceAll("<.*?>", ""));
                        vod.put("vod_pic", "https:" + item.optString("pic"));
                        vod.put("vod_remarks", item.optString("duration"));
                        list.put(vod);
                    }
                }
            }
        }

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String[] parts = id.split("\\?cid=");
        String bvid = parts[0];
        String cid = parts.length > 1 ? parts[1] : "";

        String playUrl = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid + "&cid=" + cid + "&qn=64&fnval=0&fnver=0&fourk=1";
        String json = OkHttp.get(playUrl);

        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");

        if (!json.isEmpty()) {
            JSONObject root = new JSONObject(json);
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                JSONArray durl = data.optJSONArray("durl");
                if (durl != null && durl.length() > 0) {
                    String videoStream = durl.getJSONObject(0).optString("url");
                    result.put("url", videoStream);
                    JSONObject header = new JSONObject();
                    header.put("User-Agent", OkHttp.CHROME);
                    header.put("Referer", "https://www.bilibili.com");
                    result.put("header", header.toString());
                    return result.toString();
                }
            }
        }

        result.put("url", "https://www.bilibili.com/video/" + bvid);
        return result.toString();
    }
}
