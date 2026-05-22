package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

data class CustomEvaluateExpressionBlockRule(
    var enabled: Boolean = true,
    var pattern: String = "",
    var reason: String = ""
) {
    fun copyForPersistence(): CustomEvaluateExpressionBlockRule {
        return copy(
            pattern = pattern.trim(),
            reason = reason.trim()
        )
    }
}

object CustomEvaluateExpressionBlockRuleValidator {
    const val MAX_RULES = 50
    const val MAX_PATTERN_LENGTH = 500

    data class ValidationError(
        val row: Int,
        val message: String
    )

    fun findValidationError(rules: List<CustomEvaluateExpressionBlockRule>): ValidationError? {
        if (rules.size > MAX_RULES) {
            return ValidationError(
                row = 0,
                message = "At most $MAX_RULES custom evaluate_expression regex rules are allowed."
            )
        }

        rules.forEachIndexed { index, rule ->
            val row = index + 1
            val pattern = rule.pattern.trim()

            if (rule.enabled && pattern.isBlank()) {
                return ValidationError(
                    row = row,
                    message = "Custom regex rule #$row is enabled but its pattern is blank."
                )
            }

            if (pattern.length > MAX_PATTERN_LENGTH) {
                return ValidationError(
                    row = row,
                    message = "Custom regex rule #$row is longer than $MAX_PATTERN_LENGTH characters."
                )
            }

            if (rule.enabled && pattern.isNotBlank()) {
                try {
                    pattern.toRegex()
                } catch (e: IllegalArgumentException) {
                    return ValidationError(
                        row = row,
                        message = "Custom regex rule #$row is invalid: '$pattern'. ${e.message.orEmpty()}".trim()
                    )
                }
            }
        }

        return null
    }
}
