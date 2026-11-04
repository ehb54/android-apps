#!/usr/bin/env bash
# The one build recipe every environment runs: lint, unit tests, debug APKs,
# release bundles, and the desktop apps' installers for this OS, for all modules.
# GitHub Actions calls it directly; Docker hosts call it through tools/docker-build.sh.
# Arguments replace the default task list, e.g. :apps:starter:assembleDebug;
# "desktop" alone builds only the desktop apps (see settings.gradle.kts, desktopOnly).
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/version-env.sh

# unitTests: each module's own unit tests (testDebugUnitTest on Android, test on the JVM).
tasks=(lint unitTests assembleDebug bundleRelease packageDistributionForCurrentOS)
extra=()
if [ "${1:-}" = desktop ]; then
    # Desktop apps only (CI's macOS and Windows jobs): their tests and this OS's installers.
    tasks=(unitTests packageDistributionForCurrentOS)
    extra=(-PdesktopOnly)
elif [ $# -gt 0 ]; then
    tasks=("$@")
fi

echo "Building $APP_VERSION_NAME (versionCode $APP_VERSION_CODE, $APP_BUILD_TIME): ${tasks[*]}"
./gradlew "${tasks[@]}" ${extra[@]+"${extra[@]}"} \
    -PappVersionCode="$APP_VERSION_CODE" \
    -PappVersionName="$APP_VERSION_NAME" \
    -PappBuildTime="$APP_BUILD_TIME"
