package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.util

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Resolves user-supplied paths to virtual files across file systems.
 *
 * Supported forms:
 * - Absolute local path: `/Users/dev/project/src/Main.java`, `D:/project/src/Main.java`
 * - Entry inside a JAR/ZIP (the IDE's "Copy Absolute Path" format for library sources):
 *   `/path/to/lib-sources.jar!/com/example/Foo.kt`
 * - VFS URL: `file:///path/to/Main.java`, `jar:///path/to/lib.jar!/com/example/Foo.kt`
 *
 * LocalFileSystem cannot see JAR entries (issue #51), so paths containing the `!/`
 * separator are routed through JarFileSystem.
 */
object VirtualFileResolver {

    private const val URL_SCHEME_SEPARATOR = "://"

    fun resolve(filePath: String): VirtualFile? {
        val normalized = FileUtil.toSystemIndependentName(filePath.trim())
        if (normalized.isEmpty()) return null
        return when {
            normalized.contains(URL_SCHEME_SEPARATOR) ->
                VirtualFileManager.getInstance().findFileByUrl(normalized)

            normalized.contains(JarFileSystem.JAR_SEPARATOR) ->
                JarFileSystem.getInstance().findFileByPath(normalized)
                    ?: JarFileSystem.getInstance().refreshAndFindFileByPath(normalized)

            else ->
                LocalFileSystem.getInstance().findFileByPath(normalized)
                    ?: LocalFileSystem.getInstance().refreshAndFindFileByPath(normalized)
        }
    }
}
