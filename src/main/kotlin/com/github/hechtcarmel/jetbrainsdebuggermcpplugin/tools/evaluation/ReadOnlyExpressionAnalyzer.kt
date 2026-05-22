package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XSourcePosition

interface ReadOnlyExpressionAnalyzer {
    fun check(
        project: Project,
        expression: String,
        sourcePosition: XSourcePosition?
    ): EvaluationSafetyViolation?
}
