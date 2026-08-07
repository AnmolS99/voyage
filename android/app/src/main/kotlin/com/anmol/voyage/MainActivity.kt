package com.anmol.voyage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.anmol.voyage.ui.theme.VoyageTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash Screen API (backported to API 26 by core-splashscreen).
        installSplashScreen()
        // Draw behind the system bars; every screen consumes insets itself.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            VoyageTheme {
                VoyageApp()
            }
        }
    }
}
