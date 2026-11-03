#!/usr/bin/env bash
# From a laptop: copy this checkout (with .git, for the version numbers) to a
# Docker host over ssh, build there with tools/docker-build.sh, and bring the
# debug APKs back to out/.
# Set BUILD_HOST (an ssh alias) in tools/local.env, which git ignores.
# Arguments are passed to tools/ci-build.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f tools/local.env ]; then
    . tools/local.env
fi
: "${BUILD_HOST:?set BUILD_HOST in tools/local.env (see CLAUDE.md)}"
REMOTE_DIR=${REMOTE_DIR:-claude/android-apps}   # relative to the remote home

rsync -a --delete \
    --exclude build/ --exclude .gradle/ --exclude .kotlin/ \
    --exclude /out/ --exclude /site/ --exclude local.properties --exclude /tools/local.env \
    ./ "$BUILD_HOST:$REMOTE_DIR/"

ssh "$BUILD_HOST" "$REMOTE_DIR/tools/docker-build.sh" "$@"

# Only apps that exist here: the remote keeps build/ of apps deleted since.
rm -rf out
mkdir out
for build_file in apps/*/build.gradle.kts; do
    app=$(basename "$(dirname "$build_file")")
    scp -q "$BUILD_HOST:$REMOTE_DIR/apps/$app/build/outputs/apk/debug/$app-debug.apk" out/ \
        || echo "No debug APK for $app" >&2
done
ls -l out
