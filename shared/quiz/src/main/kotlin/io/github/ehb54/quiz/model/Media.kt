package io.github.ehb54.quiz.model

/** What a card side's file holds. */
enum class MediaKind { Picture, Sound }

/** Extensions read as pictures, in zips and in the app's media folder. */
val PICTURE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

/** Extensions read as sounds: formats Android 8 and 9 play. */
val SOUND_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "wav", "flac")

/** A file's kind, by its extension (the app names the files it stores), or null for neither. */
fun mediaKind(name: String): MediaKind? = when (name.substringAfterLast('.', "").lowercase()) {
    in PICTURE_EXTENSIONS -> MediaKind.Picture
    in SOUND_EXTENSIONS -> MediaKind.Sound
    else -> null
}

fun isSound(name: String?) = name != null && mediaKind(name) == MediaKind.Sound
