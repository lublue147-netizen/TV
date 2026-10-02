package com.fongmi.android.tv.bean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Preset {

    public static final String ACCELERATED = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/accelerated.json";
    public static final String AIWEX = "https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/aiwex.json";
    public static final String FULL_CLEAN = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_full_clean.json";
    public static final String MULTI_CLEAN = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_multi_clean.json";
    public static final String COLLECT = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_collect.json";
    public static final String FULL_ADULT = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_full_adult.json";
    public static final String MULTI_ADULT = "https://raw.githubusercontent.com/lublue147-netizen/tvyuan/master/tvbox_multi_adult.json";

    public static final List<String> URLS = Arrays.asList(ACCELERATED, AIWEX, FULL_CLEAN, MULTI_CLEAN, COLLECT, FULL_ADULT, MULTI_ADULT);

    public static boolean isPreset(String url) {
        return url != null && URLS.contains(url);
    }

    public static boolean isDepot(String url) {
        return MULTI_CLEAN.equals(url) || MULTI_ADULT.equals(url);
    }

    public static List<Config> getVodPresets() {
        List<Config> items = new ArrayList<>();
        items.add(Config.create(0).url(ACCELERATED).name("🚀自建网盘极速源（Go加速）"));
        items.add(Config.create(0).url(AIWEX).name("自建全能聚合源（aiwex 96站）"));
        items.add(Config.create(0).url(FULL_CLEAN).name("完整聚合源（无成人）"));
        items.add(Config.create(0).url(MULTI_CLEAN).name("多仓聚合源（无成人）"));
        items.add(Config.create(0).url(COLLECT).name("影视采集源"));
        items.add(Config.create(0).url(FULL_ADULT).name("完整聚合源（含成人）"));
        items.add(Config.create(0).url(MULTI_ADULT).name("多仓聚合源（含成人）"));
        return items;
    }
}
