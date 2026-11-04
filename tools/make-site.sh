#!/usr/bin/env bash
# Assemble the GitHub Pages download site from the builds tools/collect-dist.sh
# gathered:
#   <site>/index.html               list of apps
#   <site>/<app>/index.html         download page: Android APK, desktop installers
#   <site>/<app>/<app>-<code>.apk   the APK, named by versionCode so a cached
#                                   page never points at a newer file
#   <site>/<app>/<app>-<code>-<platform>.<dmg|msi|deb>   desktop installers
#   <site>/<app>/samples/           the app's sample files and catalog.json
# Usage: tools/make-site.sh [site-dir] [dist-dir]     (defaults: site, dist)
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/version-env.sh

site=${1:-site}
dist=${2:-dist}
rm -rf "$site"
mkdir -p "$site"
touch "$site/.nojekyll"

style='body{font-family:system-ui,sans-serif;max-width:34rem;margin:2rem auto;padding:0 1rem;line-height:1.5;color:#1b1b1b;background:#fafafa}
a.button{display:block;text-align:center;padding:1rem;border-radius:.75rem;background:#1e6b52;color:#fff;text-decoration:none;font-size:1.2rem}
li.button{list-style:none;margin:.5rem 0}ul.buttons{padding:0}
small{color:#555}
@media (prefers-color-scheme:dark){body{background:#121212;color:#e6e6e6}small{color:#aaa}a{color:#8fd3b6}a.button{color:#fff}}'

# Platform name in installer file names -> what the page calls it.
platform_label() {
    case $1 in
        macos-arm64) echo "macOS, Apple silicon (M1 and later)" ;;
        macos-x64) echo "macOS, Intel" ;;
        windows-x64) echo "Windows" ;;
        linux-x64) echo "Linux (Debian, Ubuntu)" ;;
        *) echo "$1" ;;
    esac
}

apps=$(
    {
        for apk in "$dist"/android/*-debug.apk; do [ -f "$apk" ] && basename "$apk" -debug.apk; done
        for dir in "$dist"/desktop/*/; do [ -d "$dir" ] && basename "$dir"; done
    } | sort -u
)

items=
for app in $apps; do
    mkdir -p "$site/$app"

    android=
    if [ -f "$dist/android/$app-debug.apk" ]; then
        file=$app-$APP_VERSION_CODE.apk
        cp "$dist/android/$app-debug.apk" "$site/$app/$file"
        android="<h2>Android</h2>
<p><a class=\"button\" href=\"$file\">Download APK</a></p>
<p><small>Needs Android 8.0 or newer. Open the downloaded file; if asked, allow
your browser to install unknown apps. This is a debug build signed with a public
test key: for testing only.</small></p>"
    fi

    desktop=
    buttons=
    for installer in "$dist/desktop/$app"/*; do
        [ -f "$installer" ] || continue
        base=$(basename "$installer")
        cp "$installer" "$site/$app/"
        platform=${base#"$app-$APP_VERSION_CODE-"}
        platform=${platform%.*}
        buttons="$buttons<li class=\"button\"><a class=\"button\" href=\"$base\">$(platform_label "$platform")</a></li>"
    done
    if [ -n "$buttons" ]; then
        desktop="<h2>Desktop</h2>
<ul class=\"buttons\">$buttons</ul>
<p><small><b>macOS</b> (14 or newer): open the .dmg and drag $app to Applications.
The app is not signed by Apple, so the first time, Control-click it in Applications,
choose Open, then Open again. (On macOS 15 and newer: try to open it once, then
System Settings, Privacy &amp; Security, Open Anyway.)<br>
<b>Windows</b>: run the .msi; it installs for you only. If SmartScreen warns about
an unknown publisher: More info, then Run anyway.<br>
<b>Linux</b>: <code>sudo apt install ./$app-$APP_VERSION_CODE-linux-x64.deb</code>
(the ./ matters).<br>
Test builds, not signed: for testing only.</small></p>"
    fi

    # An app's sample files go to <app>/samples/: the ones built into the app
    # (src/main/assets/samples) and the online-only ones (apps/<app>/samples),
    # with that folder's catalog.json, from which an app can list and download them.
    samples=
    for sample in apps/$app/src/main/assets/samples/* apps/$app/samples/*; do
        [ -f "$sample" ] || continue
        base=$(basename "$sample")
        mkdir -p "$site/$app/samples"
        cp "$sample" "$site/$app/samples/"
        [ "$base" = catalog.json ] && continue
        samples="$samples<li><a href=\"samples/${base// /%20}\">$base</a></li>"
    done
    [ -n "$samples" ] && samples="<p>Sample files:</p><ul>$samples</ul>"

    cat > "$site/$app/index.html" << EOF
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>$app download</title>
<style>$style</style>
</head>
<body>
<h1>$app</h1>
<p>Version $APP_VERSION_NAME (versionCode $APP_VERSION_CODE)<br><small>Built $APP_BUILD_TIME</small></p>
$android
$desktop
$samples
<p><small><a href="../">All apps</a></small></p>
</body>
</html>
EOF
    items="$items<li><a href=\"$app/\">$app</a></li>"
done

cat > "$site/index.html" << EOF
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Apps</title>
<style>$style</style>
</head>
<body>
<h1>Apps</h1>
<p><small>For Android and the desktop. Test builds of $APP_VERSION_NAME, built $APP_BUILD_TIME.</small></p>
<ul>$items</ul>
</body>
</html>
EOF
echo "Site in $site/: $(find "$site" -name '*.apk' | wc -l | tr -d ' ') APK(s)," \
    "$(find "$site" \( -name '*.dmg' -o -name '*.msi' -o -name '*.deb' \) | wc -l | tr -d ' ') desktop installer(s)"
