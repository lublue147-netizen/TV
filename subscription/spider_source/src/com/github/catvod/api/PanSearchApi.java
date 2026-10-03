package com.github.catvod.api;

import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PanSearch 多网盘综合搜索引擎 API
 * 覆盖 夸克 / 阿里 / 百度 / UC / 115 全网 4K 影视资源索引
 */
public class PanSearchApi {

    private static final String BASE_URL = "https://www.pansearch.me";
    private static volatile String cachedBuildId = "";
    private static volatile long lastBuildIdTime = 0;

    private static final Pattern PATTERN_BUILD_ID = Pattern.compile("\"buildId\":\"([^\"]+)\"");
    private static final Pattern PATTERN_LINK = Pattern.compile("https?://(?:pan\\.quark\\.cn|www\\.alipan\\.com|www\\.aliyundrive\\.com|pan\\.baidu\\.com|drive\\.uc\\.cn)/s/[a-zA-Z0-9_-]+");
    private static final Pattern PATTERN_TITLE = Pattern.compile("(?:名称：|资源标题：)?([^\\n\\r<#]+)");

    public static class Item {
        public String title;
        public String shareUrl;
        public String content;
        public String time;
        public String pic;
        public String diskType;

        public Item(String title, String shareUrl, String content, String time, String pic, String diskType) {
            this.title = title;
            this.shareUrl = shareUrl;
            this.content = content;
            this.time = time;
            this.pic = pic;
            this.diskType = diskType;
        }
    }

    private static synchronized String getBuildId() {
        long now = System.currentTimeMillis();
        if (!cachedBuildId.isEmpty() && (now - lastBuildIdTime < 3600000)) {
            return cachedBuildId;
        }

        try {
            Map<String, String> h = new HashMap<>();
            h.put("User-Agent", OkHttp.CHROME);
            String html = OkHttp.get(BASE_URL, h);
            Matcher m = PATTERN_BUILD_ID.matcher(html);
            if (m.find()) {
                cachedBuildId = m.group(1);
                lastBuildIdTime = now;
                return cachedBuildId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cachedBuildId.isEmpty() ? "latest" : cachedBuildId;
    }

    public static List<Item> search(String keyword) {
        List<Item> allResults = new ArrayList<>();
        String buildId = getBuildId();
        String[] pans = {"quark", "aliyundrive"};

        for (String pan : pans) {
            try {
                String encodedKey = URLEncoder.encode(keyword, "UTF-8");
                String url = BASE_URL + "/_next/data/" + buildId + "/search.json?keyword=" + encodedKey + "&pan=" + pan;

                Map<String, String> h = new HashMap<>();
                h.put("User-Agent", OkHttp.CHROME);
                h.put("x-nextjs-data", "1");
                h.put("Referer", BASE_URL);

                String res = OkHttp.get(url, h);
                if (res.isEmpty()) continue;

                JSONObject root = new JSONObject(res);
                JSONObject dataObj = root.optJSONObject("pageProps");
                if (dataObj == null) continue;
                JSONObject d = dataObj.optJSONObject("data");
                if (d == null) continue;
                JSONArray arr = d.optJSONArray("data");
                if (arr == null) continue;

                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String rawContent = item.optString("content", "");
                    String time = item.optString("time", "");
                    String pic = item.optString("image", "");

                    // 提取网盘分享链接
                    Matcher mLink = PATTERN_LINK.matcher(rawContent);
                    if (!mLink.find()) continue;
                    String shareUrl = mLink.group(0);

                    // 清理并提取标题
                    String cleanContent = rawContent.replaceAll("<[^>]+>", "").trim();
                    String title = "";
                    Matcher mTitle = PATTERN_TITLE.matcher(cleanContent);
                    if (mTitle.find()) {
                        title = mTitle.group(1).trim();
                    }
                    if (title.isEmpty()) {
                        title = cleanContent.split("\\n")[0].trim();
                    }
                    if (title.length() > 60) {
                        title = title.substring(0, 60);
                    }

                    String diskType = pan.contains("quark") ? "夸克网盘" : "阿里云盘";
                    allResults.add(new Item(title, shareUrl, cleanContent, time, pic, diskType));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return allResults;
    }
}
