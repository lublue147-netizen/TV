package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class PanSou extends Spider {

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] tags = {
            {"4k", "4K影视"},
            {"animation", "热门动漫"},
            {"tv", "全集电视剧"},
            {"doc", "纪录片专区"}
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
        return searchContent(tid, false);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids.isEmpty()) return "";
        String shareUrl = ids.get(0);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        JSONObject vod = new JSONObject();
        vod.put("vod_id", shareUrl);
        vod.put("vod_name", "网盘资源直链");
        vod.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
        vod.put("vod_content", "已解析网盘分享链接: " + shareUrl);
        vod.put("vod_play_from", "网盘播放");
        vod.put("vod_play_url", "原画播放$" + shareUrl);
        list.put(vod);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // Universal search aggregation endpoint
        String searchUrl = "https://api.funletu.com/engine/search?keyword=" + java.net.URLEncoder.encode(key, "UTF-8");
        String json = OkHttp.get(searchUrl);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        if (!json.isEmpty()) {
            try {
                JSONObject obj = new JSONObject(json);
                JSONArray data = obj.optJSONArray("data");
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", item.optString("url"));
                        vod.put("vod_name", item.optString("title"));
                        vod.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
                        vod.put("vod_remarks", item.optString("disk_type", "网盘资源"));
                        list.put(vod);
                    }
                }
            } catch (Exception ignored) {}
        }

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");
        result.put("url", id);
        return result.toString();
    }
}
