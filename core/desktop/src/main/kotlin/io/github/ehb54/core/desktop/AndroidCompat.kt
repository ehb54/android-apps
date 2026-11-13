package io.github.ehb54.core.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import java.awt.Frame
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/*
 * Desktop stand-ins for the few Android classes an app's screens and view model
 * use, with the same names and signatures. A desktop app compiles its Android
 * app's Kotlin sources with their Android imports pointed here (build-logic's
 * CopyAndroidSources), so the screens exist once. Only what the apps use is
 * here; a desktop build that fails on a missing name means adding it.
 */

/** A file, where Android has a content URI. */
class Uri(val file: File) {
    override fun toString(): String = file.path
}

object SystemClock {
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000

    fun uptimeMillis(): Long = elapsedRealtime()
}

object OpenableColumns {
    const val DISPLAY_NAME = "_display_name"
    const val SIZE = "_size"
}

/** Answers ContentResolver.query with one row of the asked columns. */
class Cursor internal constructor(private val values: List<String?>) : Closeable {
    fun moveToFirst(): Boolean = true

    fun getString(column: Int): String? = values[column]

    override fun close() = Unit
}

class ContentResolver internal constructor() {
    fun openInputStream(uri: Uri): InputStream? = FileInputStream(uri.file)

    /** Mode "w" (truncate) or "wa" (append). */
    fun openOutputStream(uri: Uri, mode: String = "w"): OutputStream? = FileOutputStream(uri.file, mode.contains('a'))

    /** Nullable as on Android, where a provider can fail to answer; a file always answers. */
    fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? = Cursor(
        projection.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> uri.file.name
                OpenableColumns.SIZE -> uri.file.length().toString()
                else -> null
            }
        },
    )
}

/** An app's assets (its Android app's src/main/assets), from the classpath. */
class AssetManager internal constructor() {
    private val index: List<String> by lazy {
        javaClass.classLoader.getResourceAsStream("$ROOT/.index")?.bufferedReader()?.readLines().orEmpty()
    }

    fun open(path: String): InputStream =
        javaClass.classLoader.getResourceAsStream("$ROOT/$path") ?: throw IOException("no asset $path")

    /** The names directly in [path], as on Android. */
    fun list(path: String): Array<String> {
        val prefix = path.trimEnd('/') + "/"
        return index.filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).substringBefore('/') }
            .distinct()
            .toTypedArray()
    }

    private companion object {
        const val ROOT = "android-assets"
    }
}

open class Context internal constructor(name: String, dataDir: File?) {
    private val folders = AppFolders(name)

    /** The app's own data folder, as Android's files folder. */
    val filesDir: File = (dataDir ?: folders.data).apply { mkdirs() }
    val cacheDir: File = (if (dataDir != null) File(dataDir, "cache") else folders.cache).apply { mkdirs() }
    val assets = AssetManager()
    val contentResolver = ContentResolver()
    val resources: Resources get() = Resources

    fun getString(id: Int, vararg formatArgs: Any): String = Resources.string(id, *formatArgs)
}

/**
 * The running app. The desktop main creates it once with the app's name, which
 * names its data and cache folders; [dataDir] puts everything in one folder
 * instead (tests).
 */
open class Application(name: String, dataDir: File? = null) : Context(name, dataDir) {
    init {
        current = this
    }

    companion object {
        lateinit var current: Application
            private set
    }
}

val LocalContext = staticCompositionLocalOf<Context> { Application.current }

/** Android's AndroidViewModel: the app, and a scope on the UI thread. */
open class AndroidViewModel(application: Application) {
    @PublishedApi internal val app = application

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Reified, so the cast is checked (Android's is unchecked). */
    inline fun <reified T : Application> getApplication(): T = app as T
}

val AndroidViewModel.viewModelScope: CoroutineScope get() = scope

/** One view model per call site, made with the running Application. */
@Composable
inline fun <reified VM : AndroidViewModel> viewModel(): VM = remember {
    VM::class.java.getConstructor(Application::class.java).newInstance(Application.current)
}

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> = collectAsState()

object Formatter {
    /** "870 B", "1.2 kB", "45 MB": decimal units, as Android uses. */
    fun formatShortFileSize(context: Context?, sizeBytes: Long): String {
        val units = listOf("B", "kB", "MB", "GB", "TB")
        var value = sizeBytes.toDouble()
        var unit = 0
        while (value >= 900 && unit < units.lastIndex) {
            value /= 1000
            unit++
        }
        val number = if (unit == 0 || value >= 10) "%.0f".format(Locale.getDefault(), value) else "%.1f".format(Locale.getDefault(), value)
        return "$number ${units[unit]}"
    }
}

/** A decoded picture, where Android has a Bitmap. */
fun org.jetbrains.skia.Image.asImageBitmap(): ImageBitmap = toComposeImageBitmap()

/** Android's file pickers, as the system's Open and Save dialogs. */
abstract class ActivityResultContract<I, O> {
    internal abstract fun run(parent: Frame?, input: I): O
}

class ActivityResultLauncher<I> internal constructor(private val start: (I) -> Unit) {
    fun launch(input: I) = start(input)
}

class PickVisualMediaRequest(val mediaType: ActivityResultContracts.PickVisualMedia.VisualMediaType)

object ActivityResultContracts {
    /** Any file; the MIME types are not used to filter. */
    class OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        override fun run(parent: Frame?, input: Array<String>): Uri? =
            openFileDialog(parent, Resources.string(R.string.core_desktop_open))?.let(::Uri)
    }

    /** Input: the suggested file name. The MIME type is Android's; the Save dialog has no use for it. */
    class CreateDocument(mimeType: String) : ActivityResultContract<String, Uri?>() {
        override fun run(parent: Frame?, input: String): Uri? =
            saveFileDialog(parent, Resources.string(R.string.core_desktop_save), input)?.let(::Uri)
    }

    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType

        object ImageOnly : VisualMediaType

        object ImageAndVideo : VisualMediaType

        override fun run(parent: Frame?, input: PickVisualMediaRequest): Uri? =
            openFileDialog(parent, Resources.string(R.string.core_desktop_choose_picture), PICTURES)?.let(::Uri)
    }

    private val PICTURES = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic")
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
    contract: ActivityResultContract<I, O>,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> {
    val parent = LocalAppWindow.current
    val latestContract = rememberUpdatedState(contract)
    val latestResult = rememberUpdatedState(onResult)
    return remember { ActivityResultLauncher { input -> latestResult.value(latestContract.value.run(parent, input)) } }
}
