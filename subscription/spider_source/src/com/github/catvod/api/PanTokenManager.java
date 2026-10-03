package com.github.catvod.api;

import android.content.Context;
import android.content.SharedPreferences;
import com.github.catvod.spider.Init;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 集中管理各网盘凭证（Cookie / Token）持久化存储与多级回退
 * 具备以下层级容灾：
 * 1. SharedPreferences (pan_config)
 * 2. 内部私有存储文件 (files/pan_token.json)
 * 3. 缓存目录存储文件 (cache/pan_token.json)
 * 4. 外部共享存储文件 (/sdcard/TV/pan_token.json)
 */
public class PanTokenManager {

    private static final String PREF_NAME = "pan_config";
    private static final String FILE_NAME = "pan_token.json";

    private static volatile PanTokenManager instance;
    private String quarkCookie = "";
    private String aliRefreshToken = "";
    private String aliAccessToken = "";

    public static PanTokenManager get() {
        if (instance == null) {
            synchronized (PanTokenManager.class) {
                if (instance == null) {
                    instance = new PanTokenManager();
                }
            }
        }
        return instance;
    }

    private PanTokenManager() {
        load();
    }

    public synchronized void load() {
        // 1. 读取 SharedPreferences
        try {
            Context ctx = Init.get();
            if (ctx != null) {
                SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String q = sp.getString("quark_cookie", "");
                String a = sp.getString("ali_token", "");
                String aAcc = sp.getString("ali_access_token", "");
                if (!q.isEmpty()) this.quarkCookie = q.trim();
                if (!a.isEmpty()) this.aliRefreshToken = a.trim();
                if (!aAcc.isEmpty()) this.aliAccessToken = aAcc.trim();
            }
        } catch (Throwable ignored) {}

        // 2. 本地文件回退
        if (this.quarkCookie.isEmpty() || this.aliRefreshToken.isEmpty()) {
            loadFileFallback();
        }
    }

    private void loadFileFallback() {
        File[] candidateFiles = getCandidateFiles();
        for (File f : candidateFiles) {
            if (f != null && f.exists() && f.canRead()) {
                try {
                    FileInputStream fis = new FileInputStream(f);
                    byte[] data = new byte[(int) f.length()];
                    int read = fis.read(data);
                    fis.close();
                    if (read > 0) {
                        String jsonStr = new String(data, 0, read, StandardCharsets.UTF_8);
                        JSONObject json = new JSONObject(jsonStr);
                        if (this.quarkCookie.isEmpty()) {
                            this.quarkCookie = json.optString("quark_cookie", json.optString("quark", "")).trim();
                        }
                        if (this.aliRefreshToken.isEmpty()) {
                            this.aliRefreshToken = json.optString("ali_token", json.optString("token", json.optString("ali", ""))).trim();
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    public synchronized void save() {
        // 1. 保存至 SharedPreferences
        try {
            Context ctx = Init.get();
            if (ctx != null) {
                SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                sp.edit()
                    .putString("quark_cookie", this.quarkCookie)
                    .putString("ali_token", this.aliRefreshToken)
                    .putString("ali_access_token", this.aliAccessToken)
                    .apply();
            }
        } catch (Throwable ignored) {}

        // 2. 保存至本地 JSON 文件
        File[] candidateFiles = getCandidateFiles();
        for (File f : candidateFiles) {
            if (f != null) {
                try {
                    File parent = f.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    JSONObject json = new JSONObject();
                    json.put("quark_cookie", this.quarkCookie);
                    json.put("ali_token", this.aliRefreshToken);
                    json.put("token", this.aliRefreshToken);
                    json.put("ali_access_token", this.aliAccessToken);
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                    fos.close();
                    break;
                } catch (Throwable ignored) {}
            }
        }
    }

    private File[] getCandidateFiles() {
        File f1 = null;
        File f2 = null;
        File f3 = null;
        try {
            Context ctx = Init.get();
            if (ctx != null) {
                if (ctx.getFilesDir() != null) f1 = new File(ctx.getFilesDir(), FILE_NAME);
                if (ctx.getCacheDir() != null) f2 = new File(ctx.getCacheDir(), FILE_NAME);
            }
        } catch (Throwable ignored) {}
        try {
            f3 = new File("/sdcard/TV/" + FILE_NAME);
        } catch (Throwable ignored) {}
        return new File[]{f1, f2, f3};
    }

    public synchronized String getQuarkCookie() {
        if (quarkCookie.isEmpty()) load();
        return quarkCookie;
    }

    public synchronized void setQuarkCookie(String cookie) {
        this.quarkCookie = cookie != null ? cookie.trim() : "";
        save();
    }

    public boolean hasQuarkCookie() {
        return !getQuarkCookie().isEmpty();
    }

    public synchronized String getAliRefreshToken() {
        if (aliRefreshToken.isEmpty()) load();
        return aliRefreshToken;
    }

    public synchronized void setAliRefreshToken(String token) {
        this.aliRefreshToken = token != null ? token.trim() : "";
        save();
    }

    public boolean hasAliRefreshToken() {
        return !getAliRefreshToken().isEmpty();
    }

    public synchronized String getAliAccessToken() {
        return aliAccessToken;
    }

    public synchronized void setAliAccessToken(String token) {
        this.aliAccessToken = token != null ? token.trim() : "";
        save();
    }

    public synchronized void clearAll() {
        this.quarkCookie = "";
        this.aliRefreshToken = "";
        this.aliAccessToken = "";
        save();
    }
}
