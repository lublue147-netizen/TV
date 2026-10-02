package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class Push extends Spider {

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids.isEmpty()) return "";
        String url = ids.get(0);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        JSONObject vod = new JSONObject();
        vod.put("vod_id", url);
        vod.put("vod_name", "推送流: " + (url.length() > 30 ? url.substring(0, 30) + "..." : url));
        vod.put("vod_pic", "https://img.icons8.com/color/480/play--v1.png");
        vod.put("vod_content", url);
        vod.put("vod_play_from", "推送直连");
        vod.put("vod_play_url", "立即播放$" + url);
        list.put(vod);

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
