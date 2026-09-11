package dev.auralis.studio.resolver.api

/**
 * Public resolver interface for Lyrune Resolver Runtime (LRR).
 *
 * This contract is pure Kotlin and does not expose Python, yt-dlp, QuickJS,
 * FFmpeg, or process management details to callers.
 */
interface MediaResolver {
    /**
     * Inspects a media source to obtain metadata and available formats without
     * fetching final direct media payloads.
     */
    suspend fun inspect(request: ResolverRequest): ResolverResult

    /**
     * Resolves a media source to obtain fresh, immediately usable media formats
     * with active resource leases.
     */
    suspend fun resolve(request: ResolverRequest): ResolverResult

    /**
     * Returns the current operational health of the resolver runtime.
     */
    suspend fun health(): RuntimeHealth

    /**
     * Cancels any in-flight inspection or resolution task matching [requestId].
     */
    suspend fun cancel(requestId: String)
}
