package com.storyforge.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** Shared material language: carved stone edges, dark timber grain and root-like seams. */
internal val WeatheredShape = CutCornerShape(12.dp)

internal fun Modifier.storyforgeBackdrop(): Modifier = this
    .background(Brush.verticalGradient(listOf(Color(0xFF111812), Color(0xFF090D0B), Color(0xFF050706))))
    .drawBehind {
        val w = size.width
        val h = size.height
        // Very faint root silhouettes, intentionally kept behind controls and text.
        for (i in 0..5) {
            val x = w * (0.08f + i * 0.19f)
            val path = Path().apply {
                moveTo(x, -h * 0.02f)
                cubicTo(x + w * 0.07f, h * 0.18f, x - w * 0.06f, h * 0.28f, x + w * 0.015f, h * 0.43f)
                cubicTo(x + w * 0.035f, h * 0.53f, x - w * 0.055f, h * 0.62f, x - w * 0.025f, h * 0.77f)
            }
            drawPath(path, Color(0xFF819078).copy(alpha = 0.045f), style = Stroke(width = 2.2f))
            val twig = Path().apply {
                moveTo(x + w * 0.01f, h * 0.29f)
                cubicTo(x + w * 0.055f, h * 0.25f, x + w * 0.07f, h * 0.21f, x + w * 0.085f, h * 0.17f)
            }
            drawPath(twig, Color(0xFF9A8A69).copy(alpha = 0.035f), style = Stroke(width = 1.4f))
        }
        // Uneven mineral seams in the lower corners, not a repeating grid.
        for (i in 0..3) {
            val y = h * (0.72f + i * 0.075f)
            drawLine(Color(0xFFB29B70).copy(alpha = 0.025f), Offset(0f, y), Offset(w * (0.13f + i * 0.035f), y - h * 0.025f), 1.2f)
            drawLine(Color(0xFFB29B70).copy(alpha = 0.025f), Offset(w, y - h * 0.02f), Offset(w * (0.88f - i * 0.025f), y + h * 0.018f), 1.2f)
        }
    }

internal fun Modifier.weatheredStone(): Modifier = this
    .clip(WeatheredShape)
    .background(Brush.linearGradient(listOf(Color(0xFF252A25), Color(0xFF171D19), Color(0xFF101511))))
    .border(BorderStroke(1.dp, Color(0xFF68705C).copy(alpha = 0.42f)), WeatheredShape)
    .drawBehind {
        val w = size.width
        val h = size.height
        val grain = Path().apply {
            moveTo(w * 0.06f, h * 0.18f)
            cubicTo(w * 0.19f, h * 0.12f, w * 0.28f, h * 0.21f, w * 0.39f, h * 0.16f)
            moveTo(w * 0.62f, h * 0.83f)
            cubicTo(w * 0.74f, h * 0.76f, w * 0.83f, h * 0.88f, w * 0.96f, h * 0.79f)
        }
        drawPath(grain, Color(0xFFB8A47B).copy(alpha = 0.075f), style = Stroke(width = 1.1f))
        drawLine(Color(0xFF080B08).copy(alpha = 0.35f), Offset(w * 0.03f, h * 0.94f), Offset(w * 0.22f, h * 0.91f), 2f)
    }
