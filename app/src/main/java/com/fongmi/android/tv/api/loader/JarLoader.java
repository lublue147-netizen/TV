package com.fongmi.android.tv.api.loader;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Path;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dalvik.system.DexClassLoader;

public class JarLoader {

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private final ConcurrentHashMap<String, Boolean> weaponized;
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
        weaponized = new ConcurrentHashMap<>();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        weaponized.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void load(String key, File file) {
        if (Thread.interrupted()) return;
        if (!Path.exists(file) || !file.setReadOnly()) return;
        boolean guarded = isGuardedJar(file);
        if (guarded) weaponized.put(key, Boolean.TRUE);
        String cachePath = Path.jar().getAbsolutePath();
        DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
        if (!guarded && isWeaponized(loader)) {
            weaponized.put(key, Boolean.TRUE);
        }
        invokeInit(key, loader);
        if (!Boolean.TRUE.equals(weaponized.get(key))) {
            invokeProxy(key, loader);
        }
        loaders.put(key, loader);
    }

    private boolean isGuardedJar(File file) {
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName().toLowerCase();
                if (name.endsWith(".so") || name.endsWith(".guard") || name.contains("shinidie") || name.contains("dexnative") || name.contains("wexguard")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isWeaponized(DexClassLoader loader) {
        try {
            loader.loadClass("com.github.catvod.spider.DexNative");
            return true;
        } catch (Throwable ignored) {
        }
        try {
            loader.loadClass("com.github.catvod.spider.BaseSpiderGuard");
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            for (Method m : clz.getDeclaredMethods()) {
                if (Modifier.isNative(m.getModifiers())) return true;
                String name = m.getName();
                if (name.contains("GoProxy") || name.contains("FloatBall") || name.contains("ActivityStart") || name.contains("killProcess") || name.contains("getLoader") || name.contains("proxyInvoke")) {
                    return true;
                }
            }
            for (Class<?> inner : clz.getDeclaredClasses()) {
                for (Method m : inner.getDeclaredMethods()) {
                    if (Modifier.isNative(m.getModifiers())) return true;
                    String name = m.getName();
                    if (name.contains("killProcess") || name.contains("GoProxy") || name.contains("getLoader")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void invokeInit(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            neutralizeWeaponizedInit(clz, loader);
            if (Boolean.TRUE.equals(weaponized.get(key))) return;
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, SpiderContext.get());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void neutralizeWeaponizedInit(Class<?> clz, DexClassLoader loader) {
        try {
            Object instance = null;
            try {
                Method getMethod = clz.getDeclaredMethod("get");
                getMethod.setAccessible(true);
                instance = getMethod.invoke(null);
            } catch (Throwable ignored) {
                try {
                    Method getMethod = clz.getDeclaredMethod("getInstance");
                    getMethod.setAccessible(true);
                    instance = getMethod.invoke(null);
                } catch (Throwable ignored2) {
                }
            }

            Context spiderContext = SpiderContext.get();
            List<Object> targets = new ArrayList<>();
            targets.add(null);
            if (instance != null) targets.add(instance);

            for (Object target : targets) {
                for (Field f : clz.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Class<?> type = f.getType();
                        if (type.equals(Application.class)) {
                            f.set(target, App.get());
                        } else if (Context.class.isAssignableFrom(type)) {
                            f.set(target, spiderContext);
                        } else if (Handler.class.isAssignableFrom(type)) {
                            if (f.get(target) == null) {
                                f.set(target, new Handler(Looper.getMainLooper()));
                            }
                        } else if (ExecutorService.class.isAssignableFrom(type)) {
                            if (f.get(target) == null) {
                                f.set(target, Executors.newCachedThreadPool());
                            }
                        } else if (ClassLoader.class.isAssignableFrom(type)) {
                            if (f.get(target) == null) {
                                f.set(target, loader);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key)) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (loaders.containsKey(key)) return;
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];
            if (!md5.isEmpty() && Crypto.equals(Path.jar(jar), md5)) {
                load(key, Path.jar(jar));
            } else if (jar.startsWith("http")) {
                load(key, Download.create(jar, Path.jar(jar)).get());
            } else if (jar.startsWith("file")) {
                load(key, Path.local(jar));
            }
        }
    }

    public DexClassLoader dex(String jar) {
        try {
            String jaKey = Crypto.md5(jar);
            parseJar(jaKey, jar);
            return loaders.get(jaKey);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Crypto.md5(jar);
        String spKey = jaKey + key;
        return spiders.computeIfAbsent(spKey, k -> {
            try {
                parseJar(jaKey, jar);
                if (Boolean.TRUE.equals(weaponized.get(jaKey))) {
                    return new SpiderNull();
                }
                if (api.endsWith("Guard") || api.contains("Guard")) {
                    return new SpiderNull();
                }
                DexClassLoader loader = loaders.get(jaKey);
                if (loader == null) return new SpiderNull();
                Class<?> clz = loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]);
                if (clz.getName().endsWith("Guard") || (clz.getSuperclass() != null && clz.getSuperclass().getName().contains("Guard"))) {
                    return new SpiderNull();
                }
                Spider spider = (Spider) clz.newInstance();
                spider.siteKey = key;
                spider.init(SpiderContext.get(), ext);
                return spider;
            } catch (Throwable e) {
                e.printStackTrace();
                return new SpiderNull();
            }
        });
    }

    private DexClassLoader requireRecentLoader() {
        return recent != null ? loaders.get(recent) : null;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        try {
            DexClassLoader loader = requireRecentLoader();
            if (loader == null) return new JSONObject();
            Class<?> clz = loader.loadClass("com.github.catvod.parser.Json" + key);
            Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) method.invoke(null, jxs, url);
        } catch (Throwable e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        try {
            DexClassLoader loader = requireRecentLoader();
            if (loader == null) return new JSONObject();
            Class<?> clz = loader.loadClass("com.github.catvod.parser.Mix" + key);
            Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
            return (JSONObject) method.invoke(null, jxs, name, flag, url);
        } catch (Throwable e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
