package com.storyforge.player

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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun AtmosphereOverlay(effects: List<String>, theme: String, intensity: Float = 0.72f, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "darkwood-atmosphere")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart), label = "drift")
    val active = effects.toSet() + when (theme) {
        "ashen" -> setOf("ash", "fog", "branches")
        "haunted" -> setOf("fog", "black_shadows", "branches", "dead_leaves")
        "fungal" -> setOf("spores", "dense_fog", "branches")
        "bloodmoon" -> setOf("blood_mist", "black_shadows", "embers")
        "frozen" -> setOf("snow", "fog", "wind")
        else -> setOf("fog", "branches", "dead_leaves")
    }
    Canvas(modifier.fillMaxSize().graphicsLayer(alpha = intensity.coerceIn(0f, 1f))) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0x22020708), Color.Transparent, Color(0x66030708))))
        if ("branches" in active || "dead_leaves" in active) {
            for (i in 0..8) {
                val x = w * (0.04f + i * 0.115f)
                val sway = sin((phase * 2.0 * PI + i).toFloat()) * 12f
                drawLine(Color(0x66515A4D), Offset(x, h * 0.05f), Offset(x + sway, h * (0.38f + (i % 3) * 0.12f)), strokeWidth = 8f, cap = StrokeCap.Round)
                drawLine(Color(0x66515A4D), Offset(x + sway, h * 0.24f), Offset(x - 34f + sway, h * 0.17f), strokeWidth = 4f)
                drawLine(Color(0x66515A4D), Offset(x + sway, h * 0.31f), Offset(x + 28f + sway, h * 0.22f), strokeWidth = 3f)
            }
        }
        if ("fog" in active || "dense_fog" in active || "blood_mist" in active) {
            val tint = if ("blood_mist" in active) Color(0x30A52D36) else if ("snow" in active) Color(0x337F9EAE) else Color(0x447F8B83)
            for (i in 0..4) {
                val x = ((phase + i * 0.23f) % 1.35f - 0.15f) * w
                val y = h * (0.35f + (i % 3) * 0.17f)
                drawCircle(tint, radius = w * (if ("dense_fog" in active) 0.34f else 0.23f), center = Offset(x, y))
            }
        }
        if ("ground_fog" in active) {
            for (i in 0..5) {
                val x = ((i * w * 0.24f + phase * w * 0.55f) % (w + 100f)) - 50f
                val y = h * (0.76f + (i % 2) * 0.08f)
                drawCircle(Color(0x447D8980), radius = w * 0.19f, center = Offset(x, y))
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x337C887E), Color(0x117C887E)), startY = h * 0.58f, endY = h))
        }
        if ("distant_glow" in active) {
            for (i in 0..2) {
                val x = w * (0.22f + i * 0.29f)
                val y = h * (0.43f + (i % 2) * 0.08f)
                drawCircle(Color(0x227E9A69), radius = w * 0.1f, center = Offset(x, y))
                drawCircle(Color(0x668CA66C), radius = 2.2f, center = Offset(x, y))
            }
        }
        if ("rain" in active || "heavy_rain" in active || "wind" in active) {
            val count = if ("heavy_rain" in active) 150 else 75
            for (i in 0 until count) {
                val x = ((i * 73f + phase * w * 1.8f) % w)
                val y = ((i * 131f + phase * h * 2.2f) % h)
                drawLine(Color(0x889CBAC0), Offset(x, y), Offset(x - 10f, y + if ("heavy_rain" in active) 30f else 19f), strokeWidth = if ("heavy_rain" in active) 2f else 1.2f)
            }
        }
        val particleEffects = listOf("ash", "dust", "fireflies", "spores", "embers", "snow", "dead_leaves")
        if (particleEffects.any { it in active }) {
            val count = if ("snow" in active || "spores" in active) 65 else 42
            for (i in 0 until count) {
                val x = ((i * 97f + sin((phase * 6 + i).toFloat()) * 24f + phase * 22f) % w)
                val y = ((i * 151f + phase * h * (if ("snow" in active) 0.55f else 0.8f)) % h)
                val color = when {
                    "embers" in active -> Color(0xCCF08A45)
                    "fireflies" in active -> Color(0xCCB7D98B)
                    "spores" in active -> Color(0xAA9BBE86)
                    "snow" in active -> Color(0xAADDDFE3)
                    "ash" in active -> Color(0x99A6A6A0)
                    else -> Color(0x887E827C)
                }
                drawCircle(color, radius = if ("fireflies" in active || "embers" in active) 3.2f else 1.8f, center = Offset(x, y))
            }
        }
        if ("black_shadows" in active) {
            for (i in 0..3) {
                val x = ((phase * w * 1.7f + i * w * 0.37f) % (w + 100f)) - 50f
                drawLine(Color(0x55404040), Offset(x, h * 0.15f), Offset(x - 45f, h * 0.72f), strokeWidth = 24f, cap = StrokeCap.Round)
            }
        }
    }
}
