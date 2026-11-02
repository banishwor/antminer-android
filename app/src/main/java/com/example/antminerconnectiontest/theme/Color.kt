package com.example.antminerconnectiontest.theme

import androidx.compose.ui.graphics.Color

// ── Violet palette (v0.2.2, mockup mode 8 "Violet") ────────────────────
// Values taken from the approved mockup page (artifact
// 'antminer-ui-mockups-3-styles', .th-violet-l / .th-violet-d CSS vars).
// One palette for both themes: light is lavender-white with a deep
// violet accent; dark is TRUE AMOLED — pure #000000 background and
// near-black #0A0A0B cards with violet-tinted borders, never grey.
// Colour carries STATUS only, never the data source: a cloud machine
// and a local machine are told apart by the LIVE / REMOTE chip, not by
// paint. Status stays instantly distinguishable in both themes:
// online = violet accent (colorScheme.primary), offline = red
// (colorScheme.error), needs-password / warning = amber
// (colorScheme.tertiary) — the red and the violet are far enough apart
// in hue and lightness to never be confused.

// --- Light theme: lavender-white ---
val LightBackground = Color(0xFFF4F1FF) // mockup --bg
val LightSurface = Color(0xFFFFFFFF) // mockup --surface
val LightSurfaceVariant = Color(0xFFEDE9FE) // mockup --chip / --selRow / --accentBg
val LightBorder = Color(0xFFDDD6FE) // mockup --border
val LightBorderSubtle = Color(0xFFEDE9FE) // mockup --chip tint

val LightTextPrimary = Color(0xFF1E1040) // mockup --text
val LightTextSecondary = Color(0xFF6B5F8A) // mockup --muted
val LightTextMuted = Color(0xFF9689B8) // derived: --muted lightened

// --- Dark theme: AMOLED black ---
val DarkBackground = Color(0xFF000000) // mockup --bg: pure black, AMOLED
val DarkSurface = Color(0xFF0A0A0B) // mockup --surface: near-black cards
val DarkSurfaceVariant = Color(0xFF171226) // mockup --chip: violet-tinted near-black
val DarkBorder = Color(0xFF251D3D) // mockup --border
val DarkBorderSubtle = Color(0xFF171226) // mockup --chip tint

val DarkTextPrimary = Color(0xFFF5F3FF) // mockup --text
val DarkTextSecondary = Color(0xFF9A8FB8) // mockup --muted
val DarkTextMuted = Color(0xFF7A6F99) // derived: --muted dimmed

// --- Violet accents (shared constants; the colour schemes carry the
// per-theme text-safe variants — see Theme.kt). ---
/**
 * The palette's defining violet (mockup light --accent #6D28D9). Kept
 * under its historic name; it is the "positive / OK" accent — chips OK,
 * accepted shares, active states — matching the mockup, where the
 * online status dot is the accent colour itself.
 */
val AntminerEmerald = Color(0xFF6D28D9)
/** Deep violet (mockup light --chipText #4C2D8A). */
val AntminerEmeraldDark = Color(0xFF4C2D8A)
/**
 * Mid violet for chart series / secondary figures. Kept under its
 * historic name; it is a violet now, not a cyan. Derived step between
 * the light accent #6D28D9 and the dark accent #A78BFA.
 */
val CyberCyan = Color(0xFF8B5CF6)
/** Pale lavender (mockup dark --accentSoft #C4B5FD) — decorative accents and chart series only. */
val ElectricBlue = Color(0xFFC4B5FD)
/** Amber for warnings / needs-password (mockup light --amber). */
val WarningAmber = Color(0xFFBE7D1C)
/** Offline / error red (mockup light --red). */
val ErrorRuby = Color(0xFFBC4B4B)

// Compatibility aliases (prefer MaterialTheme tokens in new code)
val MinerBackground = DarkBackground
val MinerSurface = DarkSurface
val MinerSurfaceVariant = DarkSurfaceVariant
val MinerBorder = DarkBorder
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val TextMuted = DarkTextMuted
