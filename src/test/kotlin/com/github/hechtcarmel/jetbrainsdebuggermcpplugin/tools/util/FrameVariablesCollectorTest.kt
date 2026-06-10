package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.util

import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

class FrameVariablesCollectorTest {

    private val placeholder = "Collecting data…"
    private val placeholders = setOf(placeholder)

    private fun syncValue(text: String, type: String = "int"): XValue = object : XValue() {
        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(null, type, text, false)
        }
    }

    /** Mimics JavaValue + ClassRenderer: placeholder first, real value async (issue #51). */
    private fun asyncValue(finalText: String, type: String = "Obj@1"): XValue = object : XValue() {
        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            node.setPresentation(null, type, placeholder, true)
            thread {
                Thread.sleep(50)
                node.setPresentation(null, type, finalText, true)
            }
        }
    }

    private fun frameOf(vararg children: Pair<String, XValue>, batches: Int = 1): XStackFrame =
        object : XStackFrame() {
            override fun computeChildren(node: XCompositeNode) {
                val chunks = children.toList().chunked(maxOf(1, children.size / batches))
                chunks.forEachIndexed { index, chunk ->
                    val list = XValueChildrenList()
                    chunk.forEach { (name, value) -> list.add(name, value) }
                    node.addChildren(list, index == chunks.lastIndex)
                }
                if (chunks.isEmpty()) {
                    node.addChildren(XValueChildrenList.EMPTY, true)
                }
            }
        }

    @Test
    fun `collects sync and async values without placeholders or duplicates`() = runBlocking {
        val frame = frameOf(
            "this" to asyncValue("MyObject{x=1}"),
            "count" to syncValue("42"),
            "data" to asyncValue("Data{size=3}", type = "Data@7")
        )
        val variables = FrameVariablesCollector.collectVariables(
            frame, placeholderTexts = placeholders
        )
        assertEquals(3, variables.size)
        assertEquals(listOf("this", "count", "data"), variables.map { it.name })
        assertEquals("MyObject{x=1}", variables[0].value)
        assertEquals("42", variables[1].value)
        assertEquals("Data{size=3}", variables[2].value)
        assertTrue(variables.none { it.value == placeholder })
    }

    @Test
    fun `handles multiple addChildren batches`() = runBlocking {
        val frame = frameOf(
            "a" to syncValue("1"),
            "b" to syncValue("2"),
            "c" to syncValue("3"),
            "d" to syncValue("4"),
            batches = 2
        )
        val variables = FrameVariablesCollector.collectVariables(frame, placeholderTexts = placeholders)
        assertEquals(listOf("a", "b", "c", "d"), variables.map { it.name })
    }

    @Test
    fun `marks values stuck on placeholder as pending instead of leaking localized text`() = runBlocking {
        val stuck = object : XValue() {
            override fun computePresentation(node: XValueNode, place: XValuePlace) {
                node.setPresentation(null, "Slow@1", placeholder, true)
                // final presentation never arrives
            }
        }
        val frame = frameOf("slow" to stuck, "fast" to syncValue("7"))
        val variables = FrameVariablesCollector.collectVariables(
            frame, presentationTimeoutMs = 200L, placeholderTexts = placeholders
        )
        assertEquals(2, variables.size)
        assertEquals(VariablePresentationUtils.PENDING_VALUE_TEXT, variables[0].value)
        assertEquals("Slow@1", variables[0].type)
        assertEquals("7", variables[1].value)
    }

    @Test
    fun `returns empty list when the frame reports an error`() = runBlocking {
        val frame = object : XStackFrame() {
            override fun computeChildren(node: XCompositeNode) {
                node.setErrorMessage("frame gone")
            }
        }
        val variables = FrameVariablesCollector.collectVariables(frame, placeholderTexts = placeholders)
        assertTrue(variables.isEmpty())
    }

    @Test
    fun `returns empty list when children never arrive`() = runBlocking {
        val frame = object : XStackFrame() {
            override fun computeChildren(node: XCompositeNode) { /* never answers */ }
        }
        val variables = FrameVariablesCollector.collectVariables(
            frame, childrenTimeoutMs = 200L, placeholderTexts = placeholders
        )
        assertTrue(variables.isEmpty())
    }

    @Test
    fun `a value whose presentation computation throws does not fail the whole collection`() = runBlocking {
        val throwing = object : XValue() {
            override fun computePresentation(node: XValueNode, place: XValuePlace) {
                throw IllegalStateException("debugger disposed")
            }
        }
        val frame = frameOf("broken" to throwing, "ok" to syncValue("1"))
        val variables = FrameVariablesCollector.collectVariables(frame, placeholderTexts = placeholders)
        assertEquals(2, variables.size)
        assertEquals(VariablePresentationUtils.UNAVAILABLE_VALUE_TEXT, variables[0].value)
        assertEquals("1", variables[1].value)
    }
}
