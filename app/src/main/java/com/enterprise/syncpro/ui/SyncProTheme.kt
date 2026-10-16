package com.enterprise.syncpro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ObsidianBackground = Color(0xFF080B11)
val ObsidianSurface = Color(0xFF111726)
val ObsidianCard = Color(0xFF172033)
val EmeraldNeon = Color(0xFF00E676)
val EmeraldDeep = Color(0xFF059669)
val IndigoNeon = Color(0xFF6366F1)
val IndigoDeep = Color(0xFF4338CA)
val AmberWarning = Color(0xFFF59E0B)
val CrimsonAlert = Color(0xFFEF4444)

val LightBackground = Color(0xFFF4F7FB)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFEFF3F9)

private val SyncProDarkColors = darkColorScheme(
    primary = EmeraldNeon,
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = IndigoNeon,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    background = ObsidianBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = ObsidianSurface,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = CrimsonAlert,
    onError = Color.White
)

private val SyncProLightColors = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = IndigoDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    background = LightBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = LightCard,
    onSurfaceVariant = Color(0xFF475569),
    error = CrimsonAlert,
    onError = Color.White
)

private val SyncProTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

object SyncProGradients {
    val EmeraldIndigoHero = Brush.linearGradient(
        colors = listOf(Color(0xFF064E3B), Color(0xFF1E1B4B), Color(0xFF0F172A))
    )
    val IndigoEmeraldCard = Brush.horizontalGradient(
        colors = listOf(Color(0xFF1E293B), Color(0xFF172033))
    )
}

@Composable
fun SyncProTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SyncProDarkColors else SyncProLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SyncProTypography,
        content = content
    )
}