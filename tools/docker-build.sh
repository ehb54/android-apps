#!/usr/bin/env bash
# Build inside the image from docker/Dockerfile on this machine (any Docker host).
# The image is tagged by SDK level and build-tools, so a version bump in
# gradle/libs.versions.toml builds a new one. Gradle and Android caches persist
# in STATE_DIR (default: android-apps-state next to this checkout).
# Arguments are passed to tools/ci-build.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

STATE_DIR=${STATE_DIR:-$(cd .. && pwd)/android-apps-state}

toml() {
    sed -n "s/^$1 *= *\"\([^\"]*\)\".*/\1/p" gradle/libs.versions.toml
}
platform=$(toml compileSdk)
# From API 37 the SDK platform packages carry a minor version: 37 -> android-37.0
if [[ $platform != *.* ]] && [ "$platform" -ge 37 ]; then
    platform=$platform.0
fi
build_tools=$(toml androidBuildTools)
image=android-apps-build:sdk$platform-bt$build_tools

docker build --quiet --tag "$image" \
    --build-arg PLATFORM="android-$platform" \
    --build-arg BUILD_TOOLS="$build_tools" \
    docker > /dev/null

mkdir -p "$STATE_DIR/home" "$STATE_DIR/gradle" "$STATE_DIR/android"
docker run --rm --user "$(id -u):$(id -g)" \
    -e HOME=/state/home \
    -e GRADLE_USER_HOME=/state/gradle \
    -e ANDROID_USER_HOME=/state/android \
    -v "$PWD:/src" -v "$STATE_DIR:/state" -w /src \
    "$image" tools/ci-build.sh "$@"
