package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.util

import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

class VariablePresentationUtilsTest {

    private val placeholder = "Collecting data…"
    private val placeholders = setOf(placeholder)

    private fun valueWith(behavior: (XValueNode) -> Unit): XValue = object : XValue() {
        override fun computePresentation(node: XValueNode, place: XValuePlace) {
            behavior(node)
        }
    }

    @Test
    fun `returns immediately when first presentation is not a placeholder`() = runBlocking {
        val value = valueWith { node ->
            node.setPresentation(null, "int", "42", false)
        }
        val result = VariablePresentationUtils.awaitPresentation(value, 1000L, placeholders)
        assertNotNull(result)
        assertEquals("42", result!!.value)
        assertEquals("int", result.type)
        assertEquals(false, result.hasChildren)
    }

    @Test
    fun `waits for the final presentation when the first is a placeholder`() = runBlocking {
        val value = valueWith { node ->
            node.setPresentation(null, "MyType@123", placeholder, true)
            thread {
                Thread.sleep(100)
                node.setPresentation(null, "MyType@123", "MyType{x=1}", true)
            }
        }
        val result = VariablePresentationUtils.awaitPresentation(value, 3000L, placeholders)
        assertNotNull(result)
        assertEquals("MyType{x=1}", result!!.value)
        assertEquals("MyType@123", result.type)
        assertEquals(true, result.hasChildren)
    }

    @Test
    fun `falls back to pending marker when only a placeholder arrives before timeout`() = runBlocking {
        val value = valueWith { node ->
            node.setPresentation(null, "MyType@123", placeholder, true)
        }
        val result = VariablePresentationUtils.awaitPresentation(value, 200L, placeholders)
        assertNotNull(result)
        assertEquals(VariablePresentationUtils.PENDING_VALUE_TEXT, result!!.value)
        assertEquals("MyType@123", result.type)
    }

    @Test
    fun `returns null when no presentation arrives before timeout`() = runBlocking {
        val value = valueWith { /* never calls setPresentation */ }
        val result = VariablePresentationUtils.awaitPresentation(value, 200L, placeholders)
        assertNull(result)
    }

    @Test
    fun `detects placeholder delivered as a rendered string value`() = runBlocking {
        // Reproduces evaluate_expression(x.toString()) from issue #51: the placeholder
        // arrives through XValuePresentation.renderStringValue and gets quote-wrapped.
        val stringPresentation = object : XValuePresentation() {
            override fun renderValue(renderer: XValueTextRenderer) {
                renderer.renderStringValue(placeholder)
            }
            override fun getType(): String? = null
        }
        val value = valueWith { node ->
            node.setPresentation(null, stringPresentation, true)
            thread {
                Thread.sleep(100)
                node.setPresentation(null, "java.lang.String", "\"real toString\"", false)
            }
        }
        val result = VariablePresentationUtils.awaitPresentation(value, 3000L, placeholders)
        assertNotNull(result)
        assertEquals("\"real toString\"", result!!.value)
    }

    @Test
    fun `first non-placeholder presentation wins over later updates`() = runBlocking {
        val value = valueWith { node ->
            node.setPresentation(null, "T", "first", false)
            node.setPresentation(null, "T", "second", false)
        }
        val result = VariablePresentationUtils.awaitPresentation(value, 1000L, placeholders)
        assertEquals("first", result!!.value)
    }

    @Test
    fun `node reports obsolete after awaitPresentation completes`() = runBlocking {
        var capturedNode: XValueNode? = null
        val value = valueWith { node ->
            capturedNode = node
            node.setPresentation(null, "int", "42", false)
        }
        VariablePresentationUtils.awaitPresentation(value, 1000L, placeholders)
        assertTrue(capturedNode!!.isObsolete)
    }

    @Test
    fun `renders XValuePresentation fragments like the debugger tree`() = runBlocking {
        val presentation = object : XValuePresentation() {
            override fun renderValue(renderer: XValueTextRenderer) {
                renderer.renderValue("size = ")
                renderer.renderNumericValue("3")
                renderer.renderComment("ArrayList")
            }
            override fun getType(): String? = "java.util.ArrayList"
        }
        val value = valueWith { node -> node.setPresentation(null, presentation, true) }
        val result = VariablePresentationUtils.awaitPresentation(value, 1000L, placeholders)
        assertEquals("size = 3 // ArrayList", result!!.value)
        assertEquals("java.util.ArrayList", result.type)
    }

    @Test
    fun `detects pre-quoted placeholder delivered through the plain text overload`() = runBlocking {
        val value = valueWith { node ->
            node.setPresentation(null, "java.lang.String", "\"$placeholder\"", true)
            thread {
                Thread.sleep(100)
                node.setPresentation(null, "java.lang.String", "\"real\"", false)
            }
        }
        val result = VariablePresentationUtils.awaitPresentation(value, 3000L, placeholders)
        assertEquals("\"real\"", result!!.value)
    }

    @Test
    fun `placeholder detection tolerates ellipsis form differences`() = runBlocking {
        // Placeholder registered with U+2026, delivered with three dots.
        val value = valueWith { node ->
            node.setPresentation(null, "T", "Collecting data...", true)
        }
        val result = VariablePresentationUtils.awaitPresentation(
            value, 200L, setOf("Collecting data…")
        )
        assertEquals(VariablePresentationUtils.PENDING_VALUE_TEXT, result!!.value)
    }
}
