package io.github.ehb54.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Android resources for a desktop module: the strings, plurals and vector
 * drawables of [resDirs] (Android res folders), so a desktop app shows its
 * Android app's text and icons without a copy that could drift.
 *
 * Writes an `R` object in [packageName] with Android-style Int ids
 * (`R.string.ok`, `R.plurals.card_count`, `R.drawable.ic_add`), so code written
 * for Android compiles unchanged, and copies the files under
 * `android-res/<package>/` on the classpath, where core/desktop's
 * `stringResource`, `pluralStringResource` and `painterResource` read them.
 * A later folder in [resDirs] overrides an earlier one's entries of the same name.
 */
@CacheableTask
abstract class GenerateAndroidResources : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val resDirs: ConfigurableFileCollection

    /** Android assets folders (src/main/assets), served under `android-assets/` with an `.index` of their files. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirs: ConfigurableFileCollection

    @get:Input
    abstract val packageName: Property<String>

    /** The top byte of every id, so two modules' ids never clash (Android apps use 0x7f). */
    @get:Input
    abstract val packageId: Property<Int>

    @get:OutputDirectory
    abstract val kotlinDir: DirectoryProperty

    @get:OutputDirectory
    abstract val resourcesDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = packageName.get()
        val kotlinOut = kotlinDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val resOut = resourcesDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val root = File(resOut, "android-res/$pkg")

        // Per values folder ("values", "values-de"): name -> <string> or <plurals> element, later folders winning.
        val values = sortedMapOf<String, LinkedHashMap<String, Element>>()
        val drawables = sortedMapOf<String, File>()
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        for (dir in resDirs.files.filter { it.isDirectory }) {
            for (folder in dir.listFiles().orEmpty().sortedBy { it.name }) {
                when {
                    folder.name == "values" || folder.name.startsWith("values-") -> {
                        val entries = values.getOrPut(folder.name) { LinkedHashMap() }
                        for (file in folder.listFiles { f -> f.extension == "xml" }.orEmpty().sortedBy { it.name }) {
                            val elements = builder.parse(file).documentElement.childNodes
                            for (i in 0 until elements.length) {
                                val element = elements.item(i) as? Element ?: continue
                                if (element.tagName in listOf("string", "plurals", "color")) {
                                    entries[element.getAttribute("name")] = element
                                }
                            }
                        }
                    }
                    folder.name == "drawable" ->
                        folder.listFiles { f -> f.extension == "xml" }.orEmpty().forEach { drawables[it.nameWithoutExtension] = it }
                }
            }
        }

        // One merged file per values folder, holding only strings, plurals and colors.
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        for ((folder, entries) in values) {
            val document = builder.newDocument()
            val resources = document.createElement("resources").also(document::appendChild)
            entries.values.forEach { resources.appendChild(document.importNode(it, true)) }
            val target = File(root, "$folder/strings.xml").apply { parentFile.mkdirs() }
            transformer.transform(DOMSource(document), StreamResult(target))
        }
        for ((name, file) in drawables) {
            file.copyTo(File(root, "drawable/$name.xml"), overwrite = true)
        }

        val assets = File(resOut, "android-assets")
        val index = sortedSetOf<String>()
        for (dir in assetDirs.files.filter { it.isDirectory }) {
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relative = file.relativeTo(dir).invariantSeparatorsPath
                file.copyTo(File(assets, relative), overwrite = true)
                index += relative
            }
        }
        if (index.isNotEmpty()) File(assets, ".index").writeText(index.joinToString("\n", postfix = "\n"))

        // Ids come from the default folder: every string must have a default, as on Android.
        val defaults = values["values"].orEmpty()
        val strings = defaults.filterValues { it.tagName == "string" }.keys.sorted()
        val plurals = defaults.filterValues { it.tagName == "plurals" }.keys.sorted()
        val colors = defaults.filterValues { it.tagName == "color" }.keys.sorted()
        val icons = drawables.keys.toList()
        val top = packageId.get() shl 24
        fun constants(type: Int, names: List<String>) =
            names.withIndex().joinToString("\n") { (i, name) ->
                "        const val `$name`: Int = 0x%08x".format(top or (type shl 16) or i)
            }
        fun list(names: List<String>) = names.joinToString(", ") { "\"$it\"" }

        val dir = File(kotlinOut, pkg.replace('.', '/')).apply { mkdirs() }
        File(dir, "R.kt").writeText(
            """
            |// Generated by build-logic's GenerateAndroidResources from Android res folders. Do not edit.
            |package $pkg
            |
            |@Suppress("ClassName", "ObjectPropertyName", "unused")
            |object R {
            |    object string {
            |${constants(1, strings)}
            |    }
            |
            |    object plurals {
            |${constants(2, plurals)}
            |    }
            |
            |    object drawable {
            |${constants(3, icons)}
            |    }
            |
            |    object color {
            |${constants(4, colors)}
            |    }
            |}
            |
            |/** Found by core/desktop's Resources through META-INF/services. */
            |class GeneratedResources : io.github.ehb54.core.desktop.ResourceTable {
            |    override val packageId = ${packageId.get()}
            |    override val root = "android-res/$pkg"
            |    override val strings = listOf<String>(${list(strings)})
            |    override val plurals = listOf<String>(${list(plurals)})
            |    override val drawables = listOf<String>(${list(icons)})
            |    override val colors = listOf<String>(${list(colors)})
            |}
            |
            """.trimMargin(),
        )
        File(resOut, "META-INF/services/io.github.ehb54.core.desktop.ResourceTable")
            .apply { parentFile.mkdirs() }
            .writeText("$pkg.GeneratedResources\n")
    }
}
