package io.github.ehb54.quiz.ui

import android.os.Build

/** The system and device, for issue reports: "Android 9 (API 28), samsung SM-G955F". The desktop has its own. */
fun deviceDescription(): String =
    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
