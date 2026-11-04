#!/usr/bin/env bash
# Runs tools/pictures/pictures.py in its venv (made by setup.sh), with the
# settings from tools/local.env (model cache, Python, temp folder).
# Usage: tools/pictures/run.sh <pictures.py arguments>
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ -f tools/local.env ]; then
    . tools/local.env
fi
tools/pictures/setup.sh > /dev/null
cd tools/pictures
exec .venv/bin/python pictures.py "$@"
