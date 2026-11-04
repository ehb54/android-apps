import io.github.ehb54.buildlogic.configureComposeDesktop
import io.github.ehb54.buildlogic.registerAndroidResources
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Framework code for desktop apps (core/desktop/): Compose for Desktop, and an
 * `R` for the Android resources it shares with core/ui (`androidResources`,
 * package `io.github.ehb54.core.<module>`).
 */
class DesktopLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            configureComposeDesktop()
            registerAndroidResources(packageId = 0x7e).apply {
                packageName.convention("io.github.ehb54.core.$name")
                resDirs.from("src/main/res")
            }
        }
    }
}
