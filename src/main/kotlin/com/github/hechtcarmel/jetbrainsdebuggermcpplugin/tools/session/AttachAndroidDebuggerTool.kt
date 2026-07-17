package com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.session

import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.models.ToolAnnotations
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.server.models.ToolCallResult
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.AbstractMcpTool
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.models.AttachAndroidDebuggerResult
import com.github.hechtcarmel.jetbrainsdebuggermcpplugin.tools.models.DebugSessionInfo
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier

/**
 * Attaches Android Studio's debugger to an already-running Android process.
 */
class AttachAndroidDebuggerTool : AbstractMcpTool() {

    override val name = "attach_android_debugger"

    override val description = """
        Attaches the Android Studio debugger to a running Android app process without opening the IDE chooser.
        Use process_name, package_name, pid, and optionally device_serial to select the target.
    """.trimIndent()

    override val annotations = ToolAnnotations.mutable("Attach Android Debugger")

    override val inputSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            val (propName, propSchema) = projectPathProperty()
            put(propName, propSchema)
            putJsonObject("device_serial") {
                put("type", "string")
                put("description", "Android device serial to attach on. Optional when only one matching device/process exists.")
            }
            putJsonObject("process_name") {
                put("type", "string")
                put("description", "Exact Android process name to attach to, for example com.example.app or com.example.app:remote.")
            }
            putJsonObject("package_name") {
                put("type", "string")
                put("description", "Exact Android package name to attach to.")
            }
            putJsonObject("pid") {
                put("type", "integer")
                put("description", "Android process ID to attach to.")
            }
            putJsonObject("debugger_id") {
                put("type", "string")
                put("description", "Android debugger ID. Common values: Auto, Java, Native, Hybrid. Defaults to Android Studio's default debugger.")
            }
            putJsonObject("timeout_ms") {
                put("type", "integer")
                put("description", "Maximum time to wait for the debug session to appear.")
                put("default", 30000)
                put("minimum", 1)
            }
        }
        put("required", buildJsonArray { })
        put("additionalProperties", false)
    }

    override suspend fun doExecute(project: Project, arguments: JsonObject): ToolCallResult {
        val filters = AndroidAttachFilters(
            deviceSerial = arguments.stringOrNull("device_serial"),
            processName = arguments.stringOrNull("process_name"),
            packageName = arguments.stringOrNull("package_name"),
            pid = arguments["pid"]?.jsonPrimitive?.intOrNull
        )
        val debuggerId = arguments.stringOrNull("debugger_id")
        val timeoutMs = arguments["timeout_ms"]?.jsonPrimitive?.intOrNull ?: 30000
        if (timeoutMs <= 0) {
            return createErrorResult("timeout_ms must be greater than 0")
        }

        return try {
            val android = AndroidDebuggerReflection(project)
            val candidates = withContext(Dispatchers.IO) { android.listProcesses() }
            val matches = candidates.matching(filters)
            val target = when {
                matches.isEmpty() -> return createErrorResult(
                    "No matching Android process found.${formatCandidates(candidates)}"
                )
                matches.size > 1 -> return createErrorResult(
                    "Multiple Android processes matched. Narrow the target with device_serial, process_name, package_name, or pid.${formatCandidates(matches)}"
                )
                else -> matches.single()
            }
            val debugger = android.selectDebugger(debuggerId)
            val existingSession = android.getExistingSession(debugger, target)
            if (existingSession != null) {
                return createJsonResult(result("already_attached", "Android debugger is already attached", existingSession, target, debugger))
            }

            withContext(Dispatchers.Main) {
                android.attach(debugger, target)
            }

            val session = withTimeoutOrNull(timeoutMs.toLong()) {
                while (true) {
                    delay(500)
                    val attached = android.getExistingSession(debugger, target)
                    if (attached != null) {
                        return@withTimeoutOrNull attached
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                null
            }

            if (session != null) {
                createJsonResult(result("attached", "Android debugger attached", session, target, debugger))
            } else {
                createJsonResult(result("starting", "Android debugger attach was started, but no session appeared before timeout", null, target, debugger))
            }
        } catch (e: AndroidAttachException) {
            createErrorResult(e.message ?: "Failed to attach Android debugger")
        } catch (e: Exception) {
            createErrorResult("Failed to attach Android debugger: ${e.rootMessage()}")
        }
    }

    private fun result(
        status: String,
        message: String,
        session: XDebugSession?,
        target: AndroidProcessCandidate,
        debugger: AndroidDebuggerChoice
    ): AttachAndroidDebuggerResult {
        return AttachAndroidDebuggerResult(
            status = status,
            message = message,
            session = session?.let {
                DebugSessionInfo(
                    id = getSessionId(it),
                    name = it.sessionName,
                    state = if (it.isPaused) "paused" else "running",
                    isCurrent = it == getCurrentSession(it.project),
                    processId = target.pid.toLong()
                )
            },
            deviceSerial = target.deviceSerial,
            pid = target.pid,
            packageName = target.packageName,
            processName = target.processName,
            debuggerId = debugger.id,
            debuggerName = debugger.displayName
        )
    }

    private fun formatCandidates(candidates: List<AndroidProcessCandidate>): String {
        if (candidates.isEmpty()) return ""
        return candidates.joinToString(
            prefix = "\nAvailable candidates:\n",
            separator = "\n"
        ) {
            "- device=${it.deviceSerial}, pid=${it.pid}, package=${it.packageName ?: "unknown"}, process=${it.processName ?: "unknown"}"
        }
    }
}

internal data class AndroidAttachFilters(
    val deviceSerial: String? = null,
    val processName: String? = null,
    val packageName: String? = null,
    val pid: Int? = null
)

internal data class AndroidProcessCandidate(
    val deviceSerial: String,
    val pid: Int,
    val packageName: String?,
    val processName: String?,
    val client: Any
)

internal data class AndroidDebuggerChoice(
    val id: String,
    val displayName: String,
    val debugger: Any
)

internal fun List<AndroidProcessCandidate>.matching(filters: AndroidAttachFilters): List<AndroidProcessCandidate> {
    return filter { candidate ->
        (filters.deviceSerial == null || candidate.deviceSerial == filters.deviceSerial) &&
            (filters.processName == null || candidate.processName == filters.processName) &&
            (filters.packageName == null || candidate.packageName == filters.packageName) &&
            (filters.pid == null || candidate.pid == filters.pid)
    }
}

private class AndroidDebuggerReflection(private val project: Project) {
    private val loader: ClassLoader = PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.android"))
        ?.pluginClassLoader
        ?: throw AndroidAttachException("Android plugin is not available in this IDE")

    fun listProcesses(): List<AndroidProcessCandidate> {
        val sdkUtils = load("org.jetbrains.android.sdk.AndroidSdkUtils")
        val bridge = sdkUtils.getMethod("getDebugBridge", Project::class.java).invoke(null, project)
            ?: throw AndroidAttachException("Android Debug Bridge is not available for this project")

        return bridge.invokeArray("getDevices").flatMap { device ->
            val deviceSerial = device.invokeString("getSerialNumber")
                ?: device.invokeString("getName")
                ?: "unknown"
            device.invokeArray("getClients").mapNotNull { client ->
                val data = client.invoke("getClientData") ?: return@mapNotNull null
                val processName = data.invokeString("getProcessName")
                val packageName = data.invokeStringOrMissing("getPackageName")
                if (processName == null && packageName == null) {
                    return@mapNotNull null
                }
                AndroidProcessCandidate(
                    deviceSerial = deviceSerial,
                    pid = data.invokeInt("getPid") ?: -1,
                    packageName = packageName,
                    processName = processName,
                    client = client
                )
            }
        }
    }

    fun selectDebugger(debuggerId: String?): AndroidDebuggerChoice {
        val debuggerClass = load("com.android.tools.idea.execution.common.debug.AndroidDebugger")
        val extensionPoint = debuggerClass.getField("EP_NAME").get(null)
        val supported = extensionPoint.invokeArray("getExtensions")
            .filter { it.invokeBoolean("supportsProject", project) == true }
            .map {
                AndroidDebuggerChoice(
                    id = it.invokeString("getId") ?: "unknown",
                    displayName = it.invokeString("getDisplayName") ?: "unknown",
                    debugger = it
                )
            }
        if (supported.isEmpty()) {
            throw AndroidAttachException("No Android debuggers are available for this project")
        }

        if (debuggerId != null) {
            return supported.find { it.id == debuggerId }
                ?: throw AndroidAttachException("Android debugger '$debuggerId' was not found. Available debuggers: ${supported.joinToString { it.id }}")
        }

        return supported.find { it.debugger.invokeBoolean("shouldBeDefault") == true }
            ?: supported.find { it.id == "Java" }
            ?: supported.first()
    }

    fun getExistingSession(debugger: AndroidDebuggerChoice, target: AndroidProcessCandidate): XDebugSession? {
        return debugger.debugger.invoke("getExistingDebugSession", project, target.client) as? XDebugSession
    }

    fun attach(debugger: AndroidDebuggerChoice, target: AndroidProcessCandidate) {
        val connectDebugger = load("com.android.tools.idea.execution.common.debug.utils.AndroidConnectDebugger")
        val method = connectDebugger.methods.firstOrNull {
            it.name == "closeOldSessionAndRun" && Modifier.isStatic(it.modifiers) && it.parameterCount == 4
        } ?: throw AndroidAttachException("Android Studio attach API was not found")
        method.invoke(null, project, debugger.debugger, target.client, null)
    }

    private fun load(className: String): Class<*> = try {
        Class.forName(className, true, loader)
    } catch (_: ClassNotFoundException) {
        throw AndroidAttachException("Android Studio class '$className' was not found")
    }
}

private class AndroidAttachException(message: String) : Exception(message)

private fun JsonObject.stringOrNull(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun Any.invoke(methodName: String, vararg args: Any?): Any? {
    val method = javaClass.methods.firstOrNull { it.name == methodName && it.parameterCount == args.size }
        ?: throw AndroidAttachException("Method '$methodName' was not found on ${javaClass.name}")
    return try {
        method.invoke(this, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }
}

private fun Any.invokeArray(methodName: String): List<Any> {
    val array = invoke(methodName) ?: return emptyList()
    val size = java.lang.reflect.Array.getLength(array)
    return (0 until size).mapNotNull { java.lang.reflect.Array.get(array, it) }
}

private fun Any.invokeString(methodName: String): String? = invoke(methodName) as? String

private fun Any.invokeStringOrMissing(methodName: String): String? {
    return try {
        invokeString(methodName)
    } catch (_: AndroidAttachException) {
        null
    }
}

private fun Any.invokeInt(methodName: String): Int? = invoke(methodName) as? Int

private fun Any.invokeBoolean(methodName: String, vararg args: Any?): Boolean? = invoke(methodName, *args) as? Boolean

private fun Throwable.rootMessage(): String {
    val target = (this as? InvocationTargetException)?.targetException ?: this
    return target.message ?: target.javaClass.simpleName
}
