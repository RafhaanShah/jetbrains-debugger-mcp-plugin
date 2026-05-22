package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.settings

import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation.CustomEvaluateExpressionBlockRule
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation.EvaluateExpressionSafetyMode
import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class McpSettingsEvaluateExpressionSafetyTest {

    @Test
    fun `settings default evaluate expression safety mode is unrestricted`() {
        val settings = McpSettings()

        assertEquals(EvaluateExpressionSafetyMode.UNRESTRICTED, settings.evaluateExpressionSafetyMode)
    }

    @Test
    fun `settings persists evaluate expression safety mode by stable id`() {
        val settings = McpSettings()

        settings.evaluateExpressionSafetyMode = EvaluateExpressionSafetyMode.READ_ONLY

        assertEquals("read_only", settings.state.evaluateExpressionSafetyModeId)
        assertEquals(EvaluateExpressionSafetyMode.READ_ONLY, settings.evaluateExpressionSafetyMode)
    }

    @Test
    fun `settings falls back to unrestricted for unknown persisted mode id`() {
        val settings = McpSettings()

        settings.loadState(McpSettings.State(evaluateExpressionSafetyModeId = "unknown"))

        assertEquals(EvaluateExpressionSafetyMode.UNRESTRICTED, settings.evaluateExpressionSafetyMode)
    }

    @Test
    fun `settings stores defensive copies of custom regex rules`() {
        val settings = McpSettings()
        val rule = CustomEvaluateExpressionBlockRule(pattern = "SecretStore", reason = "Secrets")

        settings.customEvaluateExpressionBlockRules = mutableListOf(rule)
        rule.pattern = "Changed"
        val fromSettings = settings.customEvaluateExpressionBlockRules
        fromSettings[0].pattern = "Mutated copy"

        assertEquals("SecretStore", settings.customEvaluateExpressionBlockRules[0].pattern)
        assertEquals("Secrets", settings.state.customEvaluateExpressionBlockRules[0].reason)
    }

    @Test
    fun `state serializes and deserializes custom regex rules`() {
        val state = McpSettings.State(
            evaluateExpressionSafetyModeId = "read_only",
            customEvaluateExpressionBlockRules = mutableListOf(
                CustomEvaluateExpressionBlockRule(
                    enabled = true,
                    pattern = """com\.company\.SecretStore""",
                    reason = "Secrets"
                )
            )
        )

        val element = XmlSerializer.serialize(state)
        val deserialized = XmlSerializer.deserialize(element, McpSettings.State::class.java)

        assertEquals("read_only", deserialized.evaluateExpressionSafetyModeId)
        assertEquals(1, deserialized.customEvaluateExpressionBlockRules.size)
        assertEquals(true, deserialized.customEvaluateExpressionBlockRules[0].enabled)
        assertEquals("""com\.company\.SecretStore""", deserialized.customEvaluateExpressionBlockRules[0].pattern)
        assertEquals("Secrets", deserialized.customEvaluateExpressionBlockRules[0].reason)
    }
}
