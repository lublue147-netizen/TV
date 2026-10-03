package com.fongmi.android.tv.bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Preset {

    // 订阅仓自建核心与极速线路
    public static final String ACCELERATED_OPEN = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/accelerated_open.json";
    public static final String ACCELERATED_OPEN_V1_0 = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/v1.0/accelerated_open.json";
    public static final String ACCELERATED = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/accelerated.json";
    public static final String AIWEX = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/aiwex.json";
    public static final String CUSTOM = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/custom.json";
    public static final String OPEN_PAGES = "https://lublue147-netizen.github.io/subscription/accelerated_open.json";
    public static final String LIVE_IPTV = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/live/iptv.m3u";

    // 社区与多仓聚合线路
    public static final String FULL_CLEAN = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_full_clean.json";
    public static final String MULTI_CLEAN = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_multi_clean.json";
    public static final String COLLECT = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_collect.json";
    public static final String FULL_ADULT = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_full_adult.json";
    public static final String MULTI_ADULT = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_multi_adult.json";

    public static final List<String> URLS = Arrays.asList(
        ACCELERATED_OPEN, ACCELERATED_OPEN_V1_0, ACCELERATED, AIWEX, CUSTOM, OPEN_PAGES, LIVE_IPTV,
        FULL_CLEAN, MULTI_CLEAN, COLLECT, FULL_ADULT, MULTI_ADULT
    );

    public static boolean isPreset(String url) {
        return url != null && URLS.contains(url);
    }

    public static boolean isDepot(String url) {
        return MULTI_CLEAN.equals(url) || MULTI_ADULT.equals(url);
    }

    public static List<Config> getVodPresets() {
        List<Config> items = new ArrayList<>();
        items.add(Config.create(0).url(ACCELERATED_OPEN).name("🌟自建纯开源极速源（扫码配置·最新动态）"));
        items.add(Config.create(0).url(ACCELERATED_OPEN_V1_0).name("📌自建1.0独立固定源（永久锁定1.0·稳定不更新）"));
        items.add(Config.create(0).url(ACCELERATED).name("⚡自建网盘极速源（Go加速·4K网盘）"));
        items.add(Config.create(0).url(AIWEX).name("🚀自建全能聚合源（aiwex 96站）"));
        items.add(Config.create(0).url(CUSTOM).name("📦自建精简核心源（17核心精品站）"));
        items.add(Config.create(0).url(OPEN_PAGES).name("🌐自建开源备用源（GitHub Pages线路）"));
        items.add(Config.create(0).url(FULL_CLEAN).name("完整聚合源（无成人）"));
        items.add(Config.create(0).url(MULTI_CLEAN).name("多仓聚合源（无成人）"));
        items.add(Config.create(0).url(COLLECT).name("影视采集源"));
        items.add(Config.create(0).url(FULL_ADULT).name("完整聚合源（含成人）"));
        items.add(Config.create(0).url(MULTI_ADULT).name("多仓聚合源（含成人）"));
        return items;
    }

    public static List<Config> getLivePresets() {
        List<Config> items = new ArrayList<>();
        items.add(Config.create(1).url(LIVE_IPTV).name("🇨🇳自建IPTV精选直播（央视卫视·秒开）"));
        return items;
    }
}
