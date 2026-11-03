import com.android.build.api.dsl.LibraryExtension
import io.github.ehb54.buildlogic.addComposeDependencies
import io.github.ehb54.buildlogic.intVersion
import io.github.ehb54.buildlogic.libs
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Shared Android library setup for the framework modules under core/. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                compileSdk = libs.intVersion("compileSdk")

                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                }

                buildFeatures {
                    compose = true
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                lint {
                    abortOnError = true
                    lintConfig = rootDir.resolve("lint.xml")
                }
            }

            addComposeDependencies()
        }
    }
}
