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
 * Font for raw data — numerals and machine identifiers only. Body text
 * stays in the regular sans (see Type.kt); components should read
 * this instead of hard-coding FontFamily.Monospace.
 */
val LocalDataFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

// Soft, generous corners — one shape scale for both themes
// (16dp cards), so a layout never changes shape with the theme.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Light: lavender-white surfaces (mockup .th-violet-l), deep violet
// accent. The mockup's --accent #6D28D9 is already deep enough to serve
// as text and fills alike; its tints fill the containers.
private val AntminerLightColorScheme = lightColorScheme(
    primary = Color(0xFF6D28D9), // mockup --accent
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE), // mockup --accentBg / --selRow
    onPrimaryContainer = Color(0xFF4C2D8A), // mockup --chipText
    secondary = Color(0xFF5B21B6), // mockup --accentSoft
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDD6FE), // mockup --navPill / --border
    onSecondaryContainer = Color(0xFF3B1F73), // derived deep violet
    tertiary = Color(0xFFBE7D1C), // mockup --amber (needs-password / warning)
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9ECD2), // derived light amber tint
    onTertiaryContainer = Color(0xFF5C430A),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = ErrorRuby, // mockup --red #BC4B4B
    onError = Color.White,
    errorContainer = Color(0xFFF9E9E9), // mockup --redBg
    onErrorContainer = Color(0xFF5F2323) // derived deep red
)

// Dark: TRUE AMOLED (mockup .th-violet-d) — pure #000000 background,
// near-black #0A0A0B cards, violet-tinted borders; the bright violet
// #A78BFA accent glows on black. No grey surfaces anywhere.
private val AntminerDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA), // mockup --accent
    onPrimary = Color(0xFF1A0F38), // mockup --navPill
    primaryContainer = Color(0xFF160B31), // mockup --selRow
    onPrimaryContainer = Color(0xFFC4B5FD), // mockup --accentSoft
    secondary = Color(0xFFC4B5FD), // mockup --accentSoft
    onSecondary = Color(0xFF1A0F38),
    secondaryContainer = Color(0xFF171226), // mockup --chip
    onSecondaryContainer = Color(0xFFB8A8E0), // mockup --chipText
    tertiary = Color(0xFFE8A33D), // mockup --amber
    onTertiary = Color(0xFF241600), // derived dark amber-brown
    tertiaryContainer = Color(0xFF33270C), // derived dark amber container
    onTertiaryContainer = Color(0xFFF7E8C3),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = Color(0xFFE0655F), // mockup --red, brightened for black
    onError = Color(0xFF2A0E0D),
    errorContainer = Color(0xFF2A0E0D), // mockup --redBg
    onErrorContainer = Color(0xFFF2C9C5) // derived pale red
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
            // The mockup's nav bar sits on the background tone (--navBg:
            // #F5F3FF light / pure #000000 dark), not on the card surface.
            window.navigationBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalDataFontFamily provides FontFamily.Monospace) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NordTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
