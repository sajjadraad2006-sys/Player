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
         * Detects the semantic version qualifier from a title or metadata string.
         */
        fun parse(title: String): VersionQualifier {
            val norm = IdentityNormalization.normalize(title)
            return when {
                norm.hasToken("nightcore") -> NIGHTCORE
                norm.hasToken("sped up") || norm.hasToken("speed up") || norm.hasToken("speedup") -> SPED_UP
                norm.hasToken("slowed") || norm.hasToken("slowed reverb") || norm.hasToken("slowed down") -> SLOWED
                norm.hasToken("karaoke") -> KARAOKE
                norm.hasToken("instrumental") || norm.hasToken("backing track") -> INSTRUMENTAL
                norm.hasToken("acoustic") -> ACOUSTIC
                norm.hasToken("unplugged") -> UNPLUGGED
                norm.hasToken("live") || norm.hasToken("concert") || norm.hasToken("live at") || norm.hasToken("live session") -> LIVE
                norm.hasToken("remix") || norm.hasToken("club mix") || norm.hasToken("extended mix") || norm.hasToken("dub mix") || norm.hasToken("vip mix") || norm.hasToken("mashup") -> REMIX
                norm.hasToken("cover") || norm.hasToken("tribute") -> COVER
                norm.hasToken("demo") -> DEMO
                norm.hasToken("radio edit") || norm.hasToken("radio version") || norm.hasToken("clean edit") || norm.hasToken("clean version") -> RADIO_EDIT
                norm.hasToken("re recorded") || norm.hasToken("rerecorded") || norm.hasToken("taylor s version") -> RE_RECORDED
                else -> ORIGINAL
            }
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
