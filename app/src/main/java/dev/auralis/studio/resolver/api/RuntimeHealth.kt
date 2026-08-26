package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable

/**
 * Health diagnostic report for the Lyrune Resolver Runtime (LRR).
 *
 * Explicitly distinguishes EJS package installation from operational JS execution.
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
    val ffmpegReady: Boolean = false,
    val details: Map<String, String> = emptyMap(),
) {
    /** Backward compatibility helper for legacy UI checks */
    val ejsReady: Boolean get() = ejsInstalled && ejsCompatible
    val jsRuntimeReady: Boolean get() = jsRuntimeDetectedByYtDlp

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
            ffmpegReady = false,
            details = mapOf("status" to "NOT_INITIALIZED"),
        )
    }
}

