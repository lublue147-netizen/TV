package server

import (
	"net/http"
	"strings"
)

// ApplyNetdiskRules enriches HTTP request headers based on target netdisk domain.
func ApplyNetdiskRules(req *http.Request, rawURL string) {
	lowerURL := strings.ToLower(rawURL)

	// Quark Pan rules
	if strings.Contains(lowerURL, "quark.cn") {
		if req.Header.Get("User-Agent") == "" || strings.Contains(req.Header.Get("User-Agent"), "ExoPlayer") {
			req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
		}
		if req.Header.Get("Referer") == "" {
			req.Header.Set("Referer", "https://pan.quark.cn/")
		}
		return
	}

	// Aliyun Pan / OSS rules
	if strings.Contains(lowerURL, "aliyundrive") || strings.Contains(lowerURL, "alipan") || strings.Contains(lowerURL, "aliyuncs.com") {
		if req.Header.Get("Referer") == "" {
			req.Header.Set("Referer", "https://www.aliyundrive.com/")
		}
		if req.Header.Get("User-Agent") == "" || strings.Contains(req.Header.Get("User-Agent"), "ExoPlayer") {
			req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
		}
		return
	}

	// 115 Pan rules
	if strings.Contains(lowerURL, "115.com") || strings.Contains(lowerURL, "anxia.com") {
		if req.Header.Get("User-Agent") == "" || strings.Contains(req.Header.Get("User-Agent"), "ExoPlayer") {
			req.Header.Set("User-Agent", "Mozilla/5.0 115disk/30.1.0")
		}
		if req.Header.Get("Referer") == "" {
			req.Header.Set("Referer", "https://115.com/")
		}
		return
	}

	// Baidu Netdisk rules
	if strings.Contains(lowerURL, "baidupcs.com") || strings.Contains(lowerURL, "pan.baidu.com") {
		if req.Header.Get("User-Agent") == "" || strings.Contains(req.Header.Get("User-Agent"), "ExoPlayer") {
			req.Header.Set("User-Agent", "pan.baidu.com")
		}
		return
	}

	// Default fallback User-Agent for standard streaming if ExoPlayer is detected
	if req.Header.Get("User-Agent") == "" {
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
	}
}
