package io.github.ehb54.core.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourcesTest {
    @Test
    fun stringsAreFormattedAsOnAndroid() {
        // core/ui's own strings, read through core/desktop's R.
        assertEquals("Version 1.2 (7)", Resources.string(R.string.core_ui_version, "1.2", 7))
        assertEquals("Open", Resources.string(R.string.core_desktop_open))
    }

    @Test
    fun androidTextRules() {
        assertEquals("one two", androidText("  one\n    two  "))
        assertEquals("  kept  ", androidText("\"  kept  \""))
        assertEquals("it's \"x\"\nnext", androidText("it\\'s \\\"x\\\"\\nnext"))
        assertEquals("é", androidText("\\u00e9"))
    }

    @Test
    fun vectorIconsLoad() {
        val add = Resources.vector(R.drawable.core_ui_ic_add)
        assertEquals(24f, add.viewportWidth)
        assertTrue(add.root.size > 0)
    }

    @Test
    fun fileSizesReadLikeAndroid() {
        assertEquals("870 B", Formatter.formatShortFileSize(null, 870))
        assertEquals("1.2 kB", Formatter.formatShortFileSize(null, 1_234))
        assertEquals("45 MB", Formatter.formatShortFileSize(null, 45_000_000))
    }
}
