#!/usr/bin/env bash
# The one build recipe every environment runs: lint, unit tests, debug APKs and
# release bundles for all modules. GitHub Actions calls it directly; Docker hosts
# call it through tools/docker-build.sh.
# Arguments replace the default task list, e.g. :apps:starter:assembleDebug
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/version-env.sh

tasks=(lint testDebugUnitTest assembleDebug bundleRelease)
if [ $# -gt 0 ]; then
    tasks=("$@")
fi

echo "Building $APP_VERSION_NAME (versionCode $APP_VERSION_CODE, $APP_BUILD_TIME): ${tasks[*]}"
./gradlew "${tasks[@]}" \
    -PappVersionCode="$APP_VERSION_CODE" \
    -PappVersionName="$APP_VERSION_NAME" \
    -PappBuildTime="$APP_BUILD_TIME"
