package dev.auralis.studio.core.model

enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    ARABIC("ar");

    companion object {
        fun fromLanguageTags(tags: String?): AppLanguage = when {
            tags.isNullOrBlank() -> SYSTEM
            tags.split(',').first().startsWith("ar", ignoreCase = true) -> ARABIC
            else -> ENGLISH
        }
    }
}
