package com.example.antminerconnectiontest.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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

// Grouped Sheets shape scale (v0.4.0): 12 dp panels per the V3
// mockup, tighter chips, sheet/dialog corners from the same family.
// The app's only shape scale since the Violet style was removed in
// v0.5.0.
private val GroupedShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp)
)

// ── Grouped Sheets schemes (v0.4.0) ────────────────────────────────
// The V3 redesign (approved whole-app mockup, Round-12 "Grouped
// Sheets" final) — the app's ONE design since v0.5.0. The schemes are
// FULLY OPAQUE — a flat grouped base, white / #1C1C1E panels, hairline
// borders — with Apple blue as the only accent and status carried by
// labels + monochrome slots (tertiary and error resolve to the text
// colour; their containers are neutral greys). Real glass survives
// only in the floating dock, whose colours come from
// [GroupedSheetsSpec] directly, not from these schemes. Tokens are
// pinned in [GroupedSheetsSpec]; the mapping below is the one place
// they meet Material3.
private val GlassLightColorScheme = lightColorScheme(
    primary = GroupedSheetsSpec.LightAccent,
    onPrimary = Color.White,
    primaryContainer = GroupedSheetsSpec.LightAccent.copy(alpha = 0.12f),
    onPrimaryContainer = GroupedSheetsSpec.LightAccent,
    secondary = GroupedSheetsSpec.LightAccent,
    onSecondary = Color.White,
    secondaryContainer = GroupedSheetsSpec.LightChipBackground,
    onSecondaryContainer = GroupedSheetsSpec.LightText,
    tertiary = GroupedSheetsSpec.LightText,
    onTertiary = Color.White,
    tertiaryContainer = GroupedSheetsSpec.LightChipBackground,
    onTertiaryContainer = GroupedSheetsSpec.LightText,
    background = GroupedSheetsSpec.LightBackground,
    onBackground = GroupedSheetsSpec.LightText,
    surface = GroupedSheetsSpec.LightPanel,
    onSurface = GroupedSheetsSpec.LightText,
    surfaceVariant = GroupedSheetsSpec.LightChipBackground,
    onSurfaceVariant = GroupedSheetsSpec.LightSecondary,
    surfaceContainerLowest = GroupedSheetsSpec.LightPanel,
    surfaceContainerLow = GroupedSheetsSpec.LightPanel,
    surfaceContainer = GroupedSheetsSpec.LightPanel,
    surfaceContainerHigh = GroupedSheetsSpec.LightPanelElevated,
    surfaceContainerHighest = GroupedSheetsSpec.LightPanel,
    outline = GroupedSheetsSpec.LightHairline,
    outlineVariant = GroupedSheetsSpec.LightSeparator,
    error = GroupedSheetsSpec.LightText,
    onError = Color.White,
    errorContainer = GroupedSheetsSpec.LightPanelElevated,
    onErrorContainer = GroupedSheetsSpec.LightText,
    inverseSurface = GroupedSheetsSpec.DarkPanel,
    inverseOnSurface = GroupedSheetsSpec.DarkText,
    inversePrimary = GroupedSheetsSpec.DarkAccent
)

private val GlassDarkColorScheme = darkColorScheme(
    primary = GroupedSheetsSpec.DarkAccent,
    onPrimary = Color.White,
    primaryContainer = GroupedSheetsSpec.DarkAccent.copy(alpha = 0.16f),
    onPrimaryContainer = GroupedSheetsSpec.DarkAccent,
    secondary = GroupedSheetsSpec.DarkAccent,
    onSecondary = Color.White,
    secondaryContainer = GroupedSheetsSpec.DarkChipBackground,
    onSecondaryContainer = GroupedSheetsSpec.DarkText,
    tertiary = GroupedSheetsSpec.DarkText,
    onTertiary = Color.Black,
    tertiaryContainer = GroupedSheetsSpec.DarkChipBackground,
    onTertiaryContainer = GroupedSheetsSpec.DarkText,
    background = GroupedSheetsSpec.DarkBackground,
    onBackground = GroupedSheetsSpec.DarkText,
    surface = GroupedSheetsSpec.DarkPanel,
    onSurface = GroupedSheetsSpec.DarkText,
    surfaceVariant = GroupedSheetsSpec.DarkChipBackground,
    onSurfaceVariant = GroupedSheetsSpec.DarkSecondary,
    surfaceContainerLowest = GroupedSheetsSpec.DarkPanel,
    surfaceContainerLow = GroupedSheetsSpec.DarkPanel,
    surfaceContainer = GroupedSheetsSpec.DarkPanel,
    surfaceContainerHigh = GroupedSheetsSpec.DarkPanelElevated,
    surfaceContainerHighest = GroupedSheetsSpec.DarkPanelElevated,
    outline = GroupedSheetsSpec.DarkHairline,
    outlineVariant = GroupedSheetsSpec.DarkSeparator,
    error = GroupedSheetsSpec.DarkText,
    onError = Color.Black,
    errorContainer = GroupedSheetsSpec.DarkPanelElevated,
    onErrorContainer = GroupedSheetsSpec.DarkText,
    inverseSurface = GroupedSheetsSpec.LightBackground,
    inverseOnSurface = GroupedSheetsSpec.LightText,
    inversePrimary = GroupedSheetsSpec.LightAccent
)

/**
 * The Grouped Sheets scheme for the resolved darkness, for surfaces
 * that resolve their own scheme regardless of the shell's theme —
 * the relay-only status screen, which renders the grouped design in
 * fully opaque form (the grouped schemes are already opaque, so this
 * is simply the same scheme the rest of the app uses).
 */
fun groupedSheetsColorScheme(isDark: Boolean): ColorScheme =
    if (isDark) GlassDarkColorScheme else GlassLightColorScheme

@Composable
fun AntminerConnectionTestTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    glassTier: GlassTier = GlassTier.LITE,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) GlassDarkColorScheme else GlassLightColorScheme

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
            // The nav bar sits on the background tone (the grouped
            // base: #F2F2F7 light / pure #000000 dark), not on a panel.
            window.navigationBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(
        LocalDataFontFamily provides FontFamily.Monospace,
        LocalGlassSettings provides GlassSettings(tier = glassTier, isDark = isDark)
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NordTypography,
            shapes = GroupedShapes,
            content = content
        )
    }
}
