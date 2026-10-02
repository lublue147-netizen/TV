package com.fongmi.android.tv.api.loader;

import android.content.Context;

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
        }
    }

    @Override
    public void init(Context context, String extend) {
        try {
            delegate.init(context, extend);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean filter) {
        try {
            return delegate.homeContent(filter);
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public String homeVideoContent() {
        try {
            return delegate.homeVideoContent();
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            return delegate.categoryContent(tid, pg, filter, extend);
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        try {
            return delegate.detailContent(ids);
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick) {
        try {
            return delegate.searchContent(key, quick);
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        try {
            return delegate.searchContent(key, quick, pg);
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            return delegate.playerContent(flag, id, vipFlags);
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public String liveContent(String url) {
        try {
            return delegate.liveContent(url);
        } catch (Throwable t) {
            t.printStackTrace();
            return "";
        }
    }

    @Override
    public boolean manualVideoCheck() {
        try {
            return delegate.manualVideoCheck();
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean isVideoFormat(String url) {
        try {
            return delegate.isVideoFormat(url);
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    @Override
    public Object[] proxy(Map<String, String> params) {
        try {
            return delegate.proxy(params);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    @Override
    public String action(String action) {
        try {
            return delegate.action(action);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    @Override
    public void destroy() {
        try {
            delegate.destroy();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
