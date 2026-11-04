import io.github.ehb54.buildlogic.configureJvmLint
import io.github.ehb54.buildlogic.configureKotlinJvm
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plain Kotlin shared by an app's Android and desktop versions (shared/<app>/):
 * rules, file formats and storage, with no Android or desktop classes in them.
 * Android lint checks it as part of the Android app that uses it.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureKotlinJvm()
            configureJvmLint()
        }
    }
}
