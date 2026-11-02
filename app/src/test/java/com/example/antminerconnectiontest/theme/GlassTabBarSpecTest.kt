package com.example.antminerconnectiontest.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression pins for the glass tab-bar capsule geometry (v0.3.2).
 *
 * The v0.3.0 capsule let its blurred backdrop size the bar: the
 * backdrop's `fillMaxSize()` resolved against the Scaffold bottomBar
 * slot's full-screen maxHeight and the "bar" became a screen-filling
 * pill that hid all content. The capsule's size now comes ONLY from
 * [GlassTabBarSpec]; these tests pin the values so a future edit
 * can't silently unbound the bar again — it must stay a normal-height
 * bottom bar that leaves the screen for the content.
 */
class GlassTabBarSpecTest {

    @Test
    fun capsuleHeight_isExactlyThePinnedBarHeight() {
        assertEquals(72.dp, GlassTabBarSpec.Height)
    }

    @Test
    fun capsuleHeight_staysInTheStandardNavigationBarBand() {
        assertTrue(
            "capsule must be a bar, not a panel",
            GlassTabBarSpec.Height >= 64.dp
        )
        assertTrue(
            "capsule must be a bar, not a screen",
            GlassTabBarSpec.Height <= 80.dp
        )
    }

    @Test
    fun margins_arePositiveAndSmall() {
        assertEquals(20.dp, GlassTabBarSpec.HorizontalMargin)
        assertEquals(10.dp, GlassTabBarSpec.VerticalMargin)
        assertTrue(GlassTabBarSpec.HorizontalMargin > 0.dp)
        assertTrue(GlassTabBarSpec.VerticalMargin >= 0.dp)
        assertTrue(GlassTabBarSpec.InnerHorizontalPadding >= 0.dp)
    }

    @Test
    fun capsuleEnds_areFullyRounded() {
        assertEquals(50, GlassTabBarSpec.CornerRadiusPercent)
    }

    @Test
    fun reservedHeight_isHeightPlusVerticalMargins_andStaysSmall() {
        assertEquals(
            GlassTabBarSpec.Height + GlassTabBarSpec.VerticalMargin * 2,
            GlassTabBarSpec.ReservedHeight
        )
        // The Scaffold takes this much from the content — it must
        // remain a small strip at the bottom, never a screenful.
        assertTrue(GlassTabBarSpec.ReservedHeight <= 100.dp)
    }
}
