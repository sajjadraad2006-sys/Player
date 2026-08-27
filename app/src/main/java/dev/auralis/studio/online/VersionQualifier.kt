package dev.auralis.studio.online

/**
 * First-class semantic version qualifier for recordings.
 *
 * Qualifier conflicts (e.g. ORIGINAL vs LIVE, ORIGINAL vs REMIX, ORIGINAL vs COVER)
 * are strong merge blockers. Fuzzy matching scores must NEVER override a version qualifier conflict.
 */
enum class VersionQualifier {
    ORIGINAL,
    LIVE,
    REMIX,
    ACOUSTIC,
    UNPLUGGED,
    COVER,
    INSTRUMENTAL,
    KARAOKE,
    DEMO,
    RADIO_EDIT,
    SLOWED,
    SPED_UP,
    NIGHTCORE,
    RE_RECORDED,
    UNKNOWN;

    companion object {
        /**
         * Detects the primary semantic version qualifier from a title or metadata string.
         */
        fun parse(title: String): VersionQualifier = VersionProfile.parse(title).primary
    }
}

/**
 * Multi-qualifier version profile (e.g. {LIVE, ACOUSTIC}, {RADIO_EDIT, REMIX}).
 *
 * Evaluates semantic version equality independently of keyword parsing order.
 */
data class VersionProfile(
    val qualifiers: Set<VersionQualifier> = setOf(VersionQualifier.ORIGINAL),
) {
    val primary: VersionQualifier get() = qualifiers.firstOrNull() ?: VersionQualifier.ORIGINAL
    val isOriginal: Boolean get() = qualifiers.isEmpty() || qualifiers == setOf(VersionQualifier.ORIGINAL)

    fun isCompatibleWith(other: VersionProfile): Boolean {
        if (isOriginal && other.isOriginal) return true
        return qualifiers == other.qualifiers
    }

    companion object {
        val ORIGINAL = VersionProfile(setOf(VersionQualifier.ORIGINAL))

        fun parse(title: String): VersionProfile {
            val norm = IdentityNormalization.normalize(title)
            val found = mutableSetOf<VersionQualifier>()

            if (norm.hasToken("nightcore")) found.add(VersionQualifier.NIGHTCORE)
            if (norm.hasToken("sped up") || norm.hasToken("speed up") || norm.hasToken("speedup")) found.add(VersionQualifier.SPED_UP)
            if (norm.hasToken("slowed") || norm.hasToken("slowed reverb") || norm.hasToken("slowed down")) found.add(VersionQualifier.SLOWED)
            if (norm.hasToken("karaoke")) found.add(VersionQualifier.KARAOKE)
            if (norm.hasToken("instrumental") || norm.hasToken("backing track")) found.add(VersionQualifier.INSTRUMENTAL)
            if (norm.hasToken("acoustic")) found.add(VersionQualifier.ACOUSTIC)
            if (norm.hasToken("unplugged")) found.add(VersionQualifier.UNPLUGGED)
            if (norm.hasToken("live") || norm.hasToken("concert") || norm.hasToken("live at") || norm.hasToken("live session")) found.add(VersionQualifier.LIVE)
            if (norm.hasToken("remix") || norm.hasToken("club mix") || norm.hasToken("extended mix") || norm.hasToken("dub mix") || norm.hasToken("vip mix") || norm.hasToken("mashup")) found.add(VersionQualifier.REMIX)
            if (norm.hasToken("cover") || norm.hasToken("tribute")) found.add(VersionQualifier.COVER)
            if (norm.hasToken("demo")) found.add(VersionQualifier.DEMO)
            if (norm.hasToken("radio edit") || norm.hasToken("radio version") || norm.hasToken("clean edit") || norm.hasToken("clean version")) found.add(VersionQualifier.RADIO_EDIT)
            if (norm.hasToken("re recorded") || norm.hasToken("rerecorded") || norm.hasToken("taylor s version")) found.add(VersionQualifier.RE_RECORDED)

            return if (found.isEmpty()) ORIGINAL else VersionProfile(found)
        }

        private fun String.hasToken(token: String): Boolean {
            if (this == token) return true
            if (startsWith("$token ")) return true
            if (endsWith(" $token")) return true
            if (contains(" $token ")) return true
            return false
        }
    }
}
