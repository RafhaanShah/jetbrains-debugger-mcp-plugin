package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.evaluation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.xdebugger.XDebuggerUtil

class JavaReadOnlyExpressionAnalyzerTest : BasePlatformTestCase() {

    fun `test allows simple Java arithmetic expression`() {
        val sourcePosition = configureJavaContext()

        val violation = JavaReadOnlyExpressionAnalyzer.check(
            project = project,
            expression = "x + y",
            sourcePosition = sourcePosition
        )

        assertNull(violation)
    }

    fun `test rejects Java assignment expression`() {
        val sourcePosition = configureJavaContext()

        val violation = JavaReadOnlyExpressionAnalyzer.check(
            project = project,
            expression = "x = y",
            sourcePosition = sourcePosition
        )

        assertNotNull(violation)
        assertEquals("read-only-side-effect", violation?.ruleId)
    }

    fun `test rejects Java increment expression`() {
        val sourcePosition = configureJavaContext()

        val violation = JavaReadOnlyExpressionAnalyzer.check(
            project = project,
            expression = "x++",
            sourcePosition = sourcePosition
        )

        assertNotNull(violation)
        assertEquals("read-only-side-effect", violation?.ruleId)
    }

    fun `test rejects uncertain Java method call`() {
        val sourcePosition = configureJavaContext()

        val violation = JavaReadOnlyExpressionAnalyzer.check(
            project = project,
            expression = "name.trim()",
            sourcePosition = sourcePosition
        )

        assertNotNull(violation)
        assertEquals("read-only-uncertain-side-effect", violation?.ruleId)
    }

    fun `test rejects Java constructor expression`() {
        val sourcePosition = configureJavaContext()

        val violation = JavaReadOnlyExpressionAnalyzer.check(
            project = project,
            expression = """new String("Ada")""",
            sourcePosition = sourcePosition
        )

        assertNotNull(violation)
        assertEquals("read-only-constructor", violation?.ruleId)
    }

    fun `test rejects invalid Java expression`() {
        val sourcePosition = configureJavaContext()

        val violation = JavaReadOnlyExpressionAnalyzer.check(
            project = project,
            expression = "return x",
            sourcePosition = sourcePosition
        )

        assertNotNull(violation)
        assertEquals("read-only-parse-error", violation?.ruleId)
    }

    private fun configureJavaContext() =
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                void run() {
                    int x = 1;
                    int y = 2;
                    String name = "Ada";
                    System.out.println(<caret>x);
                }
            }
            """.trimIndent()
        ).let { file ->
            XDebuggerUtil.getInstance().createPositionByOffset(file.virtualFile, myFixture.caretOffset)
        }
}
