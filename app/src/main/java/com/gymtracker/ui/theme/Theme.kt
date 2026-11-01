package com.gymtracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Orange = Color(0xFFFF6D2E)
private val OrangeDark = Color(0xFFFFB59A)

private val DarkColors = darkColorScheme(
    primary = OrangeDark,
    onPrimary = Color(0xFF5A1B00),
    primaryContainer = Color(0xFF7F2B00),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFF7BD3C4),
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB33F00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF390C00),
    secondary = Color(0xFF00695C),
)

@Composable
fun GymTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

val AccentOrange = Orange
