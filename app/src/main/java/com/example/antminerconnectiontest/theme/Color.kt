package com.example.antminerconnectiontest.theme

import androidx.compose.ui.graphics.Color

// --- Dark Mode: "Control Room" ---
// Near-black, green-tinted surfaces; emerald is the working accent and
// amber is reserved for warnings / offline emphasis. Dense, technical.
val DarkBackground = Color(0xFF070B08)
val DarkSurface = Color(0xFF0C120E)
val DarkSurfaceVariant = Color(0xFF141C16)
val DarkBorder = Color(0xFF243529)
val DarkBorderSubtle = Color(0xFF1B2920)

val AntminerEmerald = Color(0xFF00E676)
val AntminerEmeraldDark = Color(0xFF0D5C35)
val CyberCyan = Color(0xFF00E5FF)
val ElectricBlue = Color(0xFF2979FF)
val WarningAmber = Color(0xFFFFB300)
val ErrorRuby = Color(0xFFFF5252)

val DarkTextPrimary = Color(0xFFF0F6FC)
val DarkTextSecondary = Color(0xFF8B949E)
val DarkTextMuted = Color(0xFF5A6472)

// --- Light Mode: "Clean Light" ---
// White / soft-grey surfaces with a single green accent; airy and calm.
val LightBackground = Color(0xFFF6F7F6)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEDF1ED)
val LightBorder = Color(0xFFDEE5DE)
val LightBorderSubtle = Color(0xFFCBD5CB)

val LightEmerald = Color(0xFF16A34A)
val LightEmeraldDark = Color(0xFF14532D)
val LightCyan = Color(0xFF0284C7)
val LightBlue = Color(0xFF2563EB)
val LightWarning = Color(0xFFD97706)
val LightError = Color(0xFFDC2626)

val LightTextPrimary = Color(0xFF14231A)
val LightTextSecondary = Color(0xFF4B5A50)
val LightTextMuted = Color(0xFF8A978D)

// Compatibility aliases (dark values; prefer MaterialTheme tokens in new code)
val MinerBackground = DarkBackground
val MinerSurface = DarkSurface
val MinerSurfaceVariant = DarkSurfaceVariant
val MinerBorder = DarkBorder
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val TextMuted = DarkTextMuted
