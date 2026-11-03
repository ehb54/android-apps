plugins {
    alias(libs.plugins.ehb54.android.library)
}

android {
    namespace = "io.github.ehb54.core.ui"
    // Library resources share the app's namespace at runtime; the prefix avoids clashes.
    resourcePrefix = "core_ui_"
}
