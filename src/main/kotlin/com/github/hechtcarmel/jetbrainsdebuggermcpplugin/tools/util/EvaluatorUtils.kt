package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.util

import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Bridges XDebuggerEvaluator's callback API to coroutines. Resolving the resulting
 * XValue's presentation is a separate step (VariablePresentationUtils.awaitPresentation),
 * because presentations may complete asynchronously after evaluation succeeds.
 */
object EvaluatorUtils {

    sealed class EvaluationOutcome {
        data class Success(val value: XValue) : EvaluationOutcome()
        data class Failure(val error: String) : EvaluationOutcome()
        object Timeout : EvaluationOutcome()
    }

    suspend fun evaluate(
        evaluator: XDebuggerEvaluator,
        expression: String,
        timeoutMs: Long
    ): EvaluationOutcome {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<EvaluationOutcome> { continuation ->
                // The callback contract is exactly-once, but a misbehaving evaluator firing
                // both callbacks would throw "Already resumed" on the debugger thread.
                val resumed = AtomicBoolean(false)
                evaluator.evaluate(
                    expression,
                    object : XDebuggerEvaluator.XEvaluationCallback {
                        override fun evaluated(result: XValue) {
                            if (resumed.compareAndSet(false, true)) {
                                continuation.resume(EvaluationOutcome.Success(result))
                            }
                        }

                        override fun errorOccurred(errorMessage: String) {
                            if (resumed.compareAndSet(false, true)) {
                                continuation.resume(EvaluationOutcome.Failure(errorMessage))
                            }
                        }
                    },
                    null
                )
            }
        } ?: EvaluationOutcome.Timeout
    }
}
