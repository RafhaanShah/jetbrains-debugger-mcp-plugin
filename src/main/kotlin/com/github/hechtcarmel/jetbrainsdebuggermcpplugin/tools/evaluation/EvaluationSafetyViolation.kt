package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

data class EvaluationSafetyViolation(
    val mode: EvaluateExpressionSafetyMode,
    val ruleId: String,
    val message: String,
    val token: String? = null,
    val customRulePattern: String? = null,
    val customRuleReason: String? = null
) {
    fun toUserMessage(): String {
        val matched = token?.let { " Matched token: $it." } ?: ""
        val customRule = customRulePattern?.let { pattern ->
            val reason = customRuleReason?.takeIf { it.isNotBlank() }?.let { " Reason: $it." } ?: ""
            " Matched custom regex: $pattern.$reason"
        } ?: ""

        return "Expression blocked by evaluate_expression safety mode '${mode.id}': $message.$matched$customRule " +
            "Change the Evaluate Expression safety mode or custom regex rules in Debugger MCP Server settings to allow this expression."
    }
}
