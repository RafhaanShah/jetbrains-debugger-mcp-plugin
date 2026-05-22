package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomEvaluateExpressionBlockRuleValidatorTest {

    @Test
    fun `valid rules pass validation`() {
        val error = CustomEvaluateExpressionBlockRuleValidator.findValidationError(
            listOf(
                CustomEvaluateExpressionBlockRule(
                    enabled = true,
                    pattern = """com\.company\.SecretStore""",
                    reason = "Internal secret access"
                ),
                CustomEvaluateExpressionBlockRule(
                    enabled = false,
                    pattern = """ExperimentalApi""",
                    reason = "Draft rule"
                )
            )
        )

        assertNull(error)
    }

    @Test
    fun `enabled blank pattern is invalid`() {
        val error = CustomEvaluateExpressionBlockRuleValidator.findValidationError(
            listOf(CustomEvaluateExpressionBlockRule(enabled = true, pattern = " ", reason = "blank"))
        )

        assertEquals(1, error?.row)
        assertTrue(error!!.message.contains("blank"))
    }

    @Test
    fun `invalid regex reports row and pattern`() {
        val error = CustomEvaluateExpressionBlockRuleValidator.findValidationError(
            listOf(
                CustomEvaluateExpressionBlockRule(pattern = """SafePattern"""),
                CustomEvaluateExpressionBlockRule(pattern = """[""")
            )
        )

        assertEquals(2, error?.row)
        assertTrue(error!!.message.contains("["))
    }

    @Test
    fun `disabled invalid regex is allowed as a draft rule`() {
        val error = CustomEvaluateExpressionBlockRuleValidator.findValidationError(
            listOf(CustomEvaluateExpressionBlockRule(enabled = false, pattern = """["""))
        )

        assertNull(error)
    }

    @Test
    fun `too many custom rules are rejected`() {
        val rules = List(CustomEvaluateExpressionBlockRuleValidator.MAX_RULES + 1) { index ->
            CustomEvaluateExpressionBlockRule(pattern = "Rule$index")
        }

        val error = CustomEvaluateExpressionBlockRuleValidator.findValidationError(rules)

        assertEquals(0, error?.row)
        assertTrue(error!!.message.contains(CustomEvaluateExpressionBlockRuleValidator.MAX_RULES.toString()))
    }

    @Test
    fun `overly long patterns are rejected`() {
        val pattern = "a".repeat(CustomEvaluateExpressionBlockRuleValidator.MAX_PATTERN_LENGTH + 1)

        val error = CustomEvaluateExpressionBlockRuleValidator.findValidationError(
            listOf(CustomEvaluateExpressionBlockRule(pattern = pattern))
        )

        assertEquals(1, error?.row)
        assertTrue(error!!.message.contains(CustomEvaluateExpressionBlockRuleValidator.MAX_PATTERN_LENGTH.toString()))
    }
}
