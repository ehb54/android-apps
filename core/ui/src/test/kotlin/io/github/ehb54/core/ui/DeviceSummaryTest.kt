package io.github.ehb54.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSummaryTest {
    @Test
    fun formatsReleaseApiAndModel() {
        assertEquals(
            "Android 9 (API 28), samsung SM-G955U",
            deviceSummary("9", 28, "samsung", "SM-G955U"),
        )
    }
}
