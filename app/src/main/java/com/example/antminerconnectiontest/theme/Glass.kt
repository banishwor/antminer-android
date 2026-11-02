package com.example.antminerconnectiontest.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Appearance style (v0.3.0): the app's look, chosen in Settings →
 * Appearance → Style and persisted like the theme mode. [VIOLET] is
 * the default solid theme; [LIQUID_GLASS] is the opt-in frosted-glass
 * look from the approved round-5 mockup. The style never changes
 * layout, content or status-colour semantics — only the material.
 */
enum class AppStyle {
    VIOLET,
    LIQUID_GLASS
}

/**
 * How much real blur a device can afford. [FULL] = live-rendered blur
 * is allowed (scoped: the static wallpaper, and a blurred-wallpaper
 * backdrop inside the hero + tab bar only). [LITE] = "glass-lite":
 * identical translucency, specular edges and floating capsule, but a
 * pre-blurred static background and NO live blur anywhere — a cheap
 * relay phone can never be slowed by a style it was never meant to
 * run heavy effects for.
 */
enum class GlassTier {
    FULL,
    LITE
}

/**
 * The low-end guard, as one pure function so the rule is unit
 * testable: live blur only on a device that is NOT flagged low-RAM
 * AND runs Android 12 (API 31, the first version with RenderEffect
 * blur) or newer. Everything else gets [GlassTier.LITE].
 */
object GlassCapability {
    fun tier(isLowRamDevice: Boolean, sdkInt: Int): GlassTier =
        if (!isLowRamDevice && sdkInt >= 31) GlassTier.FULL else GlassTier.LITE
}

/** Resolves the [GlassTier] for the device the app is running on. */
@Composable
fun rememberGlassTier(): GlassTier {
    val context = LocalContext.current
    return remember(context) {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        GlassCapability.tier(
            isLowRamDevice = activityManager?.isLowRamDevice == true,
            sdkInt = Build.VERSION.SDK_INT
        )
    }
}

/** The resolved glass settings for the current composition. */
data class GlassSettings(
    val style: AppStyle = AppStyle.VIOLET,
    val tier: GlassTier = GlassTier.LITE,
    /** The resolved dark/light of the current theme (edges differ). */
    val isDark: Boolean = false
) {
    val isGlass: Boolean get() = style == AppStyle.LIQUID_GLASS
    /** Live blur allowed (style on, device capable). */
    val liveBlur: Boolean get() = isGlass && tier == GlassTier.FULL
}

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }

/**
 * The wallpaper brush: the soft colour wash the glass floats over.
 * Light = violet-blue-green wash (round-5 mockup); dark = deep
 * violet-blue glows on true #000000 — where there is no colour, the
 * base stays pure black, so the AMOLED preference still holds.
 */
fun glassWallpaperBrush(isDark: Boolean): Brush =
    if (isDark) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF2A1650), // deep violet glow
                Color(0xFF0E2A52), // deep blue
                Color(0xFF000000)  // true black base
            ),
            radius = 1400f
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFDDD3FC), // violet wash
                Color(0xFFC9DCF7), // blue wash
                Color(0xFFCDF0DC), // green wash
                Color(0xFFE9E4FB)  // back to lavender
            )
        )
    }

/** The solid base under the wallpaper (also the status-bar tone). */
fun glassBaseColor(isDark: Boolean): Color =
    if (isDark) Color(0xFF000000) else Color(0xFFE9E4FB)

/**
 * The glass wallpaper, drawn behind the whole shell. The glow layer
 * is blurred ONCE — it is static, so even on [GlassTier.FULL] this is
 * a fixed cost at composition, not a per-frame effect. On LITE the
 * gradients are simply drawn soft (a linear/radial gradient is
 * already blurred by nature), which is the pre-blurred-static
 * fallback: same picture, no RenderEffect anywhere.
 */
@Composable
fun GlassWallpaper(isDark: Boolean, tier: GlassTier, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(glassBaseColor(isDark))) {
        val glowModifier = if (tier == GlassTier.FULL) {
            Modifier.fillMaxSize().blur(48.dp)
        } else {
            Modifier.fillMaxSize()
        }
        Box(modifier = glowModifier.background(glassWallpaperBrush(isDark)))
        // Specular sheen: a faint white wash at the very top, like
        // light catching the top edge of a glass pane.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.05f else 0.18f),
                            Color.Transparent
                        ),
                        endY = 420f
                    )
                )
        )
    }
}

/**
 * The blurred-wallpaper backdrop placed inside a FULL-tier glass
 * panel (hero, tab bar): a copy of the wallpaper, blurred, clipped by
 * the parent's shape — it reads as the panel frosting whatever is
 * behind it. Renders nothing on LITE (the translucent fill + edge
 * carries the look there) and nothing when glass is off.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    val settings = LocalGlassSettings.current
    if (!settings.liveBlur) return
    Box(
        modifier = modifier
            .fillMaxSize()
            .blur(22.dp)
            .background(glassWallpaperBrush(settings.isDark))
    )
}

/**
 * The bright hairline "specular edge" of a glass panel: lit from the
 * top-left, fading to a quiet border at the bottom — the detail that
 * makes a translucent fill read as glass instead of flat tint.
 */
fun glassEdgeBrush(isDark: Boolean): Brush =
    Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = if (isDark) 0.42f else 0.75f),
            Color.White.copy(alpha = if (isDark) 0.10f else 0.25f),
            Color.White.copy(alpha = if (isDark) 0.04f else 0.10f)
        )
    )
