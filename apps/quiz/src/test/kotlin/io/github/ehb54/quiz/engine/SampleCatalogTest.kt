package io.github.ehb54.quiz.engine

import io.github.ehb54.quiz.model.Quiz
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * samples/catalog.json must describe the sample files exactly, with the app's
 * own fingerprints. When a sample changes, regenerate it with
 * `tools/ci-build.sh :apps:quiz:testDebugUnitTest --tests '*SampleCatalogTest*' -PwriteCatalog=true`.
 */
class SampleCatalogTest {
    private val online = File("samples")
    private val builtIn = File("src/main/assets/samples")

    @Test
    fun catalogDescribesTheSampleFiles() {
        val onlineFiles = online.listFiles { f -> f.isFile && f.name != "catalog.json" }.orEmpty().toList()
        val builtInFiles = builtIn.listFiles { f -> f.isFile }.orEmpty().toList()
        val both = onlineFiles.map { it.name }.intersect(builtInFiles.map { it.name }.toSet())
        assertEquals("sample files in both folders", emptySet<String>(), both)

        val catalog = buildCatalog(
            (onlineFiles + builtInFiles).associate { it.name to it.readBytes() },
            builtInFiles.map { it.name }.toSet(),
        )
        val file = File(online, "catalog.json")
        if (System.getProperty("writeCatalog") == "true") file.writeText(catalog.toJson())
        assertEquals("samples/catalog.json is out of date (see this test's comment)", catalog.toJson(), file.readText())
        assertEquals(catalog, parseCatalog(file.readText()))
    }

    @Test
    fun workbookTabsAreSeparateQuizzes() {
        val entries = sampleEntries("German starter.xlsx", File(builtIn, "German starter.xlsx").readBytes())
        assertEquals(listOf("Numbers", "Colors"), entries.map { it.name })
        assertEquals(listOf("German starter.xlsx#Numbers", "German starter.xlsx#Colors"), entries.map { it.source })
        assertEquals(listOf(10, 8), entries.map { it.cards })
    }

    @Test
    fun zipsCountTheirPicturesAndSounds() {
        val shapes = sampleEntries("Shapes.zip", File(builtIn, "Shapes.zip").readBytes()).single()
        assertEquals(listOf(6, 6, 0), listOf(shapes.cards, shapes.pictures, shapes.sounds))
        val morse = sampleEntries("Morse code.zip", File(builtIn, "Morse code.zip").readBytes()).single()
        assertEquals(listOf(26, 0, 26), listOf(morse.cards, morse.pictures, morse.sounds))
    }

    @Test
    fun soundQuizzesCountTheirSounds() {
        // Picture quizzes speak each word after the answer; listening and dictation quizzes ask with the sound.
        fun counts(file: String) = sampleEntries(file, File(online, file).readBytes()).single()
            .let { listOf(it.cards, it.pictures, it.sounds) }
        assertEquals(listOf(144, 144, 144), counts("Core verb pictures ERNIE.zip"))
        assertEquals(listOf(144, 144, 144), counts("Core verb listening ERNIE.zip"))
        assertEquals(listOf(144, 0, 144), counts("Core verb dictation.zip"))
    }

    @Test
    fun statusComparesTheFingerprint() {
        val entry = SampleEntry("Shapes", "Shapes.zip", "v2", 6, 6)
        val added = Quiz("q", "Shapes", source = "Shapes.zip", sourceVersion = "v2")
        assertEquals(SampleStatus.NotAdded, sampleStatus(entry, null))
        assertEquals(SampleStatus.Added, sampleStatus(entry, added))
        assertEquals(SampleStatus.UpdateAvailable, sampleStatus(entry, added.copy(sourceVersion = "v1")))
    }
}
