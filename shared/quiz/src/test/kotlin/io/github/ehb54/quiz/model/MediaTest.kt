package io.github.ehb54.quiz.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaTest {
    @Test
    fun kindComesFromTheExtension() {
        assertEquals(MediaKind.Picture, mediaKind("a1b2.png"))
        assertEquals(MediaKind.Picture, mediaKind("photo.JPG"))
        assertEquals(MediaKind.Sound, mediaKind("c3d4.wav"))
        assertEquals(MediaKind.Sound, mediaKind("song.M4A"))
        assertNull(mediaKind("notes.txt"))
        assertNull(mediaKind("no-extension"))
        assertTrue(isSound("x.mp3"))
        assertFalse(isSound("x.png"))
        assertFalse(isSound(null))
    }

    @Test
    fun aSoundIsPlayedNeverTyped() {
        val quiz = Quiz("q", "Morse")
            .addCard("", "A", mediaA = "a.wav")
            .addCard("", "", mediaA = "b.wav", mediaB = "b.png")

        // Hear it, type the letter: typing asks A -> B only.
        val letter = quiz.cards[0]
        assertTrue(letter.canAsk(QuizMode.Typed, Direction.AToB))
        assertFalse(letter.canAsk(QuizMode.Typed, Direction.BToA))
        assertTrue(letter.canAsk(QuizMode.Pick, Direction.BToA))
        assertTrue(letter.canAsk(QuizMode.Match, Direction.AToB))

        // A sound with a picture: nothing to type, but it can be picked and matched.
        val picture = quiz.cards[1]
        assertFalse(picture.canAsk(QuizMode.Typed, Direction.AToB))
        assertFalse(picture.canAsk(QuizMode.Typed, Direction.BToA))
        assertTrue(picture.canAsk(QuizMode.Pick, Direction.AToB))
        assertTrue(picture.canAsk(QuizMode.Match, Direction.BToA))
        assertEquals(setOf("a.wav", "b.wav", "b.png"), quiz.mediaFiles())
    }

    @Test
    fun quizzesSavedBeforeSoundsStillLoadTheirPictures() {
        val saved = """{"id":3,"a":"","b":"circle","imageA":"c1.png"}"""
        val card = Json.decodeFromString(Card.serializer(), saved)
        assertEquals("c1.png", card.mediaA)
        assertNull(card.mediaB)
        // And they are saved under the same name, so an older version still reads them.
        assertTrue(Json.encodeToString(Card.serializer(), card).contains("\"imageA\":\"c1.png\""))
    }
}
