package com.anmol.voyage

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import com.anmol.voyage.state.VoyageState
import com.anmol.voyage.ui.theme.VoyageTheme

class MainActivity : ComponentActivity() {

    private val voyageState: VoyageState by lazy {
        ViewModelProvider(this, VoyageState.Factory).get(VoyageState::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API (backported to API 26 by core-splashscreen).
        val splashScreen = installSplashScreen()
        // Saved state decides the theme, so holding the splash until it is read
        // is what keeps a cold start from flashing the wrong one. The read is a
        // single small file; the flag is set even if it fails, so this cannot
        // wedge the launch.
        splashScreen.setKeepOnScreenCondition { !voyageState.isLoaded }
        // Draw behind the system bars; every screen consumes insets itself.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val darkTheme = voyageState.themeMode.isDark(isSystemInDarkTheme())
            // The user's choice can disagree with the system's, and the system
            // bar icons follow the app, not the system.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        Color.TRANSPARENT,
                        Color.TRANSPARENT,
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        LIGHT_NAV_BAR_SCRIM,
                        DARK_NAV_BAR_SCRIM,
                    ) { darkTheme },
                )
            }

            VoyageTheme(darkTheme = darkTheme) {
                VoyageApp(state = voyageState)
            }
        }
    }

    private companion object {
        // Scrims drawn behind three-button navigation only, from the AndroidX
        // edge-to-edge guidance; gesture navigation stays fully transparent.
        val LIGHT_NAV_BAR_SCRIM = Color.argb(0xe6, 0xff, 0xff, 0xff)
        val DARK_NAV_BAR_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
