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
 * Font for raw data — numerals and machine identifiers only. Nord keeps
 * body text in the regular sans (see Type.kt); components should read
 * this instead of hard-coding FontFamily.Monospace.
 */
val LocalDataFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

// Nord: soft, generous corners — one shape scale for both themes
// (16dp cards), so a layout never changes shape with the theme.
private val NordShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// Light: snow-storm surfaces, nord0 text. The primary green is deepened
// (#5C7A45) wherever it can appear as text; the bright nord14 (#A3BE8C)
// lives in containers and fills.
private val AntminerLightColorScheme = lightColorScheme(
    primary = Color(0xFF5C7A45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8C9),
    onPrimaryContainer = Color(0xFF33481F),
    secondary = Color(0xFF5E81AC), // nord10 blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E2EE),
    onSecondaryContainer = Color(0xFF2E4258),
    tertiary = Color(0xFFB7791F), // deepened nord13 amber
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF7E8C3),
    onTertiaryContainer = Color(0xFF5C430A),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorderSubtle,
    error = ErrorRuby, // nord11 #BF616A
    onError = Color.White,
    errorContainer = Color(0xFFF3D9DB),
    onErrorContainer = Color(0xFF6E2A30)
)

// Dark: polar-night surfaces, snow-storm text. Same hues as light; the
// primary is the bright nord14 itself, which reads well on nord1.
private val AntminerDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA3BE8C), // nord14
    onPrimary = Color(0xFF2E3440),
    primaryContainer = Color(0xFF4A5D3A),
    onPrimaryContainer = Color(0xFFDCE8C9),
    secondary = Color(0xFF88C0D0), // nord8 frost
    onSecondary = Color(0xFF2E3440),
    secondaryContainer = Color(0xFF3D5A73),
    onSecondaryContainer = Color(0xFFD8E9F2),
    tertiary = Color(0xFFEBCB8B), // nord13
    onTertiary = Color(0xFF2E3440),
    tertiaryContainer = Color(0xFF5C4A1F),
    onTertiaryContainer = Color(0xFFF7E8C3),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    outlineVariant = DarkBorderSubtle,
    error = Color(0xFFD98E94), // nord11 lightened for small text on polar night
    onError = Color(0xFF2E3440),
    errorContainer = Color(0xFF5A3438),
    onErrorContainer = Color(0xFFF3D9DB)
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
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalDataFontFamily provides FontFamily.Monospace) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NordTypography,
            shapes = NordShapes,
            content = content
        )
    }
}
