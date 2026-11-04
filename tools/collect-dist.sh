#!/usr/bin/env bash
# Gathers what a build made into one folder that tools/make-site.sh reads:
#   <dist>/android/<app>-debug.apk
#   <dist>/desktop/<app>/<app>-<versionCode>-<platform>.<dmg|msi|deb>
# Each CI job collects its own platform; the site job merges them.
# Usage: tools/collect-dist.sh <dist-dir> <platform>   (e.g. dist macos-x64)
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/version-env.sh

dist=${1:?usage: tools/collect-dist.sh <dist-dir> <platform>}
platform=${2:?usage: tools/collect-dist.sh <dist-dir> <platform>}

for apk in apps/*/build/outputs/apk/debug/*-debug.apk; do
    [ -f "$apk" ] || continue
    mkdir -p "$dist/android"
    cp "$apk" "$dist/android/"
done

for dir in desktop/*/; do
    app=$(basename "$dir")
    for installer in "$dir"build/compose/binaries/main/*/*.{dmg,msi,deb}; do
        [ -f "$installer" ] || continue
        mkdir -p "$dist/desktop/$app"
        cp "$installer" "$dist/desktop/$app/$app-$APP_VERSION_CODE-$platform.${installer##*.}"
    done
done

find "$dist" -type f | sort
