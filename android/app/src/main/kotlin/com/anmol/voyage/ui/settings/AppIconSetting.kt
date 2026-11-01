package com.anmol.voyage.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
 */
internal enum class LauncherIcon(private val aliasName: String, @StringRes val labelRes: Int) {
    Light("MainActivity", R.string.theme_light),
    Dark("DarkIconActivity", R.string.theme_dark);

    fun component(context: Context) =
        ComponentName(context, "${VoyageActivity::class.java.`package`!!.name}.$aliasName")

    companion object {
        fun current(context: Context): LauncherIcon =
            if (context.packageManager.getComponentEnabledSetting(Dark.component(context)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) Dark else Light

        /** Enables [icon]'s alias before disabling the other, so there is always a launcher entry. */
        fun select(context: Context, icon: LauncherIcon) {
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

/** Light or dark launcher icon, as a segmented button row like the theme's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppIconSetting() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(LauncherIcon.current(context)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = VoyagePalette.buttonColor,
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.settings_app_icon), style = MaterialTheme.typography.bodyLarge)
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            LauncherIcon.entries.forEachIndexed { index, icon ->
                SegmentedButton(
                    selected = icon == selected,
                    onClick = {
                        LauncherIcon.select(context, icon)
                        selected = icon
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = LauncherIcon.entries.size),
                    modifier = Modifier.testTag(appIconOptionTag(icon)),
                ) {
                    Text(stringResource(icon.labelRes), maxLines = 1)
                }
            }
        }
    }
}

/** Test tag for one icon's segment in the app icon picker. */
internal fun appIconOptionTag(icon: LauncherIcon) = "settings_app_icon:${icon.name}"
