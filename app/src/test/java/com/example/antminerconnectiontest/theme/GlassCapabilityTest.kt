package com.example.antminerconnectiontest.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Liquid Glass low-end guard (v0.3.0): live blur is allowed ONLY
 * on a device that is not flagged low-RAM AND runs Android 12+
 * (API 31, the first RenderEffect blur). Everything else gets
 * glass-lite — the same translucency and edges with no live blur —
 * so a cheap relay phone is never slowed by the style.
 */
class GlassCapabilityTest {

    @Test
    fun lowRamDevice_getsLite_evenOnNewAndroid() {
        assertEquals(GlassTier.LITE, GlassCapability.tier(isLowRamDevice = true, sdkInt = 35))
    }

    @Test
    fun preAndroid12_getsLite_evenWhenNotLowRam() {
        assertEquals(GlassTier.LITE, GlassCapability.tier(isLowRamDevice = false, sdkInt = 30))
        assertEquals(GlassTier.LITE, GlassCapability.tier(isLowRamDevice = false, sdkInt = 26))
    }

    @Test
    fun capableDevice_getsFull() {
        assertEquals(GlassTier.FULL, GlassCapability.tier(isLowRamDevice = false, sdkInt = 31))
        assertEquals(GlassTier.FULL, GlassCapability.tier(isLowRamDevice = false, sdkInt = 36))
    }

    @Test
    fun lowRamAndOldAndroid_getsLite() {
        assertEquals(GlassTier.LITE, GlassCapability.tier(isLowRamDevice = true, sdkInt = 28))
    }
}
