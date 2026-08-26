package dev.auralis.studio.resolver.api

/**
 * Evaluates the download permission capability for a resolved resource format.
 *
 * Core Invariant: Technical resolution != Download permission.
 * Generic HTTP/HTTPS URLs default to [DownloadCapability.UNKNOWN].
 * A format becomes [DownloadCapability.PERMITTED] only with explicit trusted basis.
 */
object DownloadCapabilityEvaluator {

    data class EvaluationResult(
        val capability: DownloadCapability,
        val basis: DownloadCapabilityBasis,
    )

    fun evaluate(
        source: String,
        webpageUrl: String? = null,
        license: String? = null,
        isTestFixture: Boolean = false,
    ): EvaluationResult {
        if (isTestFixture) {
            return EvaluationResult(DownloadCapability.PERMITTED, DownloadCapabilityBasis.TEST_FIXTURE)
        }

        val normSource = source.lowercase().trim()
        val normUrl = (webpageUrl ?: "").lowercase().trim()
        val normLicense = (license ?: "").lowercase().trim()

        // ── 1. Known Restricted / Discovery-Only Providers ────────────────────
        if (normSource.contains("youtube") || normUrl.contains("youtube.com") || normUrl.contains("youtu.be")) {
            return EvaluationResult(DownloadCapability.STREAM_ONLY, DownloadCapabilityBasis.PROVIDER_EXPLICIT)
        }

        if (normSource.contains("itunes") || normSource.contains("apple") || normUrl.contains("apple.com")) {
            return EvaluationResult(DownloadCapability.NOT_PERMITTED, DownloadCapabilityBasis.PROVIDER_EXPLICIT)
        }

        if (normSource.contains("deezer") || normUrl.contains("deezer.com")) {
            return EvaluationResult(DownloadCapability.NOT_PERMITTED, DownloadCapabilityBasis.PROVIDER_EXPLICIT)
        }

        // ── 2. Explicit Open License Metadata ─────────────────────────────────
        if (normLicense.isNotBlank()) {
            if (normLicense.contains("cc-by") ||
                normLicense.contains("creative commons") ||
                normLicense.contains("public domain") ||
                normLicense.contains("cc0") ||
                normLicense.contains("mit") ||
                normLicense.contains("apache")
            ) {
                return EvaluationResult(DownloadCapability.PERMITTED, DownloadCapabilityBasis.LICENSE_METADATA)
            }
        }

        // ── 3. Generic / Unknown Web Resources Default to UNKNOWN ────────────
        // Never assume HTTP 200 + .mp3 = PERMITTED.
        return EvaluationResult(DownloadCapability.UNKNOWN, DownloadCapabilityBasis.NONE)
    }

    /**
     * Annotates all formats in a [ResolvedMedia] instance with evaluated capabilities.
     */
    fun annotateMedia(media: ResolvedMedia, isTestFixture: Boolean = false): ResolvedMedia {
        val eval = evaluate(
            source = media.source,
            webpageUrl = media.webpageUrl,
            isTestFixture = isTestFixture,
        )

        val annotatedFormats = media.formats.map { fmt ->
            fmt.copy(
                downloadCapability = eval.capability,
                downloadCapabilityBasis = eval.basis,
            )
        }

        return media.copy(formats = annotatedFormats)
    }
}
