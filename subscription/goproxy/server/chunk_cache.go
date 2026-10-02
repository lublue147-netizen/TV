package server

import (
	"container/list"
	"fmt"
	"sync"
)

// CacheEntry represents a cached data chunk in memory.
type CacheEntry struct {
	key       string
	streamID  string
	chunkIdx  int64
	data      []byte
	sizeBytes int64
}

// ChunkCache is a thread-safe LRU cache for video stream chunks.
type ChunkCache struct {
	mu          sync.RWMutex
	maxBytes    int64
	currBytes   int64
	items       map[string]*list.Element
	evictList   *list.List
	hitCount    int64
	missCount   int64
}

// NewChunkCache initializes a chunk cache with max memory limit in bytes.
func NewChunkCache(maxBytes int64) *ChunkCache {
	if maxBytes <= 0 {
		maxBytes = 64 * 1024 * 1024 // Default 64MB
	}
	return &ChunkCache{
		maxBytes:  maxBytes,
		items:     make(map[string]*list.Element),
		evictList: list.New(),
	}
}

func cacheKey(streamID string, chunkIdx int64) string {
	return fmt.Sprintf("%s#%d", streamID, chunkIdx)
}

// Get retrieves a chunk from cache if present.
func (c *ChunkCache) Get(streamID string, chunkIdx int64) ([]byte, bool) {
	key := cacheKey(streamID, chunkIdx)
	c.mu.Lock()
	defer c.mu.Unlock()

	if elem, ok := c.items[key]; ok {
		c.evictList.MoveToFront(elem)
		c.hitCount++
		entry := elem.Value.(*CacheEntry)
		return entry.data, true
	}

	c.missCount++
	return nil, false
}

// Put stores a chunk into cache, evicting oldest chunks if memory limit is reached.
func (c *ChunkCache) Put(streamID string, chunkIdx int64, data []byte) {
	key := cacheKey(streamID, chunkIdx)
	size := int64(len(data))
	if size > c.maxBytes {
		return // Chunk larger than entire cache
	}

	c.mu.Lock()
	defer c.mu.Unlock()

	// If already present, update and move to front
	if elem, ok := c.items[key]; ok {
		c.evictList.MoveToFront(elem)
		entry := elem.Value.(*CacheEntry)
		c.currBytes += size - entry.sizeBytes
		entry.data = data
		entry.sizeBytes = size
		return
	}

	// Evict until fits
	for c.currBytes+size > c.maxBytes && c.evictList.Len() > 0 {
		oldest := c.evictList.Back()
		if oldest != nil {
			c.removeElement(oldest)
		}
	}

	entry := &CacheEntry{
		key:       key,
		streamID:  streamID,
		chunkIdx:  chunkIdx,
		data:      data,
		sizeBytes: size,
	}
	elem := c.evictList.PushFront(entry)
	c.items[key] = elem
	c.currBytes += size
}

// Purge removes all chunks associated with a specific stream ID.
func (c *ChunkCache) Purge(streamID string) {
	c.mu.Lock()
	defer c.mu.Unlock()

	var toRemove []*list.Element
	for _, elem := range c.items {
		entry := elem.Value.(*CacheEntry)
		if entry.streamID == streamID {
			toRemove = append(toRemove, elem)
		}
	}

	for _, elem := range toRemove {
		c.removeElement(elem)
	}
}

func (c *ChunkCache) removeElement(elem *list.Element) {
	c.evictList.Remove(elem)
	entry := elem.Value.(*CacheEntry)
	delete(c.items, entry.key)
	c.currBytes -= entry.sizeBytes
}

// Stats returns cache statistics.
func (c *ChunkCache) Stats() (currBytes, maxBytes, hits, misses int64) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.currBytes, c.maxBytes, c.hitCount, c.missCount
}
