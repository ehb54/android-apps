#!/usr/bin/env bash
# Runs tools/sounds/sounds.py in its venv (made by setup.sh), with the
# settings from tools/local.env (model cache, Python, temp folder).
# Usage: tools/sounds/run.sh <sounds.py arguments>
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ -f tools/local.env ]; then
    . tools/local.env
fi
tools/sounds/setup.sh > /dev/null
cd tools/sounds
exec .venv/bin/python sounds.py "$@"
