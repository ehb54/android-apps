package io.github.ehb54.quiz.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SpreadsheetTest {
    private val main = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private val rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    /** A two-tab workbook laid out the way Google Sheets exports one. */
    private fun workbook(): ByteArray {
        val files = mapOf(
            "xl/workbook.xml" to """<workbook xmlns="$main" xmlns:r="$rel"><sheets>
                <sheet name="Nouns" sheetId="1" r:id="rId1"/><sheet name="Explanation" sheetId="2" r:id="rId2"/>
                </sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="x" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="x" Target="/xl/worksheets/sheet2.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="$main"><si><t>das Jahr</t></si><si><t>year</t></si>
                <si><r><t>die </t></r><r><t>Zeit</t></r></si><si><t xml:space="preserve">time
(hour)</t></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="$main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c></row>
                <row r="3"><c r="A3" t="inlineStr"><is><t>nur A</t></is></c></row>
                <row r="4"><c r="B4" t="inlineStr"><is><t>only B</t></is></c></row>
                <row r="5"><c r="A5"><v>42</v></c><c r="B5" t="inlineStr"><is><t>  forty-two </t></is></c></row>
                </sheetData></worksheet>""",
            "xl/worksheets/sheet2.xml" to """<worksheet xmlns="$main"><sheetData>
                <row r="1"><c r="A1" t="inlineStr"><is><t>Read me first</t></is></c></row></sheetData></worksheet>""",
        )
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, xml) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun readsEveryTabInOrder() {
        val entries = unzip(workbook().inputStream())
        assertTrue(isXlsx(entries))
        val sheets = readXlsx(entries)
        assertEquals(listOf("Nouns", "Explanation"), sheets.map { it.name })
        assertEquals(listOf("", "only B"), sheets[0].rows[3])
        assertEquals(listOf("42", "  forty-two "), sheets[0].rows[4])
    }

    @Test
    fun cardsNeedBothSides() {
        val (nouns, explanation) = readXlsx(unzip(workbook().inputStream()))
        assertEquals(
            listOf(QzxCard("das Jahr", "year"), QzxCard("die Zeit", "time (hour)"), QzxCard("42", "forty-two")),
            cardsFromSheet(nouns),
        )
        assertTrue(cardsFromSheet(explanation).isEmpty())
    }

    @Test
    fun aQuizZipIsNotAWorkbook() {
        val out = ByteArrayOutputStream()
        writeZip(out, "q", "a\nb\n", emptyMap())
        assertFalse(isXlsx(unzip(out.toByteArray().inputStream())))
    }

    @Test
    fun columnLettersToIndex() {
        assertEquals(listOf(0, 1, 25, 26, 27, 701), listOf("A1", "B7", "Z9", "AA3", "AB1", "ZZ2").map(::columnIndex))
    }
}
