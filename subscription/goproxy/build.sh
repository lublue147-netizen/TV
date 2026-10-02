#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

OUT_DIR="$DIR/bin"
mkdir -p "$OUT_DIR"

echo "=== Building GoProxy Static Executables ==="

# 1. Android / Linux ARM64
echo "-> Building goproxy-android-arm64..."
CGO_ENABLED=0 GOOS=linux GOARCH=arm64 go build -ldflags="-s -w" -o "$OUT_DIR/goproxy-android-arm64" .

# 2. Android / Linux ARMv7 (32-bit)
echo "-> Building goproxy-android-armv7..."
CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 go build -ldflags="-s -w" -o "$OUT_DIR/goproxy-android-armv7" .

# 3. Linux x86_64
echo "-> Building goproxy-linux-amd64..."
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -ldflags="-s -w" -o "$OUT_DIR/goproxy-linux-amd64" .

# 4. Windows x86_64
echo "-> Building goproxy-windows-amd64.exe..."
CGO_ENABLED=0 GOOS=windows GOARCH=amd64 go build -ldflags="-s -w" -o "$OUT_DIR/goproxy-windows-amd64.exe" .

# 5. Android SO Shared Libraries via NDK (if NDK is set)
NDK_PATH="${ANDROID_NDK_HOME:-${ANDROID_NDK_LATEST_HOME:-$NDK}}"
if [ -z "$NDK_PATH" ] && [ -d "$ANDROID_HOME/ndk" ]; then
    NDK_PATH=$(find "$ANDROID_HOME/ndk" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)
fi

if [ -n "$NDK_PATH" ] && [ -d "$NDK_PATH" ]; then
    echo "=== Found Android NDK at: $NDK_PATH ==="
    TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64/bin"
    API_LEVEL=24

    # Build arm64-v8a SO
    CC_ARM64="$TOOLCHAIN/aarch64-linux-android${API_LEVEL}-clang"
    if [ -f "$CC_ARM64" ]; then
        echo "-> Compiling libgoproxy.so (arm64-v8a)..."
        mkdir -p "$OUT_DIR/arm64-v8a"
        CGO_ENABLED=1 GOOS=android GOARCH=arm64 CC="$CC_ARM64" \
            go build -buildmode=c-shared -ldflags="-s -w" -o "$OUT_DIR/arm64-v8a/libgoproxy.so" .
    fi

    # Build armeabi-v7a SO
    CC_ARM="$TOOLCHAIN/armv7a-linux-androideabi${API_LEVEL}-clang"
    if [ -f "$CC_ARM" ]; then
        echo "-> Compiling libgoproxy.so (armeabi-v7a)..."
        mkdir -p "$OUT_DIR/armeabi-v7a"
        CGO_ENABLED=1 GOOS=android GOARCH=arm GOARM=7 CC="$CC_ARM" \
            go build -buildmode=c-shared -ldflags="-s -w" -o "$OUT_DIR/armeabi-v7a/libgoproxy.so" .
    fi
else
    echo "Notice: Android NDK not detected. Native .so compilation skipped (will be compiled in GitHub Actions CI)."
fi

echo "=== Build completed! Artifacts in $OUT_DIR ==="
ls -lh "$OUT_DIR"
