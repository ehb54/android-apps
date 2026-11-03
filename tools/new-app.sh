#!/usr/bin/env bash
# Create apps/<name> as a renamed copy of apps/starter.
# Usage: tools/new-app.sh <name> "<App label>"
#   <name>: lowercase letters and digits, starting with a letter. It becomes the
#   module, the Kotlin package and the applicationId io.github.ehb54.<name>,
#   which is permanent once the app is on Google Play.
set -euo pipefail
cd "$(dirname "$0")/.."

name=${1:-}
label=${2:-}
if [[ ! $name =~ ^[a-z][a-z0-9]*$ ]] || [ -z "$label" ]; then
    sed -n '3,6p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 1
fi
if [ -e "apps/$name" ]; then
    echo "apps/$name already exists" >&2
    exit 1
fi

cp -R apps/starter "apps/$name"
rm -rf "apps/$name/build"
for dir in apps/"$name"/src/*/kotlin/io/github/ehb54/starter; do
    if [ -d "$dir" ]; then
        mv "$dir" "${dir%/starter}/$name"
    fi
done

find "apps/$name" -type f \( -name '*.kt' -o -name '*.kts' -o -name '*.xml' \) \
    -exec perl -pi -e "s/\\bio\\.github\\.ehb54\\.starter\\b/io.github.ehb54.$name/g" {} +

# Android string resources need &, < and apostrophes escaped.
LABEL=$label perl -pi -e '
    BEGIN { ($l = $ENV{LABEL}) =~ s/&/&amp;/g; $l =~ s/</&lt;/g; $l =~ s/\x27/\\\x27/g }
    s{(<string name="app_name">)[^<]*(</string>)}{$1$l$2}
' "apps/$name/src/main/res/values/strings.xml"

echo "Created apps/$name ($label), applicationId io.github.ehb54.$name."
echo "Next: give it its own icon colors in apps/$name/src/main/res, then build and commit."
