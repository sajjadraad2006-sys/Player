package dev.auralis.studio.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import dev.auralis.studio.R

data class AppIconOption(
    val id: String,
    @StringRes val titleRes: Int,
    val defaultName: String,
    @DrawableRes val iconRes: Int,
    val componentName: String,
)

object AppIconManager {
    private const val TAG = "AppIconManager"

    val availableIcons = listOf(
        AppIconOption(
            id = "default",
            titleRes = R.string.icon_default,
            defaultName = "Lyrune Default",
            iconRes = R.drawable.ic_icon_default,
            componentName = "dev.auralis.studio.MainActivityDefault",
        ),
        AppIconOption(
            id = "azure",
            titleRes = R.string.icon_azure,
            defaultName = "Azure Neon",
            iconRes = R.drawable.ic_icon_azure,
            componentName = "dev.auralis.studio.MainActivityAliasAzure",
        ),
        AppIconOption(
            id = "silver",
            titleRes = R.string.icon_silver,
            defaultName = "Futuristic Silver",
            iconRes = R.drawable.ic_icon_silver,
            componentName = "dev.auralis.studio.MainActivityAliasSilver",
        ),
        AppIconOption(
            id = "orange",
            titleRes = R.string.icon_orange,
            defaultName = "Glowing Orange",
            iconRes = R.drawable.ic_icon_orange,
            componentName = "dev.auralis.studio.MainActivityAliasOrange",
        ),
        AppIconOption(
            id = "golden",
            titleRes = R.string.icon_golden,
            defaultName = "Golden Neon",
            iconRes = R.drawable.ic_icon_golden,
            componentName = "dev.auralis.studio.MainActivityAliasGolden",
        ),
        AppIconOption(
            id = "cyan",
            titleRes = R.string.icon_cyan,
            defaultName = "Neon Cyan",
            iconRes = R.drawable.ic_icon_cyan,
            componentName = "dev.auralis.studio.MainActivityAliasCyan",
        ),
        AppIconOption(
            id = "gradient",
            titleRes = R.string.icon_gradient,
            defaultName = "Neon Gradient",
            iconRes = R.drawable.ic_icon_gradient,
            componentName = "dev.auralis.studio.MainActivityAliasGradient",
        ),
        AppIconOption(
            id = "green",
            titleRes = R.string.icon_green,
            defaultName = "Neon Green",
            iconRes = R.drawable.ic_icon_green,
            componentName = "dev.auralis.studio.MainActivityAliasGreen",
        ),
        AppIconOption(
            id = "pink",
            titleRes = R.string.icon_pink,
            defaultName = "Neon Pink",
            iconRes = R.drawable.ic_icon_pink,
            componentName = "dev.auralis.studio.MainActivityAliasPink",
        ),
        AppIconOption(
            id = "purple",
            titleRes = R.string.icon_purple,
            defaultName = "Neon Purple",
            iconRes = R.drawable.ic_icon_purple,
            componentName = "dev.auralis.studio.MainActivityAliasPurple",
        ),
    )

    fun setAppIcon(context: Context, targetIconId: String): Boolean {
        val pm = context.packageManager
        val packageName = context.packageName
        val target = availableIcons.firstOrNull { it.id == targetIconId } ?: return false

        return try {
            // Enable the chosen component
            pm.setComponentEnabledSetting(
                ComponentName(packageName, target.componentName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )

            // Disable all other components
            availableIcons.filter { it.id != target.id }.forEach { other ->
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, other.componentName),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error switching launcher icon to $targetIconId", e)
            false
        }
    }

    fun getCurrentActiveIconId(context: Context): String {
        val pm = context.packageManager
        val packageName = context.packageName

        for (icon in availableIcons) {
            try {
                val state = pm.getComponentEnabledSetting(ComponentName(packageName, icon.componentName))
                if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    return icon.id
                }
            } catch (_: Exception) {}
        }
        return "default"
    }
}
