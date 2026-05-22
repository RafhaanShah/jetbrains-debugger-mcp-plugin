package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluateExpressionSafetyGuardTest {

    @Test
    fun `unrestricted mode allows dangerous expression and ignores custom regex`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """Runtime.getRuntime().exec("touch /tmp/pwned")""",
            mode = EvaluateExpressionSafetyMode.UNRESTRICTED,
            context = null,
            customRules = listOf(CustomEvaluateExpressionBlockRule(pattern = "Runtime"))
        )

        assertNull(violation)
    }

    @Test
    fun `default blocklist rejects process execution`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """Runtime.getRuntime().exec("touch /tmp/pwned")""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNotNull(violation)
        assertEquals("process-execution", violation?.ruleId)
        assertTrue(violation!!.toUserMessage().contains("default_blocklist"))
    }

    @Test
    fun `default blocklist rejects filesystem writes`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """Files.write(Path.of("/tmp/a"), bytes)""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNotNull(violation)
        assertEquals("filesystem-access", violation?.ruleId)
    }

    @Test
    fun `default blocklist rejects filesystem reads`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """Files.exists(Path.of("/tmp/a"))""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNotNull(violation)
        assertEquals("filesystem-access", violation?.ruleId)
    }

    @Test
    fun `default blocklist rejects runtime exit`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """Runtime.getRuntime().exit(1)""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNotNull(violation)
        assertEquals("jvm-termination", violation?.ruleId)
    }

    @Test
    fun `default blocklist rejects runtime native loading`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """Runtime.getRuntime().loadLibrary("agent")""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNotNull(violation)
        assertEquals("native-code", violation?.ruleId)
    }

    @Test
    fun `default blocklist rejects URI based network access`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """URI.create("https://example.com").toURL().openStream()""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNotNull(violation)
        assertEquals("network-access", violation?.ruleId)
    }

    @Test
    fun `default blocklist ignores dangerous token inside string literal`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """"Runtime.getRuntime().exec" + userMessage""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNull(violation)
    }

    @Test
    fun `default blocklist ignores dangerous token inside line comment`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "userId.length() // Runtime.getRuntime().exec",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null
        )

        assertNull(violation)
    }

    @Test
    fun `custom regex blocks expression in default blocklist mode`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "com.company.SecretStore.currentToken()",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null,
            customRules = listOf(
                CustomEvaluateExpressionBlockRule(
                    enabled = true,
                    pattern = """com\.company\.SecretStore""",
                    reason = "Secret store access"
                )
            )
        )

        assertNotNull(violation)
        assertEquals("custom-regex", violation?.ruleId)
        assertTrue(violation!!.toUserMessage().contains("Secret store access"))
        assertTrue(violation.toUserMessage().contains("""com\.company\.SecretStore"""))
    }

    @Test
    fun `disabled custom regex is ignored`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "com.company.SecretStore.currentToken()",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null,
            customRules = listOf(
                CustomEvaluateExpressionBlockRule(
                    enabled = false,
                    pattern = """com\.company\.SecretStore""",
                    reason = "Secret store access"
                )
            )
        )

        assertNull(violation)
    }

    @Test
    fun `invalid persisted custom regex is ignored at runtime`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "safeValue",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null,
            customRules = listOf(CustomEvaluateExpressionBlockRule(pattern = "["))
        )

        assertNull(violation)
    }

    @Test
    fun `custom regex ignores dangerous token inside string literal`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = """"com.company.SecretStore" + label""",
            mode = EvaluateExpressionSafetyMode.DEFAULT_BLOCKLIST,
            context = null,
            customRules = listOf(CustomEvaluateExpressionBlockRule(pattern = """com\.company\.SecretStore"""))
        )

        assertNull(violation)
    }

    @Test
    fun `read only rejects assignments`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "counter = counter + 1",
            mode = EvaluateExpressionSafetyMode.READ_ONLY,
            context = null
        )

        assertNotNull(violation)
        assertEquals("read-only-assignment", violation?.ruleId)
    }

    @Test
    fun `read only allows simple arithmetic and field access`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "total + user.age * 2",
            mode = EvaluateExpressionSafetyMode.READ_ONLY,
            context = null
        )

        assertNull(violation)
    }

    @Test
    fun `read only rejects uncertain method call without language analyzer`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "userRepository.count()",
            mode = EvaluateExpressionSafetyMode.READ_ONLY,
            context = null
        )

        assertNotNull(violation)
        assertEquals("read-only-uncertain-method-call", violation?.ruleId)
    }

    @Test
    fun `read only applies custom regex before generic read only checks`() {
        val violation = EvaluateExpressionSafetyGuard.validate(
            expression = "com.company.SecretStore.currentToken()",
            mode = EvaluateExpressionSafetyMode.READ_ONLY,
            context = null,
            customRules = listOf(CustomEvaluateExpressionBlockRule(pattern = """com\.company\.SecretStore"""))
        )

        assertNotNull(violation)
        assertEquals("custom-regex", violation?.ruleId)
    }
}
