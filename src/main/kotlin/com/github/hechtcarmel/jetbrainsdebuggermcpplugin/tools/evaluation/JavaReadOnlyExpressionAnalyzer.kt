package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNewExpression
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XSourcePosition
import com.siyeh.ig.psiutils.SideEffectChecker

object JavaReadOnlyExpressionAnalyzer : ReadOnlyExpressionAnalyzer {

    override fun check(
        project: Project,
        expression: String,
        sourcePosition: XSourcePosition?
    ): EvaluationSafetyViolation? {
        return ReadAction.compute<EvaluationSafetyViolation?, RuntimeException> {
            val contextElement = findContextElement(project, sourcePosition)
            val psiExpression = try {
                JavaPsiFacade.getElementFactory(project).createExpressionFromText(expression, contextElement)
            } catch (_: IncorrectOperationException) {
                return@compute EvaluationSafetyViolation(
                    mode = EvaluateExpressionSafetyMode.READ_ONLY,
                    ruleId = "read-only-parse-error",
                    message = "the expression cannot be parsed as a single Java expression in read-only mode"
                )
            }

            if (psiExpression is PsiNewExpression) {
                return@compute EvaluationSafetyViolation(
                    mode = EvaluateExpressionSafetyMode.READ_ONLY,
                    ruleId = "read-only-constructor",
                    message = "object construction is not allowed in read-only mode"
                )
            }

            when (SideEffectChecker.getSideEffectStatus(psiExpression)) {
                ThreeState.NO -> null
                ThreeState.YES -> EvaluationSafetyViolation(
                    mode = EvaluateExpressionSafetyMode.READ_ONLY,
                    ruleId = "read-only-side-effect",
                    message = "the Java PSI side-effect checker detected a mutating expression"
                )
                ThreeState.UNSURE -> EvaluationSafetyViolation(
                    mode = EvaluateExpressionSafetyMode.READ_ONLY,
                    ruleId = "read-only-uncertain-side-effect",
                    message = "the Java PSI side-effect checker could not prove the expression is read-only"
                )
            }
        }
    }

    private fun findContextElement(
        project: Project,
        sourcePosition: XSourcePosition?
    ): PsiElement? {
        sourcePosition ?: return null
        val psiFile = PsiManager.getInstance(project).findFile(sourcePosition.file) ?: return null
        val offset = sourcePosition.offset.coerceIn(0, psiFile.textLength)
        return psiFile.findElementAt(offset) ?: psiFile
    }
}
