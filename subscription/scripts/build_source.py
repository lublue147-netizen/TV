#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Subscription Source Builder for TVBox & FongMi TV
Automatically validates, computes hashes, packages assets, and builds GitHub Pages distribution.
"""

import os
import sys
import json
import hashlib
import shutil
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent.parent
CONFIG_DIR = ROOT_DIR / "config"
SPIDER_DIR = ROOT_DIR / "spider"
LIVE_DIR = ROOT_DIR / "live"
GOPROXY_BIN = ROOT_DIR / "goproxy" / "bin"
DIST_DIR = ROOT_DIR / "dist_source"

def compute_md5(file_path: Path) -> str:
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def build():
    repo = os.environ.get("GITHUB_REPOSITORY", "lublue147-netizen/subscription")
    owner, repo_name = repo.split("/") if "/" in repo else ("lublue147-netizen", "subscription")
    pages_base = f"https://{owner}.github.io/{repo_name}"
    cdn_base = f"https://cdn.jsdelivr.net/gh/{repo}@gh-pages"
    raw_base = f"https://raw.githubusercontent.com/{repo}/main"

    print(f"[*] Building subscription sources for repo: {repo}")
    print(f"[*] GitHub Pages Base: {pages_base}")
    print(f"[*] jsDelivr CDN Base: {cdn_base}")

    if DIST_DIR.exists():
        shutil.rmtree(DIST_DIR)
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    (DIST_DIR / "live").mkdir(parents=True, exist_ok=True)
    (DIST_DIR / "bin").mkdir(parents=True, exist_ok=True)

    # 1. Package Production Spider Jar (Guard engine for PanConfig, Wogg, and Netdisk sites)
    guard_src = SPIDER_DIR / "custom_spider.jar"
    spider_md5 = ""
    if guard_src.exists():
        spider_md5 = compute_md5(guard_src)
        shutil.copy2(guard_src, DIST_DIR / "spider.jar")
        shutil.copy2(guard_src, DIST_DIR / "spider.txt")
        print(f"[+] Production Spider JAR packaged: dist/spider.jar & dist/spider.txt (MD5: {spider_md5})")
    else:
        print("[!] Warning: custom_spider.jar not found, using fallback MD5")
        spider_md5 = "fc8f993c9297d38139363cd0e3db9853"

    spider_url = f"{cdn_base}/spider.txt;md5;{spider_md5}"

    # 1.2 Package Open-Source Compiled Spider Jar (Compiled from spider_source/src)
    open_src = SPIDER_DIR / "spider_open.jar"
    open_md5 = ""
    if open_src.exists():
        open_md5 = compute_md5(open_src)
        shutil.copy2(open_src, DIST_DIR / "spider_open.jar")
        shutil.copy2(open_src, DIST_DIR / "spider_open.txt")
        print(f"[+] Open-Source Spider JAR packaged: dist/spider_open.jar & dist/spider_open.txt (MD5: {open_md5})")
    else:
        open_md5 = spider_md5

    spider_open_url = f"{cdn_base}/spider_open.txt;md5;{open_md5}"

    # 2. Copy Live Streams
    live_src = LIVE_DIR / "iptv.m3u"
    if live_src.exists():
        shutil.copy2(live_src, DIST_DIR / "live" / "iptv.m3u")
        print("[+] Live IPTV packaged: dist/live/iptv.m3u")

    # 3. Copy GoProxy binaries & SO if present
    if GOPROXY_BIN.exists():
        for item in GOPROXY_BIN.glob("*"):
            if item.is_file():
                shutil.copy2(item, DIST_DIR / "bin" / item.name)
            elif item.is_dir():
                shutil.copytree(item, DIST_DIR / "bin" / item.name, dirs_exist_ok=True)
        print(f"[+] GoProxy binaries copied to dist/bin/")

    # 3.2 Copy token.json if present
    token_src = CONFIG_DIR / "token.json"
    if token_src.exists():
        shutil.copy2(token_src, DIST_DIR / "token.json")
        print("[+] Netdisk token.json packaged: dist/token.json")

    # 4. Process aiwex.json
    aiwex_src = CONFIG_DIR / "aiwex.json"
    if aiwex_src.exists():
        with open(aiwex_src, "r", encoding="utf-8") as f:
            aiwex_data = json.load(f)

        aiwex_data["spider"] = spider_url
        aiwex_data["token"] = f"{cdn_base}/token.json"
        if live_src.exists():
            self_live = {
                "name": "本地自建高清直播",
                "type": 0,
                "url": f"{cdn_base}/live/iptv.m3u",
                "playerType": 2
            }
            if "lives" in aiwex_data and isinstance(aiwex_data["lives"], list):
                aiwex_data["lives"].insert(0, self_live)

        with open(DIST_DIR / "aiwex.json", "w", encoding="utf-8") as f:
            json.dump(aiwex_data, f, ensure_ascii=False, indent=2)
        with open(DIST_DIR / "index.json", "w", encoding="utf-8") as f:
            json.dump(aiwex_data, f, ensure_ascii=False, indent=2)
        with open(DIST_DIR / "aiwex.min.json", "w", encoding="utf-8") as f:
            json.dump(aiwex_data, f, ensure_ascii=False, separators=(',', ':'))
        print(f"[+] aiwex.json & index.json generated ({len(aiwex_data.get('sites', []))} sites)")

    # 5. Process custom.json
    custom_src = CONFIG_DIR / "custom.json"
    if custom_src.exists():
        with open(custom_src, "r", encoding="utf-8") as f:
            custom_data = json.load(f)
        custom_data["spider"] = spider_url
        custom_data["token"] = f"{cdn_base}/token.json"
        with open(DIST_DIR / "custom.json", "w", encoding="utf-8") as f:
            json.dump(custom_data, f, ensure_ascii=False, indent=2)
        print(f"[+] custom.json generated ({len(custom_data.get('sites', []))} sites)")

    # 6. Process accelerated.json (GoProxy Netdisk Optimized)
    acc_src = CONFIG_DIR / "accelerated.json"
    if acc_src.exists():
        with open(acc_src, "r", encoding="utf-8") as f:
            acc_data = json.load(f)
        acc_data["spider"] = spider_url
        acc_data["token"] = f"{cdn_base}/token.json"
        if live_src.exists() and "lives" in acc_data and isinstance(acc_data["lives"], list):
            for l in acc_data["lives"]:
                if l.get("url", "").startswith("./"):
                    l["url"] = f"{cdn_base}/{l['url'].lstrip('./')}"
        with open(DIST_DIR / "accelerated.json", "w", encoding="utf-8") as f:
            json.dump(acc_data, f, ensure_ascii=False, indent=2)
        print(f"[+] accelerated.json generated ({len(acc_data.get('sites', []))} sites)")

        # 6.2 Process accelerated_open.json & open.json (100% Open Source Spider)
        acc_open_data = json.loads(json.dumps(acc_data))
        acc_open_data["spider"] = spider_open_url
        with open(DIST_DIR / "accelerated_open.json", "w", encoding="utf-8") as f:
            json.dump(acc_open_data, f, ensure_ascii=False, indent=2)
        with open(DIST_DIR / "open.json", "w", encoding="utf-8") as f:
            json.dump(acc_open_data, f, ensure_ascii=False, indent=2)
        print(f"[+] accelerated_open.json & open.json generated ({len(acc_open_data.get('sites', []))} sites)")

    # 7. Generate Landing Page
    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TVBox / FongMi 影视源与网盘播放加速发布中心</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        body {{
            background: linear-gradient(135deg, #0b0f19 0%, #1e293b 100%);
            color: #f8fafc;
            min-height: 100vh;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            padding-bottom: 50px;
        }}
        .hero {{
            padding: 50px 0 30px;
            text-align: center;
        }}
        .card-custom {{
            background: rgba(30, 41, 59, 0.75);
            backdrop-filter: blur(14px);
            border: 1px solid rgba(255, 255, 255, 0.12);
            border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.35);
            margin-bottom: 24px;
        }}
        .code-box {{
            background: #090d16;
            color: #38bdf8;
            padding: 12px 16px;
            border-radius: 8px;
            font-family: monospace;
            word-break: break-all;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }}
        .badge-stat {{
            background: rgba(56, 189, 248, 0.15);
            color: #38bdf8;
            padding: 6px 14px;
            border-radius: 20px;
            font-weight: 600;
            display: inline-block;
            margin: 4px;
        }}
        .btn-copy {{
            background: #2563eb;
            color: #fff;
            border: none;
            padding: 6px 16px;
            border-radius: 6px;
            cursor: pointer;
            transition: 0.2s;
            white-space: nowrap;
        }}
        .btn-copy:hover {{
            background: #1d4ed8;
            color: #fff;
        }}
    </style>
</head>
<body>
    <div class="container">
        <div class="hero">
            <h1 class="display-5 fw-bold mb-3"><i class="fa-solid fa-tv text-primary me-2"></i> 自建 TVBox / FongMi 影视源与网盘加速</h1>
            <p class="lead text-light opacity-75">全能聚合影视源 + Go/SO 网盘播放多线程加速引擎，秒播 4K、零缓冲体验</p>
            <div class="mt-3">
                <span class="badge-stat"><i class="fa-solid fa-film me-1"></i> 全能源 96 个站点</span>
                <span class="badge-stat"><i class="fa-solid fa-bolt me-1"></i> 网盘 Range 多线程预加载</span>
                <span class="badge-stat"><i class="fa-solid fa-microchip me-1"></i> Go / SO 跨平台加速</span>
                <span class="badge-stat"><i class="fa-solid fa-satellite-dish me-1"></i> 高清直播与 VIP 解析</span>
            </div>
        </div>

        <div class="row justify-content-center">
            <div class="col-lg-9">
                <div class="card card-custom p-4">
                    <h4 class="mb-3 text-white"><i class="fa-solid fa-link text-info me-2"></i> 订阅配置接口</h4>
                    <p class="text-secondary small mb-3">直接将以下任一链接复制粘贴至 FongMi TV 或 TVBox 的「配置地址 / 接口地址」中即可使用：</p>
                    
                    <label class="form-label text-light fw-bold">1. 🚀 全功能聚合源 (jsDelivr CDN 国内首选推荐)</label>
                    <div class="code-box mb-4">
                        <span id="url1">{cdn_base}/aiwex.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url1')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">2. ⚡ 网盘加速极速源 (Go/SO 预加载与自建 Spider)</label>
                    <div class="code-box mb-4">
                        <span id="url2">{cdn_base}/accelerated.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url2')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">3. 🌟 纯开源原生源 (100% Java 开源 Spider · 扫码配置+分集直解)</label>
                    <div class="code-box mb-4">
                        <span id="url_open">{cdn_base}/accelerated_open.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url_open')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">4. 🌐 精简核心源 (17 核心精品站点)</label>
                    <div class="code-box mb-4">
                        <span id="url3">{cdn_base}/custom.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url3')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">5. 📡 GitHub Pages 官方线路</label>
                    <div class="code-box mb-2">
                        <span id="url4">{pages_base}/aiwex.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url4')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>
                </div>

                <div class="card card-custom p-4">
                    <h4 class="mb-3 text-white"><i class="fa-solid fa-rocket text-warning me-2"></i> 网盘播放加速引擎 (GoProxy / SO)</h4>
                    <p class="text-secondary small mb-3">专为夸克、阿里、115、百度网盘设计的并发预取代理，支持直接在 Android 电视盒子或 PC/软路由运行：</p>
                    <div class="table-responsive">
                        <table class="table table-dark table-borderless align-middle mb-0">
                            <thead>
                                <tr>
                                    <th>平台 / 架构</th>
                                    <th>核心类型</th>
                                    <th>说明</th>
                                    <th>获取地址</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td><strong>Android ARM64</strong></td>
                                    <td><span class="badge bg-primary">ELF 可执行程序</span></td>
                                    <td>适用于大部分智能电视盒子与手机 (arm64-v8a)</td>
                                    <td><a href="{cdn_base}/bin/goproxy-android-arm64" class="btn btn-sm btn-outline-info">下载</a></td>
                                </tr>
                                <tr>
                                    <td><strong>Android ARMv7</strong></td>
                                    <td><span class="badge bg-primary">ELF 可执行程序</span></td>
                                    <td>适用于 32 位老旧电视盒子/投影仪 (armeabi-v7a)</td>
                                    <td><a href="{cdn_base}/bin/goproxy-android-armv7" class="btn btn-sm btn-outline-info">下载</a></td>
                                </tr>
                                <tr>
                                    <td><strong>Android Native SO</strong></td>
                                    <td><span class="badge bg-success">JNI 动态库 (.so)</span></td>
                                    <td>JNI 动态链接库，供 APK/Spider 直接调用</td>
                                    <td><a href="{cdn_base}/bin/arm64-v8a/libgoproxy.so" class="btn btn-sm btn-outline-info">libgoproxy.so</a></td>
                                </tr>
                                <tr>
                                    <td><strong>Linux x86_64</strong></td>
                                    <td><span class="badge bg-secondary">Linux 可执行程序</span></td>
                                    <td>软路由 / NAS / Docker / VPS 本地加速</td>
                                    <td><a href="{cdn_base}/bin/goproxy-linux-amd64" class="btn btn-sm btn-outline-info">下载</a></td>
                                </tr>
                                <tr>
                                    <td><strong>Windows x86_64</strong></td>
                                    <td><span class="badge bg-secondary">Windows EXE</span></td>
                                    <td>Windows 电脑端使用</td>
                                    <td><a href="{cdn_base}/bin/goproxy-windows-amd64.exe" class="btn btn-sm btn-outline-info">下载</a></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <script>
        function copyText(elementId) {{
            const text = document.getElementById(elementId).innerText;
            navigator.clipboard.writeText(text).then(() => {{
                alert('已复制到剪贴板: ' + text);
            }}).catch(err => {{
                prompt('请手动复制链接:', text);
            }});
        }}
    </script>
</body>
</html>
"""
    with open(DIST_DIR / "index.html", "w", encoding="utf-8") as f:
        f.write(html_content)

    print("[+] Generated Web Portal: dist/index.html")
    print(f"[*] Build complete! Output folder: {DIST_DIR}")

if __name__ == "__main__":
    build()
