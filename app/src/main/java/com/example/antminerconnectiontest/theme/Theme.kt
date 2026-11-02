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

// ── Liquid Glass schemes (v0.3.0, round-5 mockup) ────────────────────
// Same violet hues and status semantics as the solid schemes — only
// the MATERIAL changes: surfaces become translucent fills that float
// over the wallpaper (see GlassWallpaper), borders brighten toward a
// specular edge. Background stays a solid base colour so window bars
// and anything painted behind the wallpaper keep a sane tone; the
// shell draws the wallpaper over it and makes its own containers
// transparent. Surface-container slots are overridden too: Material3
// defaults (bottom sheet, menus, cards in some versions) read them.
private val GlassLightColorScheme = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE).copy(alpha = 0.72f),
    onPrimaryContainer = Color(0xFF4C2D8A),
    secondary = Color(0xFF5B21B6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDD6FE).copy(alpha = 0.66f),
    onSecondaryContainer = Color(0xFF3B1F73),
    tertiary = Color(0xFFBE7D1C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF9ECD2).copy(alpha = 0.78f),
    onTertiaryContainer = Color(0xFF5C430A),
    background = Color(0xFFE9E4FB), // glass base (wallpaper covers it)
    onBackground = LightTextPrimary,
    surface = Color.White.copy(alpha = 0.62f),
    onSurface = LightTextPrimary,
    surfaceVariant = Color(0xFFEDE9FE).copy(alpha = 0.55f),
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = Color.White.copy(alpha = 0.45f),
    surfaceContainerLow = Color.White.copy(alpha = 0.55f),
    surfaceContainer = Color.White.copy(alpha = 0.62f),
    surfaceContainerHigh = Color.White.copy(alpha = 0.72f),
    surfaceContainerHighest = Color.White.copy(alpha = 0.82f),
    outline = Color(0xFFB7A8E8),
    outlineVariant = Color(0xFFD9D0F5),
    error = ErrorRuby,
    onError = Color.White,
    errorContainer = Color(0xFFF9E9E9).copy(alpha = 0.85f),
    onErrorContainer = Color(0xFF5F2323)
)

private val GlassDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1A0F38),
    primaryContainer = Color(0xFF160B31).copy(alpha = 0.72f),
    onPrimaryContainer = Color(0xFFC4B5FD),
    secondary = Color(0xFFC4B5FD),
    onSecondary = Color(0xFF1A0F38),
    secondaryContainer = Color(0xFF171226).copy(alpha = 0.66f),
    onSecondaryContainer = Color(0xFFB8A8E0),
    tertiary = Color(0xFFE8A33D),
    onTertiary = Color(0xFF241600),
    tertiaryContainer = Color(0xFF33270C).copy(alpha = 0.78f),
    onTertiaryContainer = Color(0xFFF7E8C3),
    background = Color(0xFF000000), // glass base: true black
    onBackground = DarkTextPrimary,
    surface = Color(0xFF171226).copy(alpha = 0.55f),
    onSurface = DarkTextPrimary,
    surfaceVariant = Color(0xFF171226).copy(alpha = 0.48f),
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = Color(0xFF0A0A0B).copy(alpha = 0.45f),
    surfaceContainerLow = Color(0xFF12101E).copy(alpha = 0.52f),
    surfaceContainer = Color(0xFF171226).copy(alpha = 0.55f),
    surfaceContainerHigh = Color(0xFF1E1836).copy(alpha = 0.65f),
    surfaceContainerHighest = Color(0xFF251D42).copy(alpha = 0.75f),
    outline = Color(0xFF4A3A75),
    outlineVariant = Color(0xFF2C2149),
    error = Color(0xFFE0655F),
    onError = Color(0xFF2A0E0D),
    errorContainer = Color(0xFF2A0E0D).copy(alpha = 0.85f),
    onErrorContainer = Color(0xFFF2C9C5)
)

@Composable
fun AntminerConnectionTestTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    appStyle: AppStyle = AppStyle.VIOLET,
    glassTier: GlassTier = GlassTier.LITE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val isGlass = appStyle == AppStyle.LIQUID_GLASS
    val colorScheme = when {
        isGlass && isDark -> GlassDarkColorScheme
        isGlass -> GlassLightColorScheme
        isDark -> AntminerDarkColorScheme
        else -> AntminerLightColorScheme
    }

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

    CompositionLocalProvider(
        LocalDataFontFamily provides FontFamily.Monospace,
        LocalGlassSettings provides GlassSettings(style = appStyle, tier = glassTier, isDark = isDark)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NordTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
