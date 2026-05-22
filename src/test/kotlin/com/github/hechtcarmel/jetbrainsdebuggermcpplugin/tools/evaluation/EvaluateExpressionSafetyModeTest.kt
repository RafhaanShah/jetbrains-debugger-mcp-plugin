package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateExpressionSafetyModeTest {

    @Test
    fun `fromId resolves all stable ids`() {
        assertEquals(EvaluateExpressionSafetyMode.UNRESTRICTED, EvaluateExpressionSafetyMode.fromId("unrestricted"))
        assertEquals(EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST, EvaluateExpressionSafetyMode.fromId("default_blocklist"))
        assertEquals(EvaluateExpressionSafetyMode.READ_ONLY, EvaluateExpressionSafetyMode.fromId("read_only"))
    }

    @Test
    fun `fromId falls back to unrestricted for null or unknown ids`() {
        assertEquals(EvaluateExpressionSafetyMode.UNRESTRICTED, EvaluateExpressionSafetyMode.fromId(null))
        assertEquals(EvaluateExpressionSafetyMode.UNRESTRICTED, EvaluateExpressionSafetyMode.fromId(""))
        assertEquals(EvaluateExpressionSafetyMode.UNRESTRICTED, EvaluateExpressionSafetyMode.fromId("legacy-value"))
    }

    @Test
    fun `labels are suitable for settings UI`() {
        assertEquals("Unrestricted", EvaluateExpressionSafetyMode.UNRESTRICTED.toString())
        assertEquals("Default blocklist", EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST.toString())
        assertEquals("Read-only", EvaluateExpressionSafetyMode.READ_ONLY.toString())
    }

    @Test
    fun `settings explanations make restrictions clear`() {
        assertTrue(EvaluateExpressionSafetyMode.UNRESTRICTED.explanation.contains("No plugin-side filtering"))
        assertTrue(EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST.explanation.contains("process execution"))
        assertTrue(EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST.explanation.contains("custom regex"))
        assertTrue(EvaluateExpressionSafetyMode.READ_ONLY.explanation.contains("assignments"))
        assertTrue(EvaluateExpressionSafetyMode.READ_ONLY.explanation.contains("method calls"))
    }
}
