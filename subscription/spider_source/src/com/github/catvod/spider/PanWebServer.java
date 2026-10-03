package com.github.catvod.spider;

import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.BaiduApi;
import com.github.catvod.api.PanTokenManager;
import com.github.catvod.api.QuarkApi;
import com.github.catvod.qrcode.QrUtil;
import com.github.catvod.utils.NotifyToast;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 电视端内置轻量级局域网 Web 配置中心
 * 允许手机/电脑在同一 WiFi 下直接打开网页输入或扫码配置网盘凭证
 */
public class PanWebServer {

    private static volatile PanWebServer instance;
    private static volatile int serverPort = 9979;
    private ServerSocket serverSocket;
    private boolean running = false;

    public static synchronized void start() {
        if (instance == null) {
            instance = new PanWebServer();
            instance.initServer();
        }
    }

    public static int getPort() {
        return serverPort;
    }

    public static String getLocalIp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "127.0.0.1";
    }

    private void initServer() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (int p = 9979; p <= 9985; p++) {
                    try {
                        serverSocket = new ServerSocket(p);
                        serverPort = p;
                        running = true;
                        break;
                    } catch (Exception ignored) {}
                }
                if (!running || serverSocket == null) return;

                while (running) {
                    try {
                        final Socket socket = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handleClient(socket);
                            }
                        }).start();
                    } catch (Exception e) {
                        if (!running) break;
                    }
                }
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        try {
            socket.setSoTimeout(10000);
            InputStream is = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) {
                socket.close();
                return;
            }

            String[] parts = line.split(" ");
            if (parts.length < 2) {
                socket.close();
                return;
            }

            String method = parts[0];
            String uri = parts[1];

            // 读取 Headers 提取 Content-Length
            int contentLength = 0;
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(headerLine.substring(15).trim());
                }
            }

            // 读取 POST Body
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = reader.read(buf, 0, contentLength);
                if (read > 0) body = new String(buf, 0, read);
            }

            OutputStream os = socket.getOutputStream();
            dispatch(method, uri, body, os);
            os.flush();
            socket.close();
        } catch (Exception ignored) {
            try { socket.close(); } catch (Exception ignored2) {}
        }
    }

    private void dispatch(String method, String uri, String body, OutputStream os) throws Exception {
        String path = uri;
        String query = "";
        int qIdx = uri.indexOf('?');
        if (qIdx != -1) {
            path = uri.substring(0, qIdx);
            query = uri.substring(qIdx + 1);
        }

        Map<String, String> params = parseQuery(query);
        if ("POST".equalsIgnoreCase(method) && !body.isEmpty()) {
            params.putAll(parseQuery(body));
        }

        if ("/".equals(path) || "/index.html".equals(path)) {
            sendHtml(os, renderHtml());
        } else if ("/qrcode".equals(path)) {
            String type = params.get("type");
            byte[] bmp = null;
            if ("quark".equalsIgnoreCase(type)) {
                QuarkApi.QrResult qr = QuarkApi.get().getQrcode();
                if (qr != null) bmp = QrUtil.createBmp(qr.qrUrl, 6, 2);
            } else if ("baidu".equalsIgnoreCase(type)) {
                BaiduApi.QrResult qr = BaiduApi.get().getQrcode();
                if (qr != null) bmp = QrUtil.createBmp(qr.qrUrl, 6, 2);
            } else {
                AliYunApi.QrResult qr = AliYunApi.get().getQrcode();
                if (qr != null) bmp = QrUtil.createBmp(qr.codeContent, 6, 2);
            }
            if (bmp != null) {
                sendBinary(os, "image/bmp", bmp);
            } else {
                sendText(os, 404, "QR Generate Failed");
            }
        } else if ("/save".equals(path)) {
            String type = params.get("type");
            String val = params.get("value");
            if (val != null) {
                try { val = URLDecoder.decode(val, "UTF-8"); } catch (Exception ignored) {}
            }
            if ("quark".equalsIgnoreCase(type)) {
                PanTokenManager.get().setQuarkCookie(val);
                NotifyToast.show("✅ 手机端已更新夸克网盘配置！");
            } else if ("ali".equalsIgnoreCase(type)) {
                PanTokenManager.get().setAliRefreshToken(val);
                AliYunApi.get().refreshAccessToken();
                NotifyToast.show("✅ 手机端已更新阿里云盘配置！");
            } else if ("baidu".equalsIgnoreCase(type)) {
                PanTokenManager.get().setBaiduCookie(val);
                NotifyToast.show("✅ 手机端已更新百度网盘配置！");
            } else if ("uc".equalsIgnoreCase(type)) {
                PanTokenManager.get().setUcCookie(val);
                NotifyToast.show("✅ 手机端已更新UC网盘配置！");
            }
            sendJson(os, "{\"code\": 200, \"message\": \"保存成功！电视端已同步生效。\"}");
        } else if ("/clear".equals(path)) {
            PanTokenManager.get().clearAll();
            NotifyToast.show("🗑️ 已通过网页清除网盘凭证！");
            sendJson(os, "{\"code\": 200, \"message\": \"已成功清除所有网盘凭证。\"}");
        } else if ("/status".equals(path)) {
            boolean q = PanTokenManager.get().hasQuarkCookie();
            boolean a = PanTokenManager.get().hasAliRefreshToken();
            boolean b = PanTokenManager.get().hasBaiduCookie();
            boolean u = PanTokenManager.get().hasUcCookie();
            sendJson(os, "{\"quark\": " + q + ", \"ali\": " + a + ", \"baidu\": " + b + ", \"uc\": " + u + "}");
        } else if ("/blank.mp4".equals(path)) {
            // 返回极简视频字节，避免播放器提示错误
            byte[] blank = new byte[]{
                0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70,
                0x69, 0x73, 0x6F, 0x6D, 0x00, 0x00, 0x02, 0x00,
                0x69, 0x73, 0x6F, 0x6D, 0x69, 0x73, 0x6F, 0x32,
                0x61, 0x76, 0x63, 0x31, 0x6D, 0x70, 0x34, 0x31
            };
            sendBinary(os, "video/mp4", blank);
        } else {
            sendText(os, 404, "Not Found");
        }
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = pair.substring(0, idx).trim();
                String v = pair.substring(idx + 1).trim();
                try {
                    k = URLDecoder.decode(k, "UTF-8");
                    v = URLDecoder.decode(v, "UTF-8");
                } catch (Exception ignored) {}
                map.put(k, v);
            }
        }
        return map;
    }

    private void sendHtml(OutputStream os, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.UTF_8));
        os.write(bytes);
    }

    private void sendJson(OutputStream os, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.UTF_8));
        os.write(bytes);
    }

    private void sendBinary(OutputStream os, String contentType, byte[] data) throws IOException {
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.UTF_8));
        os.write(data);
    }

    private void sendText(OutputStream os, int code, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " OK\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        os.write(header.getBytes(StandardCharsets.UTF_8));
        os.write(bytes);
    }

    private String renderHtml() {
        boolean q = PanTokenManager.get().hasQuarkCookie();
        boolean a = PanTokenManager.get().hasAliRefreshToken();
        boolean b = PanTokenManager.get().hasBaiduCookie();
        boolean u = PanTokenManager.get().hasUcCookie();
        return "<!DOCTYPE html>\n"
                + "<html lang=\"zh-CN\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>TVBox / FongMi 云盘极速配置中心</title>\n"
                + "  <style>\n"
                + "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; padding: 18px; max-width: 600px; margin: auto; }\n"
                + "    h2 { text-align: center; color: #38bdf8; margin-bottom: 20px; }\n"
                + "    .card { background: #1e293b; border-radius: 14px; padding: 18px; margin-bottom: 20px; border: 1px solid rgba(255,255,255,0.1); box-shadow: 0 4px 15px rgba(0,0,0,0.3); }\n"
                + "    .badge { display: inline-block; padding: 4px 10px; border-radius: 12px; font-size: 13px; font-weight: bold; }\n"
                + "    .success { background: #059669; color: #fff; }\n"
                + "    .danger { background: #dc2626; color: #fff; }\n"
                + "    textarea, input { width: 100%; box-sizing: border-box; background: #090d16; border: 1px solid #334155; color: #38bdf8; padding: 12px; border-radius: 8px; font-family: monospace; font-size: 14px; margin: 10px 0; }\n"
                + "    button { width: 100%; background: #0284c7; color: #fff; border: none; padding: 12px; border-radius: 8px; font-weight: bold; cursor: pointer; font-size: 15px; margin-top: 6px; }\n"
                + "    button:hover { background: #0369a1; }\n"
                + "    .btn-clear { background: #475569; margin-top: 15px; }\n"
                + "    .btn-clear:hover { background: #334155; }\n"
                + "    .qr-box { text-align: center; margin: 14px 0; }\n"
                + "    .qr-img { width: 220px; height: 220px; background: #fff; padding: 6px; border-radius: 8px; }\n"
                + "    .tip { font-size: 13px; color: #94a3b8; line-height: 1.5; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <h2>📺 TVBox 网盘极速配置</h2>\n"
                + "  <div class=\"card\">\n"
                + "    <h3>🐮 百度网盘配置 (原画与无限) <span class=\"badge " + (b ? "success" : "danger") + "\">" + (b ? "已配置" : "尚未配置") + "</span></h3>\n"
                + "    <p class=\"tip\">输入百度网盘 Cookie 或 BDUSS（配置后可直连原画及突破限速）</p>\n"
                + "    <textarea id=\"baidu_cookie\" rows=\"3\" placeholder=\"在此粘贴完整的 Baidu Cookie 或 BDUSS=...\">" + (b ? "******(已配置)******" : "") + "</textarea>\n"
                + "    <button onclick=\"saveBaidu()\">💾 保存百度 Cookie / BDUSS</button>\n"
                + "  </div>\n"
                + "  <div class=\"card\">\n"
                + "    <h3>🐮 夸克网盘配置 <span class=\"badge " + (q ? "success" : "danger") + "\">" + (q ? "已授权绑定" : "尚未配置") + "</span></h3>\n"
                + "    <p class=\"tip\">方式 1：输入夸克 Cookie（支持手机/电脑抓包提取的 Cookie）</p>\n"
                + "    <textarea id=\"quark_cookie\" rows=\"3\" placeholder=\"在此粘贴完整的 Quark Cookie (格式如: _UP_A4A_11_=...; cookie2=...)\">" + (q ? "******(已配置)******" : "") + "</textarea>\n"
                + "    <button onclick=\"saveQuark()\">💾 保存夸克 Cookie</button>\n"
                + "    <hr style=\"border:0;border-top:1px solid #334155;margin:18px 0\">\n"
                + "    <p class=\"tip\">方式 2：使用夸克 App 扫描下方二维码一键授权绑定：</p>\n"
                + "    <div class=\"qr-box\"><img class=\"qr-img\" src=\"/qrcode?type=quark&r=" + System.currentTimeMillis() + "\" alt=\"夸克扫码\"></div>\n"
                + "  </div>\n"
                + "  <div class=\"card\">\n"
                + "    <h3>🐮 UC 网盘配置 <span class=\"badge " + (u ? "success" : "danger") + "\">" + (u ? "已配置" : "尚未配置") + "</span></h3>\n"
                + "    <p class=\"tip\">输入 UC 网盘 Cookie</p>\n"
                + "    <textarea id=\"uc_cookie\" rows=\"3\" placeholder=\"在此粘贴完整的 UC Cookie\">" + (u ? "******(已配置)******" : "") + "</textarea>\n"
                + "    <button onclick=\"saveUc()\">💾 保存 UC Cookie</button>\n"
                + "  </div>\n"
                + "  <div class=\"card\">\n"
                + "    <h3>🐮 阿里云盘配置 <span class=\"badge " + (a ? "success" : "danger") + "\">" + (a ? "已授权绑定" : "尚未配置") + "</span></h3>\n"
                + "    <p class=\"tip\">方式 1：输入阿里云盘 32 位 Refresh Token</p>\n"
                + "    <input type=\"text\" id=\"ali_token\" placeholder=\"在此输入 32 位 Refresh Token\" value=\"" + (a ? "******(已配置)******" : "") + "\">\n"
                + "    <button onclick=\"saveAli()\">💾 保存阿里 Token</button>\n"
                + "    <hr style=\"border:0;border-top:1px solid #334155;margin:18px 0\">\n"
                + "    <p class=\"tip\">方式 2：使用阿里云盘 App 扫描下方二维码一键授权绑定：</p>\n"
                + "    <div class=\"qr-box\"><img class=\"qr-img\" src=\"/qrcode?type=ali&r=" + System.currentTimeMillis() + "\" alt=\"阿里扫码\"></div>\n"
                + "  </div>\n"
                + "  <button class=\"btn-clear\" onclick=\"clearAll()\">🗑️ 清除电视端所有网盘凭证</button>\n"
                + "  <script>\n"
                + "    function saveBaidu() {\n"
                + "      const val = document.getElementById('baidu_cookie').value;\n"
                + "      if (!val || val.includes('已配置')) return alert('请输入新的 Cookie');\n"
                + "      fetch('/save?type=baidu&value=' + encodeURIComponent(val)).then(r => r.json()).then(res => { alert(res.message); location.reload(); });\n"
                + "    }\n"
                + "    function saveQuark() {\n"
                + "      const val = document.getElementById('quark_cookie').value;\n"
                + "      if (!val || val.includes('已配置')) return alert('请输入新的 Cookie');\n"
                + "      fetch('/save?type=quark&value=' + encodeURIComponent(val)).then(r => r.json()).then(res => { alert(res.message); location.reload(); });\n"
                + "    }\n"
                + "    function saveUc() {\n"
                + "      const val = document.getElementById('uc_cookie').value;\n"
                + "      if (!val || val.includes('已配置')) return alert('请输入新的 Cookie');\n"
                + "      fetch('/save?type=uc&value=' + encodeURIComponent(val)).then(r => r.json()).then(res => { alert(res.message); location.reload(); });\n"
                + "    }\n"
                + "    function saveAli() {\n"
                + "      const val = document.getElementById('ali_token').value;\n"
                + "      if (!val || val.includes('已配置')) return alert('请输入新的 Token');\n"
                + "      fetch('/save?type=ali&value=' + encodeURIComponent(val)).then(r => r.json()).then(res => { alert(res.message); location.reload(); });\n"
                + "    }\n"
                + "    function clearAll() {\n"
                + "      if (confirm('确定要清除电视端已保存的所有凭证吗？')) {\n"
                + "        fetch('/clear').then(r => r.json()).then(res => { alert(res.message); location.reload(); });\n"
                + "      }\n"
                + "    }\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>";
    }
}
