package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.util

import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

class EvaluatorUtilsTest {

    private val dummyValue = object : XValue() {
        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(null, "int", "42", false)
        }
    }

    private fun evaluatorThat(
        behavior: (XDebuggerEvaluator.XEvaluationCallback) -> Unit
    ): XDebuggerEvaluator = object : XDebuggerEvaluator() {
        override fun evaluate(
            expression: String,
            callback: XEvaluationCallback,
            expressionPosition: XSourcePosition?
        ) {
            behavior(callback)
        }

        override fun evaluate(
            expression: XExpression,
            callback: XEvaluationCallback,
            expressionPosition: XSourcePosition?
        ) {
            behavior(callback)
        }
    }

    @Test
    fun `returns Success when evaluation succeeds`() = runBlocking {
        val evaluator = evaluatorThat { callback -> callback.evaluated(dummyValue) }
        val outcome = EvaluatorUtils.evaluate(evaluator, "x + 1", 1000L)
        assertTrue(outcome is EvaluatorUtils.EvaluationOutcome.Success)
        assertEquals(dummyValue, (outcome as EvaluatorUtils.EvaluationOutcome.Success).value)
    }

    @Test
    fun `returns Failure with the debugger message when evaluation fails`() = runBlocking {
        val evaluator = evaluatorThat { callback -> callback.errorOccurred("No such variable: y") }
        val outcome = EvaluatorUtils.evaluate(evaluator, "y", 1000L)
        assertTrue(outcome is EvaluatorUtils.EvaluationOutcome.Failure)
        assertEquals("No such variable: y", (outcome as EvaluatorUtils.EvaluationOutcome.Failure).error)
    }

    @Test
    fun `returns Timeout when the evaluator never answers`() = runBlocking {
        val evaluator = evaluatorThat { /* never calls back */ }
        val outcome = EvaluatorUtils.evaluate(evaluator, "x", 200L)
        assertTrue(outcome is EvaluatorUtils.EvaluationOutcome.Timeout)
    }

    @Test
    fun `supports asynchronous callbacks`() = runBlocking {
        val evaluator = evaluatorThat { callback ->
            thread {
                Thread.sleep(50)
                callback.evaluated(dummyValue)
            }
        }
        val outcome = EvaluatorUtils.evaluate(evaluator, "x", 1000L)
        assertTrue(outcome is EvaluatorUtils.EvaluationOutcome.Success)
    }

    @Test
    fun `tolerates a misbehaving evaluator that fires both callbacks`() = runBlocking {
        val evaluator = evaluatorThat { callback ->
            callback.evaluated(dummyValue)
            callback.errorOccurred("late duplicate callback")
        }
        val outcome = EvaluatorUtils.evaluate(evaluator, "x", 1000L)
        assertTrue(outcome is EvaluatorUtils.EvaluationOutcome.Success)
    }
}
