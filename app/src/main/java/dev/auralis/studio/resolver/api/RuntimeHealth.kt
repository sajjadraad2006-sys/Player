package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable

/**
 * Health diagnostic report for the Lyrune Resolver Runtime (LRR).
 *
 * Unsupported/uninstalled components report explicitly as NOT_INSTALLED rather than fake OK states.
 */
@Serializable
data class RuntimeHealth(
    val resolverServiceReady: Boolean,
    val pythonReady: Boolean,
    val ytDlpReady: Boolean,
    val ejsReady: Boolean = false,
    val jsRuntimeReady: Boolean = false,
    val ffmpegReady: Boolean = false,
    val details: Map<String, String> = emptyMap(),
) {
    companion object {
        val UNINITIALIZED = RuntimeHealth(
            resolverServiceReady = false,
            pythonReady = false,
            ytDlpReady = false,
            ejsReady = false,
            jsRuntimeReady = false,
            ffmpegReady = false,
            details = mapOf("status" to "NOT_INITIALIZED"),
        )
    }
}
