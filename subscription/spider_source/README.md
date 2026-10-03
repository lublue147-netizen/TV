# 🕷️ TVBox & FongMi TV Spider 爬虫开源工程

本目录是 **100% 纯 Java 全开源、可编译、可扩展** 的 Spider 爬虫工程。遵循标准 CatVod / FongMi TV 接口规范，完全替代封闭混淆的 Guard 系列闭源爬虫（如 `PanConfigGuard`, `AiNewWoggGuard`, `MyPanGuard` 等），支持在 GitHub Actions 云端全自动编译成 Android DEX 字节码与 `spider_open.jar`。

---

## 一、核心特性与架构升级

1. **网盘扫码配置中心 (`PanConfig.java` / `csp_PanConfigGuard`)**：
   * **官方扫码授权**：集成夸克网盘与阿里云盘官方 OAuth 登录流程，在 TV 详情页动态生成高分辨率扫码二维码（亦支持在电视大屏与手机端直接扫码）。
   * **轮询与自动持久化**：扫码后一键点击「检查授权」，自动完成 Token/Cookie 提取与换取，并持久化写入 Android `SharedPreferences`。
   * **GoProxy 加速引擎监控**：实时检查 127.0.0.1:9978 并发加速状态。

2. **玩偶哥哥与 4K 网盘影视站 (`Wogg.java` / `csp_AiNewWoggGuard` 及 14 个网盘站点)**：
   * **秒级多网盘聚合引擎 (`PanSearchApi.java`)**：自动聚合千万级 4K 网盘影视资源索引，搜索响应 < 500ms，彻底告别单站点 Cloudflare 拦截和“没有内容”的问题。
   * **目录递归分集提取 (`QuarkApi.java` & `AliYunApi.java`)**：针对每一个网盘分享链接，自动深入遍历文件夹全部视频文件（第01集、第02集...），在 TV 端精准展示完整剧集选集列表。
   * **4K 原画直链与 GoProxy 加速**：配合个人账号凭证与 Go 原生并发切片预取，消除 4K 播放起播与快进缓冲等待。

3. **个人网盘与 AList 挂载 (`MyPan.java` / `csp_MyPanGuard`)**：
   * 支持挂载本地或局域网 AList 服务（默认 `http://127.0.0.1:5244`），将个人云盘文件夹直接映射为 TV 分类浏览与原画点播。
   * 支持快速跳转已绑定的阿里云盘与夸克网盘个人媒体库。

4. **100% 站点兼容覆盖**：
   * 实现了 `aiwex.json` 中全部 96 个站点所调用的所有 `csp_*Guard` 爬虫类（共 50 个兼容别名类），杜绝任何 `ClassNotFoundException`。

---

## 二、源码目录结构

```
subscription/spider_source/
├── src/
│   └── com/github/catvod/
│       ├── api/
│       │   ├── QuarkApi.java        # 夸克网盘官方 API (扫码授权/目录递归/4K解析)
│       │   ├── AliYunApi.java       # 阿里云盘官方 API (扫码授权/匿名遍历/直链解析)
│       │   └── PanSearchApi.java    # PanSearch 多网盘千万级资源秒级聚合搜索
│       ├── crawler/
│       │   └── Spider.java          # 爬虫核心基类（标准 TVBox / CatVod 规范）
│       ├── proxy/
│       │   ├── GoProxy.java         # Go 语言原生协程 Range 并发切片加速控制器
│       │   └── NetdiskStream.java   # 纯 Java HTTP 视频流式切片引擎 (本地回退)
│       ├── spider/
│       │   ├── Init.java            # 初始化与 Context / Loader 注册
│       │   ├── PanConfig.java       # 网盘扫码配置中心 (csp_PanConfigGuard)
│       │   ├── Wogg.java            # 玩偶哥哥与 4K 网盘爬虫 (csp_AiNewWoggGuard)
│       │   ├── MyPan.java           # 个人网盘与媒体库 (csp_MyPanGuard)
│       │   ├── PanSou.java          # 综合多网盘搜索
│       │   ├── AList.java           # AList 文件系统直连
│       │   ├── Douban.java          # 豆瓣热播电影与热剧索引
│       │   ├── Bili.java            # 哔哩哔哩与名师课堂
│       │   ├── AppV7.java           # CMS/V7 秒播通用爬虫
│       │   ├── Push.java            # 跨屏视频直链推送
│       │   ├── Proxy.java           # TVBox proxy:// 协议路由器
│       │   └── *Guard.java          # 50 个兼容别名类 (100% 覆盖 aiwex.json 96 站点)
│       └── utils/
│           └── OkHttp.java          # 纯 Java 原生零依赖 HTTP 网络层
└── README.md
```

---

## 三、GitHub Actions 全自动编译发布流程

在 [`.github/workflows/deploy-source.yml`](../../.github/workflows/deploy-source.yml) 中已配置好云端 CI：
1. **源码编译**：使用 `javac -source 17 -target 17` 将全部 94 个 Java 源文件编译为字节码。
2. **D8 Dex 优化**：调用 Android SDK `d8` 转换为 Android 虚拟机高兼容性 `classes.dex`。
3. **打包分发**：封装为 `spider_open.jar`，同时支持与闭源 `spider.jar` 并行发布。
4. **全球 CDN 缓存自动刷新**：发布至 `gh-pages` 分支并触发全球 jsDelivr 缓存刷新。
