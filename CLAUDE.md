# android-apps: working notes

Read this first, whether you are Claude or a person picking the repo up on a new
machine. The repo is public: never commit hostnames, IP addresses, user names,
keys or passwords. Machine-specific settings go in `tools/local.env` (git-ignored).

## What this is

A monorepo of small Android apps that share one framework, in a single Gradle
build, with desktop versions (macOS, Windows, Linux) built from the same code:

| Path | What lives there |
|---|---|
| `build-logic/convention/` | Convention plugins `ehb54.android.application` and `ehb54.android.library` (SDK levels, Compose, versioning, signing, R8, lint), `ehb54.jvm.library` (plain Kotlin shared by an app's Android and desktop versions) and `ehb54.desktop.application` / `ehb54.desktop.library` (see Desktop apps). Shared build behavior changes here, never in an app's build file. |
| `core/ui/` | Shared Compose library: `AppTheme`, `BuildInfo`, `BuildInfoCard`, and the icons `core_ui_ic_add`, `core_ui_ic_more`, `core_ui_ic_back` (Google's material-icons library is deprecated, so icons are small vector drawables). Resources must start with `core_ui_` (`resourcePrefix`; lint warns otherwise). New shared code goes in `core/<module>/`; include it in `settings.gradle.kts`. |
| `apps/<name>/` | One app each, picked up automatically by `settings.gradle.kts`. Its build file sets `namespace` and `applicationId`, plus any dependencies only that app uses. `apps/starter` is the template for new apps. |
| `apps/quiz/` | Quiz, the flash-card app. Its rules come from a 1980/1993 terminal program: read `apps/quiz/DESIGN.md` before changing how sessions, levels or files work. |
| `shared/<name>/` | Plain Kotlin that apps/<name> and desktop/<name> both use (Quiz: rules, file formats, storage). No Android or desktop classes. |
| `desktop/<name>/` | The desktop version of apps/<name> (see Desktop apps). |
| `core/desktop/` | Framework for desktop apps, the counterpart of `core/ui`: theme, build info, Android resources and stand-ins for the Android classes apps use. |
| `gradle/libs.versions.toml` | Every version, including the SDK levels. |
| `lint.xml` | Lint settings shared by every module; each entry says why it is there. |
| `docker/Dockerfile`, `tools/` | The build pipeline (below). |
| `tools/wordlists/` | Noun frequency lists from public-domain texts (Gutenberg + spaCy + Wikidata + emoji), reviewed by hand, built into Quiz samples (online-only, in `apps/quiz/samples/`). Read its README before adding a list. |
| `tools/pictures/` | Original pictures for the word lists' picture cards, generated locally on Apple Silicon (MLX/mflux, ERNIE-Image-Turbo). The plan and the pick per word are committed, the images are not. Read its README and `PLAN.md`. |
| `tools/sounds/` | German speech for every text on the sample quizzes, generated locally on Apple Silicon (Qwen3-TTS, OmniVoice, Chatterbox, Piper), picked by Whisper and by ear; its `samples` step adds them to the sample zips (picture quizzes speak the word after the answer; listening and dictation quizzes). The picks are committed, the sounds are not. Read its README. |
| `.github/workflows/build.yml` | CI and the GitHub Pages download site. |
| `keystore/debug.keystore` | Shared debug signing key (see Signing). |

Stack: Kotlin, Jetpack Compose with Material 3, AGP 9 with its built-in Kotlin
support (no `org.jetbrains.kotlin.android` plugin in modules; the root build file
only pins its version), Gradle with configuration cache and build cache, JDK 21 to
run the build, Java 17 bytecode. Desktop apps use JetBrains' Compose
Multiplatform (Compose for Desktop) on the JVM; Android apps keep Google's Compose.

## Device constraints

- `minSdk` is 26 (Android 8.0) and must stay at 28 or lower: the test phone, a
  Galaxy S8+, cannot go past Android 9 (API 28).
- Any API above 26 needs a `Build.VERSION.SDK_INT` check (lint's NewApi check
  fails the build otherwise). `AppTheme` shows the pattern for dynamic color.
- `compileSdk` only decides which APIs code may call; current Compose requires 37.

## Building

There is one recipe, `tools/ci-build.sh`: `lint unitTests assembleDebug
bundleRelease packageDistributionForCurrentOS` across all modules (`unitTests` is
each module's own: `testDebugUnitTest` on Android, `test` on the JVM; the last task
makes the desktop installers for the OS it runs on). Arguments replace the task
list, e.g. `tools/ci-build.sh :apps:starter:assembleDebug`; `tools/ci-build.sh
desktop` builds only the desktop apps (`-PdesktopOnly` leaves the Android modules
out of the build). Version numbers come from git via
`tools/version-env.sh`, so the same commit gets the same numbers everywhere:

- versionCode = number of commits on HEAD (only grows along `main`);
- versionName = `0.1.<versionCode>-<sha>`, with `-dirty` for uncommitted changes.

Ways to run it:

1. **GitHub Actions**: every push and pull request. The Linux job runs the whole
   recipe; macOS (Apple silicon on `macos-15`, Intel on `macos-15-intel`) and
   Windows jobs build the desktop installers; `tools/collect-dist.sh` gathers each
   job's results. A push to `main` also deploys the download site to
   `https://ehb54.github.io/android-apps/<app>/`.
2. **Any Docker host**: `tools/docker-build.sh [tasks]`. Builds the image from
   `docker/Dockerfile` (JDK 21, Gradle, SDK; tagged by compileSdk and build-tools,
   so a version bump builds a new one), runs the recipe in it as the calling user,
   and keeps the Gradle and Android caches in `../android-apps-state`.
3. **Laptop to a remote Docker host**: put `BUILD_HOST=<ssh alias>` in
   `tools/local.env`, then `tools/remote-build.sh [tasks]`. It rsyncs the checkout
   (including `.git`) to `~/claude/android-apps` on that host, runs
   `tools/docker-build.sh` there, and copies the debug APKs back to `out/`.
4. **Android Studio**: open the repository root; it is an ordinary Gradle project.

The SDK tools in the image are installed with `android --no-metrics sdk install`.
The `android` CLI that replaced `sdkmanager` reports usage to Google unless it gets
`--no-metrics`, and plain `sdkmanager` cannot turn that off.

### Gotchas

- **Renaming a resource folder** (e.g. `mipmap-anydpi-v26` to `mipmap-anydpi`) can
  make an incremental build lose the resource, and the Gradle build cache then
  stores the broken output, so even `clean` restores it ("resource mipmap/... not
  found"). Fix: delete `caches/build-cache-1` under the Gradle user home
  (`../android-apps-state/gradle/` for Docker builds) and build with `clean`.
- If AGP wants different build-tools, the Docker build fails because it cannot
  install into the image's SDK. Set `androidBuildTools` in the toml to the version
  it names.
- From API 37 the SDK platform packages carry a minor version (`android-37.0`);
  `tools/docker-build.sh` adds the `.0` itself.
- On an arm64 Docker host (Apple Silicon), the image's SDK tools are x86-64
  only and the arm64 image fails to install them ("rosetta error: failed to
  open elf at /lib64/ld-linux-x86-64.so.2"). Run
  `DOCKER_DEFAULT_PLATFORM=linux/amd64 tools/docker-build.sh` to build and run
  the amd64 image under Rosetta (slower; the Quiz unit tests take minutes).

## Tests

- Plain Kotlin logic (engines, file formats, storage) lives in `shared/<app>/`
  with JVM unit tests in its `src/test/`. Keep it free of Android classes so it
  stays testable and the desktop app can use it.
- UI tests run the real app on **Robolectric** (the Android framework on the JVM),
  with no device or emulator: see `apps/quiz/src/test/.../QuizUiTest.kt`. What it
  took to make them work:
  - **One test per class**, and `forkEvery = 1` on the test task. A second test
    in the same JVM sees the previous test's screen rather than its own.
  - `@GraphicsMode(LEGACY)`. Native graphics aborts the JVM
    (`JniConstants: Class not found: java/nio/ShortBuffer`) when pictures are
    decoded off the main thread.
  - JVM flags `--add-exports/--add-opens java.base/jdk.internal.access=ALL-UNNAMED`
    for Robolectric's Android 17 runtime on JDK 21.
  - Pinned `androidx.test` core and Espresso: the Compose test library still
    pulls Espresso 3.5, which calls an `InputManager` method that Android 17 removed.
  - `@Config(qualifiers = "w412dp-h846dp-xxhdpi")` (the S8+ screen). Robolectric's
    default 320×470 px screen leaves buttons off-screen, and taps on them do nothing.
  - On a timeout, the `waitForText` helper prints the whole semantics tree; read
    it before guessing.
  - Lists scroll under the top bar, and `performScrollToNode` counts that area as
    visible, so a tap on a button there hits the bar. `pressInList` presses list
    buttons through their click action instead.
- Sample files: `src/main/assets/samples/` (built into the app) and
  `apps/<app>/samples/` (online only). `tools/make-site.sh` publishes both, with
  `apps/<app>/samples/catalog.json`, to `<app>/samples/` on the download site.
  Quiz's Sample quizzes screen lists the catalog there and downloads what the
  user adds (see `apps/quiz/DESIGN.md`; the catalog is regenerated by a test).

## Desktop apps

`desktop/<name>/` is the desktop version of `apps/<name>`, built from the Android
app's own code, so the Android app is never edited for it and screens exist once:

- **Sources.** The Android app's Kotlin (screens, view model) is compiled for the
  desktop by `copyAndroidSources` (build-logic's `CopyAndroidSources`), which points
  its Android imports (`android.*`, `androidx.activity`, `androidx.lifecycle`,
  `androidx.compose.ui.res`, `LocalContext`, `core.ui`) at stand-ins of the same
  names in `core/desktop` (`AndroidCompat.kt`, `Resources.kt`, `Back.kt`). A file
  in `desktop/<name>/src/main/kotlin` replaces the Android file at the same path
  (Quiz: `data/Images.kt`, pictures with Skia; `ui/SoundPlayer.kt`, sound with
  Java Sound; `ui/Away.kt`, the window losing focus); `*Activity.kt` is left out, and the desktop app has its own `Main.kt`.
- **Resources.** `generateAndroidResources` turns the Android app's res folder
  (strings, plurals, colors, vector drawables) and assets into an `R` with Android
  style ids in the app's package, so `R.string.x` code compiles unchanged.
  `desktop/<name>/src/main/res` overrides entries by name (desktop wording, e.g.
  "Click" for "Tap"). Text is formatted with `String.format`, as on Android.
- **Missing stand-in.** A desktop build that fails with an unresolved
  `io.github.ehb54.core.desktop.<Name>` means the Android code started using an
  Android class with no stand-in: add one to `core/desktop` with the same
  signature (only what is used), or give the desktop app its own version of that file.
- **Behavior.** Escape is Back (`BackHandler`); file pickers are the system's
  Open and Save dialogs; data goes in the OS's application data folder
  (`AppFolders`: macOS `~/Library/Application Support/<App>`, Windows
  `%APPDATA%\<App>`, Linux `~/.local/share/<app>`).
- **Versions.** `BuildConfig` has the Android build's numbers. Installers need
  `MAJOR.MINOR.PATCH` (macOS wants MAJOR above 0, Windows MINOR at most 255), so
  their version is `1.0.<versionCode>`.
- **Installers.** `packageDistributionForCurrentOS`: `.dmg` on macOS, `.msi` on
  Windows, `.deb` on Linux, each with its own Java runtime, for the processor of
  the machine that builds it (hence two macOS jobs in CI). They are not signed:
  macOS asks for Control-click, Open the first time; Windows SmartScreen asks for
  More info, Run anyway. Signing (Apple Developer ID and notarization, a Windows
  code-signing certificate) is eb's decision. Each app's build file sets a fixed
  Windows `upgradeUuid`; never change it.
- **Icons.** The window and installer icons are the Android launcher icon
  (`ic_launcher_foreground` over the `ic_launcher_background` color), shown as
  Android launchers show it: the middle 72 of 108 dp on a rounded square.
  `generateInstallerIcons` runs core/desktop's `InstallerIcons.kt` to draw
  `.icns` (macOS, inside Apple's 824-of-1024 grid), `.ico` (Windows, 16 to 256 px)
  and `.png` (Linux) into `build/generated/installerIcons`; nothing is drawn by hand.
- **Running one from a checkout:** `./gradlew :desktop:quiz:run` on a computer
  with a screen (not on the build hosts).
- **Tests:** `src/test/` with Compose's desktop test rule (`createComposeRule`),
  which renders off screen, so they run headless in CI and Docker.

## Getting an app onto the phone

**Download page.** On the phone, open `https://ehb54.github.io/android-apps/` and
tap the app, then Download APK. Open the downloaded file; the first time, Android
asks to allow the browser to install unknown apps (Settings, then allow from this
source). Play Protect may warn about an unknown developer: choose Install anyway.
Pages can cache for about 10 minutes after a deploy; the APK file name carries the
versionCode, so the page never points at a mismatched file.

**USB (adb).**
1. Once, on the phone: Settings > About phone > Software information, tap Build
   number seven times. Then Settings > Developer options > USB debugging on.
2. Connect with a data cable (many cables are charge-only). Unlock the phone and
   accept "Allow USB debugging?" (tick Always allow from this computer).
3. `adb devices` must list the phone as `device`. `unauthorized` means the prompt
   was not accepted; no line at all usually means the cable, or the phone's USB mode
   (pull down the notification shade and pick File transfer). On a Mac, quit
   Samsung Smart Switch or Android File Transfer if they are running, since they
   can hold the device.
4. `tools/adb-install.sh [app]` installs `out/<app>-debug.apk` over the previous
   version and keeps its data. Set `ADB=` in `tools/local.env` if adb is not on PATH.

**adb over Wi-Fi.** Android 9 has no Wireless debugging setting. While connected
by USB, run `adb tcpip 5555`, unplug, and `adb connect <phone-ip>:5555`. This lasts
until the phone reboots.

## Signing

- **Debug:** `keystore/debug.keystore` is committed and public on purpose (the
  standard passwords `android` / alias `androiddebugkey`). Every host and CI run
  signs with it, so debug builds install over each other. Never sign anything
  else with it.
- **Release (Google Play upload key):** never in the repo. Make it once, keep it and
  its password outside the repo, and back it up:
  `keytool -genkeypair -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
  Then set the Gradle properties `uploadStoreFile`, `uploadStorePassword`,
  `uploadKeyAlias` and `uploadKeyPassword`, either in `~/.gradle/gradle.properties`
  (under `GRADLE_USER_HOME`; for Docker builds that is
  `../android-apps-state/gradle/gradle.properties`) or, in CI, as secrets passed as
  `ORG_GRADLE_PROJECT_<name>` environment variables. Without them `bundleRelease`
  still runs R8 and produces an unsigned bundle, which is how CI checks release builds.
- Enroll each app in Play App Signing. Google then holds the real signing key, and
  a lost upload key can be reset from the Play Console.

## Getting a desktop app onto a computer

On the computer, open `https://ehb54.github.io/android-apps/<app>/` and pick the
installer for it under Desktop (macOS Apple silicon or Intel, Windows, Linux); the
page says how to get past the unsigned-app warnings.

## Adding an app

`tools/new-app.sh <name> "<App label>"` copies `apps/starter` to `apps/<name>`,
renames the package and sets the applicationId to `io.github.ehb54.<name>` (it is
permanent once the app is on Play, so pick the name carefully). Then change its
icon colors in `res/values/colors.xml` and `res/drawable/ic_launcher_foreground.xml`
(its desktop version, if it gets one, draws its icons from the same two),
build, and commit. CI builds it and the download site lists it automatically.

## Play-readiness rules

Keep these so publishing an app is a Console task, not a code rewrite:

- `targetSdk` at the latest API level. Play raises its minimum every August; bump
  it in the toml and check that year's behavior changes.
- Release builds keep R8 and resource shrinking on. Code reached by reflection needs
  keep rules in `apps/<name>/proguard-rules.pro`.
- Upload `.aab` files (`bundleRelease`), not APKs.
- All UI text in string resources (plurals for counts), so apps can be translated.
- Edge-to-edge: `enableEdgeToEdge()` plus Scaffold insets (enforced on Android 15+).
- Backups: each app's `res/xml/data_extraction_rules.xml` (Android 12+) and
  `res/xml/backup_rules.xml` (older) must exclude anything that must not leave the
  device.
- Lint has to pass with no findings. Fix new warnings rather than adding a baseline;
  the few deliberate exceptions live in `lint.xml` with their reasons.
- Ask only for the permissions an app needs; each one becomes a Data safety answer
  in the Play Console, and every app needs a privacy policy URL there. Pick files
  and photos through the system pickers (`OpenDocument`, `CreateDocument`,
  `PickVisualMedia`), which need no storage permission.
- No native code at present. Adding any brings Play's 16 KB page-size requirement.
- Testing-only shortcuts are tied to `BuildConfig.DEBUG` so release builds never
  have them (Quiz: `ESCAPE_HATCH`, long-press the round counter to leave a session).

## Updating versions

- Edit `gradle/libs.versions.toml`. `kotlin` must stay at or above the Kotlin that
  AGP bundles; the Compose compiler plugin uses the same version.
- New Gradle: `./gradlew wrapper --gradle-version <v> --gradle-distribution-sha256-sum <sha>`
  (the sha256 is published next to each distribution on services.gradle.org), and
  update the `FROM gradle:<v>-jdk21` line in `docker/Dockerfile`.
- New SDK command-line tools: `CMDLINE_TOOLS` in `docker/Dockerfile`.

## Conventions

- Kotlin official style, 4 spaces, trailing commas (`.editorconfig`).
- Packages: `io.github.ehb54.<app>` for apps, `io.github.ehb54.core.<module>` for
  the framework.
- Keep app build files minimal; anything two apps need moves to `build-logic/` or `core/`.
- Code an app's Android and desktop versions share either lives in `shared/<app>`
  (plain Kotlin) or stays in `apps/<app>` and is compiled for the desktop through
  the stand-ins; never copy it into `desktop/<app>`.
