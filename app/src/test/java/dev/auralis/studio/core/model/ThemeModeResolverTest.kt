package dev.auralis.studio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeResolverTest {
    @Test fun lightNeverBecomesDark() = assertFalse(ThemeModeResolver.isDark(AppearanceMode.LIGHT, systemDark = true))
    @Test fun darkNeverBecomesLight() = assertTrue(ThemeModeResolver.isDark(AppearanceMode.DARK, systemDark = false))
    @Test fun systemFollowsPlatform() {
        assertTrue(ThemeModeResolver.isDark(AppearanceMode.SYSTEM, true))
        assertFalse(ThemeModeResolver.isDark(AppearanceMode.SYSTEM, false))
    }
    @Test fun amoledIsOnlyAnAppearance() {
        assertTrue(ThemeModeResolver.isAmoled(AppearanceMode.AMOLED))
        assertEquals(ThemeStyle.MINIMAL, ThemeModeResolver.migrateLegacyStyle("AMOLED"))
    }
}
