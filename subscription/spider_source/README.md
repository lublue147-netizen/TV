# 🕷️ TVBox & FongMi TV Spider 爬虫源码工程

本目录是 **全开源、可编译、可扩展** 的 Spider 爬虫 Java 源码工程。遵循标准 CatVod / FongMi TV 接口规范，支持在 GitHub Actions 云端全自动编译成 Android DEX 字节码与 `spider.jar`。

---

## 一、为什么会有这个源码工程？

在分析 `aiwex.json` 时，其主配置中的 `spider` 是一个包含 `assets/wexshinidie.guard` 与 native `.so` 的**混淆/加壳二进制 JAR 包**。由于加壳作者闭源保护其爬虫代码，外部无法直接获取其 `.java` 源码。

为了让用户**真正拥有并自主掌控自己的源与爬虫**，我们构建了这套标准的开源 Spider 源码工程：
* 包含所有核心核心接口：豆瓣热榜、网盘搜索、推送播放、B站与课堂教育、AList挂载、CMS秒播采集等。
* 完全采用标准 Android SDK 与轻量级原生网络层，无冗余臃肿第三方依赖。
* **零本地安装要求**：只要修改 `.java` 文件并推送到 GitHub，GitHub Actions 会自动编译生成 `classes.dex`、打包 `spider.jar`、更新 MD5 并发布到全球 CDN。

---

## 二、目录结构

```
subscription/spider_source/
├── src/
│   └── com/github/catvod/
│       ├── crawler/
│       │   └── Spider.java          # 核心基类（定义生命周期方法）
│       ├── spider/
│       │   ├── Init.java            # 初始化入口类（提供上下文和 loader 标志）
│       │   ├── Douban.java          # 豆瓣热播电影/电视剧爬虫
│       │   ├── PanSou.java          # 网盘综合搜索爬虫（夸克/阿里/百度/115）
│       │   ├── Bili.java            # 哔哩哔哩名师课堂/纪录片爬虫
│       │   ├── AList.java           # AList 网盘挂载爬虫（支持多级目录直链）
│       │   ├── AppV7.java           # CMS/V7 秒播通用爬虫
│       │   └── Push.java            # 跨屏推送爬虫（直接播放推送的直链）
│       └── utils/
│           └── OkHttp.java          # 轻量级 HTTP 请求工具类
└── README.md                        # 本说明文档
```

---

## 三、Spider 核心生命周期与接口规范

每个爬虫类均继承自 `com.github.catvod.crawler.Spider`，按需覆写以下方法：

### 1. `init(Context context, String extend)`
爬虫初始化入口。`extend` 为 `aiwex.json` 中配置的 `"ext"` 字段（可为字符串或 JSON 配置）。

### 2. `homeContent(boolean filter)`
首页推荐与分类导航：
返回 JSON 格式：
```json
{
  "class": [
    { "type_id": "movie", "type_name": "电影" },
    { "type_id": "tv", "type_name": "电视剧" }
  ],
  "list": [
    { "vod_id": "123", "vod_name": "庆余年", "vod_pic": "...", "vod_remarks": "更新至第10集" }
  ]
}
```

### 3. `categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend)`
分类筛选与分页加载：返回当前分类的视频列表。

### 4. `detailContent(List<String> ids)`
获取视频详情与播放集数列表：
```json
{
  "list": [
    {
      "vod_id": "123",
      "vod_name": "庆余年",
      "vod_play_from": "播放源1$$$播放源2",
      "vod_play_url": "第01集$http://...#第02集$http://...$$$第01集$http://..."
    }
  ]
}
```

### 5. `searchContent(String key, boolean quick)`
全站聚合搜索接口：根据关键字返回匹配视频列表。

### 6. `playerContent(String flag, String id, List<String> vipFlags)`
解析播放地址：
```json
{
  "parse": 0,
  "url": "https://example.com/video.m3u8",
  "header": "{\"User-Agent\": \"...\"}"
}
```

---

## 四、GitHub Actions 云端编译流程

在 [`.github/workflows/deploy-source.yml`](../../.github/workflows/deploy-source.yml) 中已集成自动编译步骤：
1. **源码编译**：使用 `javac` 将 `src/**/*.java` 编译为 `.class` 文件。
2. **D8 Dex 化**：调用 Android SDK 的 `d8` 编译器将 `.class` 转换为 Android 虚拟机兼容的 `classes.dex`。
3. **打包与哈希**：将 `classes.dex` 打包进 `spider.jar` 与 `spider.txt`，自动计算新 MD5 并填入 `aiwex.json`。
4. **全球发布**：自动部署到 `gh-pages` 分支与 jsDelivr CDN。
