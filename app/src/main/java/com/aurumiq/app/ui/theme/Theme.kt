package com.aurumiq.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════════
// AurumIQ Color Palette — Professional Quantitative Research Tool
// ═══════════════════════════════════════════════════════════════

// Primary: Deep Gold / Amber tones
val GoldPrimary = Color(0xFFD4A017)
val GoldPrimaryDark = Color(0xFFB8860B)
val GoldOnPrimary = Color(0xFF1A1100)

// Surface: Dark slate for professional look
val SurfaceDark = Color(0xFF0F1419)
val SurfaceContainerDark = Color(0xFF161B22)
val SurfaceContainerHighDark = Color(0xFF1C2128)
val SurfaceVariantDark = Color(0xFF21262D)

// Accent colors for signals
val SignalGreen = Color(0xFF2EA043)      // Premium / Positive
val SignalRed = Color(0xFFDA3633)        // Discount / Negative
val SignalAmber = Color(0xFFE3B341)      // Watch
val SignalBlue = Color(0xFF388BFD)       // Information
val SignalGray = Color(0xFF8B949E)       // Normal / Insufficient

// Chart colors
val ChartLine1 = Color(0xFFD4A017)       // Gold
val ChartLine2 = Color(0xFF388BFD)       // Blue
val ChartFill1 = Color(0x40D4A017)       // Gold transparent
val ChartFill2 = Color(0x40388BFD)       // Blue transparent
val ChartGrid = Color(0xFF30363D)
val ChartZeroLine = Color(0xFF484F58)

// Text
val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8B949E)
val TextMuted = Color(0xFF6E7681)

// Borders
val BorderDefault = Color(0xFF30363D)
val BorderMuted = Color(0xFF21262D)

// Status badges
val BadgeSuccess = Color(0xFF1A7F37)
val BadgeWarning = Color(0xFF9A6700)
val BadgeDanger = Color(0xFFCF222E)
val BadgeInfo = Color(0xFF0969DA)

private val DarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = GoldOnPrimary,
    primaryContainer = Color(0xFF3D2E00),
    onPrimaryContainer = Color(0xFFFFE08C),
    secondary = SignalBlue,
    onSecondary = Color(0xFF003258),
    secondaryContainer = Color(0xFF004A7D),
    onSecondaryContainer = Color(0xFFCAE6FF),
    tertiary = SignalGreen,
    error = SignalRed,
    onError = Color(0xFF690005),
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDefault,
    outlineVariant = BorderMuted,
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = Color(0xFF272C33),
)

val AurumTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextMuted
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun AurumIQTheme(content: @Composable () -> Unit) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = SurfaceDark.toArgb()
            window.navigationBarColor = SurfaceDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AurumTypography,
        content = content
    )
}
