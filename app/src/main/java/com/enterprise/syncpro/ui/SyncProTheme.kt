package com.enterprise.syncpro.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// 4-Color Unified Palette: AMOLED Black, Electric Emerald Primary, Crisp Slate Neutral, Crimson Alert
val ObsidianBackground = Color(0xFF000000) // Pure AMOLED Black
val ObsidianSurface = Color(0xFF08080A)    // Deep AMOLED Surface
val ObsidianCard = Color(0xFF121214)       // AMOLED Card Container
val EmeraldNeon = Color(0xFF00E676)        // Primary Accent
val EmeraldDeep = Color(0xFF059669)        // Light Mode Primary
val EmeraldDark = EmeraldDeep              // High-contrast accent for light backgrounds
val IndigoNeon = Color(0xFF00E676)         // Unified with Primary Brand for 3-4 color elegance
val IndigoDeep = Color(0xFF059669)         // Unified with Primary Brand for 3-4 color elegance
val AmberWarning = Color(0xFFEF4444)       // Unified Alert
val CrimsonAlert = Color(0xFFEF4444)       // Semantic Destructive / Stop / Error

val LightBackground = Color(0xFFF8F9FA)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFF1F3F5)

private val SyncProDarkColors = darkColorScheme(
    primary = EmeraldNeon,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF04261B),
    onPrimaryContainer = EmeraldNeon,
    secondary = EmeraldNeon,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF04261B),
    onSecondaryContainer = EmeraldNeon,
    background = Color(0xFF000000), // Pure AMOLED Black
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF121214), // AMOLED Card
    onSurfaceVariant = Color(0xFF9E9EA4),
    outline = Color(0xFF26262B),
    outlineVariant = Color(0xFF1C1C20), // Crisp hairline divider
    error = CrimsonAlert,
    onError = Color.White
)

private val SyncProLightColors = lightColorScheme(
    primary = EmeraldDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = EmeraldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F3F5),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFD1D5DB),
    outlineVariant = Color(0xFFE5E7EB), // Crisp hairline divider
    error = CrimsonAlert,
    onError = Color.White
)

private val SyncProTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

object SyncProGradients {
    val EmeraldIndigoHero = Brush.verticalGradient(
        colors = listOf(Color(0xFF042017), Color(0xFF08080A), Color(0xFF000000))
    )
    val EmeraldHeroLight = Brush.verticalGradient(
        colors = listOf(Color(0xFFE8F8F0), Color(0xFFF1F5F9))
    )
    val IndigoEmeraldCard = Brush.horizontalGradient(
        colors = listOf(Color(0xFF121214), Color(0xFF141418))
    )
}

@Composable
fun syncProSwitchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedThumbColor = Color.Black,
    checkedTrackColor = EmeraldNeon,
    checkedBorderColor = EmeraldNeon,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

@Composable
fun SyncProTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) SyncProDarkColors else SyncProLightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SyncProTypography,
        content = content
    )
}