#!/usr/bin/env bash
# Creates tools/pictures/.venv (git-ignored) with requirements.txt, using
# PICTURES_PYTHON from tools/local.env (Python 3.11 or newer; default python3).
# Skips the install when the venv already has this requirements.txt.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [ -f tools/local.env ]; then
    . tools/local.env
fi
venv=tools/pictures/.venv
stamp=$venv/requirements.txt
if [ -f "$stamp" ] && cmp -s "$stamp" tools/pictures/requirements.txt; then
    echo "$venv is up to date"
    exit 0
fi
[ -x "$venv/bin/python" ] || "${PICTURES_PYTHON:-python3}" -m venv "$venv"
"$venv/bin/python" -m pip install --quiet --upgrade pip
"$venv/bin/python" -m pip install --quiet -r tools/pictures/requirements.txt
cp tools/pictures/requirements.txt "$stamp"
"$venv/bin/python" -c "import importlib.metadata as m; print('mflux', m.version('mflux'), 'mlx', m.version('mlx'))"
