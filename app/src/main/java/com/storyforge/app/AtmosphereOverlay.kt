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
        if ("fog" in active || "dense_fog" in active || "blood_mist" in active || "ground_fog" in active) {
            val tint = when {
                "blood_mist" in active -> Color(0xFF74464A)
                "snow" in active -> Color(0xFF8D9EAA)
                "spores" in active -> Color(0xFF788A67)
                else -> Color(0xFF929B8F)
            }
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
        if ("rain" in active || "heavy_rain" in active || "wind" in active) {
            val count = if ("heavy_rain" in active) 58 else 32
            for (i in 0 until count) {
                val speed = if ("heavy_rain" in active) 1.7f else 1.0f
                val x = (i * 97.3f + drift * w * speed * 1.25f) % (w + 60f) - 30f
                val y = (i * 61.7f + drift * h * speed * 1.8f) % (h + 40f) - 20f
                val length = 9f + (i % 5) * 3.1f
                drawLine(Color(0x668EA7AD).copy(alpha = (0.22f + (i % 4) * 0.035f) * strength),
                    Offset(x, y), Offset(x - length * 0.32f, y + length), strokeWidth = if (i % 5 == 0) 1.35f else 0.75f, cap = StrokeCap.Round)
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
        if ("fireflies" in active || "embers" in active || "spores" in active || "ash" in active || "dust" in active || "snow" in active) {
            val count = when { "snow" in active -> 25; "spores" in active -> 18; else -> 12 }
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
        if ("distant_glow" in active) {
            val glowX = w * 0.76f
            val glowY = h * 0.43f
            val radius = w * 0.13f
            drawCircle(Brush.radialGradient(listOf(Color(0x557E9A69).copy(alpha = 0.32f * strength), Color.Transparent), center = Offset(glowX, glowY), radius = radius),
                radius = radius, center = Offset(glowX, glowY))
        }
        if ("lightning" in active) {
            val pulse = (1f - kotlin.math.abs(flash - 0.18f) / 0.12f).coerceIn(0f, 1f)
            if (pulse > 0f) drawRect(Color(0xFFBAC7CE).copy(alpha = pulse * 0.075f * strength))
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
