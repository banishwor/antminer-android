package com.example.antminerconnectiontest.theme

import androidx.compose.ui.graphics.Color

// ── Nord palette (v0.2.0, "Style A — Nord Clean Light") ─────────────────
// One palette for both themes: light uses the bright Nord snow-storm
// surfaces, dark uses the polar-night surfaces, and every layout and
// component is identical across the two — only the surface/text tones
// swap. Colour carries STATUS only (green = online, red = offline,
// amber = needs-password / warning), never the data source: a cloud
// machine and a local machine are the same machine in two states, told
// apart by the LIVE / REMOTE chip, not by paint.

// --- Light theme: snow storm ---
val LightBackground = Color(0xFFECEFF4) // nord6
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE5E9F0) // nord5
val LightBorder = Color(0xFFD8DEE9) // nord4
val LightBorderSubtle = Color(0xFFE5E9F0) // nord5

val LightTextPrimary = Color(0xFF2E3440) // nord0
val LightTextSecondary = Color(0xFF4C566A) // nord3
val LightTextMuted = Color(0xFF616E88) // brightened nord3

// --- Dark theme: polar night ---
val DarkBackground = Color(0xFF2E3440) // nord0
val DarkSurface = Color(0xFF3B4252) // nord1
val DarkSurfaceVariant = Color(0xFF434C5E) // nord2
val DarkBorder = Color(0xFF4C566A) // nord3
val DarkBorderSubtle = Color(0xFF434C5E) // nord2

val DarkTextPrimary = Color(0xFFECEFF4) // nord6
val DarkTextSecondary = Color(0xFFD8DEE9) // nord4
val DarkTextMuted = Color(0xFF8F9BB3)

// --- Nord accents (shared hues; the colour schemes deepen/lighten per
// theme where a token is used as text — see Theme.kt). ---
/** Nord green (nord14 #A3BE8C) deepened so it stays legible as text/icons on light surfaces. */
val AntminerEmerald = Color(0xFF7A9A5B)
val AntminerEmeraldDark = Color(0xFF4A6338)
/** Nord blue (nord10). Kept under its historic name; it is a blue now, not a cyan. */
val CyberCyan = Color(0xFF5E81AC)
/** Nord frost (nord8) — decorative accents and chart series only. */
val ElectricBlue = Color(0xFF88C0D0)
/** Amber for warnings / needs-password (a #B7791F-style deepened nord13). */
val WarningAmber = Color(0xFFB7791F)
/** Nord red (nord11). */
val ErrorRuby = Color(0xFFBF616A)

// Compatibility aliases (prefer MaterialTheme tokens in new code)
val MinerBackground = DarkBackground
val MinerSurface = DarkSurface
val MinerSurfaceVariant = DarkSurfaceVariant
val MinerBorder = DarkBorder
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val TextMuted = DarkTextMuted
