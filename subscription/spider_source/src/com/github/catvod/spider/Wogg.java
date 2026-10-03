package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.api.AliYunApi;
import com.github.catvod.api.BaiduApi;
import com.github.catvod.api.PanSearchApi;
import com.github.catvod.api.PanTokenManager;
import com.github.catvod.api.QuarkApi;
import com.github.catvod.api.UcApi;
import com.github.catvod.crawler.Spider;
import com.github.catvod.proxy.GoProxy;
import com.github.catvod.utils.NotifyToast;
import com.github.catvod.utils.OkHttp;
import com.github.catvod.utils.SpiderFirebaseLogger;
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

    private String siteUrl = "https://www.wogg.lol";
    private static final String[] CANDIDATE_MIRRORS = {
        "https://www.wogg.lol",
        "https://wogg.link",
        "https://www.wogg.one",
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

    public static class SourceConfig {
        public String[][] categories;
        public String[][] homeTags;
        public Map<String, String[]> catMap = new HashMap<>();

        public SourceConfig(String[][] categories, String[][] homeTags, String[][] catMappings) {
            this.categories = categories;
            this.homeTags = homeTags;
            for (String[] m : catMappings) {
                catMap.put(m[0], new String[]{m[1], m[2]});
            }
        }
    }

    protected SourceConfig getSourceConfig() {
        String cls = this.getClass().getSimpleName();
        if (cls.contains("HuaJuan")) {
            // 花卷：专注国产剧集与华语精品
            return new SourceConfig(
                new String[][]{{"1", "国产热播剧"}, {"2", "华语院线"}, {"3", "古装仙侠"}, {"4", "都市悬疑"}, {"5", "精品微短剧"}},
                new String[][]{{"tv", "国产剧", "16"}, {"movie", "华语", "14"}},
                new String[][]{{"1", "tv", "国产剧"}, {"2", "movie", "华语"}, {"3", "tv", "古装"}, {"4", "tv", "悬疑"}, {"5", "tv", "短剧"}}
            );
        } else if (cls.contains("HuBan")) {
            // 虎斑：专注动作大片与顶级科幻
            return new SourceConfig(
                new String[][]{{"1", "动作大片"}, {"2", "科幻震撼"}, {"3", "硬核战争"}, {"4", "犯罪惊悚"}, {"5", "冒险巨制"}},
                new String[][]{{"movie", "动作", "16"}, {"movie", "科幻", "14"}},
                new String[][]{{"1", "movie", "动作"}, {"2", "movie", "科幻"}, {"3", "movie", "战争"}, {"4", "movie", "犯罪"}, {"5", "movie", "冒险"}}
            );
        } else if (cls.contains("GuanYing")) {
            // 观影：院线新片与全网最新热播
            return new SourceConfig(
                new String[][]{{"1", "院线新片"}, {"2", "最新热播剧"}, {"3", "热播综艺"}, {"4", "最新纪录片"}},
                new String[][]{{"movie", "最新", "16"}, {"tv", "最新", "14"}},
                new String[][]{{"1", "movie", "最新"}, {"2", "tv", "最新"}, {"3", "tv", "综艺"}, {"4", "tv", "纪录片"}}
            );
        } else if (cls.contains("PianKu")) {
            // 盘库：豆瓣高分神作与经典影史
            return new SourceConfig(
                new String[][]{{"1", "豆瓣高分电影"}, {"2", "经典必看神剧"}, {"3", "冷门佳片"}, {"4", "高分纪录片"}},
                new String[][]{{"movie", "豆瓣高分", "16"}, {"movie", "经典", "14"}},
                new String[][]{{"1", "movie", "豆瓣高分"}, {"2", "tv", "经典"}, {"3", "movie", "冷门佳片"}, {"4", "tv", "纪录片"}}
            );
        } else if (cls.contains("MuOu")) {
            // 木偶：爆笑喜剧与热门综艺
            return new SourceConfig(
                new String[][]{{"1", "爆笑喜剧"}, {"2", "热门综艺"}, {"3", "治愈轻喜"}, {"4", "欢乐脱口秀"}},
                new String[][]{{"movie", "喜剧", "16"}, {"tv", "综艺", "14"}},
                new String[][]{{"1", "movie", "喜剧"}, {"2", "tv", "综艺"}, {"3", "movie", "治愈"}, {"4", "tv", "脱口秀"}}
            );
        } else if (cls.contains("DuoDuo")) {
            // 多多：热门日本番剧与动画剧场版
            return new SourceConfig(
                new String[][]{{"1", "日本新番"}, {"2", "动画电影"}, {"3", "热血国漫"}, {"4", "经典动漫"}},
                new String[][]{{"tv", "日本动画", "16"}, {"movie", "动画", "14"}},
                new String[][]{{"1", "tv", "日本动画"}, {"2", "movie", "动画"}, {"3", "tv", "国产动画"}, {"4", "tv", "经典动漫"}}
            );
        } else if (cls.contains("QwMkv")) {
            // 七味：悬疑惊悚与烧脑顶级美剧
            return new SourceConfig(
                new String[][]{{"1", "悬疑惊悚"}, {"2", "顶级美剧"}, {"3", "高分罪案"}, {"4", "暗黑脑洞"}},
                new String[][]{{"movie", "悬疑", "16"}, {"tv", "美剧", "14"}},
                new String[][]{{"1", "movie", "悬疑"}, {"2", "tv", "美剧"}, {"3", "movie", "犯罪"}, {"4", "movie", "惊悚"}}
            );
        } else if (cls.contains("Libvio")) {
            // 立播：欧美大片与全网欧美剧场
            return new SourceConfig(
                new String[][]{{"1", "欧美大片"}, {"2", "欧美剧场"}, {"3", "精选英剧"}, {"4", "科幻美剧"}},
                new String[][]{{"movie", "欧美", "16"}, {"tv", "美剧", "14"}},
                new String[][]{{"1", "movie", "欧美"}, {"2", "tv", "美剧"}, {"3", "tv", "英剧"}, {"4", "movie", "科幻"}}
            );
        } else if (cls.contains("WoNiu")) {
            // 蜗牛：浪漫爱情与热播韩剧
            return new SourceConfig(
                new String[][]{{"1", "浪漫爱情"}, {"2", "都市情感"}, {"3", "热播韩剧"}, {"4", "治愈温情"}},
                new String[][]{{"movie", "爱情", "16"}, {"tv", "韩剧", "14"}},
                new String[][]{{"1", "movie", "爱情"}, {"2", "tv", "国产剧"}, {"3", "tv", "韩剧"}, {"4", "movie", "治愈"}}
            );
        } else if (cls.contains("HanXiaoQuan")) {
            // 韩小圈：同步水木剧与韩国电影
            return new SourceConfig(
                new String[][]{{"1", "同步韩剧"}, {"2", "高分韩影"}, {"3", "韩国综艺"}, {"4", "日韩精选"}},
                new String[][]{{"tv", "韩剧", "20"}, {"movie", "韩国", "10"}},
                new String[][]{{"1", "tv", "韩剧"}, {"2", "movie", "韩国"}, {"3", "tv", "综艺"}, {"4", "tv", "日本动画"}}
            );
        } else if (cls.contains("DouBan") || cls.contains("Douban")) {
            // 豆瓣：豆瓣官方热门影视榜单
            return new SourceConfig(
                new String[][]{{"1", "豆瓣电影热门"}, {"2", "热门电视剧"}, {"3", "高分综艺"}, {"4", "高分动漫"}},
                new String[][]{{"movie", "热门", "15"}, {"tv", "热门", "15"}},
                new String[][]{{"1", "movie", "热门"}, {"2", "tv", "热门"}, {"3", "tv", "综艺"}, {"4", "tv", "日本动画"}}
            );
        } else if (cls.contains("YiDong")) {
            // 移动4K：高分纪录片与人文探索
            return new SourceConfig(
                new String[][]{{"1", "高分纪录片"}, {"2", "自然地理"}, {"3", "历史人文"}, {"4", "科学宇宙"}},
                new String[][]{{"tv", "纪录片", "20"}, {"movie", "纪录片", "10"}},
                new String[][]{{"1", "tv", "纪录片"}, {"2", "tv", "自然"}, {"3", "tv", "历史"}, {"4", "tv", "科学"}}
            );
        }
        // 默认 / 玩偶哥哥 (Wogg / AiNewWoggGuard)
        return new SourceConfig(
            new String[][]{{"1", "4K电影"}, {"2", "全集剧集"}, {"3", "精品动漫"}, {"4", "热门综艺"}, {"5", "爽文短剧"}, {"6", "热播韩剧"}, {"7", "经典纪录片"}},
            new String[][]{{"movie", "热门", "15"}, {"tv", "热门", "15"}},
            new String[][]{{"1", "movie", "热门"}, {"2", "tv", "热门"}, {"3", "tv", "日本动画"}, {"4", "tv", "综艺"}, {"5", "tv", "国产剧"}, {"6", "tv", "韩剧"}, {"7", "tv", "纪录片"}}
        );
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray classes = new JSONArray();

        SourceConfig cfg = getSourceConfig();
        for (String[] cat : cfg.categories) {
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

        // 2. 高清豆瓣热门影视库 (按源特定主题精准加载，杜绝千篇一律)
        if (list.length() == 0) {
            for (String[] ht : cfg.homeTags) {
                String type = ht[0];
                String tag = ht[1];
                int count = 15;
                try { count = Integer.parseInt(ht[2]); } catch (Exception ignored) {}
                fetchDoubanSubjects(type, tag, count, 0, list);
            }
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

        SourceConfig cfg = getSourceConfig();

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

        // 2. 映射当前源对应的豆瓣分类库 (分类精准匹配，绝不报找不到数据，分页稳定)
        if (list.length() == 0) {
            String type = "movie";
            String tag = "热门";
            if (cfg.catMap.containsKey(tid)) {
                String[] m = cfg.catMap.get(tid);
                type = m[0];
                tag = m[1];
            }
            fetchDoubanSubjects(type, tag, limit, start, list);
        }

        // 3. PanSearch 关键词保底
        if (list.length() == 0) {
            String keyword = "4K";
            if (cfg.catMap.containsKey(tid)) {
                keyword = cfg.catMap.get(tid)[1] + " 4K";
            }

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

    private static class SearchCandidate {
        String title;
        String pic;
        String year;
        String remark;

        SearchCandidate(String title, String pic, String year, String remark) {
            this.title = title;
            this.pic = pic;
            this.year = year;
            this.remark = remark;
        }
    }

    private List<SearchCandidate> queryIqiyiSuggest(String key) {
        List<SearchCandidate> candidates = new ArrayList<>();
        if (key == null || key.trim().isEmpty()) return candidates;
        try {
            String url = "https://suggest.video.iqiyi.com/?if=mobile&key=" + URLEncoder.encode(key.trim(), "UTF-8");
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", OkHttp.CHROME);
            String json = OkHttp.get(url, headers);
            if (!json.isEmpty()) {
                JSONObject obj = new JSONObject(json);
                JSONArray data = obj.optJSONArray("data");
                if (data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject item = data.getJSONObject(i);
                        String name = item.optString("name").trim();
                        if (name.isEmpty()) continue;
                        String pic = item.optString("picture_url");
                        int yr = item.optInt("year", 0);
                        String yearStr = yr > 0 ? String.valueOf(yr) : "";
                        String cname = item.optString("cname");
                        String remark = yearStr.isEmpty() ? (cname.isEmpty() ? "4K原画" : cname) : (yearStr + "·" + (cname.isEmpty() ? "4K原画" : cname));
                        candidates.add(new SearchCandidate(name, pic, yearStr, remark));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return candidates;
    }

    private void fetchDoubanSuggest(String q, JSONArray list, Set<String> seenNames) {
        if (q == null || q.trim().isEmpty()) return;
        try {
            String url = "https://movie.douban.com/j/subject_suggest?q=" + URLEncoder.encode(q.trim(), "UTF-8");
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

                    if (seenNames.contains(title)) continue;
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
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray list = new JSONArray();
        Set<String> seenNames = new HashSet<>();

        if (key == null || key.trim().isEmpty()) {
            result.put("list", list);
            return result.toString();
        }

        String rawKey = key.trim();
        boolean isPureLatin = rawKey.matches("^[a-zA-Z0-9\\s]+$");

        // 1. 爱奇艺影视联想词引擎 (毫秒级精准解析拼音缩写如 zgss/qyn/zfz 与全拼/汉字)
        List<SearchCandidate> candidates = queryIqiyiSuggest(rawKey);

        // 确定用于检索豆瓣和网盘的目标关键词集合
        List<String> targetQueries = new ArrayList<>();
        if (!isPureLatin) {
            targetQueries.add(rawKey);
        }
        for (SearchCandidate c : candidates) {
            String cTitle = cleanSearchKey(c.title);
            if (!cTitle.isEmpty() && !targetQueries.contains(cTitle)) {
                targetQueries.add(cTitle);
            }
            if (targetQueries.size() >= 3) break;
        }
        if (targetQueries.isEmpty()) {
            targetQueries.add(rawKey);
        }

        // 2. 豆瓣实时精准联想 (获取高清封面与条目)
        for (String q : targetQueries) {
            fetchDoubanSuggest(q, list, seenNames);
        }

        // 3. 将爱奇艺联想到的热门条目加入结果列表 (若豆瓣未命中)
        for (SearchCandidate c : candidates) {
            if (seenNames.contains(c.title)) continue;
            seenNames.add(c.title);
            JSONObject v = new JSONObject();
            v.put("vod_id", "db::::" + c.title + "::" + (c.pic != null ? c.pic : ""));
            v.put("vod_name", c.title);
            v.put("vod_pic", (c.pic != null && !c.pic.isEmpty()) ? c.pic : "https://img.icons8.com/color/480/film-reel.png");
            v.put("vod_remarks", c.remark != null && !c.remark.isEmpty() ? c.remark : "4K原画");
            list.put(v);
        }

        // 4. PanSearch 多网盘综合搜索 (补充直接网盘条目)
        for (String q : targetQueries) {
            if (isPureLatin && q.equals(rawKey)) continue; // 纯拼音跳过网盘搜索
            List<PanSearchApi.Item> searchItems = PanSearchApi.search(q);
            for (PanSearchApi.Item item : searchItems) {
                if (seenNames.contains(item.title)) continue;
                seenNames.add(item.title);

                JSONObject v = new JSONObject();
                v.put("vod_id", "pan::" + item.diskType + "::" + item.title + "::" + (item.pic != null ? item.pic : "") + "::" + item.shareUrl);
                v.put("vod_name", item.title);
                v.put("vod_pic", (item.pic != null && !item.pic.isEmpty()) ? item.pic : "https://img.icons8.com/color/480/film-reel.png");
                v.put("vod_remarks", item.diskType + "·4K");
                list.put(v);
            }
            if (list.length() >= 30) break;
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

        // 情况 B: vodId 是 PanSearch 综合条目 (pan::diskType::title::pic::shareUrl)
        if (vodId.startsWith("pan::")) {
            String[] parts = vodId.split("::", 5);
            if (parts.length > 2 && !parts[2].isEmpty()) {
                title = parts[2];
            }
            if (parts.length > 3 && !parts[3].isEmpty()) {
                pic = parts[3];
            }
            if (parts.length > 4 && !parts[4].isEmpty()) {
                extractAllShares(parts[4], quarkShareIds, ucShareIds, baiduShares, aliShareIds);
            }
        }

        // 情况 C: vodId 直接是网盘分享链接 (来自原始 HTTP URL)
        if (vodId.startsWith("http://") || vodId.startsWith("https://")) {
            extractAllShares(vodId, quarkShareIds, ucShareIds, baiduShares, aliShareIds);
            if ("4K网盘影视".equals(title)) {
                if (!baiduShares.isEmpty()) title = "百度4K原画影视";
                else if (!quarkShareIds.isEmpty()) title = "夸克4K极速影视";
                else if (!ucShareIds.isEmpty()) title = "UC4K极速影视";
                else if (!aliShareIds.isEmpty()) title = "阿里4K原画影视";
            }
        }

        // 情况 D: vodId 是 Wogg 详情页相对路径或网页 URL
        if (quarkShareIds.isEmpty() && ucShareIds.isEmpty() && baiduShares.isEmpty() && aliShareIds.isEmpty() && !vodId.startsWith("db::") && !vodId.startsWith("pan::")) {
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

        boolean quarkAuthed = PanTokenManager.get().hasQuarkCookie();
        boolean baiduAuthed = PanTokenManager.get().hasBaiduCookie();
        boolean ucAuthed = PanTokenManager.get().hasUcCookie();
        boolean aliAuthed = PanTokenManager.get().hasAliRefreshToken();

        // 1. 构建【夸克原画】
        StringBuilder qEp = new StringBuilder();
        boolean hasRealQuark = false;
        for (String qId : quarkShareIds) {
            String stoken = QuarkApi.get().getShareToken(qId, "");
            List<QuarkApi.FileItem> qFiles = QuarkApi.get().listShareFiles(qId, stoken);
            int idx = 1;
            for (QuarkApi.FileItem item : qFiles) {
                if (qEp.length() > 0) qEp.append("#");
                String epName = cleanEpisodeName(item.name, idx);
                String playParam = "quark::" + qId + "::" + stoken + "::" + item.fid + "::" + item.shareFidToken + "::" + item.pdirFid;
                qEp.append(epName).append("$").append(playParam);
                idx++;
            }
            if (qEp.length() > 0) {
                hasRealQuark = true;
                break;
            }
        }

        // 2. 构建【百度原画】与【百度无限】（突破限速双线路）
        StringBuilder bOrigEp = new StringBuilder();
        StringBuilder bUnlimitEp = new StringBuilder();
        boolean hasRealBaidu = false;
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
                String pOrig = "baidu_orig::" + item.shareUrl + "::" + item.fsId + "::" + item.pwd + "::" + item.shareUk + "::" + item.shareId;
                String pUnlimit = "baidu_unlimit::" + item.shareUrl + "::" + item.fsId + "::" + item.pwd + "::" + item.shareUk + "::" + item.shareId;
                bOrigEp.append(epName).append("$").append(pOrig);
                bUnlimitEp.append(epName).append("$").append(pUnlimit);
                idx++;
            }
            if (bOrigEp.length() > 0) {
                hasRealBaidu = true;
                break;
            }
        }

        // 3. 构建【UC原画】
        StringBuilder ucEp = new StringBuilder();
        boolean hasRealUc = false;
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
            if (ucEp.length() > 0) {
                hasRealUc = true;
                break;
            }
        }

        // 4. 构建【阿里原画】
        StringBuilder aEp = new StringBuilder();
        boolean hasRealAli = false;
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
            if (aEp.length() > 0) {
                hasRealAli = true;
                break;
            }
        }

        // 兜底保底逻辑：如果未解析出分集，但有分享链接，生成默认正片；仅当没有任何真实资源时才用 search:// 保底
        if (!hasRealBaidu && !baiduShares.isEmpty()) {
            String sUrl = baiduShares.get(0)[0];
            String pwd = baiduShares.get(0)[2];
            bOrigEp.append("4K原画正片$baidu_orig::").append(sUrl).append("::0::").append(pwd);
            bUnlimitEp.append("4K极速正片$baidu_unlimit::").append(sUrl).append("::0::").append(pwd);
        } else if (!hasRealBaidu && !hasRealQuark && !hasRealUc && !hasRealAli && quarkShareIds.isEmpty()) {
            bOrigEp.append("4K原画正片$baidu_orig::search://").append(searchKey).append("::0::");
            bUnlimitEp.append("4K极速正片$baidu_unlimit::search://").append(searchKey).append("::0::");
        }

        if (!hasRealQuark && !quarkShareIds.isEmpty()) {
            String qId = quarkShareIds.iterator().next();
            qEp.append("4K极速正片$quark::").append(qId).append("::::0::");
        } else if (!hasRealQuark && !hasRealBaidu && !hasRealUc && !hasRealAli && baiduShares.isEmpty()) {
            qEp.append("4K极速正片$quark::search://").append(searchKey).append("::::0::");
        }

        // 智能自适应线路排序器：结合用户授权配置状态及真实剧集可用性动态排序
        class TabLine {
            String from;
            String url;
            int score;
            TabLine(String from, String url, int score) {
                this.from = from;
                this.url = url;
                this.score = score;
            }
        }
        List<TabLine> tabLines = new ArrayList<>();

        if (bOrigEp.length() > 0) {
            int score = (baiduAuthed ? 1000 : 0) + (hasRealBaidu ? 200 : 0) + 50;
            tabLines.add(new TabLine("百度原画", bOrigEp.toString(), score));
            tabLines.add(new TabLine("百度无限", bUnlimitEp.toString(), score - 1));
        }

        if (qEp.length() > 0) {
            int score = (quarkAuthed ? 1000 : 0) + (hasRealQuark ? 200 : 0) + 40;
            tabLines.add(new TabLine("夸克原画", qEp.toString(), score));
        }

        if (ucEp.length() > 0) {
            int score = (ucAuthed ? 1000 : 0) + (hasRealUc ? 200 : 0) + 30;
            tabLines.add(new TabLine("UC原画", ucEp.toString(), score));
        }

        if (aEp.length() > 0) {
            int score = (aliAuthed ? 1000 : 0) + (hasRealAli ? 200 : 0) + 20;
            tabLines.add(new TabLine("阿里原画", aEp.toString(), score));
        }

        Collections.sort(tabLines, (o1, o2) -> Integer.compare(o2.score, o1.score));

        for (int i = 0; i < tabLines.size(); i++) {
            TabLine line = tabLines.get(i);
            if (i > 0) {
                playFrom.append("$$$");
                playUrl.append("$$$");
            }
            playFrom.append(line.from);
            playUrl.append(line.url);
        }

        vod.put("vod_play_from", playFrom.toString());
        vod.put("vod_play_url", playUrl.toString());
        list.put(vod);

        result.put("list", list);
        return result.toString();
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        SpiderFirebaseLogger.log(String.format("Wogg.playerContent START: flag=%s, id=%s", flag, id));
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
            String shareId = parts.length > 1 ? parts[1] : "";
            String stoken = parts.length > 2 ? parts[2] : "";
            String fid = parts.length > 3 ? parts[3] : "0";
            String shareFidToken = parts.length > 4 ? parts[4] : "";
            String pdirFid = parts.length > 5 ? parts[5] : "0";

            if (shareId.startsWith("search://")) {
                String kw = PanSearchApi.cleanKeyword(shareId.substring(9).trim());
                List<PanSearchApi.Item> qSearch = PanSearchApi.searchPan(kw, "quark");
                if (qSearch.isEmpty()) {
                    qSearch = PanSearchApi.search(kw);
                }
                for (PanSearchApi.Item item : qSearch) {
                    Matcher mQ = REGEX_QUARK_LINK.matcher(item.content + " " + item.shareUrl);
                    while (mQ.find()) {
                        String candShareId = mQ.group(1);
                        String candToken = QuarkApi.get().getShareToken(candShareId, "");
                        List<QuarkApi.FileItem> candFiles = QuarkApi.get().listShareFiles(candShareId, candToken);
                        if (!candFiles.isEmpty()) {
                            shareId = candShareId;
                            stoken = candToken;
                            fid = candFiles.get(0).fid;
                            shareFidToken = candFiles.get(0).shareFidToken;
                            pdirFid = candFiles.get(0).pdirFid;
                            break;
                        }
                    }
                    if (!shareId.startsWith("search://")) break;
                }
            }

            if (!shareId.isEmpty() && !shareId.startsWith("search://")) {
                if (stoken.isEmpty()) {
                    stoken = QuarkApi.get().getShareToken(shareId, "");
                }
                if ("0".equals(fid) || fid.isEmpty()) {
                    List<QuarkApi.FileItem> qFiles = QuarkApi.get().listShareFiles(shareId, stoken);
                    if (!qFiles.isEmpty()) {
                        fid = qFiles.get(0).fid;
                        shareFidToken = qFiles.get(0).shareFidToken;
                        pdirFid = qFiles.get(0).pdirFid;
                    }
                }
                if (!"0".equals(fid) && !fid.isEmpty()) {
                    rawStreamUrl = QuarkApi.get().getPlayUrl(shareId, stoken, fid, shareFidToken, pdirFid);
                }
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

        if (rawStreamUrl.isEmpty() || !rawStreamUrl.startsWith("http")) {
            String errorMsg;
            if (id.startsWith("quark::")) {
                if (!QuarkApi.get().hasCookie()) {
                    errorMsg = "请在【配置中心】扫码配置夸克网盘，或切换【百度原画】播放";
                } else {
                    errorMsg = "夸克网盘解析失败，可切换【百度原画】线路播放";
                }
            } else if (id.startsWith("ali::")) {
                errorMsg = "阿里云盘解析失败，请在【配置中心】检查配置";
            } else {
                errorMsg = "视频解析失败，请切换其他线路播放";
            }
            NotifyToast.show(errorMsg);
            SpiderFirebaseLogger.recordPlaybackError("Wogg", flag, id, "wogg_rawStreamUrl_empty: " + errorMsg, null);
            result.put("url", "");
            result.put("msg", errorMsg);
            return result.toString();
        }

        // 通过 GoProxy 乱序并发 Range 预取切片进行流媒体加速
        String proxyUrl = GoProxy.wrap(rawStreamUrl, headers);
        SpiderFirebaseLogger.log(String.format("Wogg.playerContent SUCCESS: flag=%s, proxyUrl=%s", flag, proxyUrl));
        result.put("url", proxyUrl);
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

    public static String cleanSearchKey(String title) {
        return PanSearchApi.cleanKeyword(title);
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
