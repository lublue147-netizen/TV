# GoProxy - TVBox / FongMi 影视网盘播放加速引擎 (Go & SO Native)

基于 Go 语言开发的高性能视频流与网盘播放本地加速代理服务，支持编译为原生 Linux/Android 可执行程序及 Android JNI `.so` 动态链接库。

专为解决 TVBox、FongMi TV 及各影视播放器在播放**夸克网盘、阿里云盘、115网盘、百度网盘、AList、WebDAV**时单线程限速、ExoPlayer 缓冲拖慢、拖动进度条频繁卡顿等问题而设计。

---

## ⚡ 核心加速特性

1. **多线程分块并发预加载 (Multi-Threaded Chunk Prefetching)**:
   - 传统播放器（ExoPlayer / IjkPlayer）通常发起单连接顺序读取，遇到网盘限速（如阿里/夸克单连接 500KB~2MB/s）时极易转圈缓冲。
   - GoProxy 将视频切分为 2MB/4MB 数据块，采用 3~5 个并发协程预拉取当前播放位置后方的多个分块，存入高速内存环形缓冲区。
   - 播放器读取时直接命中本地内存，提供高达数十兆的瞬时吞吐率。

2. **智能 Range 探测与拖动即时响应**:
   - 自动解析上游 `Accept-Ranges` 与 `Content-Length`。
   - 当用户拖动进度条（Seek）时，代理自动取消旧位置的在途请求，立刻重定向预加载窗口到新的播放点，实现近乎零延迟的秒播与快进。

3. **网盘专属 Header & 防盗链自动注入**:
   - **夸克 (Quark)**: 注入高版本 Chrome PC User-Agent 与 `pan.quark.cn` Referer，规避移动端限速策略。
   - **阿里云盘 (Aliyun / OSS)**: 注入 `aliyundrive.com` Referer 与自适应重定向处理。
   - **115网盘**: 注入专用客户端 UA（`115disk`）及 Cookie 转发支持。
   - **百度网盘 / AList**: 自动保留鉴权 Token 并优化 TCP 链接池。

4. **双形态运行模式**:
   - **形态一：原生 ELF 独立进程**: 编译为轻量静态二进制（无 libc 依赖），直接在 Android 设备、软路由、NAS 或 Linux/Windows 上后台运行。
   - **形态二：Android JNI 动态库 (`libgoproxy.so`)**: 导出标准 C 函数与 JNI 接口，可直接由 Spider/App 通过 `System.loadLibrary("goproxy")` 调用。

---

## 📦 架构设计

```
[ TVBox / ExoPlayer ]
         │ (HTTP GET Range: bytes=10485760-)
         ▼
[ GoProxy Accelerator :9978 ]
   ├─► Memory LRU Cache (64MB Ring Buffer)
   │     ├─ Chunk #5 [Hit -> Instant Response]
   │     ├─ Chunk #6 [Hit -> Instant Response]
   │
   ├─► Background Worker Pool (3~4 Goroutines)
   │     ├─ Worker 1 -> Fetch Chunk #7 (Range: 14M-16M)
   │     ├─ Worker 2 -> Fetch Chunk #8 (Range: 16M-18M)
   │     └─ Worker 3 -> Fetch Chunk #9 (Range: 18M-20M)
   │
   └─► Upstream Netdisks (Quark / Aliyun / 115 / AList)
```

---

## 🚀 编译与构建

项目已集成 GitHub Actions 自动化 CI，推送代码后将在云端自动完成跨平台编译与发布：

| 平台架构 | 交付产物 | 说明 |
| :--- | :--- | :--- |
| **Android ARM64** | `goproxy-android-arm64` | 绝大多数现代电视盒子与安卓手机（arm64-v8a） |
| **Android ARMv7** | `goproxy-android-armv7` | 老款电视盒子/投影仪（armeabi-v7a 32位） |
| **Android Native SO** | `libgoproxy.so` | 提供 arm64-v8a 与 armeabi-v7a 的 JNI 动态库 |
| **Linux x86_64** | `goproxy-linux-amd64` | Linux NAS、软路由、VPS、Docker 部署 |
| **Windows x86_64** | `goproxy-windows-amd64.exe`| Windows 电脑端本地加速 |

---

## 💻 启动与命令行参数

```bash
# 启动加速服务（默认监听 127.0.0.1:9978，缓存 64MB）
./goproxy-linux-amd64 -port 9978 -cache 64

# 查看帮助
./goproxy-linux-amd64 -h
```

### HTTP 接口定义

- **加速流接口**:
  `GET http://127.0.0.1:9978/play?url=<URL_ENCODED_VIDEO_URL>&workers=3`
- **普通代理接口**:
  `GET http://127.0.0.1:9978/proxy?url=<URL_ENCODED_VIDEO_URL>`
- **健康探测**:
  `GET http://127.0.0.1:9978/health`
- **运行统计与缓存状态**:
  `GET http://127.0.0.1:9978/stats`
