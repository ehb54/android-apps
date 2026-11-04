package io.github.ehb54.core.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** The app's window, as the parent of its file dialogs. */
val LocalAppWindow = staticCompositionLocalOf<Frame?> { null }

/**
 * The system's own Open dialog (the native one on macOS and Windows). Returns
 * the chosen file, or null when cancelled. [extensions] (without the dot)
 * narrows what is offered, where the system allows it.
 */
fun openFileDialog(parent: Frame?, title: String, extensions: Set<String> = emptySet()): File? {
    val dialog = FileDialog(parent, title, FileDialog.LOAD)
    if (extensions.isNotEmpty()) {
        dialog.setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
    }
    dialog.isVisible = true
    return chosen(dialog)
}

/** The system's own Save dialog, offering [suggestedName]. Returns the file to write, or null when cancelled. */
fun saveFileDialog(parent: Frame?, title: String, suggestedName: String): File? {
    val dialog = FileDialog(parent, title, FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    return chosen(dialog)
}

private fun chosen(dialog: FileDialog): File? {
    val name = dialog.file ?: return null
    val dir = dialog.directory ?: return File(name)
    return File(dir, name)
}

/**
 * Where an app keeps its data and its cache on this computer:
 *  - macOS: ~/Library/Application Support/<App> and ~/Library/Caches/<App>
 *  - Windows: %APPDATA%\<App> and %LOCALAPPDATA%\<App>\Cache
 *  - Linux: $XDG_DATA_HOME/<app> (~/.local/share/<app>) and $XDG_CACHE_HOME/<app> (~/.cache/<app>)
 */
class AppFolders(appName: String, home: File = File(System.getProperty("user.home")), env: (String) -> String? = System::getenv) {
    private val os = System.getProperty("os.name").lowercase()

    val data: File
    val cache: File

    init {
        when {
            os.startsWith("mac") -> {
                data = File(home, "Library/Application Support/$appName")
                cache = File(home, "Library/Caches/$appName")
            }
            os.startsWith("windows") -> {
                data = File(env("APPDATA") ?: File(home, "AppData/Roaming").path, appName)
                cache = File(File(env("LOCALAPPDATA") ?: File(home, "AppData/Local").path, appName), "Cache")
            }
            else -> {
                val lower = appName.lowercase()
                data = File(env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".local/share").path, lower)
                cache = File(env("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: File(home, ".cache").path, lower)
            }
        }
    }
}

/**
 * Waits up to [timeoutMillis] until the thread named [threadName] (an app's
 * background saver) has nothing left to do, so an app can exit without losing
 * its last save. Returns at once when there is no such thread.
 */
fun waitForIdle(threadName: String, timeoutMillis: Long = 3000) {
    val thread = Thread.getAllStackTraces().keys.firstOrNull { it.name == threadName } ?: return
    val end = System.currentTimeMillis() + timeoutMillis
    var idle = 0
    // Waiting on its queue three times in a row: nothing running, nothing queued.
    while (idle < 3 && System.currentTimeMillis() < end) {
        idle = if (thread.state == Thread.State.WAITING) idle + 1 else 0
        Thread.sleep(20)
    }
}
