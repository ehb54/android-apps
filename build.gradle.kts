// Puts the plugins on the build classpath; modules apply them through the
// convention plugins in build-logic/. Declaring kotlin-android here pins the
// Kotlin version AGP's built-in Kotlin support uses (it must match the Compose
// compiler plugin); no module applies it directly.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
