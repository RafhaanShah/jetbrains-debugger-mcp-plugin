package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.util

import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VirtualFileResolverTest : BasePlatformTestCase() {

    private lateinit var tempDir: File

    override fun setUp() {
        super.setUp()
        tempDir = FileUtil.createTempDirectory("vfr-test", null)
    }

    override fun tearDown() {
        try {
            FileUtil.delete(tempDir)
        } finally {
            super.tearDown()
        }
    }

    private fun createJarWithEntry(jarName: String, entryPath: String, content: String): File {
        val jarFile = File(tempDir, jarName)
        ZipOutputStream(FileOutputStream(jarFile)).use { zip ->
            zip.putNextEntry(ZipEntry(entryPath))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }
        return jarFile
    }

    fun `test resolves plain local file`() {
        val file = File(tempDir, "Sample.kt")
        file.writeText("class Sample")
        val resolved = VirtualFileResolver.resolve(file.absolutePath)
        assertNotNull(resolved)
        assertEquals("Sample.kt", resolved!!.name)
    }

    fun `test resolves file inside jar using jar separator`() {
        val jar = createJarWithEntry("lib-sources.jar", "com/example/Foo.kt", "class Foo")
        val path = FileUtil.toSystemIndependentName(jar.absolutePath) + "!/com/example/Foo.kt"
        val resolved = VirtualFileResolver.resolve(path)
        assertNotNull("Should resolve jar entry: $path", resolved)
        assertEquals("Foo.kt", resolved!!.name)
        assertEquals("jar", resolved.fileSystem.protocol)
    }

    fun `test resolves jar url form`() {
        val jar = createJarWithEntry("lib2.jar", "com/example/Bar.kt", "class Bar")
        val url = "jar://" + FileUtil.toSystemIndependentName(jar.absolutePath) + "!/com/example/Bar.kt"
        val resolved = VirtualFileResolver.resolve(url)
        assertNotNull(resolved)
        assertEquals("Bar.kt", resolved!!.name)
    }

    fun `test resolves backslash separators`() {
        val file = File(tempDir, "Win.kt")
        file.writeText("class Win")
        val backslashed = file.absolutePath.replace('/', '\\')
        val resolved = VirtualFileResolver.resolve(backslashed)
        assertNotNull(resolved)
        assertEquals("Win.kt", resolved!!.name)
    }

    fun `test returns null for missing local file`() {
        assertNull(VirtualFileResolver.resolve(File(tempDir, "nope.kt").absolutePath))
    }

    fun `test returns null for missing jar entry`() {
        val jar = createJarWithEntry("lib3.jar", "com/example/Baz.kt", "class Baz")
        val path = FileUtil.toSystemIndependentName(jar.absolutePath) + "!/com/example/Missing.kt"
        assertNull(VirtualFileResolver.resolve(path))
    }

    fun `test returns null for blank path`() {
        assertNull(VirtualFileResolver.resolve("   "))
    }
}
