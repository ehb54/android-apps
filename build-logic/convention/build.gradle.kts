plugins {
    `kotlin-dsl`
}

group = "io.github.ehb54.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
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
    }
}
