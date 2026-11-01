package com.anmol.voyage.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.anmol.voyage.R
import com.anmol.voyage.VoyageActivity
import com.anmol.voyage.ui.theme.VoyagePalette

/**
 * The launcher icon, iOS's App Icon picker less its System choice: Android
 * launchers have no dark-mode icons, so the dark one is picked here rather than
 * by the system.
 *
 * Each icon is an `activity-alias` of [VoyageActivity] in the manifest, and
 * exactly one is enabled. The package manager remembers which, so that is the
 * source of truth and nothing is saved in `VoyageState`.
 *
 * Disabling the alias the app was opened from closes its screen, so a choice
 * only waits as [pending] until [VoyageActivity] stops, and [applyPending]
 * swaps the aliases once voyage is out of sight.
 */
internal enum class LauncherIcon(
    private val aliasName: String,
    @StringRes val labelRes: Int,
    /** The adaptive icon's layers, as `mipmap-anydpi-v26/ic_launcher*.xml` stacks them. */
    @ColorRes val background: Int,
    @DrawableRes val foreground: Int,
) {
    Light("MainActivity", R.string.theme_light, R.color.voyage_orange, R.mipmap.ic_launcher_foreground),
    Dark("DarkIconActivity", R.string.theme_dark, R.color.voyage_page_bg_dark, R.mipmap.ic_launcher_dark_foreground);

    fun component(context: Context) =
        ComponentName(context, "${VoyageActivity::class.java.`package`!!.name}.$aliasName")

    companion object {
        private const val PREFS = "launcher_icon"
        private const val PENDING_KEY = "pending"

        private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** The icon the launcher shows now. */
        private fun enabled(context: Context): LauncherIcon =
            if (context.packageManager.getComponentEnabledSetting(Dark.component(context)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) Dark else Light

        /** A choice not yet on the launcher, or null when there is none. */
        fun pending(context: Context): LauncherIcon? =
            prefs(context).getString(PENDING_KEY, null)?.let { name -> entries.find { it.name == name } }

        /** The icon chosen last: the pending one if any, else the one on the launcher. */
        fun current(context: Context): LauncherIcon = pending(context) ?: enabled(context)

        /** Records [icon] to be applied when voyage leaves the screen. */
        fun select(context: Context, icon: LauncherIcon) {
            prefs(context).edit {
                if (icon == enabled(context)) remove(PENDING_KEY) else putString(PENDING_KEY, icon.name)
            }
        }

        /**
         * Puts the pending icon on the launcher, enabling its alias before
         * disabling the other so there is always a launcher entry.
         */
        fun applyPending(context: Context) {
            val icon = pending(context) ?: return
            prefs(context).edit { remove(PENDING_KEY) }
            val packageManager = context.packageManager
            packageManager.setComponentEnabledSetting(
                icon.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            entries.filter { it != icon }.forEach {
                packageManager.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/**
 * Light or dark launcher icon, as a row of icon previews like iOS's. A preview
 * is the adaptive icon's own layers, its background color under its foreground,
 * cut to a circle as the default launcher shows it.
 */
@Composable
internal fun AppIconSetting() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LauncherIcon.current(context)) }
    var isPending by remember { mutableStateOf(LauncherIcon.pending(context) != null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = VoyagePalette.buttonColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_app_icon), style = MaterialTheme.typography.bodyLarge)
        }
        Row(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            LauncherIcon.entries.forEach { icon ->
                LauncherIconOption(
                    icon = icon,
                    isSelected = icon == selected,
                    onClick = {
                        LauncherIcon.select(context, icon)
                        selected = icon
                        isPending = LauncherIcon.pending(context) != null
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (isPending) {
            Text(
                text = stringResource(R.string.settings_app_icon_pending),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One icon's preview with its name below, ringed in the app's orange when chosen. */
@Composable
private fun LauncherIconOption(
    icon: LauncherIcon,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .testTag(appIconOptionTag(icon)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .border(2.dp, if (isSelected) VoyagePalette.buttonColor else Color.Transparent, CircleShape)
                .padding(4.dp)
                .size(PREVIEW_SIZE)
                .clip(CircleShape)
                .background(colorResource(icon.background)),
            contentAlignment = Alignment.Center,
        ) {
            // The layer is 108 dp, of which the launcher shows the middle 72.
            Image(
                painter = painterResource(icon.foreground),
                contentDescription = null,
                modifier = Modifier.requiredSize(PREVIEW_SIZE * 1.5f),
            )
        }
        Text(
            text = stringResource(icon.labelRes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val PREVIEW_SIZE = 56.dp

/** Test tag for one icon's option in the app icon picker. */
internal fun appIconOptionTag(icon: LauncherIcon) = "settings_app_icon:${icon.name}"
