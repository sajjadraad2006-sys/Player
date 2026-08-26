package dev.auralis.studio.resolver.engine

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Chaquopy/Python backend engine wrapping yt-dlp metadata inspection.
 *
 * This class runs strictly inside the isolated resolver process (:resolver)
 * and must not be called directly from the main player process.
 */
class ChaquopyYtDlpEngine(private val context: Context) {

    private var pythonModule: Any? = null
    private var isInitialized = false
    private var initError: String? = null

    init {
        ensurePythonInitialized()
    }

    @Synchronized
    private fun ensurePythonInitialized() {
        if (isInitialized) return
        try {
            // Lazy reflection / platform start to allow graceful degradation in host tests
            val pythonClass = Class.forName("com.chaquo.python.Python")
            val isStartedMethod = pythonClass.getMethod("isStarted")
            val isStarted = isStartedMethod.invoke(null) as Boolean

            if (!isStarted) {
                val platformClass = Class.forName("com.chaquo.python.android.AndroidPlatform")
                val platformConstructor = platformClass.getConstructor(Context::class.java)
                val platformInstance = platformConstructor.newInstance(context.applicationContext)

                val startMethod = pythonClass.methods.first { it.name == "start" && it.parameterTypes.size == 1 }
                startMethod.invoke(null, platformInstance)
            }

            val getInstanceMethod = pythonClass.getMethod("getInstance")
            val pythonInstance = getInstanceMethod.invoke(null)

            val getModuleMethod = pythonClass.getMethod("getModule", String::class.java)
            val module = getModuleMethod.invoke(pythonInstance, "lrr_extractor")
            pythonModule = module

            if (module != null) {
                configureQuickJs(module)
            }

            isInitialized = true
            Log.i(TAG, "Chaquopy Python engine initialized successfully in process PID ${android.os.Process.myPid()}")
        } catch (t: Throwable) {
            initError = "${t.javaClass.simpleName}: ${t.message}"
            Log.w(TAG, "Python runtime initialization failed: $initError", t)
        }
    }

    private fun configureQuickJs(module: Any) {
        try {
            val nativeLibDir = context.applicationInfo.nativeLibraryDir
            val qjsFile = java.io.File(nativeLibDir, "libqjs.so")
            if (qjsFile.exists() && qjsFile.canExecute()) {
                val setPathMethod = module.javaClass.getMethod("callAttr", String::class.java, Array<Any>::class.java)
                setPathMethod.invoke(module, "set_quickjs_path", arrayOf<Any>(qjsFile.absolutePath))
                Log.i(TAG, "Configured QuickJS runtime from nativeLibraryDir: ${qjsFile.absolutePath}")
            } else {
                Log.d(TAG, "QuickJS native library not found or not executable at $nativeLibDir/libqjs.so")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not configure QuickJS path in Python module", t)
        }
    }

    fun inspect(requestJson: String): String {
        ensurePythonInitialized()
        val module = pythonModule
        if (module == null) {
            return buildFallbackError(
                source = parseSourceUrl(requestJson),
                code = "PythonUnavailable",
                message = "Python resolver engine is not initialized.",
                details = initError ?: "Python module not loaded",
            )
        }

        return try {
            val callMethod = module.javaClass.getMethod("callAttr", String::class.java, Array<Any>::class.java)
            val result = callMethod.invoke(module, "inspect_source", arrayOf<Any>(requestJson))
            result?.toString() ?: buildFallbackError(
                source = parseSourceUrl(requestJson),
                code = "Unknown",
                message = "Empty response from resolver engine.",
                details = null,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Engine inspection failure", t)
            buildFallbackError(
                source = parseSourceUrl(requestJson),
                code = "RuntimeCrashed",
                message = "Resolver engine exception during inspection.",
                details = "${t.javaClass.name}: ${t.message}",
            )
        }
    }

    fun resolve(requestJson: String): String {
        ensurePythonInitialized()
        val module = pythonModule
        if (module == null) {
            return buildFallbackError(
                source = parseSourceUrl(requestJson),
                code = "PythonUnavailable",
                message = "Python resolver engine is not initialized.",
                details = initError ?: "Python module not loaded",
            )
        }

        return try {
            val callMethod = module.javaClass.getMethod("callAttr", String::class.java, Array<Any>::class.java)
            val result = callMethod.invoke(module, "resolve_source", arrayOf<Any>(requestJson))
            result?.toString() ?: buildFallbackError(
                source = parseSourceUrl(requestJson),
                code = "Unknown",
                message = "Empty response from resolver engine.",
                details = null,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Engine resolution failure", t)
            buildFallbackError(
                source = parseSourceUrl(requestJson),
                code = "RuntimeCrashed",
                message = "Resolver engine exception during resolution.",
                details = "${t.javaClass.name}: ${t.message}",
            )
        }
    }

    fun getHealthJson(): String {
        ensurePythonInitialized()
        val module = pythonModule
        if (module == null) {
            return buildJsonObject {
                put("resolverServiceReady", true)
                put("pythonReady", false)
                put("ytDlpReady", false)
                put("ejsReady", false)
                put("jsRuntimeReady", false)
                put("ffmpegReady", false)
                putJsonObject("details") {
                    put("status", "PYTHON_UNAVAILABLE")
                    put("error", initError ?: "Module not initialized")
                    put("python_version", "NOT_INSTALLED")
                    put("yt_dlp_version", "NOT_INSTALLED")
                    put("ejs_version", "NOT_INSTALLED")
                    put("quickjs_version", "NOT_INSTALLED")
                    put("ffmpeg_version", "NOT_INSTALLED")
                }
            }.toString()
        }

        return try {
            val callMethod = module.javaClass.getMethod("callAttr", String::class.java, Array<Any>::class.java)
            val result = callMethod.invoke(module, "get_health_json", arrayOf<Any>())
            result?.toString() ?: buildJsonObject {
                put("resolverServiceReady", true)
                put("pythonReady", true)
                put("ytDlpReady", false)
            }.toString()
        } catch (t: Throwable) {
            buildJsonObject {
                put("resolverServiceReady", true)
                put("pythonReady", false)
                put("error", t.message ?: "Health check failure")
            }.toString()
        }
    }

    private fun parseSourceUrl(requestJson: String): String {
        return try {
            val element = Json.parseToJsonElement(requestJson)
            (element as? kotlinx.serialization.json.JsonObject)?.get("sourceUrl")?.toString()?.trim('"') ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun buildFallbackError(
        source: String,
        code: String,
        message: String,
        details: String?,
    ): String {
        return buildJsonObject {
            put("success", false)
            putJsonObject("error") {
                put("source", source)
                put("technicalCode", code)
                put("isRetryable", code == "RuntimeCrashed")
                put("safeUserMessage", message)
                if (details != null) {
                    put("debugDetails", details)
                }
            }
        }.toString()
    }

    companion object {
        private const val TAG = "LRR:ChaquopyEngine"
    }
}
