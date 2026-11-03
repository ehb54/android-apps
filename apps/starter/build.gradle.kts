plugins {
    alias(libs.plugins.ehb54.android.application)
}

android {
    // The applicationId is permanent once the app is on Google Play.
    namespace = "io.github.ehb54.starter"
    defaultConfig {
        applicationId = "io.github.ehb54.starter"
    }
}
