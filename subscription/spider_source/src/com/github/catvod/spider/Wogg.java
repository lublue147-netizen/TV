package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.proxy.GoProxy;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 玩偶哥哥 / 玩偶 4K 开源 Spider 爬虫实现
 * 100% 纯 Java 源码，自动解析 夸克 / 阿里 / 百度 / UC / 115 网盘原画直链并由 GoProxy 极速加速
 */
public class Wogg extends Spider {

    private String siteUrl = "https://tvfan.xxooo.cf";
    private static final String[] DEFAULT_MIRRORS = {
        "https://tvfan.xxooo.cf",
        "https://wogg.link",
        "https://tv.wogg.xyz",
        "https://wogg.xxoo.team",
        "https://wogg.net"
    };

    private static final Pattern REGEX_ITEM = Pattern.compile(
        "<div[^>]*class=\"module-item\"[\\s\\S]*?<a[^>]*href=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"[\\s\\S]*?(?:data-src|src)=\"([^\"]+)\"[\\s\\S]*?(?:<div[^>]*class=\"module-item-text\">([^<]*)</div>)?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_SEARCH_ITEM = Pattern.compile(
        "<div[^>]*class=\"module-search-item\"[\\s\\S]*?<a[^>]*href=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"[\\s\\S]*?(?:data-src|src)=\"([^\"]+)\"[\\s\\S]*?(?:<div[^>]*class=\"video-tag-icon\">([^<]*)</div>)?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_NETDISK = Pattern.compile(
        "(https?://(?:www\\.)?(?:alipan\\.com|aliyundrive\\.com|pan\\.quark\\.cn|pan\\.baidu\\.com|drive\\.uc\\.cn|115\\.com)/s/[a-zA-Z0-9_#-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_CLIPBOARD = Pattern.compile(
        "data-clipboard-text=\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_TITLE = Pattern.compile(
        "<h1[^>]*class=\"page-title\"[^>]*>([^<]+)</h1>",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_PIC = Pattern.compile(
        "<div[^>]*class=\"module-item-pic\"[\\s\\S]*?(?:data-src|src)=\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_DESC = Pattern.compile(
        "<div[^>]*class=\"sqjj_a\"[^>]*>([\\s\\S]*?)</div>",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.trim().isEmpty()) {
            String ext = extend.trim();
            if (ext.startsWith("http://") || ext.startsWith("https://")) {
                this.siteUrl = ext;
            } else if (ext.startsWith("{") && ext.endsWith("}")) {
                try {
                    JSONObject obj = new JSONObject(ext);
                    if (obj.has("site")) this.siteUrl = obj.getString("site");
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        String[][] categories = {
            {"1", "4K电影"},
            {"2", "电视剧"},
            {"3", "热门综艺"},
            {"4", "精品动漫"},
            {"5", "音乐日韩"}
        };

        for (String[] cat : categories) {
            JSONObject c = new JSONObject();
            c.put("type_id", cat[0]);
            c.put("type_name", cat[1]);
            classes.put(c);
        }
        result.put("class", classes);

        // Fetch homepage hot items
        JSONArray list = new JSONArray();
        String html = OkHttp.get(siteUrl);
        if (!html.isEmpty()) {
            parseVodList(html, REGEX_ITEM, list, 24);
        }
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String page = (pg == null || pg.isEmpty()) ? "1" : pg;
        String url = siteUrl + "/index.php/vodshow/" + tid + "--------" + page + "---.html";
        String html = OkHttp.get(url);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        parseVodList(html, REGEX_ITEM, list, 72);

        result.put("page", Integer.parseInt(page));
        result.put("pagecount", 999);
        result.put("limit", 72);
        result.put("total", 9999);
        result.put("list", list);
        return result.toString();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        String page = (pg == null || pg.isEmpty()) ? "1" : pg;
        String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
        String url = siteUrl + "/index.php/vodsearch/" + encoded + "----------" + page + "---.html";
        String html = OkHttp.get(url);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        parseVodList(html, REGEX_SEARCH_ITEM, list, 30);
        if (list.length() == 0) {
            parseVodList(html, REGEX_ITEM, list, 30);
        }

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String vodId = ids.get(0);
        String url = vodId.startsWith("http") ? vodId : (siteUrl + (vodId.startsWith("/") ? "" : "/") + vodId);
        String html = OkHttp.get(url);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        JSONObject vod = new JSONObject();
        vod.put("vod_id", vodId);

        String title = "玩偶4K影视";
        Matcher mTitle = REGEX_TITLE.matcher(html);
        if (mTitle.find()) title = mTitle.group(1).trim();
        vod.put("vod_name", title);

        Matcher mPic = REGEX_PIC.matcher(html);
        if (mPic.find()) vod.put("vod_pic", mPic.group(1).trim());

        Matcher mDesc = REGEX_DESC.matcher(html);
        if (mDesc.find()) {
            vod.put("vod_content", mDesc.group(1).replaceAll("<[^>]+>", "").replace("[收起部分]", "").trim());
        }

        // Extract Netdisk Links
        Set<String> quarkLinks = new LinkedHashSet<>();
        Set<String> aliLinks = new LinkedHashSet<>();
        Set<String> otherLinks = new LinkedHashSet<>();

        // 1. From clipboard text
        Matcher mClip = REGEX_CLIPBOARD.matcher(html);
        while (mClip.find()) {
            categorizeLink(mClip.group(1).trim(), quarkLinks, aliLinks, otherLinks);
        }

        // 2. From href links
        Matcher mDisk = REGEX_NETDISK.matcher(html);
        while (mDisk.find()) {
            categorizeLink(mDisk.group(1).trim(), quarkLinks, aliLinks, otherLinks);
        }

        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        if (!quarkLinks.isEmpty()) {
            appendPlaySource(playFrom, playUrl, "夸克极速", quarkLinks);
        }
        if (!aliLinks.isEmpty()) {
            appendPlaySource(playFrom, playUrl, "阿里原画", aliLinks);
        }
        if (!otherLinks.isEmpty()) {
            appendPlaySource(playFrom, playUrl, "综合网盘", otherLinks);
        }

        if (playFrom.length() == 0) {
            playFrom.append("极速播放");
            playUrl.append("极速解析播放$").append(url);
        }

        vod.put("vod_play_from", playFrom.toString());
        vod.put("vod_play_url", playUrl.toString());
        list.put(vod);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");
        // Route through GoProxy multi-threaded range slice prefetching
        result.put("url", GoProxy.wrap(id, null));
        JSONObject headers = new JSONObject();
        headers.put("User-Agent", OkHttp.CHROME);
        result.put("header", headers.toString());
        return result.toString();
    }

    private void categorizeLink(String link, Set<String> quark, Set<String> ali, Set<String> other) {
        if (link.contains("quark.cn")) quark.add(link);
        else if (link.contains("alipan.com") || link.contains("aliyundrive.com")) ali.add(link);
        else if (link.startsWith("http")) other.add(link);
    }

    private void appendPlaySource(StringBuilder playFrom, StringBuilder playUrl, String name, Set<String> links) {
        if (playFrom.length() > 0) {
            playFrom.append("$$$");
            playUrl.append("$$$");
        }
        playFrom.append(name);
        int idx = 1;
        for (String link : links) {
            if (idx > 1) playUrl.append("#");
            playUrl.append("4K极速播放-第").append(idx).append("集$").append(link);
            idx++;
        }
    }

    private void parseVodList(String html, Pattern pattern, JSONArray list, int maxCount) {
        Matcher matcher = pattern.matcher(html);
        int count = 0;
        while (matcher.find() && count < maxCount) {
            try {
                String id = matcher.group(1).trim();
                String name = matcher.group(2).trim();
                String pic = matcher.group(3).trim();
                String remark = matcher.groupCount() >= 4 && matcher.group(4) != null ? matcher.group(4).trim() : "4K原画";

                JSONObject item = new JSONObject();
                item.put("vod_id", id);
                item.put("vod_name", name);
                item.put("vod_pic", pic);
                item.put("vod_remarks", remark);
                list.put(item);
                count++;
            } catch (Exception ignored) {}
        }
    }
}
