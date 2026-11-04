// Quiz for macOS, Windows and Linux: the Android app's screens and rules
// (apps/quiz, shared/quiz) compiled for the desktop. Only the window (Main.kt),
// picture decoding (data/Images.kt) and sound playing (ui/SoundPlayer.kt) are
// the desktop's own.
plugins {
    alias(libs.plugins.ehb54.desktop.application)
}

compose.desktop {
    application {
        mainClass = "io.github.ehb54.quiz.MainKt"
        nativeDistributions {
            packageName = "Quiz"
            description = "Flash cards"
            windows {
                // Fixed for good: Windows uses it to install a new version over the old one.
                upgradeUuid = "f7dd0f8c-5360-4d6d-bb82-205cab6fcbb4"
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    // The UI test reads the repository's samples instead of the download site.
    systemProperty("quiz.samples", rootDir.resolve("apps/quiz/samples").toURI().toString())
}
