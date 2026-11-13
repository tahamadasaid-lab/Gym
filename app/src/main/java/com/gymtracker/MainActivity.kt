package com.gymtracker

import android.content.Intent
import android.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.gymtracker.widget.GymWidgetProvider
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gymtracker.settings.ThemeMode
import com.gymtracker.ui.GymTrackerApp
import com.gymtracker.ui.theme.GymTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsStore = (application as GymApp).settings
        handleIntent(intent)
        setContent {
            val settings by settingsStore.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Icônes de la barre d'état / de navigation lisibles selon le thème choisi.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GymTrackerTheme(darkTheme = dark, pureBlack = settings.pureBlack) {
                GymTrackerApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Bouton « Démarrer une séance » du widget. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action == GymWidgetProvider.ACTION_START_SESSION) {
            intent.action = null
            val app = application as GymApp
            lifecycleScope.launch { app.repository.startSessionIfNone() }
        }
    }
}
