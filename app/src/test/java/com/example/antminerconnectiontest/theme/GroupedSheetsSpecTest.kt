package com.example.antminerconnectiontest.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins for the V3 "Grouped Sheets" tokens (v0.4.0) — the approved
 * whole-app mockup's palette and geometry, as constants in
 * [GroupedSheetsSpec]. The design's discipline IS the values: flat
 * grouped bases, one Apple-blue accent, monochrome status slots. A
 * future edit that drifts a value should fail here first and have to
 * argue with the mockup, not slip through.
 */
class GroupedSheetsSpecTest {

    @Test
    fun bases_areTheMockupsGroupedBases() {
        assertEquals(Color(0xFFF2F2F7), GroupedSheetsSpec.LightBackground)
        // Dark base is TRUE black — the user's AMOLED rule.
        assertEquals(Color(0xFF000000), GroupedSheetsSpec.DarkBackground)
    }

    @Test
    fun panels_areWhiteAndNearBlack() {
        assertEquals(Color(0xFFFFFFFF), GroupedSheetsSpec.LightPanel)
        assertEquals(Color(0xFF1C1C1E), GroupedSheetsSpec.DarkPanel)
        assertEquals(Color(0xFF2C2C2E), GroupedSheetsSpec.DarkPanelElevated)
        assertEquals(Color(0xFFF6F6F8), GroupedSheetsSpec.LightPanelElevated)
    }

    @Test
    fun accent_isAppleBlue_andTheOnlyAccent() {
        assertEquals(Color(0xFF007AFF), GroupedSheetsSpec.LightAccent)
        assertEquals(Color(0xFF0A84FF), GroupedSheetsSpec.DarkAccent)
    }

    @Test
    fun textAndSecondary_areTheIosGreys() {
        assertEquals(Color(0xFF000000), GroupedSheetsSpec.LightText)
        assertEquals(Color(0xFFFFFFFF), GroupedSheetsSpec.DarkText)
        assertEquals(Color(0xFF6E6E73), GroupedSheetsSpec.LightSecondary)
        assertEquals(Color(0xFF98989F), GroupedSheetsSpec.DarkSecondary)
    }

    @Test
    fun geometry_matchesTheMockup() {
        assertEquals(12.dp, GroupedSheetsSpec.PanelRadius)
        assertEquals(44.dp, GroupedSheetsSpec.RowMinHeight)
        assertEquals(51.dp, GroupedSheetsSpec.SwitchTrackWidth)
        assertEquals(31.dp, GroupedSheetsSpec.SwitchTrackHeight)
        assertEquals(27.dp, GroupedSheetsSpec.SwitchKnobSize)
        assertEquals(9.dp, GroupedSheetsSpec.SegmentedTrackRadius)
        assertEquals(7.dp, GroupedSheetsSpec.SegmentedPillRadius)
    }

    @Test
    fun console_isAlwaysTheDarkTerminal() {
        assertEquals(Color(0xFF0B0B0D), GroupedSheetsSpec.ConsoleBackground)
        assertEquals(Color(0xFFE8E8EA), GroupedSheetsSpec.ConsoleText)
    }

    @Test
    fun resolvers_followDarkness() {
        assertEquals(GroupedSheetsSpec.LightAccent, GroupedSheetsSpec.accent(isDark = false))
        assertEquals(GroupedSheetsSpec.DarkAccent, GroupedSheetsSpec.accent(isDark = true))
        assertEquals(GroupedSheetsSpec.DarkPanel, GroupedSheetsSpec.panel(isDark = true))
        assertEquals(GroupedSheetsSpec.LightBackground, GroupedSheetsSpec.background(isDark = false))
    }

    @Test
    fun groupedSchemes_carryTheSpecTokens() {
        val light = groupedSheetsColorScheme(isDark = false)
        assertEquals(GroupedSheetsSpec.LightAccent, light.primary)
        assertEquals(GroupedSheetsSpec.LightBackground, light.background)
        assertEquals(GroupedSheetsSpec.LightPanel, light.surface)
        assertEquals(GroupedSheetsSpec.LightSecondary, light.onSurfaceVariant)
        // Status slots are monochrome in this style: error and
        // tertiary resolve to the text colour, never a hue.
        assertEquals(GroupedSheetsSpec.LightText, light.error)
        assertEquals(GroupedSheetsSpec.LightText, light.tertiary)

        val dark = groupedSheetsColorScheme(isDark = true)
        assertEquals(GroupedSheetsSpec.DarkAccent, dark.primary)
        assertEquals(GroupedSheetsSpec.DarkBackground, dark.background)
        assertEquals(GroupedSheetsSpec.DarkPanel, dark.surface)
        assertEquals(GroupedSheetsSpec.DarkText, dark.error)
        assertEquals(GroupedSheetsSpec.DarkText, dark.tertiary)
    }

    @Test
    fun dockTokens_areTranslucentGlass() {
        // The dock is the ONLY translucent surface in the design.
        assertTrue(GroupedSheetsSpec.LightDockBackground.alpha < 1f)
        assertTrue(GroupedSheetsSpec.DarkDockBackground.alpha < 1f)
        // Panels, by contrast, are fully opaque.
        assertEquals(1f, GroupedSheetsSpec.LightPanel.alpha)
        assertEquals(1f, GroupedSheetsSpec.DarkPanel.alpha)
    }
}
