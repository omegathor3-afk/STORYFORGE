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
            splashCount = 16,
            driftDurationMillis = 5200,
            opacityScale = 1.4f,
            lengthScale = 1.28f
        )
    } else {
        RainEffectProfile(
            distantDrops = 56,
            midDrops = 30,
            nearDrops = 12,
            splashCount = 8,
            driftDurationMillis = 7600,
            opacityScale = 1.15f,
            lengthScale = 1.12f
        )
    }
}
