package com.storyforge.player

/** Keeps wind as a directional modifier; it must not enable precipitation by itself. */
internal fun shouldRenderRain(effects: Set<String>): Boolean =
    "rain" in effects || "heavy_rain" in effects
