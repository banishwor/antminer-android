package com.example.antminerconnectiontest.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Status/accent colours, resolved per appearance style (v0.4.0).
 *
 * The Violet style keeps its historic constant palette (violet =
 * positive, amber = warning, ruby = error, pale violet = info). The
 * Grouped Sheets (Glass) style has exactly ONE accent — Apple blue —
 * and carries status by label + monochrome treatment instead, per the
 * approved V3 mockup: "ok" collapses to the blue accent, warnings and
 * errors collapse to the text colour (solid black / white), info and
 * cloud accents collapse to blue. Components read these helpers
 * instead of the raw constants so the same layout renders correctly
 * in both styles and no violet can leak into the V3 style.
 */

/** Positive / primary accent: check marks, accepted shares, active states. */
@Composable
fun accentOk(): Color =
    if (LocalGlassSettings.current.isGlass) MaterialTheme.colorScheme.primary else AntminerEmerald

/** Warning accent: slow fans, stale data, needs-password. Monochrome in V3. */
@Composable
fun accentWarn(): Color =
    if (LocalGlassSettings.current.isGlass) MaterialTheme.colorScheme.onSurface else WarningAmber

/** Error accent: failures, hot temperatures. Monochrome in V3 (labels carry it). */
@Composable
fun accentError(): Color =
    if (LocalGlassSettings.current.isGlass) MaterialTheme.colorScheme.error else ErrorRuby

/** Info accent. Blue in V3. */
@Composable
fun accentInfo(): Color =
    if (LocalGlassSettings.current.isGlass) MaterialTheme.colorScheme.primary else ElectricBlue

/** Cloud / remote accent (the switcher's cloud rows, pool worker marks). Blue in V3. */
@Composable
fun accentCloud(): Color =
    if (LocalGlassSettings.current.isGlass) MaterialTheme.colorScheme.primary else CyberCyan

/** Secondary data accent (diff figures, secondary series). Neutral grey in V3. */
@Composable
fun accentData(): Color =
    if (LocalGlassSettings.current.isGlass) MaterialTheme.colorScheme.onSurfaceVariant else CyberCyan

/**
 * Hashrate-chart series colours: the TOTAL line is drawn separately
 * with [accentOk]; these are the per-chain colours. Violet keeps its
 * violet-family series; V3 uses the mockup's greys under the blue
 * total.
 */
@Composable
fun chainSeriesColors(): List<Color> =
    if (LocalGlassSettings.current.isGlass) {
        GroupedSheetsSpec.ChartChainGreys
    } else {
        listOf(AntminerEmerald, CyberCyan, ElectricBlue, WarningAmber)
    }
