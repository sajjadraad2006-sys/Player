package dev.auralis.studio.resolver.api

/**
 * Recognized machine-readable open licenses that permit offline audio download.
 *
 * Strict Rule: Vague human-readable claims (e.g. "Creative Commons", "Free", "No Copyright")
 * are NOT trusted automatically unless they match a canonical verified license specification.
 */
enum class RecognizedDownloadLicense {
    CC0,
    PUBLIC_DOMAIN,
    CC_BY,
    CC_BY_SA,
    CC_BY_NC,
    CC_BY_NC_SA,
    CC_BY_ND,
    CC_BY_NC_ND,
    MIT,
    APACHE_2_0,
    UNRECOGNIZED;

    val isPermittedDownload: Boolean
        get() = this != UNRECOGNIZED

    companion object {
        fun parse(licenseString: String?, licenseUrl: String? = null): RecognizedDownloadLicense {
            val url = (licenseUrl ?: "").lowercase().trim()
            val text = (licenseString ?: "").lowercase().trim()

            // 1. Strict Canonical License URLs
            if (url.contains("creativecommons.org/publicdomain/zero/1.0") ||
                url.contains("creativecommons.org/publicdomain/zero")
            ) return CC0

            if (url.contains("creativecommons.org/publicdomain/mark") ||
                url.contains("publicdomain")
            ) return PUBLIC_DOMAIN

            if (url.contains("creativecommons.org/licenses/by/")) return CC_BY
            if (url.contains("creativecommons.org/licenses/by-sa/")) return CC_BY_SA
            if (url.contains("creativecommons.org/licenses/by-nc/")) return CC_BY_NC
            if (url.contains("creativecommons.org/licenses/by-nc-sa/")) return CC_BY_NC_SA
            if (url.contains("creativecommons.org/licenses/by-nd/")) return CC_BY_ND
            if (url.contains("creativecommons.org/licenses/by-nc-nd/")) return CC_BY_NC_ND
            if (url.contains("opensource.org/licenses/mit")) return MIT
            if (url.contains("apache.org/licenses/license-2.0")) return APACHE_2_0

            // 2. Strict Explicit Machine-Readable or Canonical Text Tokens
            // Reject vague strings like "Creative Commons", "Free", "No Copyright", "Royalty Free", "Download allowed"
            return when {
                text == "cc0" || text == "cc0 1.0" || text.startsWith("cc0 1.0 universal") -> CC0
                text == "public domain" || text == "pd" || text == "public domain / cc0" || text.startsWith("cc-pd") -> PUBLIC_DOMAIN
                text.matches(Regex("""^cc[ -_]?by([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY
                text.matches(Regex("""^cc[ -_]?by[ -_]?sa([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_SA
                text.matches(Regex("""^cc[ -_]?by[ -_]?nc([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_NC
                text.matches(Regex("""^cc[ -_]?by[ -_]?nc[ -_]?sa([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_NC_SA
                text.matches(Regex("""^cc[ -_]?by[ -_]?nd([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_ND
                text.matches(Regex("""^cc[ -_]?by[ -_]?nc[ -_]?nd([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_NC_ND
                text == "mit" || text == "mit license" -> MIT
                text == "apache 2.0" || text == "apache-2.0" || text == "apache license 2.0" -> APACHE_2_0
                // Vague, informal, or unverified claims remain UNRECOGNIZED -> UNKNOWN
                else -> UNRECOGNIZED
            }
        }
    }
}

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
        val recognizedLicense: RecognizedDownloadLicense = RecognizedDownloadLicense.UNRECOGNIZED,
    )

    fun evaluate(
        source: String,
        webpageUrl: String? = null,
        license: String? = null,
        licenseUrl: String? = null,
        isTestFixture: Boolean = false,
    ): EvaluationResult {
        if (isTestFixture) {
            return EvaluationResult(DownloadCapability.PERMITTED, DownloadCapabilityBasis.TEST_FIXTURE)
        }

        val normSource = source.lowercase().trim()
        val normUrl = (webpageUrl ?: "").lowercase().trim()

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

        // ── 2. Official Provider Contracts ────────────────────────────────────
        if (normSource.contains("audius") || normUrl.contains("audius.co")) {
            return EvaluationResult(DownloadCapability.PERMITTED, DownloadCapabilityBasis.PROVIDER_EXPLICIT)
        }

        if (normSource.contains("jamendo") || normUrl.contains("jamendo.com")) {
            return EvaluationResult(DownloadCapability.PERMITTED, DownloadCapabilityBasis.PROVIDER_EXPLICIT)
        }

        // ── 3. Strict Recognized Open License Metadata ────────────────────────
        val parsedLicense = RecognizedDownloadLicense.parse(license, licenseUrl)
        if (parsedLicense.isPermittedDownload) {
            return EvaluationResult(
                capability = DownloadCapability.PERMITTED,
                basis = DownloadCapabilityBasis.LICENSE_METADATA,
                recognizedLicense = parsedLicense,
            )
        }

        // ── 4. Generic / Unknown Web Resources Default to UNKNOWN ────────────
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
