package com.storyforge.player

/** Keeps wind as a directional modifier; it must not enable precipitation by itself. */
internal fun shouldRenderRain(effects: Set<String>): Boolean =
    "rain" in effects || "heavy_rain" in effects

internal data class RainEffectProfile(
    val distantDrops: Int,
    val midDrops: Int,
    val nearDrops: Int,
    val splashCount: Int,
    val driftDurationMillis: Int,
    val opacityScale: Float,
    val lengthScale: Float
)

/** Profiles keep normal rain readable and heavy rain denser without changing unrelated layers. */
internal fun rainEffectProfile(effects: Set<String>): RainEffectProfile? {
    if (!shouldRenderRain(effects)) return null
    return if ("heavy_rain" in effects) {
        RainEffectProfile(
            distantDrops = 88,
            midDrops = 50,
            nearDrops = 24,
            splashCount = 20,
            driftDurationMillis = 5200,
            opacityScale = 1.55f,
            lengthScale = 1.34f
        )
    } else {
        RainEffectProfile(
            distantDrops = 56,
            midDrops = 30,
            nearDrops = 12,
            splashCount = 12,
            driftDurationMillis = 7600,
            opacityScale = 1.28f,
            lengthScale = 1.2f
        )
    }
}

/** Stable per-particle variation avoids a perfectly uniform sheet of falling lines. */
internal fun rainParticleSpeedMultiplier(index: Int, plane: Int): Float {
    val safeIndex = index.coerceAtLeast(0).toLong()
    val safePlane = plane.coerceAtLeast(0).toLong()
    val bucket = ((safeIndex * 7L + safePlane * 11L) % 13L).toInt()
    return 0.82f + bucket / 12f * 0.34f
}

/** A deterministic phase offset gives nearby drops different starting positions. */
internal fun rainParticlePhaseOffset(index: Int, plane: Int): Float {
    val safeIndex = index.coerceAtLeast(0).toLong()
    val safePlane = plane.coerceAtLeast(0).toLong()
    val bucket = ((safeIndex * 37L + safePlane * 19L) % 97L).toInt()
    return bucket / 97f
}

/** Short impact envelope: each splash appears, rises and fades instead of remaining as a fixed arc. */
internal fun rainSplashEnvelope(phase: Float): Float {
    if (!phase.isFinite()) return 0f
    val normalized = ((phase % 1f) + 1f) % 1f
    return if (normalized < 0.34f) {
        (1f - normalized / 0.34f).coerceIn(0f, 1f)
    } else {
        0f
    }
}
