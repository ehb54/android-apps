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

include(":core:ui")

// Every directory under apps/ with a build file is an app (tools/new-app.sh makes new ones).
rootDir.resolve("apps").listFiles().orEmpty()
    .filter { it.resolve("build.gradle.kts").isFile }
    .sortedBy { it.name }
    .forEach { include(":apps:${it.name}") }
