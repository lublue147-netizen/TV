#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Subscription Source Builder for TVBox & FongMi TV
Automatically validates, computes hashes, packages assets, generates versioned directories,
and builds GitHub Pages distribution with multi-version persistence.
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

def get_all_version_tags(current_tag: str):
    tags = set(["v1.0.0", "v1.0.1", "v1.0.2", "v1.0.3", "v1.0.4", "v1.0.5", "v1.0.6", "v1.0.7", "v1.0.8", "v1.0.9"])
    try:
        import subprocess
        res = subprocess.run(["git", "tag", "-l", "v*"], stdout=subprocess.PIPE, text=True)
        for t in res.stdout.splitlines():
            t = t.strip()
            if t:
                tags.add(t)
    except Exception:
        pass
    if current_tag:
        tags.add(current_tag)
    res_list = list(tags)
    res_list.sort(key=lambda v: [int(x) if x.isdigit() else 0 for x in v.lstrip('v').split('.')], reverse=True)
    return res_list

def compute_md5(file_path: Path) -> str:
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def generate_dist_files(target_dir: Path, cdn_base_url: str, spider_md5: str, open_md5: str,
                        guard_src: Path, open_src: Path, live_src: Path, token_src: Path):
    target_dir.mkdir(parents=True, exist_ok=True)
    (target_dir / "live").mkdir(parents=True, exist_ok=True)

    # 1. Spider JARs
    if guard_src.exists():
        shutil.copy2(guard_src, target_dir / "spider.jar")
        shutil.copy2(guard_src, target_dir / "spider.txt")
    if open_src.exists():
        shutil.copy2(open_src, target_dir / "spider_open.jar")
        shutil.copy2(open_src, target_dir / "spider_open.txt")

    # 2. Live streams & token
    if live_src.exists():
        shutil.copy2(live_src, target_dir / "live" / "iptv.m3u")
    if token_src.exists():
        shutil.copy2(token_src, target_dir / "token.json")

    spider_url = f"{cdn_base_url}/spider.txt;md5;{spider_md5}"
    spider_open_url = f"{cdn_base_url}/spider_open.txt;md5;{open_md5}"

    # 3. aiwex.json & index.json
    aiwex_src = CONFIG_DIR / "aiwex.json"
    if aiwex_src.exists():
        with open(aiwex_src, "r", encoding="utf-8") as f:
            aiwex_data = json.load(f)
        aiwex_data["spider"] = spider_url
        aiwex_data["token"] = f"{cdn_base_url}/token.json"
        if live_src.exists():
            self_live = {
                "name": "本地自建高清直播",
                "type": 0,
                "url": f"{cdn_base_url}/live/iptv.m3u",
                "playerType": 2
            }
            if "lives" in aiwex_data and isinstance(aiwex_data["lives"], list):
                aiwex_data["lives"].insert(0, self_live)
        with open(target_dir / "aiwex.json", "w", encoding="utf-8") as f:
            json.dump(aiwex_data, f, ensure_ascii=False, indent=2)
        with open(target_dir / "index.json", "w", encoding="utf-8") as f:
            json.dump(aiwex_data, f, ensure_ascii=False, indent=2)
        with open(target_dir / "aiwex.min.json", "w", encoding="utf-8") as f:
            json.dump(aiwex_data, f, ensure_ascii=False, separators=(',', ':'))

    # 4. custom.json
    custom_src = CONFIG_DIR / "custom.json"
    if custom_src.exists():
        with open(custom_src, "r", encoding="utf-8") as f:
            custom_data = json.load(f)
        custom_data["spider"] = spider_url
        custom_data["token"] = f"{cdn_base_url}/token.json"
        with open(target_dir / "custom.json", "w", encoding="utf-8") as f:
            json.dump(custom_data, f, ensure_ascii=False, indent=2)

    # 5. accelerated.json & accelerated_open.json
    acc_src = CONFIG_DIR / "accelerated.json"
    if acc_src.exists():
        with open(acc_src, "r", encoding="utf-8") as f:
            acc_data = json.load(f)
        acc_data["spider"] = spider_url
        acc_data["token"] = f"{cdn_base_url}/token.json"
        if live_src.exists() and "lives" in acc_data and isinstance(acc_data["lives"], list):
            for l in acc_data["lives"]:
                if l.get("url", "").startswith("./"):
                    l["url"] = f"{cdn_base_url}/{l['url'].lstrip('./')}"
        with open(target_dir / "accelerated.json", "w", encoding="utf-8") as f:
            json.dump(acc_data, f, ensure_ascii=False, indent=2)

        # 100% Open Source Spider version
        acc_open_data = json.loads(json.dumps(acc_data))
        acc_open_data["spider"] = spider_open_url
        with open(target_dir / "accelerated_open.json", "w", encoding="utf-8") as f:
            json.dump(acc_open_data, f, ensure_ascii=False, indent=2)
        with open(target_dir / "open.json", "w", encoding="utf-8") as f:
            json.dump(acc_open_data, f, ensure_ascii=False, indent=2)

def build():
    repo = os.environ.get("GITHUB_REPOSITORY", "lublue147-netizen/subscription")
    owner, repo_name = repo.split("/") if "/" in repo else ("lublue147-netizen", "subscription")
    pages_base = f"https://{owner}.github.io/{repo_name}"
    cdn_base = f"https://cdn.jsdelivr.net/gh/{repo}@gh-pages"

    current_tag = os.environ.get("RELEASE_TAG") or os.environ.get("GITHUB_REF_NAME") or "v1.0.4"
    if current_tag.startswith("refs/tags/"):
        current_tag = current_tag.replace("refs/tags/", "")

    version_list = get_all_version_tags(current_tag)

    print(f"[*] Building subscription sources for repo: {repo}")
    print(f"[*] Current Release Tag: {current_tag}")
    print(f"[*] Multi-version list: {version_list}")
    print(f"[*] GitHub Pages Base: {pages_base}")
    print(f"[*] jsDelivr CDN Base: {cdn_base}")

    if DIST_DIR.exists():
        shutil.rmtree(DIST_DIR)
    DIST_DIR.mkdir(parents=True, exist_ok=True)
    (DIST_DIR / "bin").mkdir(parents=True, exist_ok=True)

    # 1. Compute Spider MD5s
    guard_src = SPIDER_DIR / "custom_spider.jar"
    spider_md5 = compute_md5(guard_src) if guard_src.exists() else "fc8f993c9297d38139363cd0e3db9853"

    open_src = SPIDER_DIR / "spider_open.jar"
    open_md5 = compute_md5(open_src) if open_src.exists() else spider_md5

    live_src = LIVE_DIR / "iptv.m3u"
    token_src = CONFIG_DIR / "token.json"

    # 2. Copy GoProxy binaries & SO
    if GOPROXY_BIN.exists():
        for item in GOPROXY_BIN.glob("*"):
            if item.is_file():
                shutil.copy2(item, DIST_DIR / "bin" / item.name)
            elif item.is_dir():
                shutil.copytree(item, DIST_DIR / "bin" / item.name, dirs_exist_ok=True)
        print(f"[+] GoProxy binaries copied to dist/bin/")

    # 3. Generate ROOT files (Floating / Latest auto-updating URLs)
    print(f"[*] Generating root latest subscription distribution...")
    generate_dist_files(DIST_DIR, cdn_base, spider_md5, open_md5, guard_src, open_src, live_src, token_src)

    # 4. Generate EACH VERSION directory (Permanent, immutable versioned URLs)
    for ver in version_list:
        ver_dir = DIST_DIR / ver
        cdn_ver_base = f"{cdn_base}/{ver}"
        print(f"[*] Generating immutable versioned directory: dist/{ver} (Base: {cdn_ver_base})")
        generate_dist_files(ver_dir, cdn_ver_base, spider_md5, open_md5, guard_src, open_src, live_src, token_src)

    # 4.1 Generate Major Version Aliases (e.g., v1.0 and 1.0 for independent permanent links)
    for major_alias in ["v1.0", "1.0", "v1"]:
        alias_dir = DIST_DIR / major_alias
        cdn_alias_base = f"{cdn_base}/{major_alias}"
        print(f"[*] Generating independent major version alias directory: dist/{major_alias} (Base: {cdn_alias_base})")
        generate_dist_files(alias_dir, cdn_alias_base, spider_md5, open_md5, guard_src, open_src, live_src, token_src)

    # Create .nojekyll for GitHub Pages
    with open(DIST_DIR / ".nojekyll", "w") as f:
        pass

    # 5. Build History Table HTML for Web Portal
    history_rows = ""
    for idx, ver in enumerate(version_list):
        is_latest = (ver == current_tag)
        badge = '<span class="badge bg-success ms-2">最新版</span>' if is_latest else ''
        acc_open_url = f"{cdn_base}/{ver}/accelerated_open.json"
        aiwex_url = f"{cdn_base}/{ver}/aiwex.json"
        custom_url = f"{cdn_base}/{ver}/custom.json"
        pages_url = f"{pages_base}/{ver}/accelerated_open.json"
        rel_url = f"https://github.com/{repo}/releases/tag/{ver}"

        history_rows += f"""
        <tr>
            <td><strong>{ver}</strong> {badge}</td>
            <td>
                <div class="d-flex align-items-center mb-1">
                    <span class="badge bg-primary me-2">开源极速</span>
                    <input type="text" class="form-control form-control-sm bg-dark text-info border-secondary py-0 me-2" value="{acc_open_url}" id="h_acc_{idx}" readonly>
                    <button class="btn btn-sm btn-outline-info text-nowrap" onclick="copyVal('h_acc_{idx}')">复制</button>
                </div>
                <div class="d-flex align-items-center mb-1">
                    <span class="badge bg-secondary me-2">全能聚合</span>
                    <input type="text" class="form-control form-control-sm bg-dark text-light border-secondary py-0 me-2" value="{aiwex_url}" id="h_aiw_{idx}" readonly>
                    <button class="btn btn-sm btn-outline-secondary text-nowrap" onclick="copyVal('h_aiw_{idx}')">复制</button>
                </div>
            </td>
            <td>
                <a href="{rel_url}" target="_blank" class="btn btn-sm btn-outline-warning"><i class="fa-solid fa-download me-1"></i>Releases 归档</a>
            </td>
        </tr>
        """

    # 6. Generate Landing Page
    html_content = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TVBox / FongMi 影视源与网盘播放加速发布中心 ({current_tag})</title>
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
            <h1 class="display-5 fw-bold mb-3"><i class="fa-solid fa-tv text-primary me-2"></i> TVBox / FongMi 影视源与网盘加速 <span class="badge bg-primary fs-6 align-middle">{current_tag} 正式版</span></h1>
            <p class="lead text-light opacity-75">全能聚合影视源 + Go/SO 网盘播放多线程加速引擎，秒播 4K、零缓冲体验</p>
            <div class="mt-3">
                <span class="badge-stat"><i class="fa-solid fa-tag me-1"></i> Release {current_tag}</span>
                <span class="badge-stat"><i class="fa-solid fa-shield-halved me-1"></i> 多版本隔离共存</span>
                <span class="badge-stat"><i class="fa-solid fa-film me-1"></i> 全能源 96 个站点</span>
                <span class="badge-stat"><i class="fa-solid fa-bolt me-1"></i> 网盘 Range 多线程预加载</span>
                <span class="badge-stat"><i class="fa-solid fa-microchip me-1"></i> Go / SO 跨平台加速</span>
            </div>
        </div>

        <div class="row justify-content-center">
            <div class="col-lg-9">
                <!-- 1. 最新动态订阅链接 -->
                <div class="card card-custom p-4">
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <h4 class="mb-0 text-white"><i class="fa-solid fa-bolt text-warning me-2"></i> 最新动态订阅接口 (自动同步最新更新)</h4>
                        <span class="badge bg-success">自动跟随最新版</span>
                    </div>
                    <p class="text-secondary small mb-3">配置以下链接后，每次订阅发布更新时电视端将自动平滑静默升级，推荐日常使用：</p>
                    
                    <label class="form-label text-light fw-bold">1. 🌟 纯开源原生源 (100% Java 开源 Spider · 扫码配置+分集直解 · 推荐)</label>
                    <div class="code-box mb-3">
                        <span id="url_open">{cdn_base}/accelerated_open.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url_open')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">2. 🚀 全功能聚合源 (jsDelivr CDN 国内首选推荐)</label>
                    <div class="code-box mb-3">
                        <span id="url1">{cdn_base}/aiwex.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url1')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">3. ⚡ 网盘加速极速源 (Go/SO 预加载与自建 Spider)</label>
                    <div class="code-box mb-3">
                        <span id="url2">{cdn_base}/accelerated.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url2')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">4. 🌐 精简核心源 (17 核心精品站点)</label>
                    <div class="code-box mb-3">
                        <span id="url3">{cdn_base}/custom.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url3')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">5. 📡 GitHub Pages 官方线路</label>
                    <div class="code-box mb-2">
                        <span id="url4">{pages_base}/accelerated_open.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url4')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>
                </div>

                <!-- 1.5 1.0 独立固定版本专属订阅链接 (稳定不变) -->
                <div class="card card-custom p-4">
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <h4 class="mb-0 text-white"><i class="fa-solid fa-bookmark text-success me-2"></i> ⭐ 1.0 独立固定版本专属订阅接口 (锁定 1.0 版 · 永久稳定不变)</h4>
                        <span class="badge bg-success">1.0 独立固定版</span>
                    </div>
                    <p class="text-secondary small mb-3">为需要永久固定版本、不希望受后续大版本更新影响的用户特别提供，永久锁定 1.0 系列：</p>
                    
                    <label class="form-label text-light fw-bold">1. 🌟 1.0 独立开源极速源 (纯开源 Spider · 扫码配置 · 永久稳定 · 推荐)</label>
                    <div class="code-box mb-3">
                        <span id="url_v10_open">{cdn_base}/v1.0/accelerated_open.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url_v10_open')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">2. 🚀 1.0 独立全功能聚合源</label>
                    <div class="code-box mb-3">
                        <span id="url_v10_aiw">{cdn_base}/v1.0/aiwex.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url_v10_aiw')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">3. ⚡ 1.0 独立网盘极速源</label>
                    <div class="code-box mb-3">
                        <span id="url_v10_acc">{cdn_base}/v1.0/accelerated.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url_v10_acc')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">4. 🌐 1.0 独立精简核心源</label>
                    <div class="code-box mb-3">
                        <span id="url_v10_cus">{cdn_base}/v1.0/custom.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url_v10_cus')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>

                    <label class="form-label text-light fw-bold">5. 📡 1.0 GitHub Pages 官方线路</label>
                    <div class="code-box mb-2">
                        <span id="url_v10_pages">{pages_base}/v1.0/accelerated_open.json</span>
                        <button class="btn-copy ms-2" onclick="copyText('url_v10_pages')"><i class="fa-regular fa-copy me-1"></i>复制</button>
                    </div>
                </div>

                <!-- 2. 历史版本专属固定订阅链接 (多版本永久有效) -->
                <div class="card card-custom p-4">
                    <div class="d-flex justify-content-between align-items-center mb-2">
                        <h4 class="mb-0 text-white"><i class="fa-solid fa-clock-rotate-left text-info me-2"></i> 历史版本专属订阅 (独立隔离 · 永久有效不随更新变更)</h4>
                        <span class="badge bg-info">多版本并存</span>
                    </div>
                    <p class="text-secondary small mb-3">每次发布都会独立归档各版本资源文件，锁定版本号后不受后续任何更新影响：</p>
                    
                    <div class="table-responsive">
                        <table class="table table-dark table-hover table-borderless align-middle mb-0">
                            <thead>
                                <tr class="text-secondary small border-bottom border-secondary">
                                    <th style="width: 15%;">版本 Tag</th>
                                    <th style="width: 65%;">专属订阅链接 (点击直接复制)</th>
                                    <th style="width: 20%;">归档与下载</th>
                                </tr>
                            </thead>
                            <tbody>
                                {history_rows}
                            </tbody>
                        </table>
                    </div>
                </div>

                <!-- 3. 网盘加速引擎 -->
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
        function copyVal(elementId) {{
            const text = document.getElementById(elementId).value;
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
