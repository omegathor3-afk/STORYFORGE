package com.storyforge.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereEffectPolicyTest {
    @Test
    fun windAloneDoesNotEnableRain() {
        assertFalse(shouldRenderRain(setOf("wind")))
        assertNull(rainEffectProfile(setOf("wind")))
    }

    @Test
    fun rainAndHeavyRainEnablePrecipitation() {
        assertTrue(shouldRenderRain(setOf("rain")))
        assertTrue(shouldRenderRain(setOf("heavy_rain")))
        assertTrue(shouldRenderRain(setOf("wind", "rain")))
        assertTrue(rainEffectProfile(setOf("rain")) != null)
        assertTrue(rainEffectProfile(setOf("heavy_rain")) != null)
    }

    @Test
    fun unrelatedAtmosphereEffectsDoNotEnableRain() {
        assertFalse(shouldRenderRain(setOf("fog", "dead_leaves", "embers")))
        assertNull(rainEffectProfile(setOf("fog", "dead_leaves", "embers")))
    }

    @Test
    fun heavyRainIsDenserBrighterAndFasterThanNormalRain() {
        val normal = requireNotNull(rainEffectProfile(setOf("rain")))
        val heavy = requireNotNull(rainEffectProfile(setOf("heavy_rain")))
        assertTrue(heavy.distantDrops > normal.distantDrops)
        assertTrue(heavy.midDrops > normal.midDrops)
        assertTrue(heavy.nearDrops > normal.nearDrops)
        assertTrue(heavy.splashCount > normal.splashCount)
        assertTrue(heavy.opacityScale > normal.opacityScale)
        assertTrue(heavy.lengthScale > normal.lengthScale)
        assertTrue(heavy.driftDurationMillis < normal.driftDurationMillis)
    }

    @Test
    fun profileUsesBoundedPositiveAnimationAndParticleValues() {
        listOf(rainEffectProfile(setOf("rain")), rainEffectProfile(setOf("heavy_rain"))).forEach { profile ->
            assertTrue(requireNotNull(profile).distantDrops > 0)
            assertTrue(requireNotNull(profile).midDrops > 0)
            assertTrue(requireNotNull(profile).nearDrops > 0)
            assertTrue(requireNotNull(profile).splashCount > 0)
            assertTrue(requireNotNull(profile).driftDurationMillis in 4000..10000)
            assertTrue(requireNotNull(profile).opacityScale in 1f..2f)
            assertTrue(requireNotNull(profile).lengthScale in 1f..2f)
        }
    }
}
