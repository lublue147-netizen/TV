# 📺 自建 TVBox / FongMi TV 影视订阅源

本项目基于 `aiwex` 高性能影视订阅源架构，提供了一套完整的、可自主维护与扩展的订阅源仓库。所有构建、校验、哈希计算以及全球 CDN 发布均通过 **GitHub Actions** 自动化完成，无需在本地安装任何环境。

---

## 一、订阅链接（即开即用）

在 **FongMi TV** 或 **TVBox** 的「设置」->「配置地址」中填入以下任一链接即可：

| 线路类型 | 订阅链接 | 说明 |
| :--- | :--- | :--- |
| **🚀 jsDelivr 高速 CDN (推荐)** | `https://cdn.jsdelivr.net/gh/lublue147-netizen/TV@gh-pages/aiwex.json` | 国内首选，全球 CDN 节点秒级加速 |
| **🌐 GitHub Pages 官方线路** | `https://lublue147-netizen.github.io/TV/aiwex.json` | 官方静态托管源，实时更新 |
| **⚡ GitHub 加速镜像 (ghproxy)** | `https://ghproxy.net/https://raw.githubusercontent.com/lublue147-netizen/TV/fongmi/subscription/config/aiwex.json` | 镜像代理通道 |
| **📦 轻量精简版源 (Custom)** | `https://cdn.jsdelivr.net/gh/lublue147-netizen/TV@gh-pages/custom.json` | 去繁从简，精选 17 个高可用核心站点 |

---

## 二、架构分析：`aiwex.json` 核心组成

完整的订阅源由以下几个关键模块组成：

```
aiwex.json
├── spider      # 核心爬虫 Jar 包（含 DEX 字节码与 SO 库），负责各站点的解析与视频嗅探
├── wallpaper   # 背景壁纸接口（如 ACG 动漫画风）
├── logo        # 订阅源加载时的动态或静态 Logo
├── sites       # 点播站点列表（全能影视、秒播采集、网盘搜索、体育直播、课堂教育等）
├── parses      # VIP 解析接口（用于嗅探解析爱优腾芒等平台）
├── lives       # 电视直播源（M3U / TXT 列表，含央视、卫视、地方台等）
├── doh         # DNS-over-HTTPS 安全加密解析配置（防 DNS 劫持）
├── rules       # 针对特定视频流域名的嗅探拦截与重定向规则
├── ads         # 广告域名黑名单列表
└── headers     # 特殊站点所必需的自定义 HTTP 请求头（如防盗链 Referer 或特定 UA）
```

### 1. 站点分类体系（共 96 个站点）
1. **网盘聚合与榜单**：豆瓣热榜、网盘配置中心、我的网盘。
2. **4K 高清专区**：玩偶、花卷、观影、七味、盘库、虎斑、木偶、多多、剧透、立播、原盘、蜗牛等。
3. **秒播与采集站**：韩剧秒播、瓜子、独播、闪电、文才、贱片、大师兄等。
4. **垂直领域**：
   - 🤡 **动漫专区**：稀饭动漫、次元动漫、魔都动漫。
   - 🎃 **听书有声**：小红听书、小马听书、极品听书、悦庭听书。
   - 👼 **少儿早教**：宝宝儿歌、贝贝儿歌、兔兔儿歌。
   - 💃 **音乐与电台**：跳舞教学、梨园戏曲、蜻蜓电台、KTV 音乐、网易云、酷我音乐。
   - 🌐 **体育赛事**：飞球体育、瓜子体育、球通体育、八八看球、咖啡体育、WWE。
   - 📚 **名师课堂**：小学课堂、初中课堂、高中课堂、少儿教育。
5. **网盘挂载与协议**：AList 挂载、WebDAV 挂载、Emby 私人影院。
6. **聚合盘搜**：海音搜、九七搜、趣盘搜、爱盘搜、卡卡盘搜。

---

## 三、目录结构

```
subscription/
├── config/
│   ├── aiwex.json          # 完整主配置文件（96 个站点）
│   └── custom.json         # 模块化精简模板（方便日常增删调试）
├── live/
│   └── iptv.m3u            # 本地归档维护的高清直播源
├── spider/
│   └── custom_spider.jar   # 自主托管的 Spider 核心爬虫包（MD5: fc8f993c9297d38139363cd0e3db9853）
├── scripts/
│   └── build_source.py     # 自动化构建与校验脚本（支持多环境生成与网页门户）
└── README.md               # 本说明文档
```

---

## 四、如何自定义与维护你的源？

### 1. 添加或修改站点
编辑 `subscription/config/aiwex.json` 或 `subscription/config/custom.json`，在 `"sites"` 数组中添加新的对象：

```json
{
  "key": "MySiteKey",
  "name": "🎬┃我的站点┃4K",
  "type": 3,
  "api": "csp_AiNewWoggGuard",
  "searchable": 1,
  "quickSearch": 1,
  "filterable": 1,
  "ext": ""
}
```

* `type`: 站点类型（`0`: XML, `1`: JSON CMS, `3`: 爬虫 Spider/JS/Py）。
* `api`: 爬虫类名，需在 `spider` 的 jar 包中有对应类实现（如 `csp_AiNewWoggGuard`）。
* `searchable`: 是否参与全站聚合搜索（`1` 为允许，`0` 为禁止）。

### 2. 更换核心 Spider 爬虫包
若要替换为自己编译的爬虫 Jar：
1. 将新的 `.jar` 文件覆盖放置在 `subscription/spider/custom_spider.jar`。
2. 提交并推送到 GitHub。
3. GitHub Actions 会自动计算新的 MD5 值并更新到生成的订阅源中，保证客户端能感知更新并重新拉取。

### 3. 修改电视直播
编辑 `subscription/live/iptv.m3u`，增加您的私有频道或测试流，每次推送均会自动同步至 CDN 镜像。

---

## 五、GitHub Actions 自动化构建机制

本项目配置了 `.github/workflows/deploy-source.yml` 工作流：
1. **触发时机**：每次向 `fongmi` 分支推送带有 `subscription/**` 路径的改动，或手动点击 `Run workflow`。
2. **自动构建**：
   - 校验所有 JSON 语法的合法性，杜绝任何排版或逗号语法错误。
   - 自动提取并计算 `custom_spider.jar` 的最新 MD5 散列值。
   - 打包生成静态网页门户、标准 `index.json`、完整 `aiwex.json`、精简 `custom.json` 以及直播资源。
3. **自动发布**：
   - 产物自动部署到仓库的 `gh-pages` 分支。
   - 自动生成清晰的 GitHub Step Summary，直观展示所有订阅 URL。
