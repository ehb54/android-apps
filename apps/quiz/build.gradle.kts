plugins {
    alias(libs.plugins.ehb54.android.application)
}

android {
    // The applicationId is permanent once the app is on Google Play.
    namespace = "io.github.ehb54.quiz"
    defaultConfig {
        applicationId = "io.github.ehb54.quiz"
    }
    // Robolectric UI tests need the app's resources and assets.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

tasks.withType<Test>().configureEach {
    // A fresh JVM per test class: Robolectric UI tests otherwise see the previous test's screen.
    forkEvery = 1
    // SampleCatalogTest checks samples/catalog.json against the sample files;
    // -PwriteCatalog=true lets it regenerate the catalog.
    inputs.dir("samples").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("writeCatalog", providers.gradleProperty("writeCatalog").getOrElse("false"))
    // Robolectric's Android 17 runtime reaches into a JDK-internal package.
    jvmArgs(
        "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
    )
}

dependencies {
    // The rules, file formats and storage, shared with the desktop app.
    implementation(project(":shared:quiz"))
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.espresso.core)
}
