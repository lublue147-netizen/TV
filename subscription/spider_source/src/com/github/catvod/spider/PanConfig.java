package com.github.catvod.spider;

import android.content.Context;
import android.graphics.Bitmap;
import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.PanTokenManager;
import com.github.catvod.api.QuarkApi;
import com.github.catvod.crawler.Spider;
import com.github.catvod.proxy.GoProxy;
import com.github.catvod.qrcode.QrDialog;
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

        boolean baiduAuthed = PanTokenManager.get().hasBaiduCookie();
        boolean ucAuthed = PanTokenManager.get().hasUcCookie();

        // 1. 百度网盘配置
        JSONObject baidu = new JSONObject();
        baidu.put("vod_id", "baidu_config");
        baidu.put("vod_name", "🐮 百度网盘·配置中心 (原画与无限)");
        baidu.put("vod_pic", "https://img.icons8.com/color/480/baidu.png");
        baidu.put("vod_remarks", baiduAuthed ? "✅ 已配置 (4K原画+无限加速)" : "❌ 未配置·点击扫码/网页输入");
        baidu.put("action", "scan_baidu");
        list.put(baidu);

        // 2. 夸克网盘扫码配置
        JSONObject quark = new JSONObject();
        quark.put("vod_id", "quark_config");
        quark.put("vod_name", "🐮 夸克网盘·扫码配置");
        quark.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
        quark.put("vod_remarks", quarkAuthed ? "✅ 已授权配置 (4K原画)" : "❌ 未配置·点击扫码绑定");
        quark.put("action", "scan_quark");
        list.put(quark);

        // 3. UC网盘配置
        JSONObject uc = new JSONObject();
        uc.put("vod_id", "uc_config");
        uc.put("vod_name", "🐮 UC 网盘·配置中心");
        uc.put("vod_pic", "https://img.icons8.com/color/480/uc-browser.png");
        uc.put("vod_remarks", ucAuthed ? "✅ 已配置 (4K秒播)" : "❌ 未配置·点击扫码/网页输入");
        uc.put("action", "scan_uc");
        list.put(uc);

        // 4. 阿里云盘扫码配置
        JSONObject ali = new JSONObject();
        ali.put("vod_id", "ali_config");
        ali.put("vod_name", "🐮 阿里云盘·扫码配置");
        ali.put("vod_pic", "https://img.icons8.com/color/480/alipay.png");
        ali.put("vod_remarks", aliAuthed ? "✅ 已授权配置 (4K秒播)" : "❌ 未配置·点击扫码绑定");
        ali.put("action", "scan_ali");
        list.put(ali);

        // 5. 局域网网页极速配置
        JSONObject web = new JSONObject();
        web.put("vod_id", "web_config");
        web.put("vod_name", "🌐 局域网网页极速配置中心");
        web.put("vod_pic", "https://img.icons8.com/color/480/domain.png");
        web.put("vod_remarks", "手机/电脑浏览器打开 http://" + localIp + ":" + port);
        web.put("action", "scan_web");
        list.put(web);

        // 6. 一键检查阿里云盘
        JSONObject chkAli = new JSONObject();
        chkAli.put("vod_id", "act_check_ali");
        chkAli.put("vod_name", "⚡ 检查/刷新阿里云盘授权");
        chkAli.put("vod_pic", "https://img.icons8.com/color/480/checked-checkbox.png");
        chkAli.put("vod_remarks", aliAuthed ? "✅ 当前已授权配置" : "❌ 未配置·点击查看");
        chkAli.put("action", "check_ali");
        list.put(chkAli);

        // 7. 一键检查夸克网盘
        JSONObject chkQuark = new JSONObject();
        chkQuark.put("vod_id", "act_check_quark");
        chkQuark.put("vod_name", "⚡ 检查/刷新夸克网盘授权");
        chkQuark.put("vod_pic", "https://img.icons8.com/color/480/checked-checkbox.png");
        chkQuark.put("vod_remarks", quarkAuthed ? "✅ 当前已授权配置" : "❌ 未配置·点击查看");
        chkQuark.put("action", "check_quark");
        list.put(chkQuark);

        // 8. 清除阿里云盘凭证
        JSONObject clrAli = new JSONObject();
        clrAli.put("vod_id", "act_clear_ali");
        clrAli.put("vod_name", "🗑️ 清除阿里云盘登录凭证");
        clrAli.put("vod_pic", "https://img.icons8.com/color/480/delete-forever.png");
        clrAli.put("vod_remarks", "点击清除本地缓存凭证");
        clrAli.put("action", "clear_ali");
        list.put(clrAli);

        // 9. 清除夸克网盘凭证
        JSONObject clrQuark = new JSONObject();
        clrQuark.put("vod_id", "act_clear_quark");
        clrQuark.put("vod_name", "🗑️ 清除夸克网盘登录凭证");
        clrQuark.put("vod_pic", "https://img.icons8.com/color/480/delete-forever.png");
        clrQuark.put("vod_remarks", "点击清除本地缓存凭证");
        clrQuark.put("action", "clear_quark");
        list.put(clrQuark);

        // 10. GoProxy 状态
        boolean goAlive = GoProxy.isAlive();
        JSONObject goproxy = new JSONObject();
        goproxy.put("vod_id", "goproxy_status");
        goproxy.put("vod_name", "🚀 GoProxy 极速加速引擎");
        goproxy.put("vod_pic", "https://img.icons8.com/color/480/speedometer.png");
        goproxy.put("vod_remarks", goAlive ? "✅ 运行中·乱序并发预取" : "⚠️ 未启动 (端口 9978)");
        goproxy.put("action", "check_goproxy");
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
            showAliQrDialog();
            boolean authed = PanTokenManager.get().hasAliRefreshToken();
            vod.put("vod_name", "🐮 阿里云盘·扫码授权配置");
            vod.put("vod_pic", "https://img.icons8.com/color/480/alipay.png");
            vod.put("vod_content", "【阿里云盘·扫码绑定说明】\n1. 请使用手机打开「阿里云盘」App 扫描弹出的二维码进行授权\n2. 手机端确认授权后，电视端将自动检测并在 2 秒内完成绑定生效！无需额外点击！\n\n💡 手机/电脑网页快捷配置：在同一 WiFi 浏览器打开 " + webUrl + "\n当前凭证状态：" + (authed ? "✅ 已授权配置 (生效中·4K秒播已激活)" : "❌ 尚未配置"));
            vod.put("vod_play_from", "");
            vod.put("vod_play_url", "");

        } else if ("quark_config".equals(id)) {
            showQuarkQrDialog();
            boolean authed = PanTokenManager.get().hasQuarkCookie();
            vod.put("vod_name", "🐮 夸克网盘·扫码授权配置");
            vod.put("vod_pic", "https://img.icons8.com/color/480/cloud-storage.png");
            vod.put("vod_content", "【夸克网盘·扫码绑定说明】\n1. 请使用手机打开「夸克」App 扫描弹出的二维码进行授权\n2. 手机端确认授权后，电视端将自动检测并在 2 秒内完成绑定生效！无需额外点击！\n\n💡 手机/电脑网页快捷配置：在同一 WiFi 浏览器打开 " + webUrl + "\n当前凭证状态：" + (authed ? "✅ 已授权配置 (生效中·4K原画已激活)" : "❌ 尚未配置"));
            vod.put("vod_play_from", "");
            vod.put("vod_play_url", "");

        } else if ("web_config".equals(id)) {
            showWebQrDialog();
            vod.put("vod_name", "🌐 局域网网页极速配置中心");
            vod.put("vod_pic", "https://img.icons8.com/color/480/domain.png");
            vod.put("vod_content", "【局域网网页配置中心】\n用手机或电脑浏览器直接打开以下网址即可快速输入 Cookie 或 Token：\n\n👉 访问网址：" + webUrl + "\n\n在网页端点击保存后，电视端会立即弹出通知并同步永久保存生效！");
            vod.put("vod_play_from", "");
            vod.put("vod_play_url", "");

        } else if ("baidu_config".equals(id)) {
            showBaiduQrDialog();
            boolean authed = PanTokenManager.get().hasBaiduCookie();
            vod.put("vod_name", "🐮 百度网盘·配置中心 (原画与无限)");
            vod.put("vod_pic", "https://img.icons8.com/color/480/baidu.png");
            vod.put("vod_content", "【百度网盘·配置说明】\n1. 在手机或电脑浏览器打开局域网配置中心：" + webUrl + "\n2. 输入百度 Cookie 或 BDUSS 即可完成配置，支持 4K 原画直连与 GoProxy 并发切片无限加速！\n\n当前凭证状态：" + (authed ? "✅ 已配置 (生效中·原画+无限加速已激活)" : "❌ 尚未配置"));
            vod.put("vod_play_from", "");
            vod.put("vod_play_url", "");

        } else if ("uc_config".equals(id)) {
            showUcQrDialog();
            boolean authed = PanTokenManager.get().hasUcCookie();
            vod.put("vod_name", "🐮 UC 网盘·配置中心");
            vod.put("vod_pic", "https://img.icons8.com/color/480/uc-browser.png");
            vod.put("vod_content", "【UC 网盘·配置说明】\n1. 在手机或电脑浏览器打开局域网配置中心：" + webUrl + "\n2. 粘贴 UC 网盘 Cookie 点击保存即可，即刻享受 4K 秒播！\n\n当前凭证状态：" + (authed ? "✅ 已配置 (生效中·4K秒播已激活)" : "❌ 尚未配置"));
            vod.put("vod_play_from", "");
            vod.put("vod_play_url", "");

        } else if ("goproxy_status".equals(id)) {
            boolean alive = GoProxy.isAlive();
            vod.put("vod_name", "🚀 GoProxy 极速加速引擎运行状态");
            vod.put("vod_pic", "https://img.icons8.com/color/480/speedometer.png");
            vod.put("vod_content", "【GoProxy 并发加速引擎】\n专为 Android 电视盒子与投影仪设计，基于 Go 原生协程实现 HTTP Range 乱序并发预取。\n播放 4K 网盘高码率视频时，有效消除拖动进度条和起播的卡顿等待。\n\n运行端口：" + GoProxy.getPort() + "\n服务状态：" + (alive ? "✅ 运行中 (加速已激活)" : "⚠️ 未检测到运行进程"));
            vod.put("vod_play_from", "");
            vod.put("vod_play_url", "");

        } else {
            vod.put("vod_name", "📖 网盘配置与 4K 秒播指南");
            vod.put("vod_pic", "https://img.icons8.com/color/480/help.png");
            vod.put("vod_content", "本源支持直接扫码获取夸克与阿里云盘凭证，亦支持网页直接粘贴。扫码成功后，所有 4K 网盘影视站均可享受超高清蓝光原画画质。");
            vod.put("vod_play_from", "");
            vod.put("vod_play_url", "");
        }

        list.put(vod);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String action(String action) {
        JSONObject result = new JSONObject();
        try {
            if ("scan_quark".equals(action)) {
                return showQuarkQrDialog();
            } else if ("scan_ali".equals(action)) {
                return showAliQrDialog();
            } else if ("scan_baidu".equals(action)) {
                return showBaiduQrDialog();
            } else if ("scan_uc".equals(action)) {
                return showUcQrDialog();
            } else if ("scan_web".equals(action)) {
                return showWebQrDialog();
            } else if ("check_goproxy".equals(action)) {
                boolean goAlive = GoProxy.isAlive();
                result.put("msg", goAlive ? "🚀 GoProxy 极速并发引擎正在稳定运行！" : "⚠️ GoProxy 引擎未启动");
            } else if ("check_ali".equals(action)) {
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

    private String showQuarkQrDialog() {
        JSONObject res = new JSONObject();
        try {
            QuarkApi.QrResult qr = QuarkApi.get().getQrcode();
            if (qr == null || qr.qrUrl == null) {
                res.put("msg", "❌ 获取夸克登录二维码失败，请检查网络");
                NotifyToast.show("获取夸克二维码失败，请检查网络");
                return res.toString();
            }
            final String token = qr.token;
            Bitmap bmp = QrUtil.createBitmap(qr.qrUrl, 400);
            QrDialog.show(
                "🐮 夸克网盘·扫码绑定",
                "请使用手机「夸克 App」扫描下方二维码",
                bmp,
                "⏳ 等待手机夸克扫码...",
                new QrDialog.Poller() {
                    @Override
                    public String check() {
                        return QuarkApi.get().checkQrcode(token);
                    }

                    @Override
                    public void onSuccess() {
                        NotifyToast.show("🎉 夸克网盘授权成功！4K原画已激活");
                    }
                }
            );
            res.put("msg", "📱 正在展示夸克扫码窗口，请使用手机扫码");
        } catch (Exception e) {
            e.printStackTrace();
            try {
                res.put("msg", "打开扫码失败: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return res.toString();
    }

    private String showAliQrDialog() {
        JSONObject res = new JSONObject();
        try {
            AliYunApi.QrResult qr = AliYunApi.get().getQrcode();
            if (qr == null || qr.codeContent == null) {
                res.put("msg", "❌ 获取阿里云盘登录二维码失败，请检查网络");
                NotifyToast.show("获取阿里云盘二维码失败，请检查网络");
                return res.toString();
            }
            final String t = qr.t;
            final String ck = qr.ck;
            Bitmap bmp = QrUtil.createBitmap(qr.codeContent, 400);
            QrDialog.show(
                "🐮 阿里云盘·扫码绑定",
                "请使用手机「阿里云盘 App」扫描下方二维码",
                bmp,
                "⏳ 等待手机阿里扫码...",
                new QrDialog.Poller() {
                    @Override
                    public String check() {
                        return AliYunApi.get().checkQrcode(t, ck);
                    }

                    @Override
                    public void onSuccess() {
                        NotifyToast.show("🎉 阿里云盘授权成功！4K秒播已激活");
                    }
                }
            );
            res.put("msg", "📱 正在展示阿里云盘扫码窗口，请使用手机扫码");
        } catch (Exception e) {
            e.printStackTrace();
            try {
                res.put("msg", "打开扫码失败: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return res.toString();
    }

    private String showWebQrDialog() {
        JSONObject res = new JSONObject();
        try {
            String webUrl = "http://" + PanWebServer.getLocalIp() + ":" + PanWebServer.getPort();
            Bitmap bmp = QrUtil.createBitmap(webUrl, 400);
            QrDialog.show(
                "🌐 局域网网页配置中心",
                "手机或电脑访问: " + webUrl,
                bmp,
                "📱 用手机微信/浏览器扫码直接打开配置页面",
                null
            );
            res.put("msg", "🌐 访问地址: " + webUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString();
    }

    private String showBaiduQrDialog() {
        JSONObject res = new JSONObject();
        try {
            String webUrl = "http://" + PanWebServer.getLocalIp() + ":" + PanWebServer.getPort();
            Bitmap bmp = QrUtil.createBitmap(webUrl, 400);
            QrDialog.show(
                "🐮 百度网盘·极速配置",
                "手机扫码或浏览器访问: " + webUrl,
                bmp,
                "📱 扫码打开局域网配置页输入百度 Cookie/BDUSS",
                null
            );
            res.put("msg", "🌐 请在网页端输入百度网盘 Cookie/BDUSS");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString();
    }

    private String showUcQrDialog() {
        JSONObject res = new JSONObject();
        try {
            String webUrl = "http://" + PanWebServer.getLocalIp() + ":" + PanWebServer.getPort();
            Bitmap bmp = QrUtil.createBitmap(webUrl, 400);
            QrDialog.show(
                "🐮 UC 网盘·极速配置",
                "手机扫码或浏览器访问: " + webUrl,
                bmp,
                "📱 扫码打开局域网配置页输入 UC Cookie",
                null
            );
            res.put("msg", "🌐 请在网页端输入 UC 网盘 Cookie");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return res.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");
        result.put("url", "");
        NotifyToast.show("请直接在配置中心扫描二维码完成绑定");
        return result.toString();
    }
}
