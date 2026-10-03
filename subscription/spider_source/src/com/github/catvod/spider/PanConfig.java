package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.QuarkApi;
import com.github.catvod.crawler.Spider;
import com.github.catvod.proxy.GoProxy;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

/**
 * 网盘配置中心纯 Java 开源 Spider 实现 (替代 csp_PanConfigGuard)
 * 具备以下特性：
 * 1. 动态生成 阿里云盘 / 夸克网盘 官方扫码授权二维码并在 TV 详情页大图显示
 * 2. 轮询扫码状态并自动持久化 Token / Cookie 至本地存储
 * 3. 实时检测 GoProxy 加速引擎运行状态
 */
public class PanConfig extends Spider {

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        Init.init(context);
    }

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
        boolean aliAuthed = AliYunApi.get().hasToken();
        JSONObject ali = new JSONObject();
        ali.put("vod_id", "ali_config");
        ali.put("vod_name", "🐮 阿里云盘·扫码配置");
        ali.put("vod_pic", "https://img.icons8.com/color/480/alipay.png");
        ali.put("vod_remarks", aliAuthed ? "✅ 已授权配置 (4K秒播)" : "❌ 未配置·点击扫码登录");
        list.put(ali);

        // 2. 夸克网盘配置
        boolean quarkAuthed = QuarkApi.get().hasCookie();
        JSONObject quark = new JSONObject();
        quark.put("vod_id", "quark_config");
        quark.put("vod_name", "🐮 夸克网盘·扫码配置");
        quark.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
        quark.put("vod_remarks", quarkAuthed ? "✅ 已授权配置 (4K原画)" : "❌ 未配置·点击扫码登录");
        list.put(quark);

        // 3. GoProxy 状态
        boolean goAlive = GoProxy.isAlive();
        JSONObject goproxy = new JSONObject();
        goproxy.put("vod_id", "goproxy_status");
        goproxy.put("vod_name", "🚀 GoProxy 极速加速引擎");
        goproxy.put("vod_pic", "https://img.icons8.com/color/480/speedometer.png");
        goproxy.put("vod_remarks", goAlive ? "✅ 运行中·乱序并发预取" : "⚠️ 未启动 (端口 9978)");
        list.put(goproxy);

        // 4. 使用指南
        JSONObject help = new JSONObject();
        help.put("vod_id", "help_config");
        help.put("vod_name", "📖 网盘配置与 4K 秒播指南");
        help.put("vod_pic", "https://img.icons8.com/color/480/help.png");
        help.put("vod_remarks", "免转存·4K杜比视界播放说明");
        list.put(help);

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
            AliYunApi.QrResult qr = AliYunApi.get().getQrcode();
            boolean authed = AliYunApi.get().hasToken();

            vod.put("vod_name", "🐮 阿里云盘·扫码授权配置");
            vod.put("vod_pic", qr != null ? qr.qrImage : "https://img.icons8.com/color/480/alipay.png");

            StringBuilder content = new StringBuilder();
            content.append("【阿里云盘·扫码绑定说明】\n");
            content.append("1. 请使用手机打开「阿里云盘」App\n");
            content.append("2. 扫描本页面显示的二维码进行登录授权\n");
            content.append("3. 手机端确认后，点击下方「第1步：检查并保存授权」即可生效！\n\n");
            content.append("当前凭证状态：").append(authed ? "✅ 已授权配置 (生效中)" : "❌ 尚未配置");
            vod.put("vod_content", content.toString());

            vod.put("vod_play_from", "阿里云盘授权中心");
            String t = qr != null ? qr.t : "";
            String ck = qr != null ? qr.ck : "";
            String playUrls = "【第1步：手机扫码后点击检查授权】$ali_check::" + t + "::" + ck
                    + "#【第2步：查看当前凭证状态】$ali_status"
                    + "#【管理：清除本地凭证】$ali_clear";
            vod.put("vod_play_url", playUrls);

        } else if ("quark_config".equals(id)) {
            QuarkApi.QrResult qr = QuarkApi.get().getQrcode();
            boolean authed = QuarkApi.get().hasCookie();

            vod.put("vod_name", "🐮 夸克网盘·扫码授权配置");
            vod.put("vod_pic", qr != null ? qr.qrImage : "https://img.icons8.com/color/480/cloud-storage.png");

            StringBuilder content = new StringBuilder();
            content.append("【夸克网盘·扫码绑定说明】\n");
            content.append("1. 请使用手机打开「夸克」App\n");
            content.append("2. 扫描本页面显示的二维码进行登录授权\n");
            content.append("3. 手机端确认后，点击下方「第1步：检查并保存授权」即可生效！\n\n");
            content.append("当前凭证状态：").append(authed ? "✅ 已授权配置 (生效中)" : "❌ 尚未配置");
            vod.put("vod_content", content.toString());

            vod.put("vod_play_from", "夸克网盘授权中心");
            String token = qr != null ? qr.token : "";
            String playUrls = "【第1步：手机扫码后点击检查授权】$quark_check::" + token
                    + "#【第2步：查看当前凭证状态】$quark_status"
                    + "#【管理：清除本地凭证】$quark_clear";
            vod.put("vod_play_url", playUrls);

        } else if ("goproxy_status".equals(id)) {
            boolean alive = GoProxy.isAlive();
            vod.put("vod_name", "🚀 GoProxy 极速加速引擎运行状态");
            vod.put("vod_pic", "https://img.icons8.com/color/480/speedometer.png");

            StringBuilder content = new StringBuilder();
            content.append("【GoProxy 并发加速引擎】\n");
            content.append("专为 Android 电视盒子与投影仪设计，基于 Go 原生协程实现 HTTP Range 乱序并发预取。\n");
            content.append("播放 4K 网盘高码率视频时，有效消除拖动进度条和起播的卡顿等待。\n\n");
            content.append("运行端口：").append(GoProxy.getPort()).append("\n");
            content.append("服务状态：").append(alive ? "✅ 运行中 (加速已激活)" : "⚠️ 未检测到运行进程");
            vod.put("vod_content", content.toString());

            vod.put("vod_play_from", "加速引擎");
            vod.put("vod_play_url", "引擎健康检查$http://127.0.0.1:9978/ping");

        } else {
            vod.put("vod_name", "📖 网盘配置与 4K 秒播指南");
            vod.put("vod_pic", "https://img.icons8.com/color/480/help.png");
            vod.put("vod_content", "本源支持直接扫码获取夸克与阿里云盘凭证，亦支持在订阅目录中放置 token.json 进行多端同步。扫码成功后，所有 4K 网盘影视站均可享受超高清蓝光原画画质。");
            vod.put("vod_play_from", "使用指南");
            vod.put("vod_play_url", "查看在线发布中心$https://github.com/lublue147-netizen/subscription");
        }

        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");

        String messageUrl = "";

        if (id.startsWith("ali_check::")) {
            String[] parts = id.split("::");
            if (parts.length >= 3) {
                String t = parts[1];
                String ck = parts[2];
                String status = AliYunApi.get().checkQrcode(t, ck);
                if ("SUCCESS".equals(status)) {
                    messageUrl = buildNoticeUrl("✅ 阿里云盘扫码成功！凭证已保存至本地配置。");
                } else if ("SCANED".equals(status)) {
                    messageUrl = buildNoticeUrl("📱 手机端已扫描，请在手机上点击【确认登录】后再试！");
                } else if ("WAITING".equals(status)) {
                    messageUrl = buildNoticeUrl("⏳ 尚未检测到扫码，请使用阿里云盘 App 扫描二维码。");
                } else {
                    messageUrl = buildNoticeUrl("⚠️ 二维码已失效，请退出并重新进入本页面刷新。");
                }
            }
        } else if (id.startsWith("quark_check::")) {
            String[] parts = id.split("::");
            if (parts.length >= 2) {
                String token = parts[1];
                String status = QuarkApi.get().checkQrcode(token);
                if ("SUCCESS".equals(status)) {
                    messageUrl = buildNoticeUrl("✅ 夸克网盘扫码成功！凭证已保存至本地配置。");
                } else if ("WAITING".equals(status)) {
                    messageUrl = buildNoticeUrl("⏳ 尚未检测到扫码，请使用夸克 App 扫描二维码。");
                } else {
                    messageUrl = buildNoticeUrl("⚠️ 二维码已失效，请退出并重新进入本页面刷新。");
                }
            }
        } else if ("ali_status".equals(id)) {
            boolean has = AliYunApi.get().hasToken();
            messageUrl = buildNoticeUrl(has ? "✅ 阿里云盘已正常配置并授权！" : "❌ 阿里云盘未配置，请扫码登录。");
        } else if ("quark_status".equals(id)) {
            boolean has = QuarkApi.get().hasCookie();
            messageUrl = buildNoticeUrl(has ? "✅ 夸克网盘已正常配置并授权！" : "❌ 夸克网盘未配置，请扫码登录。");
        } else if ("ali_clear".equals(id)) {
            AliYunApi.get().setRefreshToken("");
            messageUrl = buildNoticeUrl("🗑️ 已清除本地阿里云盘凭证缓存。");
        } else if ("quark_clear".equals(id)) {
            QuarkApi.get().setCookie("");
            messageUrl = buildNoticeUrl("🗑️ 已清除本地夸克网盘凭证缓存。");
        } else {
            messageUrl = id;
        }

        result.put("url", messageUrl);
        return result.toString();
    }

    private String buildNoticeUrl(String message) {
        try {
            return "http://127.0.0.1:9978/notify?text=" + URLEncoder.encode(message, "UTF-8");
        } catch (Exception e) {
            return "http://127.0.0.1:9978/notify";
        }
    }
}
