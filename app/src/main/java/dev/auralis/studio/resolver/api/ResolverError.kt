package dev.auralis.studio.resolver.api

import kotlinx.serialization.Serializable

/**
 * Structured taxonomy of resolver errors eliminating generic "Source Error" strings.
 */
@Serializable
enum class ResolverErrorCode {
    UnsupportedUrl,
    NotFound,
    NetworkUnavailable,
    DnsFailure,
    Timeout,
    RateLimited,
    AuthenticationRequired,
    AccessRestricted,
    DownloadNotPermitted,
    RuntimeUnavailable,
    PythonUnavailable,
    JsRuntimeUnavailable,
    EjsIncompatible,
    ChallengeFailed,
    ExpiredResource,
    FormatUnavailable,
    ProviderChanged,
    RuntimeCrashed,
    Cancelled,
    Unknown,
}

/**
 * Structured error details returned when media inspection or resolution fails.
 */
@Serializable
data class ResolverError(
    val source: String,
    val technicalCode: ResolverErrorCode,
    val isRetryable: Boolean,
    val safeUserMessage: String,
    val debugDetails: String? = null,
) {
    companion object {
        fun unsupported(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.UnsupportedUrl,
            isRetryable = false,
            safeUserMessage = "The provided link is not supported.",
            debugDetails = details,
        )

        fun notFound(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.NotFound,
            isRetryable = false,
            safeUserMessage = "The requested media could not be found.",
            debugDetails = details,
        )

        fun networkUnavailable(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.NetworkUnavailable,
            isRetryable = true,
            safeUserMessage = "Network connection unavailable. Please check your internet connection.",
            debugDetails = details,
        )

        fun timeout(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.Timeout,
            isRetryable = true,
            safeUserMessage = "The request timed out. Please try again.",
            debugDetails = details,
        )

        fun accessRestricted(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.AccessRestricted,
            isRetryable = false,
            safeUserMessage = "Access to this content is restricted by the provider.",
            debugDetails = details,
        )

        fun downloadNotPermitted(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.DownloadNotPermitted,
            isRetryable = false,
            safeUserMessage = "Downloading this content is not permitted.",
            debugDetails = details,
        )

        fun runtimeCrashed(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.RuntimeCrashed,
            isRetryable = true,
            safeUserMessage = "The resolver background process encountered an error and was restarted.",
            debugDetails = details,
        )

        fun cancelled(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.Cancelled,
            isRetryable = false,
            safeUserMessage = "The resolution operation was cancelled.",
            debugDetails = details,
        )

        fun jsRuntimeUnavailable(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.JsRuntimeUnavailable,
            isRetryable = false,
            safeUserMessage = "JavaScript runtime is required for this media provider.",
            debugDetails = details,
        )

        fun ejsIncompatible(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.EjsIncompatible,
            isRetryable = false,
            safeUserMessage = "Extractor JavaScript bundle is incompatible with this provider version.",
            debugDetails = details,
        )

        fun challengeFailed(source: String, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.ChallengeFailed,
            isRetryable = true,
            safeUserMessage = "Could not resolve provider challenge.",
            debugDetails = details,
        )

        fun unknown(source: String, message: String? = null, details: String? = null) = ResolverError(
            source = source,
            technicalCode = ResolverErrorCode.Unknown,
            isRetryable = false,
            safeUserMessage = message ?: "Unable to resolve media from this source.",
            debugDetails = details,
        )
    }
}

/**
 * Sealed result type returned by [MediaResolver].
 */
@Serializable
sealed interface ResolverResult {
    @Serializable
    data class Success(val media: ResolvedMedia) : ResolverResult

    @Serializable
    data class Failure(val error: ResolverError) : ResolverResult
}
