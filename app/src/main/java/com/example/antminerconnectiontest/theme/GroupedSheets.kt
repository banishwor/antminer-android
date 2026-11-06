package com.example.antminerconnectiontest.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The V3 "Grouped Sheets" design tokens (v0.4.0) — the app's ONE
 * visual design since v0.5.0, from the user-approved whole-app mockup
 * (~/workspace/your_files/antminer-app-v3-mockup.html, itself the
 * Round-12 "V3 Grouped Sheets" final).
 *
 * The discipline is the design:
 *  - a flat grouped base (#F2F2F7 light / true #000000 dark) with
 *    opaque white / #1C1C1E panels, hairline borders and hairline row
 *    separators — no wallpaper, no gradients behind content;
 *  - text is black / white, secondary text is the iOS greys;
 *  - Apple blue (#007AFF light / #0A84FF dark) is the ONLY accent —
 *    links, badges that mean "remote/selected", switch ON, segmented
 *    selection, the dock's current item;
 *  - status meaning is carried by TEXT LABELS (Online / Offline /
 *    Check miner / needs password / UPLOADING …) plus monochrome
 *    treatment (solid or hollow dots, solid or outline pills) — never
 *    by hue alone, and never by violet/amber/red in this style;
 *  - real glass (translucency + blur) exists ONLY in the floating
 *    capsule dock, and only where [GlassCapability] allows it.
 *
 * Values are pinned here as named constants (the [GlassTabBarSpec]
 * precedent) and unit-tested, so a future edit cannot silently drift
 * the palette or the geometry.
 */
object GroupedSheetsSpec {

    // ── Palette — light ─────────────────────────────────────────────
    val LightBackground = Color(0xFFF2F2F7) // grouped base
    val LightPanel = Color(0xFFFFFFFF)
    val LightPanelElevated = Color(0xFFF6F6F8) // alert bars / dialog tone
    val LightText = Color(0xFF000000)
    val LightSecondary = Color(0xFF6E6E73)
    val LightTertiary = Color(0xFFAEAEB2)
    val LightAccent = Color(0xFF007AFF) // Apple blue
    val LightHairline = Color(0xFFD9D9DE) // rgba(60,60,67,.13) composited
    val LightSeparator = Color(0xFFEBEBEC) // rgba(60,60,67,.10) composited
    val LightChipBackground = Color(0xFF3C3C43).copy(alpha = 0.08f)
    val LightSegmentedTrack = Color(0xFFE9E9EB)
    val LightSegmentedSelected = Color(0xFFFFFFFF)
    val LightSwitchOff = Color(0xFFE9E9EA)
    val LightDockBackground = Color.White.copy(alpha = 0.68f)
    val LightDockBorder = Color.White.copy(alpha = 0.65f)

    // ── Palette — dark ──────────────────────────────────────────────
    val DarkBackground = Color(0xFF000000) // true black, AMOLED
    val DarkPanel = Color(0xFF1C1C1E)
    val DarkPanelElevated = Color(0xFF2C2C2E)
    val DarkText = Color(0xFFFFFFFF)
    val DarkSecondary = Color(0xFF98989F)
    val DarkTertiary = Color(0xFF5A5A5E)
    val DarkAccent = Color(0xFF0A84FF) // Apple blue, dark
    val DarkHairline = Color(0xFF3A3A3C) // rgba(255,255,255,.14) composited
    val DarkSeparator = Color(0xFF333335) // rgba(255,255,255,.10) composited
    val DarkChipBackground = Color.White.copy(alpha = 0.10f)
    val DarkSegmentedTrack = Color(0xFF2C2C2E)
    val DarkSegmentedSelected = Color(0xFF636366)
    val DarkSwitchOff = Color(0xFF39393D)
    val DarkDockBackground = Color(0xFF1E1E20).copy(alpha = 0.62f)
    val DarkDockBorder = Color.White.copy(alpha = 0.16f)

    // ── Shared surfaces ─────────────────────────────────────────────
    /** The log console is a dark terminal in BOTH themes (mockup). */
    val ConsoleBackground = Color(0xFF0B0B0D)
    val ConsoleText = Color(0xFFE8E8EA)
    /** ASIC chip-matrix cells (mockup .chipcell). */
    val LightChipCell = Color(0xFFD8D8DE)
    val DarkChipCell = Color(0xFF48484A)
    /** Chart chain series under the total (mockup chart greys). */
    val ChartChainGreys = listOf(
        Color(0xFFC7C7CC),
        Color(0xFFAEAEB2),
        Color(0xFF8E8E93)
    )
    /** Share-donut non-accepted arcs (mockup donut greys). */
    val DonutRejected = Color(0xFF55555C)
    val DonutStale = Color(0xFFD8D8DE)

    // ── Geometry ────────────────────────────────────────────────────
    /** Panel corner radius — every grouped panel in the app. */
    val PanelRadius = 12.dp
    /** Minimum tappable row height inside a panel (mockup .row). */
    val RowMinHeight = 44.dp
    /** Group label above a panel (mockup .grouplabel). */
    val GroupLabelTextSize = 12.5.sp
    val GroupLabelLetterSpacing = 0.5.sp

    // iOS switch (mockup .switch): 51×31 track, 27 knob, 2 inset.
    val SwitchTrackWidth = 51.dp
    val SwitchTrackHeight = 31.dp
    val SwitchKnobSize = 27.dp
    val SwitchKnobInset = 2.dp

    // iOS segmented control (mockup .seg): grey track, raised pill.
    val SegmentedTrackRadius = 9.dp
    val SegmentedPillRadius = 7.dp
    val SegmentedTrackPadding = 2.dp

    // ── Dark/light resolvers ────────────────────────────────────────
    fun background(isDark: Boolean) = if (isDark) DarkBackground else LightBackground
    fun panel(isDark: Boolean) = if (isDark) DarkPanel else LightPanel
    fun panelElevated(isDark: Boolean) = if (isDark) DarkPanelElevated else LightPanelElevated
    fun accent(isDark: Boolean) = if (isDark) DarkAccent else LightAccent
    fun secondary(isDark: Boolean) = if (isDark) DarkSecondary else LightSecondary
    fun hairline(isDark: Boolean) = if (isDark) DarkHairline else LightHairline
    fun separator(isDark: Boolean) = if (isDark) DarkSeparator else LightSeparator
    fun chipBackground(isDark: Boolean) = if (isDark) DarkChipBackground else LightChipBackground
    fun segmentedTrack(isDark: Boolean) = if (isDark) DarkSegmentedTrack else LightSegmentedTrack
    fun segmentedSelected(isDark: Boolean) = if (isDark) DarkSegmentedSelected else LightSegmentedSelected
    fun switchOff(isDark: Boolean) = if (isDark) DarkSwitchOff else LightSwitchOff
    fun dockBackground(isDark: Boolean) = if (isDark) DarkDockBackground else LightDockBackground
    fun dockBorder(isDark: Boolean) = if (isDark) DarkDockBorder else LightDockBorder
    fun chipCell(isDark: Boolean) = if (isDark) DarkChipCell else LightChipCell
}

/**
 * The corner shape of a content panel: [GroupedSheetsSpec.PanelRadius]
 * everywhere.
 */
@Composable
fun panelShape(): Shape = RoundedCornerShape(GroupedSheetsSpec.PanelRadius)

/**
 * The hairline border of a grouped panel (V3 mockup: panels are
 * defined by a 1 dp hairline in the outline tone).
 */
@Composable
fun panelBorder(): BorderStroke? =
    BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
