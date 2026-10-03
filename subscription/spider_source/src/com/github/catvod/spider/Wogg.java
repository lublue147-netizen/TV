package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.PanSearchApi;
import com.github.catvod.api.QuarkApi;
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
 * 玩偶哥哥 / 玩偶 4K 纯 Java 开源 Spider 爬虫
 * 具备以下特性：
 * 1. 自动聚合 PanSearch 全网网盘搜索引擎与多 Wogg 镜像
 * 2. 深度递归提取 夸克 / 阿里 云盘目录内全部视频分集
 * 3. 对接 GoProxy 多线程并发切片加速，输出 4K 原画直链
 */
public class Wogg extends Spider {

    private String siteUrl = "https://tvfan.xxooo.cf";
    private static final String[] CANDIDATE_MIRRORS = {
        "https://www.wogg.one",
        "https://www.wogg.lol",
        "https://wogg.link",
        "https://wogg.xxoo.team",
        "https://tvfan.xxooo.cf"
    };

    private static final Pattern REGEX_ITEM = Pattern.compile(
        "<div[^>]*class=\"module-item\"[\\s\\S]*?<a[^>]*href=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"[\\s\\S]*?(?:data-src|src)=\"([^\"]+)\"[\\s\\S]*?(?:<div[^>]*class=\"module-item-text\">([^<]*)</div>)?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_SEARCH_ITEM = Pattern.compile(
        "<div[^>]*class=\"module-search-item\"[\\s\\S]*?<a[^>]*href=\"([^\"]+)\"[^>]*title=\"([^\"]+)\"[\\s\\S]*?(?:data-src|src)=\"([^\"]+)\"[\\s\\S]*?(?:<div[^>]*class=\"video-tag-icon\">([^<]*)</div>)?",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_QUARK_LINK = Pattern.compile(
        "https?://pan\\.quark\\.cn/s/([a-zA-Z0-9]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_ALI_LINK = Pattern.compile(
        "https?://(?:www\\.)?(?:alipan\\.com|aliyundrive\\.com)/s/([a-zA-Z0-9]+)",
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
        Init.init(context);
        com.github.catvod.api.PanTokenManager.get().load();
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
            {"2", "全集连续剧"},
            {"3", "精品动漫"},
            {"4", "热门综艺"},
            {"5", "爽文短剧"}
        };

        for (String[] cat : categories) {
            JSONObject c = new JSONObject();
            c.put("type_id", cat[0]);
            c.put("type_name", cat[1]);
            classes.put(c);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();

        // 1. 尝试从 Wogg 镜像获取
        String html = OkHttp.get(siteUrl);
        if (!html.isEmpty() && html.contains("module-item")) {
            parseVodList(html, REGEX_ITEM, list, 30);
        }

        // 2. 若镜像不可用或被防爬拦截，使用 PanSearch 4K 热门资源作为首页兜底
        if (list.length() == 0) {
            List<PanSearchApi.Item> hotItems = PanSearchApi.search("4K");
            for (PanSearchApi.Item item : hotItems) {
                JSONObject v = new JSONObject();
                v.put("vod_id", item.shareUrl);
                v.put("vod_name", item.title);
                v.put("vod_pic", item.pic.isEmpty() ? "https://img.icons8.com/color/480/film-reel.png" : item.pic);
                v.put("vod_remarks", item.diskType + "·4K原画");
                list.put(v);
                if (list.length() >= 30) break;
            }
        }

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String page = (pg == null || pg.isEmpty()) ? "1" : pg;
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        // 1. 尝试镜像分类
        String url = siteUrl + "/index.php/vodshow/" + tid + "--------" + page + "---.html";
        String html = OkHttp.get(url);
        if (!html.isEmpty() && html.contains("module-item")) {
            parseVodList(html, REGEX_ITEM, list, 72);
        }

        // 2. 回退到 PanSearch 分类关键词搜索
        if (list.length() == 0) {
            String keyword = "4K";
            if ("1".equals(tid)) keyword = "电影 4K";
            else if ("2".equals(tid)) keyword = "剧集 4K";
            else if ("3".equals(tid)) keyword = "动漫 4K";
            else if ("4".equals(tid)) keyword = "综艺 4K";
            else if ("5".equals(tid)) keyword = "短剧";

            List<PanSearchApi.Item> searchItems = PanSearchApi.search(keyword);
            for (PanSearchApi.Item item : searchItems) {
                JSONObject v = new JSONObject();
                v.put("vod_id", item.shareUrl);
                v.put("vod_name", item.title);
                v.put("vod_pic", item.pic.isEmpty() ? "https://img.icons8.com/color/480/film-reel.png" : item.pic);
                v.put("vod_remarks", item.diskType + "·4K原画");
                list.put(v);
            }
        }

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
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        Set<String> seenUrls = new HashSet<>();

        // 1. 优先使用高速 PanSearch 多网盘引擎 (响应 < 500ms, 资源丰富)
        List<PanSearchApi.Item> searchItems = PanSearchApi.search(key);
        for (PanSearchApi.Item item : searchItems) {
            if (seenUrls.contains(item.shareUrl)) continue;
            seenUrls.add(item.shareUrl);

            JSONObject v = new JSONObject();
            v.put("vod_id", item.shareUrl);
            v.put("vod_name", item.title);
            v.put("vod_pic", item.pic.isEmpty() ? "https://img.icons8.com/color/480/film-reel.png" : item.pic);
            v.put("vod_remarks", item.diskType + "·4K");
            list.put(v);
        }

        // 2. 同时补充 Wogg 镜像站内结果
        try {
            String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
            String woggUrl = siteUrl + "/index.php/vodsearch/" + encoded + "----------" + pg + "---.html";
            String html = OkHttp.get(woggUrl);
            if (!html.isEmpty()) {
                parseVodList(html, REGEX_SEARCH_ITEM, list, 20);
                if (list.length() == searchItems.size()) {
                    parseVodList(html, REGEX_ITEM, list, 20);
                }
            }
        } catch (Exception ignored) {}

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (ids == null || ids.isEmpty()) return "";
        String vodId = ids.get(0);

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        JSONObject vod = new JSONObject();
        vod.put("vod_id", vodId);

        String title = "4K网盘影视";
        String pic = "https://img.icons8.com/color/480/film-reel.png";
        String desc = "极速秒播 4K 原画资源";

        Set<String> quarkShareIds = new LinkedHashSet<>();
        Set<String> aliShareIds = new LinkedHashSet<>();

        // 情况 A: vodId 直接是网盘分享链接 (来自 PanSearch)
        if (vodId.startsWith("http://") || vodId.startsWith("https://")) {
            Matcher mQ = REGEX_QUARK_LINK.matcher(vodId);
            if (mQ.find()) {
                quarkShareIds.add(mQ.group(1));
                title = "夸克4K极速影视";
            }
            Matcher mA = REGEX_ALI_LINK.matcher(vodId);
            if (mA.find()) {
                aliShareIds.add(mA.group(1));
                title = "阿里4K原画影视";
            }
        }

        // 情况 B: vodId 是 Wogg 详情页相对路径或网页 URL
        if (quarkShareIds.isEmpty() && aliShareIds.isEmpty()) {
            String pageUrl = vodId.startsWith("http") ? vodId : (siteUrl + (vodId.startsWith("/") ? "" : "/") + vodId);
            String html = OkHttp.get(pageUrl);

            Matcher mTitle = REGEX_TITLE.matcher(html);
            if (mTitle.find()) title = mTitle.group(1).trim();

            Matcher mPic = REGEX_PIC.matcher(html);
            if (mPic.find()) pic = mPic.group(1).trim();

            Matcher mDesc = REGEX_DESC.matcher(html);
            if (mDesc.find()) {
                desc = mDesc.group(1).replaceAll("<[^>]+>", "").replace("[收起部分]", "").trim();
            }

            // 提取剪贴板与页面所有链接
            Matcher mClip = REGEX_CLIPBOARD.matcher(html);
            while (mClip.find()) {
                extractShareIds(mClip.group(1), quarkShareIds, aliShareIds);
            }
            extractShareIds(html, quarkShareIds, aliShareIds);
        }

        vod.put("vod_name", title);
        vod.put("vod_pic", pic);
        vod.put("vod_content", desc);

        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        // 1. 递归解析夸克网盘目录中的全部剧集
        for (String qId : quarkShareIds) {
            String stoken = QuarkApi.get().getShareToken(qId, "");
            List<QuarkApi.FileItem> qFiles = QuarkApi.get().listShareFiles(qId, stoken);

            if (!qFiles.isEmpty()) {
                if (playFrom.length() > 0) {
                    playFrom.append("$$$");
                    playUrl.append("$$$");
                }
                playFrom.append("夸克极速4K");
                int idx = 1;
                for (QuarkApi.FileItem item : qFiles) {
                    if (idx > 1) playUrl.append("#");
                    String epName = cleanEpisodeName(item.name, idx);
                    // 存储定位参数供 playerContent 使用
                    String playParam = "quark::" + qId + "::" + stoken + "::" + item.fid + "::" + item.shareFidToken;
                    playUrl.append(epName).append("$").append(playParam);
                    idx++;
                }
            }
        }

        // 2. 递归解析阿里云盘目录中的全部剧集
        for (String aId : aliShareIds) {
            String shareToken = AliYunApi.get().getShareToken(aId, "");
            List<AliYunApi.FileItem> aFiles = AliYunApi.get().listShareFiles(aId, shareToken);

            if (!aFiles.isEmpty()) {
                if (playFrom.length() > 0) {
                    playFrom.append("$$$");
                    playUrl.append("$$$");
                }
                playFrom.append("阿里原画4K");
                int idx = 1;
                for (AliYunApi.FileItem item : aFiles) {
                    if (idx > 1) playUrl.append("#");
                    String epName = cleanEpisodeName(item.name, idx);
                    String playParam = "ali::" + aId + "::" + shareToken + "::" + item.fileId;
                    playUrl.append(epName).append("$").append(playParam);
                    idx++;
                }
            }
        }

        // 3. 兜底播放来源，避免出现“没有内容”
        if (playFrom.length() == 0) {
            playFrom.append("极速播放");
            playUrl.append("原画播放$").append(vodId);
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

        String rawStreamUrl = "";
        Map<String, String> headers = new HashMap<>();

        if (id.startsWith("quark::")) {
            // 解析夸克分集直链
            String[] parts = id.split("::");
            if (parts.length >= 5) {
                String shareId = parts[1];
                String stoken = parts[2];
                String fid = parts[3];
                String shareFidToken = parts[4];
                rawStreamUrl = QuarkApi.get().getPlayUrl(shareId, stoken, fid, shareFidToken);
            }
            headers.put("User-Agent", OkHttp.CHROME);
            headers.put("Referer", "https://pan.quark.cn/");
        } else if (id.startsWith("ali::")) {
            // 解析阿里分集直链
            String[] parts = id.split("::");
            if (parts.length >= 4) {
                String shareId = parts[1];
                String shareToken = parts[2];
                String fileId = parts[3];
                rawStreamUrl = AliYunApi.get().getPlayUrl(shareId, shareToken, fileId);
            }
            headers.put("User-Agent", OkHttp.CHROME);
            headers.put("Referer", "https://www.aliyundrive.com/");
        } else {
            rawStreamUrl = id;
            headers.put("User-Agent", OkHttp.CHROME);
        }

        if (rawStreamUrl.isEmpty()) {
            rawStreamUrl = id;
        }

        // 通过 GoProxy 乱序并发 Range 预取切片进行流媒体加速
        result.put("url", GoProxy.wrap(rawStreamUrl, headers));
        JSONObject hObj = new JSONObject();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            hObj.put(entry.getKey(), entry.getValue());
        }
        result.put("header", hObj.toString());
        return result.toString();
    }

    private void extractShareIds(String text, Set<String> quark, Set<String> ali) {
        if (text == null) return;
        Matcher mQ = REGEX_QUARK_LINK.matcher(text);
        while (mQ.find()) {
            quark.add(mQ.group(1));
        }
        Matcher mA = REGEX_ALI_LINK.matcher(text);
        while (mA.find()) {
            ali.add(mA.group(1));
        }
    }

    private String cleanEpisodeName(String rawName, int fallbackIndex) {
        if (rawName == null || rawName.isEmpty()) {
            return String.format(Locale.getDefault(), "第%02d集", fallbackIndex);
        }
        String clean = rawName;
        int dot = clean.lastIndexOf(".");
        if (dot > 0) clean = clean.substring(0, dot);
        if (clean.length() > 30) {
            clean = clean.substring(0, 30);
        }
        return clean;
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
