package io.github.ehb54.core.desktop

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

private const val ANDROID = "http://schemas.android.com/apk/res/android"

/**
 * An Android vector drawable (`<vector>` with paths and groups, fill and stroke
 * colors as literals) as an ImageVector. [background], when set, is painted
 * first as a rounded square: an adaptive icon's background color plus its
 * foreground drawable then make the launcher icon.
 */
fun loadVector(stream: InputStream, background: Color? = null): ImageVector {
    val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
    val root = factory.newDocumentBuilder().parse(stream).documentElement
    val width = root.float("viewportWidth") ?: 24f
    val height = root.float("viewportHeight") ?: 24f
    val builder = ImageVector.Builder(
        defaultWidth = (root.dp("width") ?: width).dp,
        defaultHeight = (root.dp("height") ?: height).dp,
        viewportWidth = width,
        viewportHeight = height,
        autoMirror = root.attr("autoMirrored") == "true",
    )
    if (background != null) {
        val r = minOf(width, height) * 0.2f
        builder.addPath(
            addPathNodes(
                "M$r,0h${width - 2 * r}a$r,$r 0 0 1 $r,${r}v${height - 2 * r}a$r,$r 0 0 1 -$r,${r}" +
                    "h${-(width - 2 * r)}a$r,$r 0 0 1 -$r,-${r}v${-(height - 2 * r)}a$r,$r 0 0 1 $r,-${r}z",
            ),
            fill = SolidColor(background),
        )
    }
    addChildren(builder, root)
    return builder.build()
}

private fun addChildren(builder: ImageVector.Builder, parent: Element) {
    val nodes = parent.childNodes
    for (i in 0 until nodes.length) {
        val element = nodes.item(i) as? Element ?: continue
        when (element.localName ?: element.tagName) {
            "path" -> builder.addPath(
                pathData = addPathNodes(element.attr("pathData")),
                pathFillType = if (element.attr("fillType") == "evenOdd") PathFillType.EvenOdd else PathFillType.NonZero,
                fill = element.brush("fillColor"),
                fillAlpha = element.float("fillAlpha") ?: 1f,
                stroke = element.brush("strokeColor"),
                strokeAlpha = element.float("strokeAlpha") ?: 1f,
                strokeLineWidth = element.float("strokeWidth") ?: 0f,
                strokeLineCap = when (element.attr("strokeLineCap")) {
                    "round" -> StrokeCap.Round
                    "square" -> StrokeCap.Square
                    else -> StrokeCap.Butt
                },
                strokeLineJoin = when (element.attr("strokeLineJoin")) {
                    "round" -> StrokeJoin.Round
                    "bevel" -> StrokeJoin.Bevel
                    else -> StrokeJoin.Miter
                },
                strokeLineMiter = element.float("strokeMiterLimit") ?: 4f,
            )
            "group" -> {
                builder.addGroup(
                    rotate = element.float("rotation") ?: 0f,
                    pivotX = element.float("pivotX") ?: 0f,
                    pivotY = element.float("pivotY") ?: 0f,
                    scaleX = element.float("scaleX") ?: 1f,
                    scaleY = element.float("scaleY") ?: 1f,
                    translationX = element.float("translateX") ?: 0f,
                    translationY = element.float("translateY") ?: 0f,
                )
                addChildren(builder, element)
                builder.clearGroup()
            }
        }
    }
}

private fun Element.attr(name: String): String = getAttributeNS(ANDROID, name)

private fun Element.float(name: String): Float? = attr(name).toFloatOrNull()

/** "24dp" as 24. */
private fun Element.dp(name: String): Float? = attr(name).removeSuffix("dp").toFloatOrNull()

private fun Element.brush(name: String): Brush? = parseColor(attr(name))?.let(::SolidColor)

/** #RGB, #ARGB, #RRGGBB or #AARRGGBB; null for none or a reference such as @color/x. */
internal fun parseColor(value: String): Color? {
    val hex = value.takeIf { it.startsWith("#") }?.substring(1) ?: return null
    val full = when (hex.length) {
        3 -> "ff" + hex.map { "$it$it" }.joinToString("")
        4 -> hex.map { "$it$it" }.joinToString("")
        6 -> "ff$hex"
        8 -> hex
        else -> return null
    }
    return Color(full.toLong(16).toInt())
}
