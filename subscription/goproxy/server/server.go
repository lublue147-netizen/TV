package server

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"log"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"sync"
	"time"
)

// Server encapsulates the local GoProxy HTTP server.
type Server struct {
	mu          sync.Mutex
	httpServer  *http.Server
	listener    net.Listener
	port        int
	accelerator *Accelerator
	cache       *ChunkCache
	startTime   time.Time
}

var DefaultServer = NewServer(64 * 1024 * 1024) // 64MB cache

// NewServer creates a new Server instance.
func NewServer(cacheBytes int64) *Server {
	cache := NewChunkCache(cacheBytes)
	acc := NewAccelerator(cache)
	return &Server{
		cache:       cache,
		accelerator: acc,
		startTime:   time.Now(),
	}
}

// Start launches the HTTP server on specified port (or auto-selects if 0).
func (s *Server) Start(port int) int {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.httpServer != nil && s.port > 0 {
		return s.port // Already running
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/play", s.handlePlay)
	mux.HandleFunc("/proxy", s.handleProxy)
	mux.HandleFunc("/health", s.handleHealth)
	mux.HandleFunc("/stats", s.handleStats)

	addr := fmt.Sprintf("127.0.0.1:%d", port)
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		// If specified port fails, try dynamic port
		ln, err = net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			log.Printf("[GoProxy] Failed to bind: %v", err)
			return -1
		}
	}

	s.listener = ln
	s.port = ln.Addr().(*net.TCPAddr).Port
	s.httpServer = &http.Server{
		Handler:      mux,
		ReadTimeout:  120 * time.Second,
		WriteTimeout: 0, // Streaming responses should not have write timeout
	}

	go func() {
		log.Printf("[GoProxy] Netdisk Acceleration Server started on http://127.0.0.1:%d", s.port)
		if err := s.httpServer.Serve(ln); err != nil && err != http.ErrServerClosed {
			log.Printf("[GoProxy] Server error: %v", err)
		}
	}()

	return s.port
}

// Stop gracefully shuts down the server.
func (s *Server) Stop() {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.httpServer != nil {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
		defer cancel()
		_ = s.httpServer.Shutdown(ctx)
		s.httpServer = nil
		s.listener = nil
		s.port = 0
		log.Println("[GoProxy] Server stopped")
	}
}

// GetPort returns the currently bound port.
func (s *Server) GetPort() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.port
}

// GetProxyUrl constructs an accelerated URL for the given video URL.
func (s *Server) GetProxyUrl(rawURL string) string {
	p := s.GetPort()
	if p <= 0 {
		return rawURL
	}
	return fmt.Sprintf("http://127.0.0.1:%d/play?url=%s", p, url.QueryEscape(rawURL))
}

func (s *Server) handlePlay(w http.ResponseWriter, r *http.Request) {
	rawURL := r.URL.Query().Get("url")
	if rawURL == "" {
		http.Error(w, "Missing 'url' query parameter", http.StatusBadRequest)
		return
	}

	// Support base64 encoded URL
	if b64 := r.URL.Query().Get("b64"); b64 == "1" || b64 == "true" {
		if decoded, err := base64.StdEncoding.DecodeString(rawURL); err == nil {
			rawURL = string(decoded)
		}
	}

	// Parse custom headers
	customHeaders := make(map[string]string)
	if hJSON := r.URL.Query().Get("headers"); hJSON != "" {
		_ = json.Unmarshal([]byte(hJSON), &customHeaders)
	}

	workers := DefaultWorkers
	if wStr := r.URL.Query().Get("workers"); wStr != "" {
		if parsedW, err := strconv.Atoi(wStr); err == nil && parsedW > 0 && parsedW <= 8 {
			workers = parsedW
		}
	}

	s.accelerator.ServeStream(w, r, rawURL, customHeaders, workers)
}

func (s *Server) handleProxy(w http.ResponseWriter, r *http.Request) {
	rawURL := r.URL.Query().Get("url")
	if rawURL == "" {
		http.Error(w, "Missing 'url' parameter", http.StatusBadRequest)
		return
	}
	customHeaders := make(map[string]string)
	if hJSON := r.URL.Query().Get("headers"); hJSON != "" {
		_ = json.Unmarshal([]byte(hJSON), &customHeaders)
	}

	s.accelerator.directProxy(w, r, rawURL, customHeaders)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	uptime := time.Since(s.startTime).String()
	res := map[string]interface{}{
		"status":  "ok",
		"service": "GoProxy Netdisk Accelerator",
		"port":    s.GetPort(),
		"uptime":  uptime,
	}
	_ = json.NewEncoder(w).Encode(res)
}

func (s *Server) handleStats(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	curr, max, hits, misses := s.cache.Stats()
	hitRate := 0.0
	if hits+misses > 0 {
		hitRate = float64(hits) / float64(hits+misses) * 100
	}
	res := map[string]interface{}{
		"cache_current_bytes": curr,
		"cache_max_bytes":     max,
		"cache_hits":          hits,
		"cache_misses":        misses,
		"cache_hit_rate_pct":  fmt.Sprintf("%.2f%%", hitRate),
		"port":                s.GetPort(),
	}
	_ = json.NewEncoder(w).Encode(res)
}
