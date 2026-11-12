package com.gymtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF2B0D00),
    primaryContainer = Color(0xFF5C2410),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFF4FD8B8),
    onSecondary = Color(0xFF00382D),
    secondaryContainer = Color(0xFF0F4D40),
    onSecondaryContainer = Color(0xFFA6F5DF),
    tertiary = Color(0xFFFFC857),
    onTertiary = Color(0xFF3D2B00),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFE6A8),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFE8E9ED),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFE8E9ED),
    surfaceVariant = Color(0xFF2A2E36),
    onSurfaceVariant = Color(0xFFA9AEB8),
    outline = Color(0xFF5A606B),
    outlineVariant = Color(0xFF2F333B),
    surfaceContainerLowest = Color(0xFF0A0B0E),
    surfaceContainerLow = Color(0xFF15181D),
    surfaceContainer = Color(0xFF191C22),
    surfaceContainerHigh = Color(0xFF20242B),
    surfaceContainerHighest = Color(0xFF272B33),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondary = Color(0xFF00796B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2F1E3),
    onSecondaryContainer = Color(0xFF00201A),
    tertiary = Color(0xFF9A6B00),
    tertiaryContainer = Color(0xFFFFE6A8),
    onTertiaryContainer = Color(0xFF2F2000),
    background = Color(0xFFF7F7FA),
    surface = Color(0xFFF7F7FA),
    surfaceVariant = Color(0xFFE2E3E9),
    onSurfaceVariant = Color(0xFF4A4F59),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F2F6),
    surfaceContainer = Color(0xFFEEEFF3),
    surfaceContainerHigh = Color(0xFFE8E9EE),
    surfaceContainerHighest = Color(0xFFE2E3E9),
)

private val base = Typography()
private val AppTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Variante « noir intégral » pour écrans OLED. */
private val PureBlackColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0C0F),
    surfaceContainer = Color(0xFF111317),
    surfaceContainerHigh = Color(0xFF181B20),
    surfaceContainerHighest = Color(0xFF1F2228),
)

/** Vert « progression ». */
val PositiveGreen = Color(0xFF3DCB7F)

@Composable
fun GymTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = when {
            !darkTheme -> LightColors
            pureBlack -> PureBlackColors
            else -> DarkColors
        },
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
