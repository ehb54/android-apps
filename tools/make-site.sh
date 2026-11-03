#!/usr/bin/env bash
# Assemble the GitHub Pages download site from the debug APKs already built:
#   <site>/index.html             list of apps
#   <site>/<app>/index.html       download page
#   <site>/<app>/<app>-<code>.apk the APK, named by versionCode so a cached
#                                 page never points at a newer file
# Usage: tools/make-site.sh [site-dir]     (default: site)
set -euo pipefail
cd "$(dirname "$0")/.."
. tools/version-env.sh

site=${1:-site}
rm -rf "$site"
mkdir -p "$site"
touch "$site/.nojekyll"

style='body{font-family:system-ui,sans-serif;max-width:34rem;margin:2rem auto;padding:0 1rem;line-height:1.5;color:#1b1b1b;background:#fafafa}
a.button{display:block;text-align:center;padding:1rem;border-radius:.75rem;background:#1e6b52;color:#fff;text-decoration:none;font-size:1.2rem}
small{color:#555}
@media (prefers-color-scheme:dark){body{background:#121212;color:#e6e6e6}small{color:#aaa}a{color:#8fd3b6}}'

items=
for apk in apps/*/build/outputs/apk/debug/*-debug.apk; do
    [ -f "$apk" ] || continue
    app=$(basename "$apk" -debug.apk)
    file=$app-$APP_VERSION_CODE.apk
    mkdir -p "$site/$app"
    cp "$apk" "$site/$app/$file"

    # An app's sample files (src/main/assets/samples) are offered for download too.
    samples=
    sample_dir=apps/$app/src/main/assets/samples
    if [ -d "$sample_dir" ]; then
        mkdir -p "$site/$app/samples"
        for sample in "$sample_dir"/*; do
            base=$(basename "$sample")
            cp "$sample" "$site/$app/samples/"
            samples="$samples<li><a href=\"samples/${base// /%20}\">$base</a></li>"
        done
        samples="<p>Sample files:</p><ul>$samples</ul>"
    fi

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
<p><a class="button" href="$file">Download APK</a></p>
<p><small>Needs Android 8.0 or newer. Open the downloaded file; if asked, allow
your browser to install unknown apps. This is a debug build signed with a public
test key: for testing only.</small></p>
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
<title>Android apps</title>
<style>$style</style>
</head>
<body>
<h1>Android apps</h1>
<p><small>Debug builds of $APP_VERSION_NAME, built $APP_BUILD_TIME.</small></p>
<ul>$items</ul>
</body>
</html>
EOF
echo "Site in $site/: $(find "$site" -name '*.apk' | wc -l | tr -d ' ') APK(s)"
