package io.github.ehb54.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.FileSystems

/**
 * The Android app's Kotlin sources, ready to compile for the desktop: each file
 * of [androidSources] with its Android imports pointed at core/desktop's
 * stand-ins (see core/desktop's AndroidCompat.kt), so the screens and view
 * model exist once. Skipped: files matching [excludes] (the Activity), and any
 * file the desktop app has at the same path in [desktopSources] (its own
 * version, e.g. picture decoding). Line numbers match the original, so a
 * compile error points at the Android file's line.
 */
@CacheableTask
abstract class CopyAndroidSources : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidSources: ConfigurableFileCollection

    /** Only their paths matter: a desktop file replaces the Android file at the same path. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val desktopSources: ConfigurableFileCollection

    /** Glob patterns, relative to the source folder. */
    @get:Input
    abstract val excludes: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val replaced = desktopSources.files.filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath } }
            .toSet()
        val matchers = excludes.get().map { FileSystems.getDefault().getPathMatcher("glob:$it") }
        for (dir in androidSources.files.filter { it.isDirectory }) {
            for (file in dir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
                val relative = file.relativeTo(dir).invariantSeparatorsPath
                if (relative in replaced) continue
                if (matchers.any { it.matches(java.nio.file.Paths.get(relative)) }) continue
                val text = file.readLines().joinToString("\n") { rewrite(it) }
                File(out, relative).apply { parentFile.mkdirs() }.writeText(
                    "$text\n// Compiled for the desktop from the Android app's $relative: edit that file.\n",
                )
            }
        }
    }

    companion object {
        private const val DESKTOP = "io.github.ehb54.core.desktop"

        private val rules = listOf(
            // Android's own classes, the activity and lifecycle libraries, Android resources.
            Regex("""^import (?:android|androidx\.activity|androidx\.lifecycle|androidx\.compose\.ui\.res)(?:\.\w+)*\.(\w+)(.*)$"""),
            // Android-only parts of Compose.
            Regex("""^import androidx\.compose\.ui\.(?:platform|graphics)\.(LocalContext|asImageBitmap)(.*)$"""),
            // The Android framework module's classes have desktop counterparts of the same name.
            Regex("""^import io\.github\.ehb54\.core\.ui\.(.*)()$"""),
        )

        /** One source line with an Android import pointed at the desktop stand-in; other lines as they are. */
        internal fun rewrite(line: String): String {
            val rule = rules.firstOrNull { it.containsMatchIn(line) } ?: return line
            return rule.replace(line, "import $DESKTOP.\$1\$2")
        }
    }
}
