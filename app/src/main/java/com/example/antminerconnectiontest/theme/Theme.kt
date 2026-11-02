package com.example.antminerconnectiontest.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * Font for raw data/numerals. The dark "Control Room" theme sets data in
 * monospace; the light "Clean Light" theme uses the regular rounded sans.
 * Screens should read this instead of hard-coding FontFamily.Monospace so
 * the same layout speaks both theme languages.
 */
val LocalDataFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

// Control Room: sharp, technical corners.
private val DarkShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

// Clean Light: soft, generous corners.
private val LightShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AntminerDarkColorScheme = darkColorScheme(
    primary = AntminerEmerald,
    onPrimary = Color(0xFF003919),
    primaryContainer = AntminerEmeraldDark,
    onPrimaryContainer = Color(0xFFA7F3D0),
    secondary = CyberCyan,
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = WarningAmber,
    onTertiary = Color(0xFF3A2A00),
    tertiaryContainer = Color(0xFF4A3600),
    onTertiaryContainer = Color(0xFFFFDE9E),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = ErrorRuby,
    onError = Color.White,
    errorContainer = Color(0xFF4C1D1D),
    onErrorContainer = Color(0xFFFFD2D2)
)

private val AntminerLightColorScheme = lightColorScheme(
    primary = LightEmerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = LightEmeraldDark,
    // Clean Light keeps a single green accent family: secondary is a
    // deeper green rather than a second hue.
    secondary = Color(0xFF15803D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9F2E2),
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = LightWarning,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B)
)

@Composable
fun AntminerConnectionTestTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) AntminerDarkColorScheme else AntminerLightColorScheme
    val typography = if (isDark) DarkTypography else LightTypography
    val shapes = if (isDark) DarkShapes else LightShapes
    val dataFont: FontFamily = if (isDark) FontFamily.Monospace else FontFamily.Default

    // Status / navigation bar appearance follows the APP theme, not the
    // system theme. enableEdgeToEdge()'s automatic style keys icon
    // brightness off the phone's day/night setting, so a user running the
    // app in Dark while the phone is in light mode (or vice versa) got
    // dark icons on a near-black bar — an illegible status bar. Setting
    // the appearance flags explicitly per resolved theme fixes the
    // contrast in both themes; the bar colors cover pre-edge-to-edge
    // Android versions, and every screen's Scaffold insets keep content
    // out from under the bars.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalDataFontFamily provides dataFont) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
