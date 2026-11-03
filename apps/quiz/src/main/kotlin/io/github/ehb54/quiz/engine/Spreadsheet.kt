package io.github.ehb54.quiz.engine

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Excel workbooks (.xlsx), as Google Sheets and Excel save them: each tab can
 * become a quiz, side A from its first column and side B from its second.
 */
class Sheet(val name: String, val rows: List<List<String>>)

private const val MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
private const val RELATIONSHIPS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
private const val PACKAGE = "http://schemas.openxmlformats.org/package/2006/relationships"

private val whitespace = Regex("\\s+")

/** Whether unzipped [entries] are an Excel workbook. */
fun isXlsx(entries: Map<String, ByteArray>) = "xl/workbook.xml" in entries

/** Every tab of a workbook, in tab order, with its rows of cell text. */
fun readXlsx(entries: Map<String, ByteArray>): List<Sheet> {
    val shared = entries["xl/sharedStrings.xml"]
        ?.let { parse(it).documentElement.children("si").map(::cellText) }
        .orEmpty()
    val targets = parse(entries.getValue("xl/_rels/workbook.xml.rels")).documentElement
        .children("Relationship", PACKAGE)
        .associate { it.getAttribute("Id") to it.getAttribute("Target") }
    val tabs = parse(entries.getValue("xl/workbook.xml")).documentElement
        .children("sheets").firstOrNull()?.children("sheet").orEmpty()
    return tabs.mapNotNull { tab ->
        val target = targets[tab.getAttributeNS(RELATIONSHIPS, "id")] ?: return@mapNotNull null
        val xml = entries[if (target.startsWith("/")) target.drop(1) else "xl/$target"] ?: return@mapNotNull null
        Sheet(tab.getAttribute("name"), readRows(parse(xml).documentElement, shared))
    }
}

/** A tab's first two columns as cards; rows missing either side are left out. */
fun cardsFromSheet(sheet: Sheet): List<QzxCard> = sheet.rows.mapNotNull { row ->
    val a = row.getOrNull(0).orEmpty().replace(whitespace, " ").trim()
    val b = row.getOrNull(1).orEmpty().replace(whitespace, " ").trim()
    if (a.isNotEmpty() && b.isNotEmpty()) QzxCard(a, b) else null
}

private fun readRows(worksheet: Element, shared: List<String>): List<List<String>> {
    val data = worksheet.children("sheetData").firstOrNull() ?: return emptyList()
    return data.children("row").map { row ->
        val cells = sortedMapOf<Int, String>()
        row.children("c").forEachIndexed { position, cell ->
            val reference = cell.getAttribute("r")
            cells[if (reference.isEmpty()) position else columnIndex(reference)] = cellValue(cell, shared)
        }
        if (cells.isEmpty()) emptyList() else (0..cells.lastKey()).map { cells[it].orEmpty() }
    }
}

private fun cellValue(cell: Element, shared: List<String>): String {
    val value = cell.children("v").firstOrNull()?.textContent
    return when (cell.getAttribute("t")) {
        "s" -> value?.toIntOrNull()?.let(shared::getOrNull).orEmpty()
        "inlineStr" -> cell.children("is").firstOrNull()?.let(::cellText).orEmpty()
        "b" -> if (value == "1") "TRUE" else "FALSE"
        else -> value.orEmpty()
    }
}

/** Text of a shared or inline string: plain, or rich-text runs (phonetic hints left out). */
private fun cellText(string: Element): String =
    string.children("t").joinToString("") { it.textContent } +
        string.children("r").joinToString("") { run -> run.children("t").joinToString("") { it.textContent } }

/** "B7" -> 1, "AA3" -> 26. */
internal fun columnIndex(reference: String): Int =
    reference.takeWhile { it.isLetter() }.fold(0) { n, letter -> n * 26 + (letter.uppercaseChar() - 'A' + 1) } - 1

private fun Element.children(name: String, namespace: String = MAIN): List<Element> {
    val found = mutableListOf<Element>()
    var node = firstChild
    while (node != null) {
        if (node is Element && node.namespaceURI == namespace && node.localName == name) found += node
        node = node.nextSibling
    }
    return found
}

private fun parse(xml: ByteArray): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // No DOCTYPEs, so a crafted file cannot pull in external entities. Android's
        // parser does not offer the switch, and does not load external entities anyway.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
}
