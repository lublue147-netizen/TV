package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

/**
 * 网盘配置中心开源 Spider 实现
 * 负责展示阿里云盘、夸克、115、百度网盘的授权状态与 GoProxy 多线程加速引擎状态
 */
public class PanConfig extends Spider {

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] tags = {
            {"config", "⚙️ 配置与授权"},
            {"engine", "🚀 GoProxy 加速引擎"},
            {"help", "📖 使用指南"}
        };

        for (String[] tag : tags) {
            JSONObject item = new JSONObject();
            item.put("type_id", tag[0]);
            item.put("type_name", tag[1]);
            classes.put(item);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();

        // 1. 阿里云盘配置
        JSONObject ali = new JSONObject();
        ali.put("vod_id", "ali_config");
        ali.put("vod_name", "🐮 阿里云盘·授权配置");
        ali.put("vod_pic", "https://img.icons8.com/color/480/alipay.png");
        ali.put("vod_remarks", "支持手机扫码或填入 OpenToken");
        list.put(ali);

        // 2. 夸克网盘配置
        JSONObject quark = new JSONObject();
        quark.put("vod_id", "quark_config");
        quark.put("vod_name", "🐮 夸克网盘·授权配置");
        quark.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
        quark.put("vod_remarks", "支持导入 quark_cookie 实现 4K 原画免转存");
        list.put(quark);

        // 3. 115 网盘配置
        JSONObject p115 = new JSONObject();
        p115.put("vod_id", "115_config");
        p115.put("vod_name", "🐮 115 网盘·授权配置");
        p115.put("vod_pic", "https://img.icons8.com/color/480/server.png");
        p115.put("vod_remarks", "支持 115 开放平台扫码登录授权");
        list.put(p115);

        // 4. GoProxy 状态
        JSONObject goproxy = new JSONObject();
        goproxy.put("vod_id", "goproxy_status");
        goproxy.put("vod_name", "🚀 GoProxy 极速加速引擎状态");
        goproxy.put("vod_pic", "https://img.icons8.com/color/480/speedometer.png");
        goproxy.put("vod_remarks", "并发切片预取·零缓冲播放 (127.0.0.1:9978)");
        list.put(goproxy);

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
        String id = ids.get(0);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        JSONObject vod = new JSONObject();
        vod.put("vod_id", id);

        if ("ali_config".equals(id)) {
            vod.put("vod_name", "🐮 阿里云盘·授权配置与绑定说明");
            vod.put("vod_pic", "https://img.icons8.com/color/480/alipay.png");
            vod.put("vod_content", "请在同一局域网电脑/手机浏览器中打开 TV 客户端「配置中心」中显示的 IP 地址，填入您的阿里云盘 OpenToken 或扫码登录。配置完成后即可畅享 4K 极速秒播。");
            vod.put("vod_play_from", "配置中心");
            vod.put("vod_play_url", "查看授权说明$https://github.com/lublue147-netizen/subscription");
        } else if ("quark_config".equals(id)) {
            vod.put("vod_name", "🐮 夸克网盘·授权配置与绑定说明");
            vod.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
            vod.put("vod_content", "夸克网盘支持免转存直接解析播放！在 token.json 或应用设置中填入夸克账号的 Cookie (包含 _UP_A4A_HP_ 等关键凭证)，即可直接播放 4K 蓝光。");
            vod.put("vod_play_from", "配置中心");
            vod.put("vod_play_url", "查看授权说明$https://github.com/lublue147-netizen/subscription");
        } else {
            vod.put("vod_name", "🚀 GoProxy 极速加速引擎运行状态");
            vod.put("vod_pic", "https://img.icons8.com/color/480/speedometer.png");
            vod.put("vod_content", "GoProxy 专为 Android 电视盒子设计，采用 Go 语言原生协程实现 HTTP Range 乱序并发预取。当播放高码率 4K 网盘视频时，大幅消除起播与快进缓冲等待。");
            vod.put("vod_play_from", "引擎状态");
            vod.put("vod_play_url", "加速测试与健康检查$http://127.0.0.1:9978/ping");
        }

        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("url", id);
        return result.toString();
    }
}
