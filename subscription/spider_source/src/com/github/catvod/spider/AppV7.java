package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.OkHttp;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;

public class AppV7 extends Spider {

    private String apiUrl = "";

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (extend != null && !extend.isEmpty()) {
            this.apiUrl = extend.trim();
        }
    }

    private String getEndpoint(String query) {
        String base = apiUrl;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        if (!base.contains("provide/vod")) {
            base = base + "/api.php/provide/vod/";
        }
        return base + (base.contains("?") ? "&" : "?") + query;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        if (apiUrl.isEmpty()) return "";
        String json = OkHttp.get(getEndpoint("ac=list"));
        if (json.isEmpty()) return "";
        return json;
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        if (apiUrl.isEmpty()) return "";
        String url = getEndpoint("ac=detail&t=" + tid + "&pg=" + pg);
        return OkHttp.get(url);
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        if (apiUrl.isEmpty() || ids.isEmpty()) return "";
        String url = getEndpoint("ac=detail&ids=" + ids.get(0));
        return OkHttp.get(url);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        if (apiUrl.isEmpty()) return "";
        String url = getEndpoint("ac=detail&wd=" + java.net.URLEncoder.encode(key, "UTF-8"));
        return OkHttp.get(url);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSONObject result = new JSONObject();
        result.put("parse", 0);
        result.put("playUrl", "");
        result.put("url", id);
        return result.toString();
    }
}
