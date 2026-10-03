package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.utils.FirebaseUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SafeSpider extends Spider {

    private final Spider delegate;

    public SafeSpider(Spider delegate) {
        this.delegate = delegate != null ? delegate : new SpiderNull();
        this.siteKey = this.delegate.siteKey;
    }

    @Override
    public void init(Context context) {
        try {
            delegate.init(context);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "init", t);
        }
    }

    @Override
    public void init(Context context, String extend) {
        try {
            delegate.init(context, extend);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "init_extend", t);
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            return delegate.homeContent(filter);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "homeContent", t);
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        try {
            return delegate.homeVideoContent();
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "homeVideoContent", t);
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            return delegate.categoryContent(tid, pg, filter, extend);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "categoryContent", t);
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            return delegate.detailContent(ids);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "detailContent", t);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            String res = delegate.searchContent(key, quick);
            if ((res == null || res.trim().isEmpty() || res.equals("{\"list\":[]}")) && delegate != null) {
                String resPg = delegate.searchContent(key, quick, "1");
                if (resPg != null && !resPg.trim().isEmpty() && !resPg.equals("{\"list\":[]}")) return resPg;
            }
            return res != null ? res : "";
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "searchContent", t);
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            String res = delegate.searchContent(key, quick, pg);
            if ((res == null || res.trim().isEmpty() || res.equals("{\"list\":[]}")) && ("1".equals(pg) || pg == null) && delegate != null) {
                String res2 = delegate.searchContent(key, quick);
                if (res2 != null && !res2.trim().isEmpty() && !res2.equals("{\"list\":[]}")) return res2;
            }
            return res != null ? res : "";
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "searchContent_pg", t);
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        long start = System.currentTimeMillis();
        try {
            String res = delegate.playerContent(flag, id, vipFlags);
            long cost = System.currentTimeMillis() - start;
            FirebaseUtil.log(String.format("SafeSpider [%s] playerContent flag=%s, cost=%dms, resLen=%d", siteKey, flag, cost, res != null ? res.length() : 0));
            if (res == null || res.trim().isEmpty() || res.contains("\"url\":\"\"") || res.contains("\"url\": \"\"")) {
                FirebaseUtil.recordSpiderError(siteKey, "playerContent_empty", new Exception("Spider returned empty URL for flag=" + flag + ", id=" + id + ", res=" + res));
            }
            return res != null ? res : "";
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "playerContent_exception", t);
            return "";
        }
    }

    @Override
    public String liveContent(String url) {
        try {
            return delegate.liveContent(url);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "liveContent", t);
            return "";
        }
    }

    @Override
    public boolean manualVideoCheck() {
        try {
            return delegate.manualVideoCheck();
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "manualVideoCheck", t);
            return false;
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        try {
            return delegate.isVideoFormat(url);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "isVideoFormat", t);
            return false;
        }
    }

    @Override
    public Object[] proxy(Map<String, String> params) {
        try {
            return delegate.proxy(params);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "proxy", t);
            return null;
        }
    }

    @Override
    public String action(String action) {
        try {
            return delegate.action(action);
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "action", t);
            return null;
        }
    }

    @Override
    public void destroy() {
        try {
            delegate.destroy();
        } catch (Throwable t) {
            t.printStackTrace();
            FirebaseUtil.recordSpiderError(siteKey, "destroy", t);
        }
    }
}
