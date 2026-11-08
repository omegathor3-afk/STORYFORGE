package com.storyforge.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Atmospheric layers are deliberately restrained: soft, irregular forms and slow
 * motion let the source illustration remain the star instead of looking like particles
 * pasted over it.
 */
@Composable
internal fun AtmosphereOverlay(effects: List<String>, theme: String, intensity: Float = 0.72f, modifier: Modifier = Modifier) {
    val motion = rememberInfiniteTransition(label = "organic-atmosphere")
    val drift by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    val breath by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse), label = "breath")
    val flash by motion.animateFloat(0f, 1f, infiniteRepeatable(tween(9700, easing = LinearEasing), RepeatMode.Restart), label = "lightning")
    val active = effects.toSet() + when (theme) {
        "ashen" -> setOf("ash", "fog")
        "haunted" -> setOf("fog", "black_shadows", "dead_leaves")
        "fungal" -> setOf("spores", "dense_fog")
        "bloodmoon" -> setOf("blood_mist", "black_shadows")
        "frozen" -> setOf("snow", "fog", "wind")
        else -> setOf("fog")
    }
    val strength = intensity.coerceIn(0f, 1f)
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // A quiet lens falloff ties every layer into the original artwork.
        drawRect(Brush.verticalGradient(listOf(Color(0x16020708), Color.Transparent, Color(0x38030708))))
        val tint = when {
            "blood_mist" in active -> Color(0xFF74464A)
            "snow" in active -> Color(0xFF8D9EAA)
            "spores" in active -> Color(0xFF788A67)
            else -> Color(0xFF929B8F)
        }
        if ("fog" in active || "dense_fog" in active || "blood_mist" in active || "ground_fog" in active) {
            // Broad overlapping gradients create soft, uneven banks rather than hard circles.
            for (i in 0..4) {
                val direction = if (i % 2 == 0) 1f else -0.72f
                val x = ((drift * direction + i * 0.29f + 0.12f) % 1.55f - 0.25f) * w
                val y = h * (0.48f + (i % 3) * 0.105f) + sin((drift * 2f * PI + i).toFloat()) * h * 0.018f
                val radius = w * (if ("dense_fog" in active) 0.39f else 0.31f)
                val alpha = (if ("dense_fog" in active) 0.105f else 0.065f) * strength
                drawCircle(Brush.radialGradient(
                    listOf(tint.copy(alpha = alpha), tint.copy(alpha = alpha * 0.48f), Color.Transparent),
                    center = Offset(x, y), radius = radius
                ), radius = radius, center = Offset(x, y))
            }
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.58f to Color.Transparent, 1f to tint.copy(alpha = 0.035f * strength)))
        }
        if ("ground_fog" in active) {
            // Low, stretched mist banks crawl along the floor instead of filling the whole frame.
            for (i in 0..5) {
                val direction = if (i % 2 == 0) 1f else -0.58f
                val x = ((drift * direction + i * 0.31f) % 1.55f - 0.25f) * w
                val y = h * (0.76f + (i % 3) * 0.055f)
                val bankWidth = w * (0.22f + (i % 2) * 0.07f)
                val bankHeight = h * 0.075f
                val alpha = (0.075f + (i % 3) * 0.012f) * strength
                drawOval(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = alpha), tint.copy(alpha = alpha * 0.42f), Color.Transparent),
                        center = Offset(x, y), radius = bankWidth
                    ),
                    topLeft = Offset(x - bankWidth, y - bankHeight),
                    size = androidx.compose.ui.geometry.Size(bankWidth * 2f, bankHeight * 2f)
                )
            }
        }
        if ("rain" in active || "heavy_rain" in active || "wind" in active) {
            // Three depth planes: fine distant rain, readable mid-rain and a few close streaks.
            val heavy = "heavy_rain" in active
            val windLean = if ("wind" in active) 0.72f else 0.28f
            val rainColor = Color(0xFF9DB7BC)
            for (plane in 0..2) {
                val count = when (plane) { 0 -> if (heavy) 64 else 38; 1 -> if (heavy) 34 else 18; else -> if (heavy) 13 else 6 }
                val planeSpeed = 0.62f + plane * 0.48f + if (heavy) 0.52f else 0f
                val alpha = (0.12f + plane * 0.075f) * strength
                for (i in 0 until count) {
                    val seed = i * (83.7f + plane * 29.1f) + plane * 41.3f
                    val x = (seed + drift * w * planeSpeed * (0.78f + windLean)) % (w + 90f) - 45f
                    val y = (i * (53.9f + plane * 17.3f) + drift * h * planeSpeed * 1.45f) % (h + 60f) - 30f
                    val length = (5.5f + (i % 6) * 2.2f) * (0.72f + plane * 0.30f)
                    drawLine(rainColor.copy(alpha = alpha), Offset(x, y),
                        Offset(x - length * windLean, y + length),
                        strokeWidth = 0.48f + plane * 0.34f, cap = StrokeCap.Round)
                }
            }
            // Ground impacts: tiny broken arcs and droplets, concentrated near the lower edge.
            if (heavy || "rain" in active) {
                val splashCount = if (heavy) 13 else 6
                for (i in 0 until splashCount) {
                    val phase = (drift * 2.4f + i * 0.173f) % 1f
                    val x = (i * w * 0.173f + drift * w * 0.07f) % w
                    val ground = h * (0.91f + (i % 3) * 0.018f)
                    val spread = (5f + (i % 4) * 2f) * (0.35f + phase)
                    val rise = 2f + phase * 7f
                    val splash = Path().apply {
                        moveTo(x - spread, ground)
                        quadraticTo(x - spread * 0.45f, ground - rise, x, ground - rise * 0.35f)
                        quadraticTo(x + spread * 0.45f, ground - rise, x + spread, ground)
                    }
                    drawPath(splash, rainColor.copy(alpha = (0.10f + phase * 0.13f) * strength),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.8f, cap = StrokeCap.Round))
                    if (i % 2 == 0) {
                        drawLine(rainColor.copy(alpha = 0.20f * strength),
                            Offset(x + spread * 0.25f, ground - rise * 0.7f),
                            Offset(x + spread * 0.34f, ground - rise * 1.35f), 0.8f, cap = StrokeCap.Round)
                    }
                }
            }
        }
        if ("branches" in active || "dead_forest" in active) {
            // Uneven silhouettes with forked, curved limbs; slow sway avoids mechanical oscillation.
            for (i in 0..5) {
                val x = w * (i / 5f)
                val sway = sin((drift * 2f * PI + i * 1.73f).toFloat()) * w * 0.008f
                val base = h * (0.47f + (i % 3) * 0.07f)
                val trunk = Path().apply {
                    moveTo(x - w * 0.018f, h * 0.02f)
                    cubicTo(x + sway - w * 0.012f, h * 0.18f, x + sway * 0.5f + w * 0.02f, h * 0.31f, x + sway, base)
                    cubicTo(x + sway + w * 0.009f, base + h * 0.035f, x + w * 0.02f, base + h * 0.045f, x + w * 0.035f, base + h * 0.07f)
                }
                drawPath(trunk, Color(0xFF252D25).copy(alpha = 0.48f * strength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.013f, cap = StrokeCap.Round))
                for (b in 0..2) {
                    val by = h * (0.15f + b * 0.105f)
                    val side = if ((i + b) % 2 == 0) -1f else 1f
                    val fork = Path().apply {
                        moveTo(x + sway * (by / h), by)
                        cubicTo(x + side * w * 0.025f, by - h * 0.025f, x + side * w * 0.035f, by - h * 0.09f, x + side * w * 0.055f, by - h * 0.12f)
                    }
                    drawPath(fork, Color(0xFF252D25).copy(alpha = 0.42f * strength), style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.004f, cap = StrokeCap.Round))
                }
            }
        }
        if ("dead_leaves" in active) {
            for (i in 0..11) {
                val x = (i * w * 0.113f + sin((drift * 2f * PI + i * 2.4f).toFloat()) * w * 0.025f + drift * w * 0.11f) % w
                val y = h * (0.35f + (i % 5) * 0.12f) + cos((drift * 2f * PI + i).toFloat()) * 13f
                val leaf = Path().apply {
                    moveTo(x, y)
                    cubicTo(x + 5f, y - 5f, x + 13f, y - 3f, x + 15f, y + 2f)
                    cubicTo(x + 10f, y + 8f, x + 4f, y + 7f, x, y)
                }
                drawPath(leaf, Color(0xFF82745B).copy(alpha = 0.36f * strength))
            }
        }
        if ("fireflies" in active || "embers" in active || "spores" in active || "ash" in active || "dust" in active || "floating_dust" in active || "snow" in active) {
            val count = when { "snow" in active -> 25; "spores" in active -> 18; "floating_dust" in active -> 22; else -> 12 }
            val color = when {
                "fireflies" in active -> Color(0xFFB6C88B)
                "embers" in active -> Color(0xFFC27A50)
                "spores" in active -> Color(0xFF9BAF82)
                "snow" in active -> Color(0xFFD0D9DE)
                "ash" in active -> Color(0xFF999C94)
                else -> Color(0xFFAAA99A)
            }
            for (i in 0 until count) {
                val x = (i * 137.1f + sin((drift * 2f * PI + i * 1.9f).toFloat()) * 18f + drift * 19f) % w
                val y = (i * 83.7f + drift * h * (if ("snow" in active) 0.24f else 0.13f)) % h
                val twinkle = (0.34f + 0.28f * sin((breath * 2f * PI + i).toFloat())).coerceIn(0.08f, 0.65f)
                val radius = if ("fireflies" in active || "embers" in active) 2.2f else 1.15f
                drawCircle(Brush.radialGradient(listOf(color.copy(alpha = twinkle * strength), Color.Transparent), center = Offset(x, y), radius = radius * 4f),
                    radius = radius * 4f, center = Offset(x, y))
                drawCircle(color.copy(alpha = twinkle * 0.52f * strength), radius = radius * 0.48f, center = Offset(x, y))
            }
        }
        if ("moon_glow" in active) {
            val moonX = w * 0.78f
            val moonY = h * 0.19f
            val moonRadius = w * 0.24f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFB9C5AE).copy(alpha = 0.16f * strength), Color(0xFF899B91).copy(alpha = 0.055f * strength), Color.Transparent),
                    center = Offset(moonX, moonY), radius = moonRadius
                ), radius = moonRadius, center = Offset(moonX, moonY)
            )
        }
        if ("light_shafts" in active) {
            for (i in 0..2) {
                val topX = w * (0.18f + i * 0.31f)
                val lowerX = topX + w * (0.10f + i * 0.018f)
                val shaft = Path().apply {
                    moveTo(topX, h * 0.08f)
                    lineTo(topX + w * 0.025f, h * 0.08f)
                    lineTo(lowerX + w * 0.055f, h * 0.72f)
                    lineTo(lowerX - w * 0.055f, h * 0.72f)
                    close()
                }
                drawPath(shaft, Color(0xFFB5C1A6).copy(alpha = (0.018f + i * 0.004f) * strength))
            }
        }
        if ("vignette" in active) {
            val radius = maxOf(w, h) * 0.78f
            drawRect(
                Brush.radialGradient(
                    0.0f to Color.Transparent,
                    0.58f to Color.Transparent,
                    1.0f to Color(0xFF020504).copy(alpha = 0.66f * strength),
                    center = Offset(w * 0.5f, h * 0.48f), radius = radius
                )
            )
        }
        if ("dead_leaves" in active) {
            for (i in 0..13) {
                val phase = (drift * (0.42f + (i % 4) * 0.09f) + i * 0.137f) % 1f
                val x = (i * w * 0.113f + sin((phase * 2f * PI).toFloat()) * w * 0.035f + drift * w * 0.045f) % w
                val y = (phase * h * 1.12f) - h * 0.06f
                val size = 3.2f + (i % 4) * 1.7f
                val sway = sin((phase * 4f * PI + i).toFloat()) * size * 1.8f
                val leaf = Path().apply {
                    moveTo(x - size, y)
                    quadraticTo(x - size * 0.15f, y - size * 0.85f, x + size, y + sway * 0.25f)
                    quadraticTo(x + size * 0.15f, y + size * 0.8f, x - size, y)
                    close()
                }
                val leafColor = if (i % 3 == 0) Color(0xFF75644A) else Color(0xFF9A805A)
                drawPath(leaf, leafColor.copy(alpha = (0.28f + (i % 3) * 0.07f) * strength))
                drawLine(Color(0xFF342E25).copy(alpha = 0.32f * strength),
                    Offset(x - size * 0.65f, y), Offset(x + size * 0.75f, y + sway * 0.2f), 0.55f)
            }
        }
        if ("distant_glow" in active) {
            val glowX = w * 0.76f
            val glowY = h * 0.43f
            val radius = w * 0.13f
            drawCircle(Brush.radialGradient(listOf(Color(0x557E9A69).copy(alpha = 0.32f * strength), Color.Transparent), center = Offset(glowX, glowY), radius = radius),
                radius = radius, center = Offset(glowX, glowY))
        }
        if ("lightning" in active) {
            val pulse = (1f - kotlin.math.abs(flash - 0.18f) / 0.12f).coerceIn(0f, 1f)
            if (pulse > 0f) {
                drawRect(Color(0xFFBAC7CE).copy(alpha = pulse * 0.052f * strength))
                // A distant, crooked bolt gives the flash a visible source in the sky.
                val boltX = w * (0.68f + sin((flash * 2f * PI).toFloat()) * 0.025f)
                val bolt = Path().apply {
                    moveTo(boltX, h * 0.04f)
                    lineTo(boltX - w * 0.018f, h * 0.13f)
                    lineTo(boltX + w * 0.006f, h * 0.13f)
                    lineTo(boltX - w * 0.032f, h * 0.27f)
                    lineTo(boltX - w * 0.004f, h * 0.205f)
                    lineTo(boltX - w * 0.012f, h * 0.34f)
                }
                drawPath(bolt, Color(0xFFD5E0E2).copy(alpha = pulse * 0.62f * strength),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f, cap = StrokeCap.Square))
                val fork = Path().apply {
                    moveTo(boltX - w * 0.018f, h * 0.13f)
                    lineTo(boltX - w * 0.064f, h * 0.205f)
                }
                drawPath(fork, Color(0xFFB8C8CA).copy(alpha = pulse * 0.40f * strength),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.9f, cap = StrokeCap.Square))
            }
        }
        // Wildlife is animated as small silhouettes with irregular paths, not glowing icon-like dots.
        if ("crows" in active) {
            for (i in 0..2) {
                val phase = drift * 2f * PI + i * 2.1f
                val x = ((drift * w * (0.20f + i * 0.035f) + i * w * 0.37f) % (w + 100f)) - 50f
                val y = h * (0.19f + i * 0.105f) + sin(phase.toFloat()) * h * 0.018f
                val wing = sin((phase * 2.4).toFloat()) * h * 0.012f
                val bird = Path().apply {
                    moveTo(x - w * 0.018f, y)
                    quadraticTo(x - w * 0.008f, y - h * 0.018f - wing, x, y - h * 0.004f)
                    quadraticTo(x + w * 0.008f, y - h * 0.018f + wing, x + w * 0.019f, y)
                    quadraticTo(x + w * 0.006f, y - h * 0.008f, x, y + h * 0.002f)
                    quadraticTo(x - w * 0.007f, y - h * 0.008f, x - w * 0.018f, y)
                    close()
                }
                drawPath(bird, Color(0xFF080C0A).copy(alpha = 0.68f * strength))
            }
        }
        if ("watching_eyes" in active) {
            for (i in 0..3) {
                val x = w * (0.16f + i * 0.215f) + sin((drift * 2f * PI + i * 1.8f).toFloat()) * w * 0.012f
                val y = h * (0.34f + (i % 2) * 0.13f)
                val blink = (0.35f + 0.35f * sin((breath * 2f * PI + i * 2.0f).toFloat())).coerceIn(0.06f, 0.72f)
                val eyeColor = if ("blood_mist" in active) Color(0xFFC17C72) else Color(0xFFB7C18B)
                drawCircle(Brush.radialGradient(listOf(eyeColor.copy(alpha = blink * 0.18f * strength), Color.Transparent), center = Offset(x, y), radius = 12f),
                    radius = 12f, center = Offset(x, y))
                drawOval(eyeColor.copy(alpha = blink * strength), Offset(x - 4.5f, y - 1.2f), androidx.compose.ui.geometry.Size(3.2f, 1.8f))
                drawOval(eyeColor.copy(alpha = blink * strength), Offset(x + 1.5f, y - 1.2f), androidx.compose.ui.geometry.Size(3.2f, 1.8f))
            }
        }
        if ("moths" in active || "insects" in active) {
            val count = if ("insects" in active) 9 else 5
            for (i in 0 until count) {
                val phase = drift * 2f * PI + i * 2.7f
                val x = (w * (0.12f + (i * 0.173f) % 0.78f) + sin(phase.toFloat()) * w * 0.025f + drift * w * 0.035f) % w
                val y = h * (0.24f + (i % 5) * 0.115f) + cos((phase * 1.3).toFloat()) * h * 0.025f
                val flap = sin((phase * 5.0).toFloat()) * 0.65f
                val wingColor = Color(0xFFB7AD91).copy(alpha = 0.27f * strength)
                val wings = Path().apply {
                    moveTo(x, y)
                    quadraticTo(x - 5f, y - 8f - flap * 3f, x - 10f, y - 4f + flap * 2f)
                    quadraticTo(x - 6f, y + 1f, x, y)
                    moveTo(x, y)
                    quadraticTo(x + 5f, y - 8f + flap * 3f, x + 10f, y - 4f - flap * 2f)
                    quadraticTo(x + 6f, y + 1f, x, y)
                }
                drawPath(wings, wingColor)
                drawLine(Color(0xFF252A22).copy(alpha = 0.5f * strength), Offset(x, y - 3f), Offset(x, y + 4f), 1f, cap = StrokeCap.Round)
            }
        }
        if ("lanterns" in active) {
            // Old iron lanterns hang at different depths; their light breathes irregularly.
            for (i in 0..3) {
                val x = w * (0.16f + i * 0.225f) + sin((drift * 2f * PI + i * 1.7f).toFloat()) * w * 0.006f
                val top = h * (0.13f + (i % 2) * 0.075f)
                val lampY = top + h * 0.105f
                val flicker = (0.48f + 0.20f * sin((breath * 2f * PI + i * 2.1f).toFloat()) +
                    0.10f * sin((drift * 6f * PI + i).toFloat())).coerceIn(0.18f, 0.82f)
                val glow = Color(0xFFD5A66A)
                drawLine(Color(0xFF29251D).copy(alpha = 0.78f * strength), Offset(x, top), Offset(x, lampY - 7f), 1.3f)
                drawCircle(Brush.radialGradient(
                    listOf(glow.copy(alpha = flicker * 0.19f * strength), glow.copy(alpha = flicker * 0.055f * strength), Color.Transparent),
                    center = Offset(x, lampY), radius = w * 0.085f
                ), radius = w * 0.085f, center = Offset(x, lampY))
                drawRoundRect(Color(0xFF25251F).copy(alpha = 0.94f * strength),
                    topLeft = Offset(x - 5f, lampY - 7f), size = androidx.compose.ui.geometry.Size(10f, 15f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f))
                drawRoundRect(glow.copy(alpha = flicker * 0.82f * strength),
                    topLeft = Offset(x - 2.7f, lampY - 4f), size = androidx.compose.ui.geometry.Size(5.4f, 9f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f))
                drawLine(Color(0xFF514332).copy(alpha = 0.9f * strength), Offset(x - 6f, lampY - 8f), Offset(x + 6f, lampY - 8f), 1.4f)
                drawLine(Color(0xFF514332).copy(alpha = 0.9f * strength), Offset(x - 4f, lampY + 9f), Offset(x + 4f, lampY + 9f), 1.1f)
            }
        }
        if ("pulsing_fungus" in active) {
            // Uneven fungal clusters pulse softly, with dim spore halos rather than neon discs.
            for (i in 0..6) {
                val x = w * (0.09f + (i * 0.137f) % 0.84f)
                val ground = h * (0.78f + (i % 3) * 0.055f)
                val pulse = (0.5f + 0.5f * sin((breath * 2f * PI + i * 1.15f).toFloat())).coerceIn(0f, 1f)
                val capColor = if (i % 3 == 0) Color(0xFF9A806A) else Color(0xFF788C69)
                val halo = w * (0.025f + pulse * 0.012f)
                drawCircle(Brush.radialGradient(
                    listOf(capColor.copy(alpha = (0.08f + pulse * 0.09f) * strength), Color.Transparent),
                    center = Offset(x, ground - 8f), radius = halo
                ), radius = halo, center = Offset(x, ground - 8f))
                drawLine(Color(0xFF555C43).copy(alpha = 0.68f * strength), Offset(x, ground), Offset(x + (i % 2 - 0.5f) * 3f, ground - 12f - (i % 3) * 3f), 2.2f)
                val cap = Path().apply {
                    moveTo(x - 8f - pulse * 1.5f, ground - 11f - (i % 3) * 3f)
                    quadraticTo(x - 6f, ground - 22f - pulse * 2f, x, ground - 20f - pulse * 3f)
                    quadraticTo(x + 7f, ground - 22f - pulse * 2f, x + 9f + pulse, ground - 11f - (i % 3) * 3f)
                    quadraticTo(x, ground - 7f - (i % 3) * 2f, x - 8f - pulse * 1.5f, ground - 11f - (i % 3) * 3f)
                    close()
                }
                drawPath(cap, capColor.copy(alpha = (0.52f + pulse * 0.22f) * strength))
                drawCircle(Color(0xFFC4C99A).copy(alpha = pulse * 0.42f * strength), 1.1f, Offset(x - 3f, ground - 14f))
                drawCircle(Color(0xFFC4C99A).copy(alpha = pulse * 0.34f * strength), 0.8f, Offset(x + 3f, ground - 12f))
            }
        }
        if ("moving_shadows" in active) {
            // Low, stretched silhouettes slide over the ground like something passing off-screen.
            for (i in 0..3) {
                val direction = if (i % 2 == 0) 1f else -1f
                val travel = (drift * direction * 0.72f + i * 0.31f + 1.4f) % 1.4f
                val x = (travel - 0.2f) * w
                val y = h * (0.79f + (i % 2) * 0.075f)
                val shadowWidth = w * (0.12f + (i % 3) * 0.035f)
                val shadowHeight = h * 0.035f
                drawOval(Brush.radialGradient(
                    listOf(Color(0xFF020403).copy(alpha = 0.34f * strength), Color(0xFF020403).copy(alpha = 0.13f * strength), Color.Transparent),
                    center = Offset(x, y), radius = shadowWidth
                ), topLeft = Offset(x - shadowWidth, y - shadowHeight),
                    size = androidx.compose.ui.geometry.Size(shadowWidth * 2f, shadowHeight * 2f))
            }
        }
        if ("black_shadows" in active) {
            for (i in 0..2) {
                val x = ((drift * w * (if (i % 2 == 0) 0.16f else -0.12f) + i * w * 0.43f) % (w + 100f))
                val shadow = Path().apply {
                    moveTo(x, h * 0.12f)
                    cubicTo(x - w * 0.04f, h * 0.35f, x + w * 0.025f, h * 0.54f, x - w * 0.06f, h * 0.82f)
                    cubicTo(x + w * 0.02f, h * 0.67f, x + w * 0.015f, h * 0.39f, x + w * 0.08f, h * 0.17f)
                    close()
                }
                drawPath(shadow, Color(0xFF090D0C).copy(alpha = 0.10f * strength))
            }
        }
    }
}
