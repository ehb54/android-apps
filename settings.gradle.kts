pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "android-apps"

// -PdesktopOnly: only the desktop apps and what they use, for CI's macOS and
// Windows jobs, which build desktop installers and have no Android SDK to rely on.
val desktopOnly = providers.gradleProperty("desktopOnly").isPresent

include(":core:desktop")
if (!desktopOnly) {
    include(":core:ui")

    // Every directory under apps/ with a build file is an app (tools/new-app.sh makes new ones).
    rootDir.resolve("apps").listFiles().orEmpty()
        .filter { it.resolve("build.gradle.kts").isFile }
        .sortedBy { it.name }
        .forEach { include(":apps:${it.name}") }
}

// shared/<app>: plain Kotlin an app's Android and desktop versions both use.
// desktop/<app>: the desktop version of apps/<app> (see CLAUDE.md, Desktop apps).
for (kind in listOf("shared", "desktop")) {
    rootDir.resolve(kind).listFiles().orEmpty()
        .filter { it.resolve("build.gradle.kts").isFile }
        .sortedBy { it.name }
        .forEach { include(":$kind:${it.name}") }
}
