plugins {
    `kotlin-dsl`
}

group = "io.github.ehb54.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "ehb54.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "ehb54.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "ehb54.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("desktopLibrary") {
            id = "ehb54.desktop.library"
            implementationClass = "DesktopLibraryConventionPlugin"
        }
        register("desktopApplication") {
            id = "ehb54.desktop.application"
            implementationClass = "DesktopApplicationConventionPlugin"
        }
    }
}
