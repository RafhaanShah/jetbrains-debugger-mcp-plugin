package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition

object EvaluateExpressionSafetyGuard {
    private const val MAX_SCANNED_EXPRESSION_LENGTH = 10_000

    data class Context(
        val project: Project,
        val sourcePosition: XSourcePosition?
    )

    private data class BlocklistRule(
        val id: String,
        val message: String,
        val regex: Regex
    )

    private val blocklistRules = listOf(
        BlocklistRule(
            id = "process-execution",
            message = "process execution APIs are not allowed",
            regex = Regex(
                """\b(?:java\.lang\.)?Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*exec\s*\(|\b(?:java\.lang\.)?ProcessBuilder\s*\(|\.\s*exec\s*\("""
            )
        ),
        BlocklistRule(
            id = "jvm-termination",
            message = "JVM termination APIs are not allowed",
            regex = Regex("""\b(?:java\.lang\.)?System\s*\.\s*(?:exit|halt)\s*\(|\b(?:java\.lang\.)?Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*(?:exit|halt)\s*\(""")
        ),
        BlocklistRule(
            id = "filesystem-access",
            message = "filesystem access APIs are not allowed",
            regex = Regex(
                """\b(?:java\.nio\.file\.)?Files\s*\.|\b(?:java\.io\.)?(?:File|FileInputStream|FileOutputStream|FileReader|FileWriter|RandomAccessFile|PrintWriter)\s*\("""
            )
        ),
        BlocklistRule(
            id = "network-access",
            message = "network access APIs are not allowed",
            regex = Regex(
                """\b(?:java\.net\.)?(?:Socket|ServerSocket|DatagramSocket|URL|URI|URLConnection|HttpURLConnection|InetAddress)\b|\b(?:java\.net\.http\.)?HttpClient\b|\.\s*(?:openConnection|openStream|connect|send)\s*\("""
            )
        ),
        BlocklistRule(
            id = "reflection-access",
            message = "reflection and access-bypass APIs are not allowed",
            regex = Regex(
                """\b(?:java\.lang\.)?Class\s*\.\s*forName\s*\(|\b(?:java\.lang\.invoke\.)?MethodHandles\b|\b(?:sun\.misc\.|jdk\.internal\.misc\.)?Unsafe\b|\bjava\.lang\.reflect\.|\bClassLoader\b|\.\s*(?:setAccessible|getDeclaredMethod|getDeclaredMethods|getDeclaredField|getDeclaredFields|getDeclaredConstructor|getDeclaredConstructors|getClassLoader)\s*\("""
            )
        ),
        BlocklistRule(
            id = "native-code",
            message = "native library loading APIs are not allowed",
            regex = Regex("""\b(?:java\.lang\.)?System\s*\.\s*(?:load|loadLibrary)\s*\(|\b(?:java\.lang\.)?Runtime\s*\.\s*getRuntime\s*\(\s*\)\s*\.\s*(?:load|loadLibrary)\s*\(""")
        ),
        BlocklistRule(
            id = "environment-access",
            message = "environment and system property access is not allowed",
            regex = Regex("""\b(?:java\.lang\.)?System\s*\.\s*(?:getenv|getProperties|getProperty|setProperty|clearProperty)\s*\(""")
        )
    )

    fun validate(
        expression: String,
        mode: EvaluateExpressionSafetyMode,
        context: Context?,
        customRules: List<CustomEvaluateExpressionBlockRule> = emptyList()
    ): EvaluationSafetyViolation? {
        if (mode == EvaluateExpressionSafetyMode.UNRESTRICTED) return null

        val searchableExpression = stripCommentsAndStringLiterals(expression)
            .take(MAX_SCANNED_EXPRESSION_LENGTH)

        checkBlocklist(searchableExpression, mode)?.let { return it }
        checkCustomRules(searchableExpression, mode, customRules)?.let { return it }

        if (mode != EvaluateExpressionSafetyMode.READ_ONLY) return null

        if (context?.sourcePosition?.file?.extension == "java") {
            val javaViolation = runJavaAnalyzer(context.project, expression, context.sourcePosition)
            if (javaViolation != null) return javaViolation
            return null
        }

        return checkGenericReadOnly(searchableExpression, mode)
    }

    private fun checkBlocklist(
        searchableExpression: String,
        mode: EvaluateExpressionSafetyMode
    ): EvaluationSafetyViolation? {
        for (rule in blocklistRules) {
            val match = rule.regex.find(searchableExpression)
            if (match != null) {
                return EvaluationSafetyViolation(
                    mode = mode,
                    ruleId = rule.id,
                    message = rule.message,
                    token = match.value.trim()
                )
            }
        }
        return null
    }

    private fun checkCustomRules(
        searchableExpression: String,
        mode: EvaluateExpressionSafetyMode,
        customRules: List<CustomEvaluateExpressionBlockRule>
    ): EvaluationSafetyViolation? {
        customRules.forEachIndexed { index, rule ->
            val pattern = rule.pattern.trim()
            if (!rule.enabled || pattern.isBlank()) return@forEachIndexed

            val regex = try {
                pattern.toRegex()
            } catch (_: IllegalArgumentException) {
                return@forEachIndexed
            }

            val match = regex.find(searchableExpression) ?: return@forEachIndexed
            val reason = rule.reason.trim()
            return EvaluationSafetyViolation(
                mode = mode,
                ruleId = "custom-regex",
                message = if (reason.isNotBlank()) {
                    "custom regex rule #${index + 1} blocked this expression: $reason"
                } else {
                    "custom regex rule #${index + 1} blocked this expression"
                },
                token = match.value.trim(),
                customRulePattern = pattern,
                customRuleReason = reason.takeIf { it.isNotBlank() }
            )
        }
        return null
    }

    private fun checkGenericReadOnly(
        searchableExpression: String,
        mode: EvaluateExpressionSafetyMode
    ): EvaluationSafetyViolation? {
        Regex("""[;\n\r]""").find(searchableExpression)?.let {
            return EvaluationSafetyViolation(
                mode = mode,
                ruleId = "read-only-code-fragment",
                message = "code fragments are not allowed in read-only mode",
                token = it.value
            )
        }

        Regex("""\+\+|--""").find(searchableExpression)?.let {
            return EvaluationSafetyViolation(
                mode = mode,
                ruleId = "read-only-increment",
                message = "increment and decrement operations are not allowed in read-only mode",
                token = it.value
            )
        }

        Regex("""\+=|-=|\*=|/=|%=|&=|\|=|\^=|<<=|>>=|>>>=|(?<![=!<>])=(?!=)""").find(searchableExpression)?.let {
            return EvaluationSafetyViolation(
                mode = mode,
                ruleId = "read-only-assignment",
                message = "assignment operations are not allowed in read-only mode",
                token = it.value
            )
        }

        Regex("""\bnew\s+[A-Za-z_$][\w$]*""").find(searchableExpression)?.let {
            return EvaluationSafetyViolation(
                mode = mode,
                ruleId = "read-only-constructor",
                message = "object construction is not allowed in read-only mode without a language-specific analyzer",
                token = it.value
            )
        }

        Regex("""(?:\.|::)?\s*[A-Za-z_$][\w$]*\s*\(""").find(searchableExpression)?.let {
            return EvaluationSafetyViolation(
                mode = mode,
                ruleId = "read-only-uncertain-method-call",
                message = "method calls are not allowed in read-only mode without a language-specific analyzer",
                token = it.value.trim()
            )
        }

        return null
    }

    private fun runJavaAnalyzer(
        project: Project,
        expression: String,
        sourcePosition: XSourcePosition?
    ): EvaluationSafetyViolation? {
        return try {
            val analyzerClass = Class.forName(
                "com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation.JavaReadOnlyExpressionAnalyzer"
            )
            val analyzer = analyzerClass.getField("INSTANCE").get(null) as ReadOnlyExpressionAnalyzer
            analyzer.check(project, expression, sourcePosition)
        } catch (_: ReflectiveOperationException) {
            checkGenericReadOnly(stripCommentsAndStringLiterals(expression), EvaluateExpressionSafetyMode.READ_ONLY)
        } catch (_: LinkageError) {
            checkGenericReadOnly(stripCommentsAndStringLiterals(expression), EvaluateExpressionSafetyMode.READ_ONLY)
        }
    }

    internal fun stripCommentsAndStringLiterals(expression: String): String {
        val result = StringBuilder(expression.length)
        var index = 0
        var inLineComment = false
        var inBlockComment = false
        var inString = false
        var stringDelimiter = '\u0000'
        var escaped = false

        while (index < expression.length) {
            val current = expression[index]
            val next = expression.getOrNull(index + 1)

            when {
                inLineComment -> {
                    if (current == '\n' || current == '\r') {
                        inLineComment = false
                        result.append(current)
                    } else {
                        result.append(' ')
                    }
                }
                inBlockComment -> {
                    if (current == '*' && next == '/') {
                        inBlockComment = false
                        result.append("  ")
                        index++
                    } else {
                        result.append(if (current == '\n' || current == '\r') current else ' ')
                    }
                }
                inString -> {
                    result.append(if (current == '\n' || current == '\r') current else ' ')
                    if (escaped) {
                        escaped = false
                    } else if (current == '\\') {
                        escaped = true
                    } else if (current == stringDelimiter) {
                        inString = false
                    }
                }
                current == '/' && next == '/' -> {
                    inLineComment = true
                    result.append("  ")
                    index++
                }
                current == '/' && next == '*' -> {
                    inBlockComment = true
                    result.append("  ")
                    index++
                }
                current == '"' || current == '\'' || current == '`' -> {
                    inString = true
                    stringDelimiter = current
                    escaped = false
                    result.append(' ')
                }
                else -> result.append(current)
            }

            index++
        }

        return result.toString()
    }
}
