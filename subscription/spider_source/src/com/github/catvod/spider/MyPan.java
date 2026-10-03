package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.proxy.GoProxy;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

/**
 * 个人网盘开源 Spider 实现
 * 支持接入挂载的云盘库与 AList 文件系统，原画无缝点播
 */
public class MyPan extends Spider {

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] tags = {
            {"all", "📁 全部网盘"},
            {"video", "🎬 影视收藏"},
            {"music", "🎵 音乐专辑"}
        };

        for (String[] tag : tags) {
            JSONObject item = new JSONObject();
            item.put("type_id", tag[0]);
            item.put("type_name", tag[1]);
            classes.put(item);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("vod_id", "root_folder");
        item.put("vod_name", "🐮 我的网盘·根目录");
        item.put("vod_pic", "https://img.icons8.com/color/480/folder-invoices.png");
        item.put("vod_remarks", "已绑定个人云盘");
        list.put(item);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return homeContent(filter);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        JSONObject vod = new JSONObject();
        vod.put("vod_id", ids.get(0));
        vod.put("vod_name", "🐮 我的个人网盘媒体库");
        vod.put("vod_pic", "https://img.icons8.com/color/480/folder-invoices.png");
        vod.put("vod_content", "已连接本地与云端媒体资产库。如需挂载第三方 WebDAV 或个人 AList，可在配置中指定服务地址。");
        vod.put("vod_play_from", "个人网盘");
        vod.put("vod_play_url", "媒体库浏览$http://127.0.0.1:9978");
        list.put(vod);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", GoProxy.wrap(id, null));
        return result.toString();
    }
}
