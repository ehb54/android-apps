# android-apps

Small Android apps built on a shared framework: Kotlin, Jetpack Compose,
Android 8.0 (API 26) and newer.

- `apps/<name>/`: the apps (`apps/starter` is the template for new ones)
- `core/`: shared UI code
- `build-logic/`: shared Gradle conventions

Every push to `main` is linted, tested and built by GitHub Actions; debug builds
can be downloaded from https://ehb54.github.io/android-apps/.

Build locally with Android Studio or any Docker host (`tools/docker-build.sh`).
See [CLAUDE.md](CLAUDE.md) for the full notes: building, installing on a phone,
signing, adding an app, and keeping apps ready for Google Play.
