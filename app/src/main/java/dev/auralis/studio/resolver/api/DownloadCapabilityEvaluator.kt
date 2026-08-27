package dev.auralis.studio.resolver.api

/**
 * Recognized machine-readable open media/content licenses that permit offline audio download.
 *
 * Strict Rule: Media licenses must be vetted open content licenses (CC0, Public Domain, CC-BY, CC-BY-SA, etc.).
 * Software licenses (e.g. MIT, Apache-2.0) and vague human claims (e.g. "Creative Commons", "Free", "No Copyright")
 * are NOT sufficient evidence that arbitrary resolved audio may be downloaded.
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
    UNRECOGNIZED;

    val isPermittedDownload: Boolean
        get() = when (this) {
            CC0, PUBLIC_DOMAIN, CC_BY, CC_BY_SA -> true
            CC_BY_NC, CC_BY_NC_SA, CC_BY_ND, CC_BY_NC_ND, UNRECOGNIZED -> false
        }

    companion object {
        fun parse(licenseString: String?, licenseUrl: String? = null): RecognizedDownloadLicense {
            val url = (licenseUrl ?: "").lowercase().trim()
            val text = (licenseString ?: "").lowercase().trim()

            // 1. Strict Canonical Open Media License URLs
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

            // 2. Strict Explicit Machine-Readable or Canonical Text Tokens
            // Software licenses (MIT, Apache) and vague claims ("Creative Commons", "Free", "No Copyright") -> UNRECOGNIZED
            return when {
                text == "cc0" || text == "cc0 1.0" || text.startsWith("cc0 1.0 universal") -> CC0
                text == "public domain" || text == "pd" || text == "public domain / cc0" || text.startsWith("cc-pd") -> PUBLIC_DOMAIN
                text.matches(Regex("""^cc[ -_]?by([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY
                text.matches(Regex("""^cc[ -_]?by[ -_]?sa([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_SA
                text.matches(Regex("""^cc[ -_]?by[ -_]?nc([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_NC
                text.matches(Regex("""^cc[ -_]?by[ -_]?nc[ -_]?sa([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_NC_SA
                text.matches(Regex("""^cc[ -_]?by[ -_]?nd([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_ND
                text.matches(Regex("""^cc[ -_]?by[ -_]?nc[ -_]?nd([ -_]?(1\.0|2\.0|2\.5|3\.0|4\.0))?(\.0)?(\s+.*)?$""")) -> CC_BY_NC_ND
                // All other claims (including software licenses) remain UNRECOGNIZED -> UNKNOWN
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

        // ── 3. Strict Recognized Open Content License Metadata ────────────────
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
        return EvaluationResult(
            capability = DownloadCapability.UNKNOWN,
            basis = DownloadCapabilityBasis.NONE,
            recognizedLicense = parsedLicense,
        )
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

        val annotatedRaw = media.rawFormats.map { fmt ->
            fmt.copy(
                downloadCapability = eval.capability,
                downloadCapabilityBasis = eval.basis,
            )
        }

        return media.copy(formats = annotatedFormats, rawFormats = annotatedRaw)
    }
}
