package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.BaiduApi;
import com.github.catvod.api.PanSearchApi;
import com.github.catvod.api.QuarkApi;
import com.github.catvod.api.UcApi;
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
 * 2. 深度递归提取 百度 / 夸克 / UC / 阿里 云盘目录内全部视频分集
 * 3. 完美展示【百度原画】与【百度无限】（GoProxy 多协程并发切片加速），以及【夸克原画】/【UC原画】/【阿里原画】
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

    private static final Pattern REGEX_UC_LINK = Pattern.compile(
        "https?://drive\\.uc\\.cn/s/([a-zA-Z0-9]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_BAIDU_LINK = Pattern.compile(
        "https?://pan\\.baidu\\.com/s/(?:1)?([a-zA-Z0-9_-]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern REGEX_BAIDU_PWD = Pattern.compile(
        "(?:提取码|pwd|密码)[:：=\\s]*([a-zA-Z0-9]{4})",
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
            {"2", "全集剧集"},
            {"3", "精品动漫"},
            {"4", "热门综艺"},
            {"5", "爽文短剧"},
            {"6", "热播韩剧"},
            {"7", "经典纪录片"}
        };

        for (String[] cat : categories) {
            JSONObject c = new JSONObject();
            c.put("type_id", cat[0]);
            c.put("type_name", cat[1]);
            classes.put(c);
        }
        result.put("class", classes);

        JSONArray list = new JSONArray();

        // 1. 若配置了自定义有效镜像站，优先尝试镜像站
        if (siteUrl != null && !siteUrl.contains("tvfan.xxooo.cf")) {
            try {
                String html = OkHttp.get(siteUrl);
                if (!html.isEmpty() && html.contains("module-item")) {
                    parseVodList(html, REGEX_ITEM, list, 30);
                }
            } catch (Exception ignored) {}
        }

        // 2. 高清豆瓣热门影视库 (保证秒开加载、高清海报、绝不为空)
        if (list.length() == 0) {
            fetchDoubanSubjects("movie", "热门", 15, 0, list);
            fetchDoubanSubjects("tv", "热门", 15, 0, list);
        }

        // 3. PanSearch 4K 热门保底
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
        int page = 1;
        try { page = Integer.parseInt(pg); } catch (Exception ignored) {}
        int limit = 24;
        int start = (page - 1) * limit;

        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();

        // 1. 尝试自定义镜像分类
        if (siteUrl != null && !siteUrl.contains("tvfan.xxooo.cf")) {
            try {
                String url = siteUrl + "/index.php/vodshow/" + tid + "--------" + page + "---.html";
                String html = OkHttp.get(url);
                if (!html.isEmpty() && html.contains("module-item")) {
                    parseVodList(html, REGEX_ITEM, list, 72);
                }
            } catch (Exception ignored) {}
        }

        // 2. 映射豆瓣各大分类库 (绝不报找不到数据，分页稳定)
        if (list.length() == 0) {
            String type = "movie";
            String tag = "热门";
            if ("1".equals(tid)) {
                type = "movie";
                tag = "热门";
            } else if ("2".equals(tid)) {
                type = "tv";
                tag = "热门";
            } else if ("3".equals(tid)) {
                type = "tv";
                tag = "日本动画";
            } else if ("4".equals(tid)) {
                type = "tv";
                tag = "综艺";
            } else if ("5".equals(tid)) {
                type = "tv";
                tag = "国产剧";
            } else if ("6".equals(tid)) {
                type = "tv";
                tag = "韩剧";
            } else if ("7".equals(tid)) {
                type = "tv";
                tag = "纪录片";
            }
            fetchDoubanSubjects(type, tag, limit, start, list);
        }

        // 3. PanSearch 关键词保底
        if (list.length() == 0) {
            String keyword = "4K";
            if ("1".equals(tid)) keyword = "电影 4K";
            else if ("2".equals(tid)) keyword = "剧集 4K";
            else if ("3".equals(tid)) keyword = "动漫 4K";
            else if ("4".equals(tid)) keyword = "综艺 4K";
            else if ("5".equals(tid)) keyword = "短剧";
            else if ("6".equals(tid)) keyword = "韩剧 4K";
            else if ("7".equals(tid)) keyword = "纪录片 4K";

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

        result.put("page", page);
        result.put("pagecount", 999);
        result.put("limit", limit);
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
        Set<String> seenNames = new HashSet<>();

        // 1. 豆瓣实时精准联想 (获取高清封面与条目)
        try {
            String url = "https://movie.douban.com/j/subject_suggest?q=" + URLEncoder.encode(key, "UTF-8");
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", OkHttp.CHROME);
            headers.put("Referer", "https://movie.douban.com/");
            String json = OkHttp.get(url, headers);
            if (!json.isEmpty()) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    String id = item.optString("id");
                    String title = item.optString("title");
                    String img = item.optString("img");
                    String year = item.optString("year");

                    seenNames.add(title);
                    JSONObject v = new JSONObject();
                    v.put("vod_id", "db::" + id + "::" + title + "::" + img);
                    v.put("vod_name", title);
                    v.put("vod_pic", img.isEmpty() ? "https://img.icons8.com/color/480/film-reel.png" : (img + "@Referer=https://movie.douban.com/@User-Agent=" + OkHttp.CHROME));
                    v.put("vod_remarks", year.isEmpty() ? "4K原画" : (year + "·4K原画"));
                    list.put(v);
                }
            }
        } catch (Exception ignored) {}

        // 2. PanSearch 多网盘综合搜索 (补充直接网盘条目)
        List<PanSearchApi.Item> searchItems = PanSearchApi.search(key);
        for (PanSearchApi.Item item : searchItems) {
            if (seenNames.contains(item.title)) continue;
            seenNames.add(item.title);

            JSONObject v = new JSONObject();
            v.put("vod_id", item.shareUrl);
            v.put("vod_name", item.title);
            v.put("vod_pic", item.pic.isEmpty() ? "https://img.icons8.com/color/480/film-reel.png" : item.pic);
            v.put("vod_remarks", item.diskType + "·4K");
            list.put(v);
        }

        result.put("list", list);
        return result.toString();
    }

    private void fetchDoubanSubjects(String type, String tag, int limit, int start, JSONArray list) {
        try {
            String url = "https://movie.douban.com/j/search_subjects?type=" + type + "&tag=" + URLEncoder.encode(tag, "UTF-8") + "&page_limit=" + limit + "&page_start=" + start;
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", OkHttp.CHROME);
            headers.put("Referer", "https://movie.douban.com/");
            String json = OkHttp.get(url, headers);
            if (!json.isEmpty()) {
                JSONObject obj = new JSONObject(json);
                JSONArray subjects = obj.optJSONArray("subjects");
                if (subjects != null) {
                    for (int i = 0; i < subjects.length(); i++) {
                        JSONObject sub = subjects.getJSONObject(i);
                        String id = sub.optString("id");
                        String title = sub.optString("title");
                        String cover = sub.optString("cover");
                        String rate = sub.optString("rate");

                        JSONObject vod = new JSONObject();
                        vod.put("vod_id", "db::" + id + "::" + title + "::" + cover);
                        vod.put("vod_name", title);
                        vod.put("vod_pic", cover.isEmpty() ? "https://img.icons8.com/color/480/film-reel.png" : (cover + "@Referer=https://movie.douban.com/@User-Agent=" + OkHttp.CHROME));
                        vod.put("vod_remarks", rate.isEmpty() ? "4K原画" : (rate + "分·4K原画"));
                        list.put(vod);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        Set<String> ucShareIds = new LinkedHashSet<>();
        List<String[]> baiduShares = new ArrayList<>(); // [fullUrl, surl, pwd]
        Set<String> aliShareIds = new LinkedHashSet<>();

        // 情况 A: vodId 是豆瓣条目 (db::doubanId::title::cover)
        if (vodId.startsWith("db::")) {
            String[] parts = vodId.split("::");
            String doubanId = parts.length > 1 ? parts[1] : "";
            title = parts.length > 2 ? parts[2] : "4K网盘影视";
            if (parts.length > 3 && !parts[3].isEmpty()) {
                pic = parts[3];
                if (pic.contains("doubanio.com") && !pic.contains("@Referer=")) {
                    pic = pic + "@Referer=https://movie.douban.com/@User-Agent=" + OkHttp.CHROME;
                }
            }
            if (!doubanId.isEmpty()) {
                try {
                    String absUrl = "https://movie.douban.com/j/subject_abstract?subject_id=" + doubanId;
                    Map<String, String> h = new HashMap<>();
                    h.put("User-Agent", OkHttp.CHROME);
                    h.put("Referer", "https://movie.douban.com/");
                    String absJson = OkHttp.get(absUrl, h);
                    if (!absJson.isEmpty()) {
                        JSONObject aObj = new JSONObject(absJson).optJSONObject("subject");
                        if (aObj != null) {
                            if (aObj.has("title")) title = aObj.optString("title");
                            if (aObj.has("region")) vod.put("vod_area", aObj.optString("region"));
                            if (aObj.has("release_year")) vod.put("vod_year", aObj.optString("release_year"));
                            if (aObj.has("directors")) vod.put("vod_director", aObj.optJSONArray("directors").join("/").replace("\"", ""));
                            if (aObj.has("actors")) vod.put("vod_actor", aObj.optJSONArray("actors").join("/").replace("\"", ""));
                            if (aObj.has("types")) vod.put("vod_class", aObj.optJSONArray("types").join("/").replace("\"", ""));
                            JSONObject sc = aObj.optJSONObject("short_comment");
                            if (sc != null && sc.has("content")) desc = sc.optString("content");
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // 情况 B: vodId 直接是网盘分享链接 (来自 PanSearch)
        if (vodId.startsWith("http://") || vodId.startsWith("https://")) {
            extractAllShares(vodId, quarkShareIds, ucShareIds, baiduShares, aliShareIds);
            if (!baiduShares.isEmpty()) title = "百度4K原画影视";
            else if (!quarkShareIds.isEmpty()) title = "夸克4K极速影视";
            else if (!ucShareIds.isEmpty()) title = "UC4K极速影视";
            else if (!aliShareIds.isEmpty()) title = "阿里4K原画影视";
        }

        // 情况 C: vodId 是 Wogg 详情页相对路径或网页 URL
        if (quarkShareIds.isEmpty() && ucShareIds.isEmpty() && baiduShares.isEmpty() && aliShareIds.isEmpty() && !vodId.startsWith("db::")) {
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
                extractAllShares(mClip.group(1), quarkShareIds, ucShareIds, baiduShares, aliShareIds);
            }
            extractAllShares(html, quarkShareIds, ucShareIds, baiduShares, aliShareIds);
        }

        String searchKey = cleanSearchKey(title);
        if (searchKey.isEmpty()) searchKey = "4K";

        // 1. 深度聚合搜索：百度网盘 (确保【百度原画】与【百度无限】绝不缺失)
        List<PanSearchApi.Item> bItems = PanSearchApi.searchPan(searchKey, "baidu");
        for (PanSearchApi.Item bi : bItems) {
            extractAllShares(bi.content + " " + bi.shareUrl, quarkShareIds, ucShareIds, baiduShares, aliShareIds);
        }

        // 2. 深度聚合搜索：夸克网盘
        List<PanSearchApi.Item> qItems = PanSearchApi.searchPan(searchKey, "quark");
        for (PanSearchApi.Item qi : qItems) {
            extractAllShares(qi.content + " " + qi.shareUrl, quarkShareIds, ucShareIds, baiduShares, aliShareIds);
        }

        // 3. 深度聚合搜索：阿里云盘
        List<PanSearchApi.Item> aItems = PanSearchApi.searchPan(searchKey, "aliyundrive");
        for (PanSearchApi.Item ai : aItems) {
            extractAllShares(ai.content + " " + ai.shareUrl, quarkShareIds, ucShareIds, baiduShares, aliShareIds);
        }

        // 4. 深度聚合搜索：综合通用网盘 (覆盖 UC 网盘及其他资源)
        List<PanSearchApi.Item> allItems = PanSearchApi.search(searchKey);
        for (PanSearchApi.Item item : allItems) {
            extractAllShares(item.content + " " + item.shareUrl, quarkShareIds, ucShareIds, baiduShares, aliShareIds);
        }

        vod.put("vod_name", title);
        vod.put("vod_pic", pic);
        vod.put("vod_content", desc);

        StringBuilder playFrom = new StringBuilder();
        StringBuilder playUrl = new StringBuilder();

        // 1. 构建【百度原画】与【百度无限】（突破限速双线路，置于首位突出显示）
        StringBuilder bOrigEp = new StringBuilder();
        StringBuilder bUnlimitEp = new StringBuilder();
        for (String[] bInfo : baiduShares) {
            String sUrl = bInfo[0];
            String pwd = bInfo[2];
            List<BaiduApi.FileItem> bFiles = BaiduApi.get().listShareFiles(sUrl, pwd);
            int idx = 1;
            for (BaiduApi.FileItem item : bFiles) {
                if (bOrigEp.length() > 0) {
                    bOrigEp.append("#");
                    bUnlimitEp.append("#");
                }
                String epName = cleanEpisodeName(item.name, idx);
                String pOrig = "baidu_orig::" + item.shareUrl + "::" + item.fsId + "::" + item.pwd;
                String pUnlimit = "baidu_unlimit::" + item.shareUrl + "::" + item.fsId + "::" + item.pwd;
                bOrigEp.append(epName).append("$").append(pOrig);
                bUnlimitEp.append(epName).append("$").append(pUnlimit);
                idx++;
            }
            if (bOrigEp.length() > 0) break;
        }
        if (bOrigEp.length() == 0) {
            String sUrl = !baiduShares.isEmpty() ? baiduShares.get(0)[0] : ("search://" + searchKey);
            String pwd = !baiduShares.isEmpty() ? baiduShares.get(0)[2] : "";
            bOrigEp.append("4K原画正片$baidu_orig::").append(sUrl).append("::0::").append(pwd);
            bUnlimitEp.append("4K极速正片$baidu_unlimit::").append(sUrl).append("::0::").append(pwd);
        }

        playFrom.append("百度原画");
        playUrl.append(bOrigEp);

        playFrom.append("$$$百度无限");
        playUrl.append("$$$").append(bUnlimitEp);

        // 2. 构建【夸克原画】
        StringBuilder qEp = new StringBuilder();
        for (String qId : quarkShareIds) {
            String stoken = QuarkApi.get().getShareToken(qId, "");
            List<QuarkApi.FileItem> qFiles = QuarkApi.get().listShareFiles(qId, stoken);
            int idx = 1;
            for (QuarkApi.FileItem item : qFiles) {
                if (qEp.length() > 0) qEp.append("#");
                String epName = cleanEpisodeName(item.name, idx);
                String playParam = "quark::" + qId + "::" + stoken + "::" + item.fid + "::" + item.shareFidToken;
                qEp.append(epName).append("$").append(playParam);
                idx++;
            }
        }
        if (qEp.length() > 0) {
            playFrom.append("$$$夸克原画");
            playUrl.append("$$$").append(qEp);
        }

        // 3. 构建【UC原画】
        StringBuilder ucEp = new StringBuilder();
        for (String ucId : ucShareIds) {
            String stoken = UcApi.get().getShareToken(ucId, "");
            List<UcApi.FileItem> ucFiles = UcApi.get().listShareFiles(ucId, stoken);
            int idx = 1;
            for (UcApi.FileItem item : ucFiles) {
                if (ucEp.length() > 0) ucEp.append("#");
                String epName = cleanEpisodeName(item.name, idx);
                String playParam = "uc::" + ucId + "::" + stoken + "::" + item.fid + "::" + item.shareFidToken;
                ucEp.append(epName).append("$").append(playParam);
                idx++;
            }
        }
        if (ucEp.length() > 0) {
            playFrom.append("$$$UC原画");
            playUrl.append("$$$").append(ucEp);
        }

        // 4. 构建【阿里原画】
        StringBuilder aEp = new StringBuilder();
        for (String aId : aliShareIds) {
            String shareToken = AliYunApi.get().getShareToken(aId, "");
            List<AliYunApi.FileItem> aFiles = AliYunApi.get().listShareFiles(aId, shareToken);
            int idx = 1;
            for (AliYunApi.FileItem item : aFiles) {
                if (aEp.length() > 0) aEp.append("#");
                String epName = cleanEpisodeName(item.name, idx);
                String playParam = "ali::" + aId + "::" + shareToken + "::" + item.fileId;
                aEp.append(epName).append("$").append(playParam);
                idx++;
            }
        }
        if (aEp.length() > 0) {
            playFrom.append("$$$阿里原画");
            playUrl.append("$$$").append(aEp);
        }

        vod.put("vod_play_from", playFrom.toString());
        vod.put("vod_play_url", playUrl.toString());
        list.put(vod);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        if (id.startsWith("baidu_") || (flag != null && flag.contains("百度"))) {
            return BaiduApi.get().getPlayerContent(flag, id).toString();
        } else if (id.startsWith("uc::") || (flag != null && flag.contains("UC"))) {
            return UcApi.get().getPlayerContent(id).toString();
        }

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

    private void extractAllShares(String text, Set<String> quark, Set<String> uc, List<String[]> baidu, Set<String> ali) {
        if (text == null || text.isEmpty()) return;

        Matcher mQ = REGEX_QUARK_LINK.matcher(text);
        while (mQ.find()) {
            quark.add(mQ.group(1));
        }

        Matcher mU = REGEX_UC_LINK.matcher(text);
        while (mU.find()) {
            uc.add(mU.group(1));
        }

        Matcher mA = REGEX_ALI_LINK.matcher(text);
        while (mA.find()) {
            ali.add(mA.group(1));
        }

        Matcher mB = REGEX_BAIDU_LINK.matcher(text);
        while (mB.find()) {
            String surl = mB.group(1);
            String fullUrl = "https://pan.baidu.com/s/1" + surl;
            String pwd = "";
            int start = mB.start();
            int end = Math.min(text.length(), mB.end() + 60);
            String snippet = text.substring(start, end);
            Matcher mPwd = REGEX_BAIDU_PWD.matcher(snippet);
            if (mPwd.find()) pwd = mPwd.group(1).trim();

            boolean exists = false;
            for (String[] exist : baidu) {
                if (exist[1].equals(surl)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                baidu.add(new String[]{fullUrl, surl, pwd});
            }
        }
    }

    private String cleanSearchKey(String title) {
        if (title == null) return "";
        String s = title.replaceAll("\\(.*?\\)|\\[.*?\\]|【.*?】", " ");
        s = s.replaceAll("(?i)(4K|1080P|720P|HD|BD|国语|中字|双字|超清|高清|蓝光|60帧|杜比|HDR|SDR)", " ");
        s = s.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]", " ");
        return s.trim().replaceAll("\\s+", " ");
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
