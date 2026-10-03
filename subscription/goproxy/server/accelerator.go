package server

import (
	"context"
	"crypto/md5"
	"encoding/hex"
	"fmt"
	"io"
	"log"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	DefaultChunkSize = 2 * 1024 * 1024 // 2MB chunk
	DefaultWorkers   = 3               // Concurrently prefetch 3 chunks ahead
)

// StreamMeta holds basic info about the upstream media stream.
type StreamMeta struct {
	TotalBytes   int64
	AcceptRanges bool
	ContentType  string
	ResolvedURL  string
}

// Accelerator manages accelerated Range-based video playback.
type Accelerator struct {
	cache      *ChunkCache
	httpClient *http.Client
	metaMap    sync.Map // targetURL -> *StreamMeta
	inflight   sync.Map // streamID#chunkIdx -> chan struct{}
}

// NewAccelerator creates a new acceleration engine with memory chunk cache.
func NewAccelerator(cache *ChunkCache) *Accelerator {
	transport := &http.Transport{
		MaxIdleConns:        100,
		MaxIdleConnsPerHost: 20,
		IdleConnTimeout:     90 * time.Second,
		DisableCompression:  true, // Video files are already compressed
	}
	return &Accelerator{
		cache: cache,
		httpClient: &http.Client{
			Transport: transport,
			Timeout:   60 * time.Second,
		},
	}
}

func getStreamID(targetURL string) string {
	h := md5.Sum([]byte(targetURL))
	return hex.EncodeToString(h[:8])
}

// InspectStream queries the upstream URL to inspect Range support and total length.
func (a *Accelerator) InspectStream(ctx context.Context, targetURL string, headers map[string]string) (*StreamMeta, error) {
	if val, ok := a.metaMap.Load(targetURL); ok {
		return val.(*StreamMeta), nil
	}

	// First try HEAD
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, targetURL, nil)
	if err != nil {
		return nil, err
	}
	ApplyNetdiskRules(req, targetURL)
	for k, v := range headers {
		req.Header.Set(k, v)
	}

	resp, err := a.httpClient.Do(req)
	var meta *StreamMeta

	if err == nil && (resp.StatusCode == http.StatusOK || resp.StatusCode == http.StatusPartialContent) {
		cl := resp.ContentLength
		cType := resp.Header.Get("Content-Type")
		if cType == "" {
			cType = "video/mp4"
		}
		ranges := resp.Header.Get("Accept-Ranges") == "bytes" || resp.Header.Get("Content-Range") != ""
		meta = &StreamMeta{
			TotalBytes:   cl,
			AcceptRanges: ranges,
			ContentType:  cType,
			ResolvedURL:  resp.Request.URL.String(),
		}
		resp.Body.Close()
	}

	// If HEAD failed or gave no length, try GET with Range bytes=0-0
	if meta == nil || meta.TotalBytes <= 0 {
		reqGet, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
		if err == nil {
			ApplyNetdiskRules(reqGet, targetURL)
			for k, v := range headers {
				reqGet.Header.Set(k, v)
			}
			reqGet.Header.Set("Range", "bytes=0-0")
			respGet, errGet := a.httpClient.Do(reqGet)
			if errGet == nil {
				defer respGet.Body.Close()
				cType := respGet.Header.Get("Content-Type")
				if cType == "" {
					cType = "video/mp4"
				}
				cr := respGet.Header.Get("Content-Range")
				var total int64 = -1
				if cr != "" {
					// bytes 0-0/12345678
					parts := strings.Split(cr, "/")
					if len(parts) == 2 {
						total, _ = strconv.ParseInt(parts[1], 10, 64)
					}
				}
				meta = &StreamMeta{
					TotalBytes:   total,
					AcceptRanges: respGet.StatusCode == http.StatusPartialContent && cr != "",
					ContentType:  cType,
					ResolvedURL:  respGet.Request.URL.String(),
				}
			}
		}
	}

	if meta == nil {
		meta = &StreamMeta{
			TotalBytes:   -1,
			AcceptRanges: false,
			ContentType:  "video/mp4",
			ResolvedURL:  targetURL,
		}
	}

	a.metaMap.Store(targetURL, meta)
	return meta, nil
}

// ServeStream handles the accelerated chunk streaming for an incoming client request.
func (a *Accelerator) ServeStream(w http.ResponseWriter, r *http.Request, targetURL string, customHeaders map[string]string, workers int) {
	streamID := getStreamID(targetURL)
	meta, err := a.InspectStream(r.Context(), targetURL, customHeaders)
	if err != nil {
		http.Error(w, fmt.Sprintf("Upstream inspect failed: %v", err), http.StatusBadGateway)
		return
	}

	// Fallback to direct pipe if Range is not supported or total bytes unknown
	if !meta.AcceptRanges || meta.TotalBytes <= 0 {
		a.directProxy(w, r, targetURL, customHeaders)
		return
	}

	rangeHeader := r.Header.Get("Range")
	start, end := int64(0), meta.TotalBytes-1
	isPartial := false

	if rangeHeader != "" && strings.HasPrefix(rangeHeader, "bytes=") {
		isPartial = true
		parts := strings.Split(strings.TrimPrefix(rangeHeader, "bytes="), "-")
		if len(parts) >= 1 && parts[0] != "" {
			start, _ = strconv.ParseInt(parts[0], 10, 64)
		}
		if len(parts) >= 2 && parts[1] != "" {
			end, _ = strconv.ParseInt(parts[1], 10, 64)
		}
		if end >= meta.TotalBytes {
			end = meta.TotalBytes - 1
		}
		if start > end || start < 0 {
			w.Header().Set("Content-Range", fmt.Sprintf("bytes */%d", meta.TotalBytes))
			http.Error(w, "Requested Range Not Satisfiable", http.StatusRequestedRangeNotSatisfiable)
			return
		}
	}

	contentLength := end - start + 1
	w.Header().Set("Content-Type", meta.ContentType)
	w.Header().Set("Accept-Ranges", "bytes")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if isPartial {
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, end, meta.TotalBytes))
		w.Header().Set("Content-Length", strconv.FormatInt(contentLength, 10))
		w.WriteHeader(http.StatusPartialContent)
	} else {
		w.Header().Set("Content-Length", strconv.FormatInt(contentLength, 10))
		w.WriteHeader(http.StatusOK)
	}

	if r.Method == http.MethodHead {
		return
	}

	// Sliced streaming
	chunkSize := int64(DefaultChunkSize)
	firstChunk := start / chunkSize
	lastChunk := end / chunkSize

	if workers <= 0 {
		workers = DefaultWorkers
	}

	flusher, _ := w.(http.Flusher)

	for c := firstChunk; c <= lastChunk; c++ {
		select {
		case <-r.Context().Done():
			return // Client cancelled or closed playback
		default:
		}

		// Trigger prefetch for upcoming chunks
		for p := int64(1); p <= int64(workers); p++ {
			nextC := c + p
			if nextC*chunkSize < meta.TotalBytes {
				go a.prefetchChunk(streamID, targetURL, nextC, chunkSize, meta.TotalBytes, customHeaders)
			}
		}

		// Fetch current chunk
		chunkData, err := a.fetchOrGetChunk(r.Context(), streamID, targetURL, c, chunkSize, meta.TotalBytes, customHeaders)
		if err != nil {
			log.Printf("[Accelerator] Chunk %d fetch failed: %v", c, err)
			return
		}

		// Determine slice within this chunk
		chunkStartByte := c * chunkSize
		sliceStart := int64(0)
		if c == firstChunk {
			sliceStart = start - chunkStartByte
		}
		sliceEnd := int64(len(chunkData))
		if c == lastChunk {
			sliceEnd = end - chunkStartByte + 1
		}

		if sliceStart < 0 {
			sliceStart = 0
		}
		if sliceEnd > int64(len(chunkData)) {
			sliceEnd = int64(len(chunkData))
		}

		if sliceStart < sliceEnd {
			_, writeErr := w.Write(chunkData[sliceStart:sliceEnd])
			if writeErr != nil {
				return // Client disconnected
			}
			if flusher != nil {
				flusher.Flush()
			}
		}
	}
}

// fetchOrGetChunk returns cached chunk or downloads it.
func (a *Accelerator) fetchOrGetChunk(ctx context.Context, streamID, targetURL string, chunkIdx, chunkSize, totalBytes int64, headers map[string]string) ([]byte, error) {
	if data, hit := a.cache.Get(streamID, chunkIdx); hit {
		return data, nil
	}

	// Deduplicate concurrent fetches for the same chunk
	key := fmt.Sprintf("%s#%d", streamID, chunkIdx)
	waitCh := make(chan struct{})
	actual, loaded := a.inflight.LoadOrStore(key, waitCh)
	if loaded {
		// Another goroutine is already fetching this chunk
		select {
		case <-actual.(chan struct{}):
			if data, hit := a.cache.Get(streamID, chunkIdx); hit {
				return data, nil
			}
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	} else {
		defer func() {
			a.inflight.Delete(key)
			close(waitCh)
		}()
	}

	// Check cache again after lock
	if data, hit := a.cache.Get(streamID, chunkIdx); hit {
		return data, nil
	}

	return a.downloadChunk(ctx, streamID, targetURL, chunkIdx, chunkSize, totalBytes, headers)
}

func (a *Accelerator) prefetchChunk(streamID, targetURL string, chunkIdx, chunkSize, totalBytes int64, headers map[string]string) {
	if _, hit := a.cache.Get(streamID, chunkIdx); hit {
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
	defer cancel()
	_, _ = a.fetchOrGetChunk(ctx, streamID, targetURL, chunkIdx, chunkSize, totalBytes, headers)
}

func (a *Accelerator) downloadChunk(ctx context.Context, streamID, targetURL string, chunkIdx, chunkSize, totalBytes int64, headers map[string]string) ([]byte, error) {
	startByte := chunkIdx * chunkSize
	endByte := startByte + chunkSize - 1
	if endByte >= totalBytes {
		endByte = totalBytes - 1
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, err
	}
	ApplyNetdiskRules(req, targetURL)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	req.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", startByte, endByte))

	resp, err := a.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusPartialContent {
		return nil, fmt.Errorf("range request returned status %d", resp.StatusCode)
	}

	contentRange := resp.Header.Get("Content-Range")
	expectedPrefix := fmt.Sprintf("bytes %d-", startByte)
	if !strings.HasPrefix(contentRange, expectedPrefix) {
		return nil, fmt.Errorf("invalid Content-Range %q, expected prefix %q", contentRange, expectedPrefix)
	}

	data, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	a.cache.Put(streamID, chunkIdx, data)
	return data, nil
}

func (a *Accelerator) directProxy(w http.ResponseWriter, r *http.Request, targetURL string, headers map[string]string) {
	req, err := http.NewRequestWithContext(r.Context(), r.Method, targetURL, nil)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	ApplyNetdiskRules(req, targetURL)
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	if rng := r.Header.Get("Range"); rng != "" {
		req.Header.Set("Range", rng)
	}

	resp, err := a.httpClient.Do(req)
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	for k, v := range resp.Header {
		for _, val := range v {
			w.Header().Add(k, val)
		}
	}
	w.WriteHeader(resp.StatusCode)
	io.Copy(w, resp.Body)
}
