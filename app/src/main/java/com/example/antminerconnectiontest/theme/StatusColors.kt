package com.example.antminerconnectiontest.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Status/accent colours (v0.4.0; single-style since v0.5.0).
 *
 * The Grouped Sheets design has exactly ONE accent — Apple blue —
 * and carries status by label + monochrome treatment instead, per the
 * approved V3 mockup: "ok" collapses to the blue accent, warnings and
 * errors collapse to the text colour (solid black / white), info and
 * cloud accents collapse to blue. Components read these helpers
 * instead of raw constants so no stray hue can leak into the design.
 */

/** Positive / primary accent: check marks, accepted shares, active states. */
@Composable
fun accentOk(): Color = MaterialTheme.colorScheme.primary

/** Warning accent: slow fans, stale data, needs-password. Monochrome in V3. */
@Composable
fun accentWarn(): Color = MaterialTheme.colorScheme.onSurface

/** Error accent: failures, hot temperatures. Monochrome in V3 (labels carry it). */
@Composable
fun accentError(): Color = MaterialTheme.colorScheme.error

/** Info accent. Blue in V3. */
@Composable
fun accentInfo(): Color = MaterialTheme.colorScheme.primary

/** Cloud / remote accent (the switcher's cloud rows, pool worker marks). Blue in V3. */
@Composable
fun accentCloud(): Color = MaterialTheme.colorScheme.primary

/** Secondary data accent (diff figures, secondary series). Neutral grey in V3. */
@Composable
fun accentData(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Hashrate-chart series colours: the TOTAL line is drawn separately
 * with [accentOk]; these are the per-chain colours — the mockup's
 * greys under the blue total.
 */
@Composable
fun chainSeriesColors(): List<Color> = GroupedSheetsSpec.ChartChainGreys
