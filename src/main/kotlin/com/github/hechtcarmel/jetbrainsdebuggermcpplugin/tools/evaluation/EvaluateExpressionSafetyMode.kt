package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

enum class EvaluateExpressionSafetyMode(
    val id: String,
    private val displayName: String,
    val explanation: String
) {
    UNRESTRICTED(
        id = "unrestricted",
        displayName = "Unrestricted",
        explanation = "No plugin-side filtering. Expressions may call methods, mutate debugged process state, access the filesystem or network, and run any operation the debugger evaluator accepts."
    ),
    DEFAULT_BLOCKLIST(
        id = "default_blocklist",
        displayName = "Default blocklist",
        explanation = "Blocks built-in high-risk categories such as process execution, JVM termination, filesystem access, network access, reflection/access bypass, native code loading, and environment/system property access. Additional custom regex rules also apply."
    ),
    READ_ONLY(
        id = "read_only",
        displayName = "Read-only",
        explanation = "Includes the default blocklist and custom regex rules, then rejects assignments, increment/decrement operations, code fragments, constructors, and method calls that cannot be proven read-only."
    );

    override fun toString(): String = displayName

    companion object {
        val DEFAULT: EvaluateExpressionSafetyMode = UNRESTRICTED

        fun fromId(id: String?): EvaluateExpressionSafetyMode {
            return entries.firstOrNull { it.id == id } ?: DEFAULT
        }
    }
}
