package dev.auralis.studio.settings

import dev.auralis.studio.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconManagerTest {

    @Test
    fun availableIcons_containsAllTenIcons() {
        assertEquals("Must contain 10 launcher icon options (default + 9 custom)", 10, AppIconManager.availableIcons.size)
    }

    @Test
    fun availableIcons_hasUniqueIds() {
        val ids = AppIconManager.availableIcons.map { it.id }
        assertEquals("Icon IDs must all be unique", ids.distinct().size, ids.size)
    }

    @Test
    fun availableIcons_containsExpectedCustomThemeIds() {
        val expectedCustomIds = listOf(
            "default",
            "azure",
            "silver",
            "orange",
            "golden",
            "cyan",
            "gradient",
            "green",
            "pink",
            "purple",
        )
        val actualIds = AppIconManager.availableIcons.map { it.id }
        assertEquals(expectedCustomIds, actualIds)
    }

    @Test
    fun defaultIcon_targetsMainActivityDefaultAlias() {
        val defaultIcon = AppIconManager.availableIcons.firstOrNull { it.id == "default" }
        assertNotNull("Default icon must exist", defaultIcon)
        assertEquals("dev.auralis.studio.MainActivityDefault", defaultIcon?.componentName)
        assertEquals(R.drawable.ic_icon_default, defaultIcon?.iconRes)
    }

    @Test
    fun customIcons_targetValidActivityAliases() {
        val aliasesMap = mapOf(
            "azure" to "dev.auralis.studio.MainActivityAliasAzure",
            "silver" to "dev.auralis.studio.MainActivityAliasSilver",
            "orange" to "dev.auralis.studio.MainActivityAliasOrange",
            "golden" to "dev.auralis.studio.MainActivityAliasGolden",
            "cyan" to "dev.auralis.studio.MainActivityAliasCyan",
            "gradient" to "dev.auralis.studio.MainActivityAliasGradient",
            "green" to "dev.auralis.studio.MainActivityAliasGreen",
            "pink" to "dev.auralis.studio.MainActivityAliasPink",
            "purple" to "dev.auralis.studio.MainActivityAliasPurple",
        )

        for ((id, expectedAlias) in aliasesMap) {
            val icon = AppIconManager.availableIcons.firstOrNull { it.id == id }
            assertNotNull("Icon option for '$id' must exist", icon)
            assertEquals("Component name for '$id' must match manifest alias", expectedAlias, icon?.componentName)
            assertTrue("Drawable resource for '$id' must be valid", (icon?.iconRes ?: 0) != 0)
            assertTrue("Title resource for '$id' must be valid", (icon?.titleRes ?: 0) != 0)
            assertTrue("Default name for '$id' must not be blank", icon?.defaultName?.isNotBlank() == true)
        }
    }
}
