package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun `empty locale list means system default`() =
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags(""))

    @Test fun `explicit locales resolve independently`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en"))
        assertEquals(AppLanguage.ARABIC, AppLanguage.fromLanguageTags("ar-IQ"))
    }
}
