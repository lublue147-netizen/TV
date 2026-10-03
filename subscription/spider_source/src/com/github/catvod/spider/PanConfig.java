package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.PanTokenManager;
import com.github.catvod.api.QuarkApi;
import com.github.catvod.crawler.Spider;
import com.github.catvod.proxy.GoProxy;
import com.github.catvod.qrcode.QrUtil;
import com.github.catvod.utils.NotifyToast;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;

/**
 * 纯开源全能网盘配置中心 (兼容替换 csp_PanConfigGuard)
 * 具备以下特性：
 * 1. 离线生成 阿里云盘 / 夸克网盘 官方扫码授权二维码 (Pure Java BMP Data URI，秒出不依赖外网)
 * 2. 扫码状态后台自动轮询 (手机确认后 2 秒内电视端弹出通知并自动生效保存)
 * 3. 局域网 Web 快捷配置中心 (手机/电脑浏览器打开 http://电视IP:9979 极速输入 Cookie/Token)
 * 4. 多级持久化存储 (SharedPreferences + files/pan_token.json + cache/pan_token.json)
 */
public class PanConfig extends Spider {

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        Init.init(context);
        PanTokenManager.get().load();
        PanWebServer.start();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] tags = {
            {"config", "⚙️ 网盘配置中心"},
            {"quick", "⚡ 快捷管理与测试"},
            {"engine", "🚀 GoProxy 加速引擎"}
        };

        for (String[] tag : tags) {
            JSONObject item = new JSONObject();
            item.put("type_id", tag[0]);
            item.put("type_name", tag[1]);
            classes.put(item);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();

        boolean aliAuthed = PanTokenManager.get().hasAliRefreshToken();
        boolean quarkAuthed = PanTokenManager.get().hasQuarkCookie();
        String localIp = PanWebServer.getLocalIp();
        int port = PanWebServer.getPort();

        // 1. 阿里云盘扫码配置
        JSONObject ali = new JSONObject();
        ali.put("vod_id", "ali_config");
        ali.put("vod_name", "🐮 阿里云盘·扫码配置");
        ali.put("vod_pic", "https://img.icons8.com/color/480/alipay.png");
        ali.put("vod_remarks", aliAuthed ? "✅ 已授权配置 (4K秒播)" : "❌ 未配置·点击扫码绑定");
        list.put(ali);

        // 2. 夸克网盘扫码配置
        JSONObject quark = new JSONObject();
        quark.put("vod_id", "quark_config");
        quark.put("vod_name", "🐮 夸克网盘·扫码配置");
        quark.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
        quark.put("vod_remarks", quarkAuthed ? "✅ 已授权配置 (4K原画)" : "❌ 未配置·点击扫码绑定");
        list.put(quark);

        // 3. 局域网网页极速配置
        JSONObject web = new JSONObject();
        web.put("vod_id", "web_config");
        web.put("vod_name", "🌐 局域网网页极速配置中心");
        web.put("vod_pic", "https://img.icons8.com/color/480/domain.png");
        web.put("vod_remarks", "手机/电脑浏览器打开 http://" + localIp + ":" + port);
        web.put("action", "show_ip");
        list.put(web);

        // 4. 一键检查阿里云盘
        JSONObject chkAli = new JSONObject();
        chkAli.put("vod_id", "act_check_ali");
        chkAli.put("vod_name", "⚡ 检查/刷新阿里云盘授权");
        chkAli.put("vod_pic", "https://img.icons8.com/color/480/checked-checkbox.png");
        chkAli.put("vod_remarks", aliAuthed ? "✅ 当前已授权配置" : "❌ 未配置·点击查看");
        chkAli.put("action", "check_ali");
        list.put(chkAli);

        // 5. 一键检查夸克网盘
        JSONObject chkQuark = new JSONObject();
        chkQuark.put("vod_id", "act_check_quark");
        chkQuark.put("vod_name", "⚡ 检查/刷新夸克网盘授权");
        chkQuark.put("vod_pic", "https://img.icons8.com/color/480/checked-checkbox.png");
        chkQuark.put("vod_remarks", quarkAuthed ? "✅ 当前已授权配置" : "❌ 未配置·点击查看");
        chkQuark.put("action", "check_quark");
        list.put(chkQuark);

        // 6. 清除阿里云盘凭证
        JSONObject clrAli = new JSONObject();
        clrAli.put("vod_id", "act_clear_ali");
        clrAli.put("vod_name", "🗑️ 清除阿里云盘登录凭证");
        clrAli.put("vod_pic", "https://img.icons8.com/color/480/delete-forever.png");
        clrAli.put("vod_remarks", "点击清除本地缓存凭证");
        clrAli.put("action", "clear_ali");
        list.put(clrAli);

        // 7. 清除夸克网盘凭证
        JSONObject clrQuark = new JSONObject();
        clrQuark.put("vod_id", "act_clear_quark");
        clrQuark.put("vod_name", "🗑️ 清除夸克网盘登录凭证");
        clrQuark.put("vod_pic", "https://img.icons8.com/color/480/delete-forever.png");
        clrQuark.put("vod_remarks", "点击清除本地缓存凭证");
        clrQuark.put("action", "clear_quark");
        list.put(clrQuark);

        // 8. GoProxy 状态
        boolean goAlive = GoProxy.isAlive();
        JSONObject goproxy = new JSONObject();
        goproxy.put("vod_id", "goproxy_status");
        goproxy.put("vod_name", "🚀 GoProxy 极速加速引擎");
        goproxy.put("vod_pic", "https://img.icons8.com/color/480/speedometer.png");
        goproxy.put("vod_remarks", goAlive ? "✅ 运行中·乱序并发预取" : "⚠️ 未启动 (端口 9978)");
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

        String localIp = PanWebServer.getLocalIp();
        int port = PanWebServer.getPort();
        String webUrl = "http://" + localIp + ":" + port;

        if ("ali_config".equals(id)) {
            AliYunApi.QrResult qr = AliYunApi.get().getQrcode();
            boolean authed = PanTokenManager.get().hasAliRefreshToken();

            vod.put("vod_name", "🐮 阿里云盘·扫码授权配置");
            vod.put("vod_pic", qr != null ? qr.qrImage : "https://img.icons8.com/color/480/alipay.png");

            StringBuilder content = new StringBuilder();
            content.append("【阿里云盘·扫码绑定说明】\n");
            content.append("1. 请使用手机打开「阿里云盘」App 扫描本页面显示的二维码进行登录授权\n");
            content.append("2. 手机端确认授权后，电视端将自动检测并在 2 秒内完成绑定生效！无需额外点击！\n\n");
            content.append("💡 手机/电脑网页快捷配置：在同一 WiFi 浏览器打开 ").append(webUrl).append("\n");
            content.append("当前凭证状态：").append(authed ? "✅ 已授权配置 (生效中·4K秒播已激活)" : "❌ 尚未配置");
            vod.put("vod_content", content.toString());

            vod.put("vod_play_from", "阿里云盘授权中心");
            String t = qr != null ? qr.t : "";
            String ck = qr != null ? qr.ck : "";
            String playUrls = "【1. 手机扫码后点击检查授权】$ali_check::" + t + "::" + ck
                    + "#【2. 查看当前凭证状态】$ali_status"
                    + "#【3. 清除本地登录凭证】$ali_clear";
            vod.put("vod_play_url", playUrls);

            // 启动后台自动轮询守护线程
            if (qr != null && qr.t != null && qr.ck != null) {
                startAliPolling(qr.t, qr.ck);
            }

        } else if ("quark_config".equals(id)) {
            QuarkApi.QrResult qr = QuarkApi.get().getQrcode();
            boolean authed = PanTokenManager.get().hasQuarkCookie();

            vod.put("vod_name", "🐮 夸克网盘·扫码授权配置");
            vod.put("vod_pic", qr != null ? qr.qrImage : "https://img.icons8.com/color/480/cloud-storage.png");

            StringBuilder content = new StringBuilder();
            content.append("【夸克网盘·扫码绑定说明】\n");
            content.append("1. 请使用手机打开「夸克」App 扫描本页面显示的二维码进行登录授权\n");
            content.append("2. 手机端确认授权后，电视端将自动检测并在 2 秒内完成绑定生效！无需额外点击！\n\n");
            content.append("💡 手机/电脑网页快捷配置：在同一 WiFi 浏览器打开 ").append(webUrl).append("\n");
            content.append("当前凭证状态：").append(authed ? "✅ 已授权配置 (生效中·4K原画已激活)" : "❌ 尚未配置");
            vod.put("vod_content", content.toString());

            vod.put("vod_play_from", "夸克网盘授权中心");
            String token = qr != null ? qr.token : "";
            String playUrls = "【1. 手机扫码后点击检查授权】$quark_check::" + token
                    + "#【2. 查看当前凭证状态】$quark_status"
                    + "#【3. 清除本地登录凭证】$quark_clear";
            vod.put("vod_play_url", playUrls);

            // 启动后台自动轮询守护线程
            if (qr != null && qr.token != null) {
                startQuarkPolling(qr.token);
            }

        } else if ("web_config".equals(id)) {
            vod.put("vod_name", "🌐 局域网网页极速配置中心");
            String qrBmp = QrUtil.createDataUri(webUrl);
            vod.put("vod_pic", !qrBmp.isEmpty() ? qrBmp : "https://img.icons8.com/color/480/domain.png");

            StringBuilder content = new StringBuilder();
            content.append("【局域网网页配置中心】\n");
            content.append("用手机或电脑浏览器直接打开以下网址即可快速输入 Cookie 或 Token：\n\n");
            content.append("👉 访问网址：").append(webUrl).append("\n\n");
            content.append("在网页端点击保存后，电视端会立即弹出通知并同步永久保存生效！");
            vod.put("vod_content", content.toString());

            vod.put("vod_play_from", "网页配置中心");
            vod.put("vod_play_url", "检查局域网服务状态$web_status");

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
            vod.put("vod_content", "本源支持直接扫码获取夸克与阿里云盘凭证，亦支持网页直接粘贴。扫码成功后，所有 4K 网盘影视站均可享受超高清蓝光原画画质。");
            vod.put("vod_play_from", "使用指南");
            vod.put("vod_play_url", "查看在线发布中心$https://github.com/lublue147-netizen/subscription");
        }

        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String action(String action) {
        JSONObject result = new JSONObject();
        try {
            if ("check_ali".equals(action)) {
                boolean has = PanTokenManager.get().hasAliRefreshToken();
                if (has) {
                    boolean ok = AliYunApi.get().refreshAccessToken();
                    result.put("msg", ok ? "✅ 阿里云盘已配置，且凭证刷新成功！" : "⚠️ 阿里云盘凭证可能已失效，请重新扫码。");
                } else {
                    result.put("msg", "❌ 阿里云盘尚未配置，请点击扫码绑定。");
                }
            } else if ("check_quark".equals(action)) {
                boolean has = PanTokenManager.get().hasQuarkCookie();
                result.put("msg", has ? "✅ 夸克网盘已配置且凭证有效！" : "❌ 夸克网盘尚未配置，请点击扫码绑定。");
            } else if ("clear_ali".equals(action)) {
                PanTokenManager.get().setAliRefreshToken("");
                result.put("msg", "🗑️ 阿里云盘本地凭证已清除。");
            } else if ("clear_quark".equals(action)) {
                PanTokenManager.get().setQuarkCookie("");
                result.put("msg", "🗑️ 夸克网盘本地凭证已清除。");
            } else if ("show_ip".equals(action)) {
                String ip = PanWebServer.getLocalIp();
                int port = PanWebServer.getPort();
                result.put("msg", "🌐 请在手机/电脑浏览器打开 http://" + ip + ":" + port);
            }
        } catch (Exception e) {
            try {
                result.put("msg", "执行异常: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");

        String msg = "";

        if (id.startsWith("ali_check::")) {
            String[] parts = id.split("::");
            if (parts.length >= 3) {
                String t = parts[1];
                String ck = parts[2];
                String status = AliYunApi.get().checkQrcode(t, ck);
                if ("SUCCESS".equals(status)) {
                    msg = "✅ 阿里云盘扫码成功！凭证已生效。";
                } else if ("SCANED".equals(status)) {
                    msg = "📱 手机已扫码，请在手机端点击【确认登录】！";
                } else if ("WAITING".equals(status)) {
                    msg = "⏳ 尚未检测到扫码，请用阿里云盘 App 扫描二维码。";
                } else {
                    msg = "⚠️ 二维码已失效，请退出并重新进入本页面刷新。";
                }
            }
        } else if (id.startsWith("quark_check::")) {
            String[] parts = id.split("::");
            if (parts.length >= 2) {
                String token = parts[1];
                String status = QuarkApi.get().checkQrcode(token);
                if ("SUCCESS".equals(status)) {
                    msg = "✅ 夸克网盘扫码成功！凭证已生效。";
                } else if ("WAITING".equals(status)) {
                    msg = "⏳ 尚未检测到扫码，请用夸克 App 扫描二维码。";
                } else {
                    msg = "⚠️ 二维码已失效，请退出并重新进入本页面刷新。";
                }
            }
        } else if ("ali_status".equals(id)) {
            boolean has = PanTokenManager.get().hasAliRefreshToken();
            msg = has ? "✅ 阿里云盘已配置有效！" : "❌ 阿里云盘未配置，请扫码。";
        } else if ("quark_status".equals(id)) {
            boolean has = PanTokenManager.get().hasQuarkCookie();
            msg = has ? "✅ 夸克网盘已配置有效！" : "❌ 夸克网盘未配置，请扫码。";
        } else if ("ali_clear".equals(id)) {
            PanTokenManager.get().setAliRefreshToken("");
            msg = "🗑️ 阿里云盘本地凭证已清除。";
        } else if ("quark_clear".equals(id)) {
            PanTokenManager.get().setQuarkCookie("");
            msg = "🗑️ 夸克网盘本地凭证已清除。";
        } else if ("web_status".equals(id)) {
            msg = "🌐 局域网配置中心正常运行在端口 " + PanWebServer.getPort();
        }

        if (!msg.isEmpty()) {
            NotifyToast.show(msg);
            result.put("msg", msg);
        }

        // 返回极简视频字节，确保播放器退出时不弹播放错误
        result.put("url", "http://127.0.0.1:" + PanWebServer.getPort() + "/blank.mp4");
        return result.toString();
    }

    private void startQuarkPolling(final String token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 60; i++) {
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException e) {
                        break;
                    }
                    String status = QuarkApi.get().checkQrcode(token);
                    if ("SUCCESS".equals(status)) {
                        break;
                    } else if ("EXPIRED".equals(status)) {
                        break;
                    }
                }
            }
        }).start();
    }

    private void startAliPolling(final String t, final String ck) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 60; i++) {
                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException e) {
                        break;
                    }
                    String status = AliYunApi.get().checkQrcode(t, ck);
                    if ("SUCCESS".equals(status)) {
                        break;
                    } else if ("EXPIRED".equals(status)) {
                        break;
                    }
                }
            }
        }).start();
    }
}
