package main

import (
	"flag"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/lublue147-netizen/subscription/goproxy/server"
)

var (
	version = "1.0.0"
)

func main() {
	portFlag := flag.Int("port", 9978, "Port to listen on (default 9978)")
	cacheFlag := flag.Int64("cache", 64, "Memory cache size in MB (default 64MB)")
	versionFlag := flag.Bool("version", false, "Print version and exit")
	flag.Parse()

	if *versionFlag {
		fmt.Printf("GoProxy Netdisk Accelerator v%s\n", version)
		os.Exit(0)
	}

	cacheBytes := *cacheFlag * 1024 * 1024
	srv := server.NewServer(cacheBytes)
	actualPort := srv.Start(*portFlag)
	if actualPort <= 0 {
		log.Fatalf("[GoProxy] Failed to start server on port %d", *portFlag)
	}

	log.Printf("[GoProxy] v%s running on http://127.0.0.1:%d (Cache: %dMB)", version, actualPort, *cacheFlag)
	log.Printf("[GoProxy] Endpoints:")
	log.Printf("  - Accelerated Stream: http://127.0.0.1:%d/play?url=<URL>", actualPort)
	log.Printf("  - Direct Proxy:       http://127.0.0.1:%d/proxy?url=<URL>", actualPort)
	log.Printf("  - Health Check:       http://127.0.0.1:%d/health", actualPort)
	log.Printf("  - Runtime Stats:      http://127.0.0.1:%d/stats", actualPort)

	// Wait for termination signal
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, os.Interrupt, syscall.SIGTERM)
	<-sigCh

	log.Println("[GoProxy] Shutting down...")
	srv.Stop()
	log.Println("[GoProxy] Exited cleanly")
}
