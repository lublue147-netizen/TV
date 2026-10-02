package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class Douban extends Spider {

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] categories = {
            {"movie_hot", "热门电影"},
            {"tv_hot", "热播剧集"},
            {"tv_variety", "热门综艺"},
            {"tv_animation", "热门动漫"}
        };

        for (String[] cat : categories) {
            JSONObject item = new JSONObject();
            item.put("type_id", cat[0]);
            item.put("type_name", cat[1]);
            classes.put(item);
        }
        result.put("class", classes);

        // Load hot list as default home items
        String url = "https://movie.douban.com/j/search_subjects?type=movie&tag=%E7%83%AD%E9%97%A8&page_limit=20&page_start=0";
        String json = OkHttp.get(url);
        JSONArray list = new JSONArray();
        if (!json.isEmpty()) {
            JSONObject obj = new JSONObject(json);
            JSONArray subjects = obj.optJSONArray("subjects");
            if (subjects != null) {
                for (int i = 0; i < subjects.length(); i++) {
                    JSONObject sub = subjects.getJSONObject(i);
                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", sub.optString("id"));
                    vod.put("vod_name", sub.optString("title"));
                    vod.put("vod_pic", sub.optString("cover"));
                    vod.put("vod_remarks", "评分: " + sub.optString("rate"));
                    list.put(vod);
                }
            }
        }
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        int start = (page - 1) * 20;

        String type = tid.startsWith("tv") ? "tv" : "movie";
        String tag = tid.contains("animation") ? "动漫" : (tid.contains("variety") ? "综艺" : "热门");
        String url = "https://movie.douban.com/j/search_subjects?type=" + type + "&tag=" + java.net.URLEncoder.encode(tag, "UTF-8") + "&page_limit=20&page_start=" + start;

        String json = OkHttp.get(url);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        if (!json.isEmpty()) {
            JSONObject obj = new JSONObject(json);
            JSONArray subjects = obj.optJSONArray("subjects");
            if (subjects != null) {
                for (int i = 0; i < subjects.length(); i++) {
                    JSONObject sub = subjects.getJSONObject(i);
                    JSONObject vod = new JSONObject();
                    vod.put("vod_id", sub.optString("id"));
                    vod.put("vod_name", sub.optString("title"));
                    vod.put("vod_pic", sub.optString("cover"));
                    vod.put("vod_remarks", "评分: " + sub.optString("rate"));
                    list.put(vod);
                }
            }
        }
        result.put("page", page);
        result.put("pagecount", page + 1);
        result.put("limit", 20);
        result.put("total", 100);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids.isEmpty()) return "";
        String id = ids.get(0);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        JSONObject vod = new JSONObject();
        vod.put("vod_id", id);
        vod.put("vod_name", "豆瓣影视条目: " + id);
        vod.put("vod_play_from", "聚合搜索");
        vod.put("vod_play_url", "全网聚合播放$search://" + id);
        list.put(vod);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        String url = "https://movie.douban.com/j/subject_suggest?q=" + java.net.URLEncoder.encode(key, "UTF-8");
        String json = OkHttp.get(url);
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        if (!json.isEmpty()) {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.getJSONObject(i);
                JSONObject vod = new JSONObject();
                vod.put("vod_id", item.optString("id"));
                vod.put("vod_name", item.optString("title"));
                vod.put("vod_pic", item.optString("img"));
                vod.put("vod_remarks", item.optString("year"));
                list.put(vod);
            }
        }
        result.put("list", list);
        return result.toString();
    }
}
