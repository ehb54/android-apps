// Quiz's rules, file formats and storage: plain Kotlin that the Android app
// (apps/quiz) and the desktop app (desktop/quiz) both use, so they never differ.
plugins {
    alias(libs.plugins.ehb54.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)
}
