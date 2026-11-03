# Sourced by the build scripts. Version values come from git, so every host
# builds a given commit with the same numbers:
#   APP_VERSION_CODE  commits reachable from HEAD (only grows along main)
#   APP_VERSION_NAME  0.1.<code>-<sha>, with -dirty for uncommitted changes
#   APP_BUILD_TIME    commit time in UTC, or the current time for a dirty tree

if git rev-parse -q --verify HEAD > /dev/null 2>&1; then
    APP_VERSION_CODE=$(git rev-list --count HEAD)
    APP_VERSION_NAME="0.1.$APP_VERSION_CODE-$(git rev-parse --short HEAD)"
    APP_BUILD_TIME=$(TZ=UTC git log -1 --date=format-local:'%Y-%m-%d %H:%M UTC' --format=%cd)
else
    APP_VERSION_CODE=1
    APP_VERSION_NAME=0.1.0
    APP_BUILD_TIME=
fi

if [ -n "$(git status --porcelain 2> /dev/null)" ]; then
    APP_VERSION_NAME="$APP_VERSION_NAME-dirty"
    APP_BUILD_TIME=
fi

: "${APP_BUILD_TIME:=$(date -u '+%Y-%m-%d %H:%M UTC')}"
export APP_VERSION_CODE APP_VERSION_NAME APP_BUILD_TIME
