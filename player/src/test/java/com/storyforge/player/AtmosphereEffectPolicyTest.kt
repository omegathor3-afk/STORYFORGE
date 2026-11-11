package com.storyforge.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtmosphereEffectPolicyTest {
    @Test
    fun windAloneDoesNotEnableRain() {
        assertFalse(shouldRenderRain(setOf("wind")))
    }

    @Test
    fun rainAndHeavyRainEnablePrecipitation() {
        assertTrue(shouldRenderRain(setOf("rain")))
        assertTrue(shouldRenderRain(setOf("heavy_rain")))
        assertTrue(shouldRenderRain(setOf("wind", "rain")))
    }

    @Test
    fun unrelatedAtmosphereEffectsDoNotEnableRain() {
        assertFalse(shouldRenderRain(setOf("fog", "dead_leaves", "embers")))
    }
}
