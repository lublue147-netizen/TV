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
    private static final Pattern PATTERN_LINK = Pattern.compile("https?://(?:pan\\.quark\\.cn/s/[a-zA-Z0-9]+|drive\\.uc\\.cn/s/[a-zA-Z0-9]+|(?:www\\.)?(?:alipan\\.com|aliyundrive\\.com)/s/[a-zA-Z0-9]+|pan\\.baidu\\.com/(?:s/(?:1)?[a-zA-Z0-9_-]+|share/init\\?surl=[a-zA-Z0-9_-]+))(?:\\?[^\\s\"'<#]+)?", Pattern.CASE_INSENSITIVE);
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
        return cachedBuildId.isEmpty() ? "7a7afa2d0d0da1a862a2f9a018db90c1ca09aeb0" : cachedBuildId;
    }

    public static String cleanKeyword(String title) {
        if (title == null) return "";
        // 1. 去除各类括号内容: (), [], 【】, （）
        String s = title.replaceAll("\\(.*?\\)|\\[.*?\\]|【.*?】|（.*?）", " ");
        // 2. 去除画质/规格/版本标签
        s = s.replaceAll("(?i)(4K|1080P|720P|HD|BD|国语|粤语|中字|双字|超清|高清|蓝光|60帧|杜比|HDR|SDR|加长版|未删减版|完整版|合集|电影|系列)", " ");

        // 3. 如果标题包含连续中文，优先提取开头的纯中文主标题（彻底避免外文副标题、外文名及音标引起的网盘搜索失效）
        Matcher mCn = Pattern.compile("^\\s*([\\u4e00-\\u9fa50-9：:·\\s]+)").matcher(s);
        if (mCn.find()) {
            String cn = mCn.group(1).replaceAll("[^\\u4e00-\\u9fa50-9]", " ").trim().replaceAll("\\s+", " ");
            if (cn.length() >= 2) {
                return cn;
            }
        }

        // 4. 通用兜底：去除非汉字/英文字符
        s = s.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]", " ");
        return s.trim().replaceAll("\\s+", " ");
    }

    public static List<Item> searchPan(String keyword, String pan) {
        if (keyword == null || keyword.trim().isEmpty()) return new ArrayList<>();
        String rawKey = keyword.trim();
        List<Item> results = doSearchPan(rawKey, pan);
        if (!results.isEmpty()) return results;

        // 智能重试 1: 若关键词未清理，使用 cleanKeyword 清理重试
        String cleanKw = cleanKeyword(rawKey);
        if (!cleanKw.isEmpty() && !cleanKw.equalsIgnoreCase(rawKey)) {
            results = doSearchPan(cleanKw, pan);
            if (!results.isEmpty()) return results;
        }

        // 智能重试 2: 去除季度/部数后缀重试 (如 "庆余年 第二季" -> "庆余年")
        String baseKw = !cleanKw.isEmpty() ? cleanKw : rawKey;
        String strippedKw = baseKw.replaceAll("第[一二三四五六七八九十0-9]+[季部篇卷期]", "").trim();
        if (!strippedKw.isEmpty() && !strippedKw.equals(baseKw)) {
            results = doSearchPan(strippedKw, pan);
            if (!results.isEmpty()) return results;
        }

        // 智能重试 3: 若指定了特定网盘类型 (如 baidu/quark) 且结果为空，回退到全局不限网盘搜索
        if (pan != null && !pan.isEmpty() && !"all".equalsIgnoreCase(pan)) {
            results = doSearchPan(!cleanKw.isEmpty() ? cleanKw : rawKey, "");
            if (!results.isEmpty()) return results;
        }

        return results;
    }

    public static List<Item> doSearchPan(String keyword, String pan) {
        List<Item> results = new ArrayList<>();
        if (keyword == null || keyword.trim().isEmpty()) return results;
        String buildId = getBuildId();
        try {
            String encodedKey = URLEncoder.encode(keyword.trim(), "UTF-8");
            String panParam = "";
            if (pan != null && !pan.isEmpty() && !"all".equalsIgnoreCase(pan) && !"uc".equalsIgnoreCase(pan)) {
                panParam = "&pan=" + pan;
            }
            String url = BASE_URL + "/_next/data/" + buildId + "/search.json?keyword=" + encodedKey + panParam;

            Map<String, String> h = new HashMap<>();
            h.put("User-Agent", OkHttp.CHROME);
            h.put("x-nextjs-data", "1");
            h.put("Referer", BASE_URL);

            String res = OkHttp.get(url, h);
            if (res.isEmpty() || res.contains("404") || res.contains("Internal Server Error")) {
                cachedBuildId = "";
                buildId = getBuildId();
                url = BASE_URL + "/_next/data/" + buildId + "/search.json?keyword=" + encodedKey + panParam;
                res = OkHttp.get(url, h);
            }

            if (!res.isEmpty() && !res.contains("Internal Server Error")) {
                JSONObject root = new JSONObject(res);
                JSONObject dataObj = root.optJSONObject("pageProps");
                if (dataObj != null) {
                    JSONObject d = dataObj.optJSONObject("data");
                    if (d != null) {
                        JSONArray arr = d.optJSONArray("data");
                        if (arr != null) {
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
                                cleanContent = cleanContent.replaceAll("^(?:频道新增资源[：:]|名称[：:]|资源标题[：:]|\\d+[、.：:]|[【\\[].*?[】\\]])\\s*", "");
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

                                String diskType = "4K网盘";
                                if (shareUrl.contains("pan.quark.cn")) diskType = "夸克网盘";
                                else if (shareUrl.contains("alipan.com") || shareUrl.contains("aliyundrive.com")) diskType = "阿里云盘";
                                else if (shareUrl.contains("pan.baidu.com")) diskType = "百度网盘";
                                else if (shareUrl.contains("drive.uc.cn")) diskType = "UC网盘";

                                results.add(new Item(title, shareUrl, cleanContent, time, pic, diskType));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    public static List<Item> search(String keyword) {
        List<Item> allResults = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. 综合通用搜索 (覆盖 Quark, UC, Baidu, Ali 等)
        List<Item> general = searchPan(keyword, "");
        for (Item it : general) {
            if (seen.add(it.shareUrl)) {
                allResults.add(it);
            }
        }

        // 2. 百度专项搜索
        List<Item> baidu = searchPan(keyword, "baidu");
        for (Item it : baidu) {
            if (seen.add(it.shareUrl)) {
                allResults.add(it);
            }
        }

        // 3. 夸克专项搜索
        List<Item> quark = searchPan(keyword, "quark");
        for (Item it : quark) {
            if (seen.add(it.shareUrl)) {
                allResults.add(it);
            }
        }

        // 4. 阿里专项搜索
        List<Item> ali = searchPan(keyword, "aliyundrive");
        for (Item it : ali) {
            if (seen.add(it.shareUrl)) {
                allResults.add(it);
            }
        }

        return allResults;
    }
}
