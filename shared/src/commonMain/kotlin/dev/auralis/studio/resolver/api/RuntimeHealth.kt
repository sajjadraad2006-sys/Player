package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable

/**
 * Health diagnostic report for the Lyrune Resolver Runtime (LRR).
 *
 * Explicitly tracks process status, Python readiness, yt-dlp readiness, and extraction capabilities.
 */
@Serializable
data class RuntimeHealth(
    val resolverServiceReady: Boolean,
    val pythonReady: Boolean,
    val ytDlpReady: Boolean,
    val ejsInstalled: Boolean = false,
    val ejsCompatible: Boolean = false,
    val jsRuntimePresent: Boolean = false,
    val jsRuntimeExecutable: Boolean = false,
    val jsRuntimeDetectedByYtDlp: Boolean = false,
    val jsChallengeSelfTestReady: Boolean = false,
    val realExtractionReady: Boolean = false,
    val ffmpegReady: Boolean = false,
    val details: Map<String, String> = emptyMap(),
) {
    val ejsReady: Boolean get() = ejsInstalled && ejsCompatible
    val jsRuntimeReady: Boolean get() = jsRuntimeDetectedByYtDlp

    val resolverProcessName: String? get() = details["process_name"]
    val resolverPid: Int? get() = details["pid"]?.toIntOrNull()
    val resolverProcessRole: String? get() = details["process_role"]

    companion object {
        val UNINITIALIZED = RuntimeHealth(
            resolverServiceReady = false,
            pythonReady = false,
            ytDlpReady = false,
            ejsInstalled = false,
            ejsCompatible = false,
            jsRuntimePresent = false,
            jsRuntimeExecutable = false,
            jsRuntimeDetectedByYtDlp = false,
            jsChallengeSelfTestReady = false,
            realExtractionReady = false,
            ffmpegReady = false,
            details = mapOf("status" to "NOT_INITIALIZED"),
        )
    }
}
