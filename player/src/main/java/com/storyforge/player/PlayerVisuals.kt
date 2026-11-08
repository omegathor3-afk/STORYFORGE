package com.storyforge.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal val PlayerWeatheredShape = CutCornerShape(12.dp)

internal fun Modifier.playerBackdrop(): Modifier = this
    .background(Brush.verticalGradient(listOf(Color(0xFF111812), Color(0xFF090D0B), Color(0xFF050706))))
    .drawBehind {
        val w=size.width; val h=size.height
        for(i in 0..5) {
            val x=w*(0.08f+i*0.19f)
            val p=Path().apply {
                moveTo(x,-h*0.02f)
                cubicTo(x+w*0.07f,h*0.18f,x-w*0.06f,h*0.28f,x+w*0.015f,h*0.43f)
                cubicTo(x+w*0.035f,h*0.53f,x-w*0.055f,h*0.62f,x-w*0.025f,h*0.77f)
            }
            drawPath(p,Color(0xFF819078).copy(alpha=0.045f),style=Stroke(width=2.2f))
        }
    }

internal fun Modifier.playerWeatheredSurface(): Modifier = this
    .clip(PlayerWeatheredShape)
    .background(Brush.linearGradient(listOf(Color(0xFF252A25),Color(0xFF171D19),Color(0xFF101511))))
    .border(BorderStroke(1.dp,Color(0xFF68705C).copy(alpha=0.48f)),PlayerWeatheredShape)
    .drawBehind {
        val w=size.width; val h=size.height
        val grain=Path().apply {
            moveTo(w*0.06f,h*0.18f)
            cubicTo(w*0.19f,h*0.12f,w*0.28f,h*0.21f,w*0.39f,h*0.16f)
            moveTo(w*0.62f,h*0.83f)
            cubicTo(w*0.74f,h*0.76f,w*0.83f,h*0.88f,w*0.96f,h*0.79f)
        }
        drawPath(grain,Color(0xFFB8A47B).copy(alpha=0.075f),style=Stroke(width=1.1f))
    }
