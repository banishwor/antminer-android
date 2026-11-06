package com.example.antminerconnectiontest.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
 * The app's ONE appearance (v0.5.0): the V3 "Grouped Sheets" design
 * (see [GroupedSheetsSpec]) — flat grouped panels, Apple-blue accent,
 * and real glass only in the floating dock. The Violet style that
 * shipped alongside it through v0.4.0 was removed in v0.5.0 at the
 * user's direction; there is no style choice anymore, only the
 * Light / Dark / System theme mode.
 */

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
    val tier: GlassTier = GlassTier.LITE,
    /** The resolved dark/light of the current theme (edges differ). */
    val isDark: Boolean = false
) {
    /** Live blur allowed (device capable). */
    val liveBlur: Boolean get() = tier == GlassTier.FULL
}

val LocalGlassSettings = staticCompositionLocalOf { GlassSettings() }

/**
 * The backdrop brush behind glass surfaces. Since v0.4.0 (V3 Grouped
 * Sheets) there is NO wallpaper: the design's base is the flat
 * grouped colour, so this brush is a flat fill of that base. It is
 * kept as a brush because [GlassBackdrop] still samples it inside
 * panels on FULL-tier devices — blurring a flat colour is invisible,
 * which is exactly what the V3 design wants behind its opaque panels.
 */
fun glassWallpaperBrush(isDark: Boolean): Brush =
    Brush.linearGradient(
        colors = listOf(
            GroupedSheetsSpec.background(isDark),
            GroupedSheetsSpec.background(isDark)
        )
    )

/** The solid base under the shell (also the status-bar tone). */
fun glassBaseColor(isDark: Boolean): Color =
    GroupedSheetsSpec.background(isDark)

/**
 * The shell backdrop for the Glass style: since v0.4.0 simply the
 * flat grouped base colour — the V3 design has no wallpaper and no
 * gradients behind content. Kept as a composable so the shell's
 * layering (base first, then the transparent Scaffold) is unchanged.
 */
@Composable
fun GlassWallpaper(isDark: Boolean, tier: GlassTier, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(glassBaseColor(isDark)))
}

/**
 * The blurred-wallpaper backdrop placed inside a FULL-tier glass
 * panel (hero, tab bar): a copy of the wallpaper, blurred, clipped by
 * the parent's shape — it reads as the panel frosting whatever is
 * behind it. Renders nothing on LITE (the translucent fill + edge
 * carries the look there).
 *
 * SIZING INVARIANT (v0.3.2 bugfix): a backdrop must NEVER size its
 * parent. This composable is a [BoxScope] member and sizes itself
 * with [Modifier.matchParentSize] — it takes exactly the size the
 * parent [Box] gets from its OTHER children and does not participate
 * in the parent's measurement at all. The v0.3.0 version used
 * `Modifier.fillMaxSize()` here instead: inside a wrap-content parent
 * that resolves against the incoming constraints, and Scaffold hands
 * its bottomBar slot a maxHeight of the full screen — so the tab-bar
 * capsule inflated to a screen-filling pill and squeezed the content
 * to nothing. Never give this layer a size-demanding modifier again.
 */
@Composable
fun BoxScope.GlassBackdrop() {
    val settings = LocalGlassSettings.current
    if (!settings.liveBlur) return
    Box(
        modifier = Modifier
            .matchParentSize()
            .blur(22.dp)
            .background(glassWallpaperBrush(settings.isDark))
    )
}

/**
 * The floating glass tab-bar capsule's geometry (v0.3.2): pinned as
 * named constants so the bar's size can never again be decided by an
 * unbounded child or a loose constraint. The capsule is a normal
 * bottom bar — [Height] sits in the standard navigation-bar band,
 * the width is the screen minus [HorizontalMargin] on each side, and
 * the total footprint the Scaffold reserves (height + vertical
 * margins) stays small enough that content always keeps the screen.
 * Unit tests pin these values.
 */
object GlassTabBarSpec {
    /** Capsule height — the V3 mockup's 65 dp dock. */
    val Height = 65.dp
    /** Space between the capsule and each screen edge (mockup: 14). */
    val HorizontalMargin = 14.dp
    /** Space above and below the capsule inside the bottomBar slot (mockup bottom: 13). */
    val VerticalMargin = 13.dp
    /** Fully rounded ends — a pill at bar height, whatever the width. */
    const val CornerRadiusPercent = 50
    /** Breathing room between the capsule edge and the first/last tab (mockup: 5). */
    val InnerHorizontalPadding = 5.dp
    /** Total height the Scaffold takes from the content for the bar. */
    val ReservedHeight = Height + VerticalMargin * 2
}

/**
 * The hairline edge of a grouped panel (v0.4.0): a near-flat border
 * in the mockup's hairline tone, fractionally stronger at the top —
 * panels in the V3 design are defined by this hairline, not by a
 * specular glass edge.
 */
fun glassEdgeBrush(isDark: Boolean): Brush =
    Brush.linearGradient(
        colors = listOf(
            GroupedSheetsSpec.hairline(isDark).copy(alpha = 1f),
            GroupedSheetsSpec.hairline(isDark).copy(alpha = 0.75f)
        )
    )
