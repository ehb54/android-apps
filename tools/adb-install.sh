#!/usr/bin/env bash
# Install an app's debug APK from out/ on the phone attached to adb (USB, or
# Wi-Fi after "adb tcpip"). Replaces the installed copy and keeps its data.
# Usage: tools/adb-install.sh [app]     (default: starter)
# Set ADB in tools/local.env if adb is not on PATH.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f tools/local.env ]; then
    . tools/local.env
fi
ADB=${ADB:-adb}
apk=out/${1:-starter}-debug.apk

if [ ! -f "$apk" ]; then
    echo "No $apk: run tools/remote-build.sh first" >&2
    exit 1
fi
"$ADB" install -r "$apk"
