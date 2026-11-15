package io.github.ehb54.quiz.ui

// The desktop's version of the Android app's ui/Device.kt, with the same API.

/** The system, for issue reports: "Mac OS X 15.5 (aarch64), Java 21.0.7". */
fun deviceDescription(): String {
    fun property(name: String) = System.getProperty(name).orEmpty()
    return "${property("os.name")} ${property("os.version")} (${property("os.arch")}), Java ${property("java.version")}"
}
