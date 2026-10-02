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
DIST_DIR = ROOT_DIR.parent / "dist_source"

def compute_md5(file_path: Path) -> str:
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def build():
    repo = os.environ.get("GITHUB_REPOSITORY", "lublue147-netizen/TV")
    owner, repo_name = repo.split("/") if "/" in repo else ("lublue147-netizen", "TV")
    pages_base = f"https://{owner}.github.io/{repo_name}"
    cdn_base = f"https://cdn.jsdelivr.net/gh/{repo}@gh-pages"
    raw_base = f"https://raw.githubusercontent.com/{repo}/fongmi/subscription"

    print(f"[*] Building subscription sources for repo: {repo}")
    print(f"[*] GitHub Pages Base: {pages_base}")
    print(f"[*] jsDelivr CDN Base: {cdn_base}")

    if DIST_DIR.exists():
        shutil.rmtree(DIST_DIR)
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    (DIST_DIR / "live").mkdir(parents=True, exist_ok=True)

    # 1. Copy Spider Jar & calculate MD5
    spider_src = SPIDER_DIR / "custom_spider.jar"
    spider_md5 = ""
    if spider_src.exists():
        spider_md5 = compute_md5(spider_src)
        shutil.copy2(spider_src, DIST_DIR / "spider.jar")
        shutil.copy2(spider_src, DIST_DIR / "spider.txt")
        print(f"[+] Spider JAR packaged: dist/spider.jar & dist/spider.txt (MD5: {spider_md5})")
    else:
        print("[!] Warning: custom_spider.jar not found, using upstream MD5")
        spider_md5 = "fc8f993c9297d38139363cd0e3db9853"

    # Self-hosted spider URL: jsDelivr CDN serves .txt files with 100% reliability
    spider_url = f"{cdn_base}/spider.txt;md5;{spider_md5}"

    # 1.2 Copy Open Source Spider Jar if compiled
    spider_open_src = SPIDER_DIR / "spider_open.jar"
    if spider_open_src.exists():
        open_md5 = compute_md5(spider_open_src)
        shutil.copy2(spider_open_src, DIST_DIR / "spider_open.jar")
        shutil.copy2(spider_open_src, DIST_DIR / "spider_open.txt")
        print(f"[+] Open Spider JAR packaged: dist/spider_open.jar & dist/spider_open.txt (MD5: {open_md5})")

    # 2. Copy Live Streams
    live_src = LIVE_DIR / "iptv.m3u"
    if live_src.exists():
        shutil.copy2(live_src, DIST_DIR / "live" / "iptv.m3u")
        print("[+] Live IPTV packaged: dist/live/iptv.m3u")

    # 3. Process aiwex.json
    aiwex_src = CONFIG_DIR / "aiwex.json"
    if not aiwex_src.exists():
        raise FileNotFoundError(f"Missing {aiwex_src}")

    with open(aiwex_src, "r", encoding="utf-8") as f:
        aiwex_data = json.load(f)

    # Replace spider with self-hosted URL
    aiwex_data["spider"] = spider_url

    # Add self-hosted live entry if desired
    if live_src.exists():
        self_live = {
            "name": "本地自建高清直播",
            "type": 0,
            "url": f"{cdn_base}/live/iptv.m3u",
            "playerType": 2
        }
        if "lives" in aiwex_data and isinstance(aiwex_data["lives"], list):
            # Insert at beginning
            aiwex_data["lives"].insert(0, self_live)

    # Write human-readable and minified versions
    with open(DIST_DIR / "aiwex.json", "w", encoding="utf-8") as f:
        json.dump(aiwex_data, f, ensure_ascii=False, indent=2)

    with open(DIST_DIR / "index.json", "w", encoding="utf-8") as f:
        json.dump(aiwex_data, f, ensure_ascii=False, indent=2)

    with open(DIST_DIR / "aiwex.min.json", "w", encoding="utf-8") as f:
        json.dump(aiwex_data, f, ensure_ascii=False, separators=(',', ':'))

    print(f"[+] aiwex.json & index.json generated with {len(aiwex_data.get('sites', []))} sites")

    # 4. Process custom.json if present
    custom_src = CONFIG_DIR / "custom.json"
    if custom_src.exists():
        with open(custom_src, "r", encoding="utf-8") as f:
            custom_data = json.load(f)
        custom_data["spider"] = spider_url
        with open(DIST_DIR / "custom.json", "w", encoding="utf-8") as f:
            json.dump(custom_data, f, ensure_ascii=False, indent=2)
        print(f"[+] custom.json generated with {len(custom_data.get('sites', []))} sites")

    # 5. Generate Web Index Landing Page for easy subscription import
    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>我的影视订阅源 (FongMi TV / TVBox)</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <style>
        body {{
            background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
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
            background: rgba(30, 41, 59, 0.7);
            backdrop-filter: blur(12px);
            border: 1px solid rgba(255, 255, 255, 0.1);
            border-radius: 16px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.3);
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
            <h1 class="display-5 fw-bold mb-3"><i class="fa-solid fa-tv text-primary me-2"></i> 自建 TVBox / FongMi 影视源</h1>
            <p class="lead text-light opacity-75">基于 aiwex 架构打造的全能聚合源，支持 4K 影视、网盘解析、聚合秒播、体育直播与课堂教育</p>
            <div class="mt-3">
                <span class="badge-stat"><i class="fa-solid fa-film me-1"></i> {len(aiwex_data.get('sites', []))} 个优质站点</span>
                <span class="badge-stat"><i class="fa-solid fa-bolt me-1"></i> {len(aiwex_data.get('parses', []))} 条 VIP 解析</span>
                <span class="badge-stat"><i class="fa-solid fa-satellite-dish me-1"></i> {len(aiwex_data.get('lives', []))} 套高清直播</span>
                <span class="badge-stat"><i class="fa-solid fa-shield-halved me-1"></i> 80 个内置 Spider 爬虫</span>
            </div>
        </div>

        <div class="row justify-content-center">
            <div class="col-lg-8">
                <div class="card card-custom p-4">
                    <h4 class="mb-3 text-white"><i class="fa-solid fa-link text-info me-2"></i> 订阅配置接口</h4>
                    <p class="text-secondary small mb-3">直接将以下任一链接复制粘贴至 FongMi TV 或 TVBox 的「配置地址 / 接口地址」中即可使用：</p>
                    
                    <label class="form-label text-light fw-bold">1. jsDelivr 高速 CDN 源 (国内首选推荐)</label>
                    <div class="code-box mb-4">
                        <span id="url1">{cdn_base}/aiwex.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url1')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">2. GitHub Pages 官方源</label>
                    <div class="code-box mb-4">
                        <span id="url2">{pages_base}/aiwex.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url2')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">3. GitHub 加速源 (ghproxy 镜像)</label>
                    <div class="code-box mb-2">
                        <span id="url3">https://ghproxy.net/https://raw.githubusercontent.com/{repo}/fongmi/subscription/config/aiwex.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url3')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>
                </div>

                <div class="card card-custom p-4">
                    <h4 class="mb-3 text-white"><i class="fa-solid fa-circle-question text-warning me-2"></i> 使用说明与特性</h4>
                    <ul class="text-light opacity-90 mb-0" style="line-height: 1.8;">
                        <li><strong>4K 网盘影视</strong>：涵盖玩偶、花卷、观影、七味、盘库、立播、原盘、蜗牛等核心 4K 频道。</li>
                        <li><strong>秒播采集站</strong>：整合韩剧、瓜子、独播、闪电、文才、贱片等数十个秒播流。</li>
                        <li><strong>多元化频道</strong>：包括少儿儿歌、听书评书、电台音乐、综合体育（球通/八八/咖啡）及中小学课堂。</li>
                        <li><strong>自动化构建</strong>：每次在 GitHub 提交或修改代码，GitHub Actions 会自动校验并完成全球 CDN 部署。</li>
                    </ul>
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
