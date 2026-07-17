package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachAndroidDebuggerToolTest {

    @Test
    fun `matching filters by device package process and pid`() {
        val candidates = listOf(
            candidate("emulator-5554", 1, "com.example", "com.example"),
            candidate("emulator-5554", 2, "com.example", "com.example:remote"),
            candidate("device-123", 3, "com.other", "com.other")
        )

        val matches = candidates.matching(
            AndroidAttachFilters(
                deviceSerial = "emulator-5554",
                packageName = "com.example",
                processName = "com.example:remote",
                pid = 2
            )
        )

        assertEquals(1, matches.size)
        assertEquals(2, matches.single().pid)
    }

    @Test
    fun `matching with empty filters returns every candidate`() {
        val candidates = listOf(
            candidate("emulator-5554", 1, "com.example", "com.example"),
            candidate("device-123", 2, "com.other", "com.other")
        )

        assertEquals(candidates, candidates.matching(AndroidAttachFilters()))
    }

    @Test
    fun `matching returns empty when target does not exist`() {
        val candidates = listOf(candidate("emulator-5554", 1, "com.example", "com.example"))

        assertTrue(candidates.matching(AndroidAttachFilters(packageName = "missing")).isEmpty())
    }

    private fun candidate(
        deviceSerial: String,
        pid: Int,
        packageName: String,
        processName: String
    ): AndroidProcessCandidate {
        return AndroidProcessCandidate(
            deviceSerial = deviceSerial,
            pid = pid,
            packageName = packageName,
            processName = processName,
            client = Any()
        )
    }
}
