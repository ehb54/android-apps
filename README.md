# android-apps

Small Android apps built on a shared framework: Kotlin, Jetpack Compose,
Android 8.0 (API 26) and newer. Some also come for the desktop (macOS 14 and
newer, Windows, Linux), built from the same code.

- `apps/<name>/`: the apps (`apps/starter` is the template for new ones)
- `apps/quiz/`: Quiz, a flash-card app (rules in `apps/quiz/DESIGN.md`)
- `desktop/<name>/`, `shared/<name>/`: an app's desktop version, and the plain
  Kotlin both versions use
- `core/`: shared UI code (`core/ui` for Android, `core/desktop` for the desktop)
- `build-logic/`: shared Gradle conventions

Every push to `main` is linted, tested and built by GitHub Actions; test builds
(Android APKs and desktop installers) can be downloaded from
https://ehb54.github.io/android-apps/.

Build locally with Android Studio or any Docker host (`tools/docker-build.sh`).
See [CLAUDE.md](CLAUDE.md) for the full notes: building, installing on a phone,
desktop apps, signing, adding an app, and keeping apps ready for Google Play.
