import com.android.build.api.dsl.ApplicationExtension
import io.github.ehb54.buildlogic.addComposeDependencies
import io.github.ehb54.buildlogic.intVersion
import io.github.ehb54.buildlogic.libs
import io.github.ehb54.buildlogic.library
import io.github.ehb54.buildlogic.registerUnitTests
import io.github.ehb54.buildlogic.requiredProperty
import io.github.ehb54.buildlogic.stringProperty
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Everything the apps in apps/ share: SDK levels, Compose, versioning, signing,
 * R8 for release builds and lint. An app's own build file sets only its
 * namespace and applicationId.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            // Play upload key: used only when its properties are set, never committed.
            val uploadStoreFile = providers.gradleProperty("uploadStoreFile").orNull

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.intVersion("compileSdk")

                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    targetSdk = libs.intVersion("targetSdk")
                    // Set by tools/ci-build.sh from git (tools/version-env.sh).
                    versionCode = stringProperty("appVersionCode", "1").toInt()
                    versionName = stringProperty("appVersionName", "0.0.0-dev")
                    buildConfigField("String", "BUILD_TIME", "\"${stringProperty("appBuildTime", "dev build")}\"")
                }

                buildFeatures {
                    compose = true
                    buildConfig = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                signingConfigs {
                    // Committed and public on purpose, so debug APKs from any host or
                    // CI run install over each other. Never sign anything else with it.
                    getByName("debug") {
                        storeFile = rootDir.resolve("keystore/debug.keystore")
                        storePassword = "android"
                        keyAlias = "androiddebugkey"
                        keyPassword = "android"
                    }
                    if (uploadStoreFile != null) {
                        create("upload") {
                            storeFile = file(uploadStoreFile)
                            storePassword = requiredProperty("uploadStorePassword")
                            keyAlias = requiredProperty("uploadKeyAlias")
                            keyPassword = requiredProperty("uploadKeyPassword")
                        }
                    }
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        if (uploadStoreFile != null) {
                            signingConfig = signingConfigs.getByName("upload")
                        }
                    }
                }

                lint {
                    abortOnError = true
                    checkDependencies = true
                    lintConfig = rootDir.resolve("lint.xml")
                }
            }

            addComposeDependencies()
            registerUnitTests("testDebugUnitTest")
            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", libs.library("androidx-activity-compose"))
            }
        }
    }
}
