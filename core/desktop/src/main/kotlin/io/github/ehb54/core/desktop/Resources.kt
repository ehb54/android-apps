package io.github.ehb54.core.desktop

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.w3c.dom.Element
import java.util.Locale
import java.util.ServiceLoader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * One module's Android resources, as build-logic's GenerateAndroidResources
 * writes them: names in id order, files under [root] on the classpath.
 */
interface ResourceTable {
    val packageId: Int
    val root: String
    val strings: List<String>
    val plurals: List<String>
    val drawables: List<String>
    val colors: List<String>
}

/**
 * Android resources on the desktop: the strings, plurals and vector icons of
 * the Android app (and core/ui), looked up by the same `R` ids, so screens
 * written for Android run unchanged. Text follows the default locale
 * (`values-de` for German, falling back to `values`).
 */
object Resources {
    private class Loaded(
        val table: ResourceTable,
        val strings: Map<String, String>,
        val plurals: Map<String, Map<String, String>>,
        val colors: Map<String, String>,
    )

    private val tables: Map<Int, Loaded> by lazy {
        ServiceLoader.load(ResourceTable::class.java, Resources::class.java.classLoader)
            .associate { it.packageId to load(it) }
    }

    private val vectors = mutableMapOf<Int, ImageVector>()

    fun string(@StringRes id: Int, vararg args: Any): String = format(template(id), args)

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String {
        val forms = table(id).plurals[name(id)] ?: missing(id)
        val form = forms[quantity(count)] ?: forms["other"] ?: forms.values.first()
        return format(form, args)
    }

    // The names Android code calls on context.resources and context.
    fun getString(@StringRes id: Int, vararg formatArgs: Any): String = string(id, *formatArgs)

    fun getQuantityString(@PluralsRes id: Int, quantity: Int, vararg formatArgs: Any): String =
        plural(id, quantity, *formatArgs)

    /** A color resource ("#2B4C7E") as Compose's Color. */
    fun color(@ColorRes id: Int): Color = parseColor(table(id).colors[name(id)] ?: missing(id)) ?: missing(id)

    /** An adaptive launcher icon: [background] color behind the [foreground] vector, as a rounded square. */
    fun launcherIcon(@DrawableRes foreground: Int, @ColorRes background: Int): ImageVector {
        val loaded = table(foreground)
        val path = "${loaded.table.root}/drawable/${name(foreground)}.xml"
        val stream = loaded.table.javaClass.classLoader.getResourceAsStream(path) ?: missing(foreground)
        return stream.use { loadVector(it, color(background)) }
    }

    /**
     * The app's own launcher icon, from the names every Android app's adaptive
     * icon uses: drawable `ic_launcher_foreground` over color `ic_launcher_background`.
     */
    fun launcherIcon(): ImageVector = launcherIcon(
        id(APP_PACKAGE, DRAWABLE, "ic_launcher_foreground"),
        id(APP_PACKAGE, COLOR, "ic_launcher_background"),
    )

    @Synchronized
    fun vector(@DrawableRes id: Int): ImageVector = vectors.getOrPut(id) {
        val loaded = table(id)
        val path = "${loaded.table.root}/drawable/${name(id)}.xml"
        val stream = loaded.table.javaClass.classLoader.getResourceAsStream(path) ?: missing(id)
        stream.use { loadVector(it) }
    }

    private fun template(id: Int) = table(id).strings[name(id)] ?: missing(id)

    private fun table(id: Int) = tables[id ushr 24] ?: missing(id)

    private fun name(id: Int): String =
        names(table(id).table, (id ushr 16) and 0xff)?.getOrNull(id and 0xffff) ?: missing(id)

    /** The names of one type of resource, in id order; the type is the id's third byte. */
    private fun names(table: ResourceTable, type: Int) = when (type) {
        STRING -> table.strings
        PLURALS -> table.plurals
        DRAWABLE -> table.drawables
        COLOR -> table.colors
        else -> null
    }

    /** The id GenerateAndroidResources gave a resource: package, type and index, a byte, a byte and two bytes. */
    private fun id(packageId: Int, type: Int, name: String): Int {
        val table = tables[packageId]?.table ?: error("No resources in package 0x%02x".format(packageId))
        val index = names(table, type)?.indexOf(name)?.takeIf { it >= 0 } ?: error("No resource $name")
        return (packageId shl 24) or (type shl 16) or index
    }

    private fun missing(id: Int): Nothing = error("No resource 0x%08x".format(id))

    private const val APP_PACKAGE = 0x7f
    private const val STRING = 1
    private const val PLURALS = 2
    private const val DRAWABLE = 3
    private const val COLOR = 4

    /** Android's "one" for English and most European languages; French counts 0 as one. */
    private fun quantity(count: Int): String {
        val one = if (Locale.getDefault().language == "fr") count in 0..1 else count == 1
        return if (one) "one" else "other"
    }

    private fun format(template: String, args: Array<out Any>) =
        if (args.isEmpty()) template else String.format(Locale.getDefault(), template, *args)

    private fun load(table: ResourceTable): Loaded {
        val strings = mutableMapOf<String, String>()
        val plurals = mutableMapOf<String, Map<String, String>>()
        val colors = mutableMapOf<String, String>()
        val locale = Locale.getDefault()
        // Most general first, so the locale's own folder wins.
        val folders = listOfNotNull(
            "values",
            "values-${locale.language}",
            locale.country.takeIf { it.isNotEmpty() }?.let { "values-${locale.language}-r$it" },
        )
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        for (folder in folders) {
            val stream = table.javaClass.classLoader.getResourceAsStream("${table.root}/$folder/strings.xml") ?: continue
            val nodes = stream.use { builder.parse(it) }.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                val name = element.getAttribute("name")
                when (element.tagName) {
                    "string" -> strings[name] = androidText(element.textContent)
                    "color" -> colors[name] = element.textContent.trim()
                    "plurals" -> {
                        val items = element.getElementsByTagName("item")
                        plurals[name] = (0 until items.length).associate { j ->
                            val item = items.item(j) as Element
                            item.getAttribute("quantity") to androidText(item.textContent)
                        }
                    }
                }
            }
        }
        return Loaded(table, strings, plurals, colors)
    }
}

/**
 * A string resource's text as Android reads it: runs of white space become one
 * space unless the text is in double quotes, and backslash escapes are decoded.
 */
internal fun androidText(raw: String): String {
    val trimmed = raw.trim()
    val text = if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
        trimmed.substring(1, trimmed.length - 1)
    } else {
        trimmed.replace(Regex("\\s+"), " ")
    }
    val out = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '\\' && i + 1 < text.length) {
            val next = text[i + 1]
            when (next) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                'u' -> if (i + 5 < text.length) {
                    out.append(text.substring(i + 2, i + 6).toInt(16).toChar())
                    i += 4
                }
                else -> out.append(next)
            }
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}

/** Android's `stringResource`, for the desktop. */
@Composable
fun stringResource(@StringRes id: Int, vararg formatArgs: Any): String = Resources.string(id, *formatArgs)

/** Android's `pluralStringResource`, for the desktop. */
@Composable
fun pluralStringResource(@PluralsRes id: Int, count: Int): String = Resources.plural(id, count)

@Composable
fun pluralStringResource(@PluralsRes id: Int, count: Int, vararg formatArgs: Any): String =
    Resources.plural(id, count, *formatArgs)

/** Android's `painterResource` for vector drawables, for the desktop. */
@Composable
fun painterResource(@DrawableRes id: Int): Painter = rememberVectorPainter(remember(id) { Resources.vector(id) })
