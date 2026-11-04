import io.github.ehb54.buildlogic.CopyAndroidSources
import io.github.ehb54.buildlogic.configureComposeDesktop
import io.github.ehb54.buildlogic.registerAndroidResources
import io.github.ehb54.buildlogic.registerBuildConfig
import io.github.ehb54.buildlogic.stringProperty
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.desktop.DesktopExtension
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * A desktop app (desktop/<name>/), the desktop version of apps/<name>:
 *  - the Android app's Kotlin sources (screens, view model) compiled for the
 *    desktop against core/desktop's stand-ins for Android classes
 *    ([CopyAndroidSources]); a file in the desktop app's src/main/kotlin replaces
 *    the Android file at the same path, and the Activity is left out;
 *  - shared/<name>, the plain Kotlin both versions use, when there is one;
 *  - its strings, plurals, colors, vector icons and assets are the Android app's
 *    (then the desktop app's own src/main/res for desktop-only text), through `R`
 *    in package `io.github.ehb54.<name>`;
 *  - `BuildConfig` with the same version numbers as the Android build;
 *  - installers: .dmg (macOS), .msi (Windows) and .deb (Linux), each built on
 *    its own OS (`packageDistributionForCurrentOS`).
 * The app's build file sets mainClass, the installer name (`packageName`) and,
 * for Windows, a fixed `upgradeUuid`.
 */
class DesktopApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val app = name
            val appPackage = "io.github.ehb54.$app"
            configureComposeDesktop()
            registerAndroidResources(packageId = 0x7f).apply {
                packageName.convention(appPackage)
                resDirs.from(rootDir.resolve("apps/$app/src/main/res"), "src/main/res")
                assetDirs.from(rootDir.resolve("apps/$app/src/main/assets"))
            }
            registerBuildConfig(appPackage)
            // The Android app's screens and view model, compiled for the desktop.
            val androidSources = tasks.register<CopyAndroidSources>("copyAndroidSources") {
                this.androidSources.from(rootDir.resolve("apps/$app/src/main/kotlin"))
                desktopSources.from("src/main/kotlin")
                excludes.add("**/*Activity.kt")
                outputDir.set(layout.buildDirectory.dir("generated/androidSources"))
            }
            extensions.getByType<KotlinJvmProjectExtension>().sourceSets.named("main") {
                kotlin.srcDir(androidSources.flatMap { it.outputDir })
            }
            dependencies {
                add("implementation", project(":core:desktop"))
                if (rootDir.resolve("shared/$app/build.gradle.kts").isFile) {
                    add("implementation", project(":shared:$app"))
                }
            }

            val versionCode = stringProperty("appVersionCode", "1")
            extensions.getByType<ComposeExtension>().extensions.getByType<DesktopExtension>().application {
                nativeDistributions {
                    targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
                    // Installers want MAJOR.MINOR.PATCH, with MAJOR above 0 on macOS and
                    // MINOR at most 255 on Windows, so the commit count is the patch number.
                    packageVersion = "1.0.$versionCode"
                    vendor = "ehb54"
                    // The JDK's own modules the app uses, beyond Compose's defaults:
                    // XML for .xlsx files and the Android resources.
                    modules("java.xml", "jdk.unsupported")
                    macOS {
                        bundleID = appPackage
                        minimumSystemVersion = "14.0"
                    }
                    windows {
                        // Installs for the current user, so no administrator is needed.
                        perUserInstall = true
                        menu = true
                        shortcut = true
                    }
                    linux {
                        packageName = app
                        shortcut = true
                        debMaintainer = "ehb54@users.noreply.github.com"
                    }
                }
            }
        }
    }
}
