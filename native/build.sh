#!/usr/bin/env bash
set -euo pipefail

NDK="/home/vend/Android/Sdk/ndk/29.0.14206865"
CMAKE="/home/vend/Android/Sdk/cmake/4.1.2/bin/cmake"
MAKE="$(command -v make || echo /nix/store/d3bwqm6bymhy3pdgbvf7vxjqfp31m3j1-gnumake-4.4.1/bin/make)"
CT2_SRC="/tmp/ctranslate2-src"
CT2_BUILD="/tmp/ct2-static-build"
PROJECT_ROOT="$(dirname "$0")/.."
CPP_DIR="$PROJECT_ROOT/app/src/main/cpp"
LIB_DIR="$CPP_DIR/lib/arm64-v8a"

build_ct2() {
    if [ -f "$CT2_BUILD/libctranslate2.a" ]; then
        echo "libctranslate2.a already built, skipping"
        return
    fi

    if [ ! -d "$CT2_SRC" ]; then
        echo "Cloning CTranslate2 source..."
        git clone --recursive https://github.com/OpenNMT/CTranslate2.git "$CT2_SRC"
    fi

    if ! grep -q '__ANDROID__' "$CT2_SRC/src/thread_pool.cc"; then
        sed -i 's/#if !defined(__linux__) || defined(_OPENMP)/#if !defined(__linux__) || defined(_OPENMP) || defined(__ANDROID__)/' "$CT2_SRC/src/thread_pool.cc"
        echo "Patched thread_pool.cc for Android"
    fi

    mkdir -p "$CT2_BUILD"
    "$CMAKE" \
        -S "$CT2_SRC" -B "$CT2_BUILD" \
        -DCMAKE_BUILD_TYPE=MinSizeRel \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-24 \
        -DWITH_MKL=OFF -DWITH_CUDA=OFF -DWITH_OPENBLAS=OFF -DWITH_RUY=ON \
        -DBUILD_CLI=OFF -DOPENMP_RUNTIME=NONE \
        -DBUILD_SHARED_LIBS=OFF \
        -DCMAKE_C_FLAGS="-fPIC -ffunction-sections -fdata-sections -ftls-model=global-dynamic -Os" \
        -DCMAKE_CXX_FLAGS="-fPIC -ffunction-sections -fdata-sections -ftls-model=global-dynamic -Os" \
        -DCMAKE_MAKE_PROGRAM="$MAKE" \
        -DCMAKE_POLICY_VERSION_MINIMUM=3.5
    "$MAKE" -C "$CT2_BUILD" -j"$(nproc)"
    echo "Built $CT2_BUILD/libctranslate2.a"
}

copy_ct2() {
    mkdir -p "$LIB_DIR/ruy/profiler" "$LIB_DIR/cpuinfo" "$LIB_DIR/clog"
    cp "$CT2_BUILD/libctranslate2.a" "$LIB_DIR/"
    cp "$CT2_BUILD/third_party/ruy/ruy/"*.a "$LIB_DIR/ruy/"
    cp "$CT2_BUILD/third_party/ruy/ruy/profiler/"*.a "$LIB_DIR/ruy/profiler/"
    cp "$CT2_BUILD/third_party/ruy/third_party/cpuinfo/"*.a "$LIB_DIR/cpuinfo/"
    cp "$CT2_BUILD/third_party/ruy/third_party/cpuinfo/deps/clog/"*.a "$LIB_DIR/clog/"
    echo "Copied static libraries to cpp/lib/arm64-v8a/"
}

update_headers() {
    mkdir -p "$CPP_DIR/include/ctranslate2" "$CPP_DIR/include/half_float" "$CPP_DIR/include/nlohmann"
    cp -r "$CT2_SRC/include/ctranslate2/"*.h "$CPP_DIR/include/ctranslate2/"
    cp -r "$CT2_SRC/third_party/half_float/"*.hpp "$CPP_DIR/include/half_float/"
    cp -r "$CT2_SRC/third_party/nlohmann/"*.hpp "$CPP_DIR/include/nlohmann/"
    echo "Updated headers in cpp/include/"
}

case "${1:-all}" in
    ct2)      build_ct2 ;;
    copy)     copy_ct2 ;;
    headers)  update_headers ;;
    all)      build_ct2; copy_ct2; update_headers ;;
    *)        echo "Usage: $0 [ct2|headers|copy|all]"; exit 1 ;;
esac
