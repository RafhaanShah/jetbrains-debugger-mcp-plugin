package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.settings

import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation.EvaluateExpressionSafetyMode
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSettingsConfigurableSafetyTextTest {

    @Test
    fun `mode explanation text describes unrestricted risk`() {
        val text = McpSettingsConfigurable.modeExplanationHtml(EvaluateExpressionSafetyMode.UNRESTRICTED)

        assertTrue(text.contains("No plugin-side filtering"))
        assertTrue(text.contains("mutate"))
    }

    @Test
    fun `mode explanation text describes default blocklist and custom regex`() {
        val text = McpSettingsConfigurable.modeExplanationHtml(EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST)

        assertTrue(text.contains("process execution"))
        assertTrue(text.contains("filesystem"))
        assertTrue(text.contains("custom regex"))
    }

    @Test
    fun `mode explanation text describes read only restrictions`() {
        val text = McpSettingsConfigurable.modeExplanationHtml(EvaluateExpressionSafetyMode.READ_ONLY)

        assertTrue(text.contains("assignments"))
        assertTrue(text.contains("constructors"))
        assertTrue(text.contains("method calls"))
    }

    @Test
    fun `custom regex help text explains normalization and mode applicability`() {
        assertTrue(McpSettingsConfigurable.CUSTOM_REGEX_HELP_HTML.contains("comments and string literals are removed"))
        assertTrue(McpSettingsConfigurable.CUSTOM_REGEX_HELP_HTML.contains("Default blocklist"))
        assertTrue(McpSettingsConfigurable.CUSTOM_REGEX_HELP_HTML.contains("Read-only"))
    }
}
