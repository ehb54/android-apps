#!/usr/bin/env bash
# Runs tools/wordlists/words.py in its Docker image on this machine (a Docker
# host; the steps download from Gutenberg, Wikidata, Unicode and GitHub).
# Downloads are cached in tools/wordlists/cache/, which git ignores.
# Usage: tools/wordlists/run.sh <words.py arguments>
set -euo pipefail
cd "$(dirname "$0")/../.."

image=android-apps-wordlists
docker build --quiet --tag "$image" tools/wordlists > /dev/null
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp \
    -v "$PWD:/src" -w /src/tools/wordlists \
    "$image" python words.py "$@"
