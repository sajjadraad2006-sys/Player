package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable

/**
 * Health diagnostic report for the Lyrune Resolver Runtime (LRR).
 *
 * Explicitly distinguishes EJS package installation from operational JS execution
 * and real extractor challenge readiness.
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
    /** Backward compatibility helpers */
    val ejsReady: Boolean get() = ejsInstalled && ejsCompatible
    val jsRuntimeReady: Boolean get() = jsRuntimeDetectedByYtDlp

    /** Process diagnostics computed on the service side */
    val resolverProcessName: String? get() = details["process_name"]
    val resolverPid: Int? get() = details["pid"]?.toIntOrNull()
    val resolverProcessRole: String? get() = details["process_role"]
    val isMainContainerInitializedInResolver: Boolean? get() = details["main_container_initialized"]?.toBooleanStrictOrNull()

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
