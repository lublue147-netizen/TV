# 📺 TVBox / FongMi 影视源与网盘加速代理独立发布中心

[![Version](https://img.shields.io/badge/Release-v1.0.0%20Official-blue.svg)](https://github.com/lublue147-netizen/subscription/releases/tag/v1.0.0)
[![CI/CD](https://github.com/lublue147-netizen/subscription/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/lublue147-netizen/subscription/actions/workflows/build-and-release.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

本项目是针对 **TVBox** 与 **FongMi TV** 打造的独立影视订阅源与网盘播放加速发布中心（**v1.0.0 正式版**）。集成全能影视源、模块化精简源、网盘极速源、开源 Java Spider 爬虫、以及高性能 **Go / SO 网盘并发加速代理引擎 (GoProxy)**。

所有组件的编译、DEX 打包、跨平台编译、MD5 校验及全球 CDN 部署均通过 **GitHub Actions** 自动化完成，无需在本地安装配置任何开发环境。

---

## ⚡ 核心功能与亮点

1. **自主独立发布 (v1.0.0 正式版)**: 
   - 具备专属独立 GitHub 仓库与独立的 GitHub Actions CI/CD 流水线。
   - 自动推送到 `gh-pages` 分支并创建 GitHub Releases，提供全球 CDN 与官方线路。
2. **Go / SO 网盘播放并发加速代理 (GoProxy)**:
   - 针对**夸克网盘、阿里云盘、115网盘、百度网盘、AList、WebDAV**等网盘播放，突破单线程限速。
   - **多线程 Range 分块并发预加载**：将视频流切分为 2MB/4MB 块，后台多协程预取并缓存至高速内存环形队列，ExoPlayer 拖动进度条即刻秒播。
   - 提供 **Android ARM64 / ARMv7 独立进程**、**JNI `.so` 动态库**（`libgoproxy.so`）及 **Java 纯流代理后备机制**。
3. **全开源 Spider 爬虫支持 (spider_open.jar)**:
   - 包含完整的 Java Spider 源码工程（`spider_source/`），CI 流水线使用 Android `d8` 自动转为 `classes.dex` 并打包成 `spider_open.jar`。
   - 内置纯离线二维码生成、扫码授权后台自动轮询、局域网 Web 控制台（9979端口）与千万级 PanSearch 多网盘秒搜。
4. **多形态订阅接口**:
   - **全功能聚合源 (`aiwex.json`)**：96 个全能站点、4 条 VIP 解析、高清直播与安全 DoH。
   - **网盘极速源 (`accelerated.json`)**：专为 Go/SO 网盘加速设计的纯净 4K 与 AList 线路。
   - **开源原生极速源 (`accelerated_open.json`)**：100% 使用开源 spider_open.jar，支持扫码配置与分集解析。
   - **精简核心源 (`custom.json`)**：精选 17 个高可用核心站点。

---

## 🚀 一、订阅配置地址（即开即用）

在 **FongMi TV** 或 **TVBox** 的「设置」->「配置地址」中填入以下任一链接即可：

| 线路类型 | 接口订阅地址 | 说明 |
| :--- | :--- | :--- |
| 🚀 **全功能聚合源 (jsDelivr CDN)** | `https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/aiwex.json` | 96 站点全能源，国内秒级加载 |
| ⚡ **网盘加速极速源 (Go/SO 预加载)** | `https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/accelerated.json` | 配合 GoProxy / 纯 Java 预拉取加速 |
| 🌟 **开源原生极速源 (纯开源 Spider)** | `https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/accelerated_open.json` | 100% 开源 Spider，扫码自动配置+千万级秒搜 |
| 🌐 **精简核心源 (17 站点)** | `https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/custom.json` | 纯净精简，启动加载快 |
| 📡 **GitHub Pages 官方线路** | `https://lublue147-netizen.github.io/subscription/aiwex.json` | 实时更新线路 |
| 📺 **电视直播源 (IPTV M3U)** | `https://cdn.jsdelivr.net/gh/lublue147-netizen/subscription@gh-pages/live/iptv.m3u` | 央视卫视高清直播流 |

---

## 🏎️ 二、GoProxy 网盘加速引擎 (Go & SO Native)

### 1. 为什么需要网盘加速代理？
- **单线程限速严重**：阿里、夸克、115 等网盘对单 HTTP 连接限速（如 500KB~2MB/s），ExoPlayer 读取 4K 蓝光高码率原盘时频繁出现“转圈”缓冲。
- **并发切片提速**：GoProxy 接收 ExoPlayer 的 HTTP 请求，拆解为多个并发 Range 请求分别抓取数据块，总带宽轻松跑满宽带上限。
- **拖动秒响应**：用户跳转进度条时，即时取消过期的预取协程，重新对准新时间戳加载，杜绝拖动时数十秒的无响应。

### 2. 预编译发布包（GitHub Actions CI 自动构建）

可以在项目的 [Releases](https://github.com/lublue147-netizen/subscription/releases) 或 `gh-pages` 分支直接获取对应平台的可执行二进制与动态库：

- **Android 智能电视盒子 / 手机**: `goproxy-android-arm64` (arm64-v8a)
- **老款电视盒子 / 投影仪**: `goproxy-android-armv7` (armeabi-v7a)
- **Android 原生 SO 库**: `libgoproxy.so` (支持 JNI 动态加载)
- **Linux NAS / 软路由 / Docker**: `goproxy-linux-amd64`
- **Windows PC**: `goproxy-windows-amd64.exe`

### 3. 本地启动加速服务

```bash
# 默认在 127.0.0.1:9978 启动，配置 64MB 内存环形缓存
./goproxy-linux-amd64 -port 9978 -cache 64
```

HTTP 端点说明：
- **加速播放端点**: `http://127.0.0.1:9978/play?url=<VIDEO_URL>&workers=3`
- **普通代理端点**: `http://127.0.0.1:9978/proxy?url=<VIDEO_URL>`
- **健康检查**: `http://127.0.0.1:9978/health`
- **运行状态与缓存命中率**: `http://127.0.0.1:9978/stats`

---

## 📁 三、仓库目录结构

```
subscription/
├── .github/
│   └── workflows/
│       └── build-and-release.yml   # 独立 CI/CD：编译 Go/SO、Spider DEX、发布 Pages 与 Release
├── config/
│   ├── aiwex.json                  # 96 个站点全能主配置
│   ├── custom.json                 # 精简核心配置模板
│   └── accelerated.json            # 专为网盘加速优化的配置
├── live/
│   └── iptv.m3u                    # 高清电视直播源
├── spider/
│   ├── custom_spider.jar           # 80 个 Spider 爬虫二进制 Jar
│   └── spider_open.jar             # 由 spider_source 编译生成的纯开源爬虫 Jar
├── spider_source/                  # 爬虫 Java 源码
│   └── src/com/github/catvod/
│       ├── crawler/Spider.java     # 基础 Spider 类
│       ├── spider/
│       │   ├── Init.java           # 爬虫初始化
│       │   ├── Proxy.java          # 核心代理桥接
│       │   ├── AList.java          # AList 挂载与加速直链
│       │   ├── PanSou.java         # 网盘聚合搜索
│       │   ├── Douban.java         # 豆瓣热榜
│       │   ├── Bili.java           # Bilibili 嗅探
│       │   ├── AppV7.java          # V7 采集站
│       │   └── Push.java           # 剪贴板推送
│       ├── proxy/
│       │   ├── GoProxy.java        # Go/SO 加速器调度管理
│       │   └── NetdiskStream.java  # 纯 Java 多线程预取兜底
│       └── utils/
│           ├── OkHttp.java         # 高性能 HTTP 客户端
│           └── Crypto.java         # 加解密工具类
├── goproxy/                        # Go 网盘加速代理与 SO 工程
│   ├── go.mod                      # Go 模块定义
│   ├── main.go                     # CLI 独立可执行程序入口
│   ├── cshared.go                  # CGO 与 JNI 导出代码 (编译 libgoproxy.so)
│   ├── build.sh                    # 跨平台交叉编译脚本
│   ├── README.md                   # GoProxy 详细技术文档
│   └── server/
│       ├── server.go               # HTTP 路由与状态服务
│       ├── accelerator.go          # Range 并发切片与预取引擎
│       ├── chunk_cache.go          # LRU 内存环形缓存
│       └── netdisk_rules.go        # 夸克/阿里/115/百度专用规则与 Header 注入
├── scripts/
│   └── build_source.py             # 静态发布打包、MD5 计算与网页门户生成
└── README.md                       # 本说明文档
```

---

## 🛠️ 四、云端全自动构建与发布说明

> 本项目遵循**“不要本地安装构建，所有代码同步到 GitHub 完成构建”**的原则。

只要在 GitHub 仓库有新的 `push` 提交，GitHub Actions 将全自动按序执行：
1. **安装 Go 1.22 与 Android NDK**，跨平台交叉编译 Android `arm64`/`armv7` 原生程序及 `libgoproxy.so` 动态库。
2. **安装 JDK 21 与 Android SDK**，调用 `javac` 编译 `spider_source/` 中的 Java 代码，并用 `d8` 转换为 Dalvik 字节码（`classes.dex`），打包为 `spider_open.jar`。
3. **运行 `scripts/build_source.py`** 校验配置、计算哈希并生成网页导航门户。
4. **全自动部署到 `gh-pages` 分支**，全球 jsDelivr CDN 节点与 GitHub Pages 即可同步生效。
5. **自动创建并更新 GitHub Release**，上传最新预编译二进制附件供用户直接下载。
