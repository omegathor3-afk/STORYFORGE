package com.storyforge.app

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.sin

@Composable
internal fun SceneEnvironmentOverlay(props: List<SceneProp>, foreground: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "environment-motion")
    val motion by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(6200, easing = LinearEasing), RepeatMode.Restart), label = "prop-motion")
    Canvas(modifier.fillMaxSize()) {
        props.filter { it.foreground == foreground }.forEachIndexed { index, prop ->
            val x = size.width * prop.x.coerceIn(0f, 1f)
            val y = size.height * prop.y.coerceIn(0f, 1f)
            val unit = (size.width * 0.12f * prop.scale.coerceIn(0.4f, 2.2f)).coerceAtLeast(12f)
            val dark = if (foreground) Color(0xEE080B09) else Color(0xBB171D18)
            when (prop.type) {
                "dead_tree" -> {
                    drawLine(dark, Offset(x, y + unit * 1.6f), Offset(x - unit * 0.08f, y - unit * 1.8f), unit * 0.22f, cap = StrokeCap.Round)
                    for (b in 0..3) {
                        val by = y - unit * (0.45f + b * 0.38f)
                        val dir = if (b % 2 == 0) -1f else 1f
                        val sway = sin(motion * 2f * Math.PI.toFloat() + index * 0.7f + b) * unit * 0.07f
                        drawLine(dark, Offset(x, by), Offset(x + dir * unit * (0.65f + b * 0.12f) + sway, by - unit * 0.55f), unit * 0.09f, cap = StrokeCap.Round)
                    }
                    drawLine(dark, Offset(x - unit * 0.1f, y + unit * 1.35f), Offset(x - unit * 0.72f, y + unit * 1.7f), unit * 0.11f)
                }
                "stump" -> {
                    drawLine(dark, Offset(x, y), Offset(x, y + unit * 0.55f), unit * 0.42f, cap = StrokeCap.Round)
                    drawOval(Color(0xFF514B3C), Offset(x - unit * 0.22f, y - unit * 0.1f), androidx.compose.ui.geometry.Size(unit * 0.44f, unit * 0.18f))
                }
                "rocks" -> {
                    val path = Path().apply { moveTo(x-unit*0.65f,y); lineTo(x-unit*0.4f,y-unit*0.55f); lineTo(x+unit*0.15f,y-unit*0.7f); lineTo(x+unit*0.68f,y-unit*0.15f); lineTo(x+unit*0.55f,y+unit*0.05f); close() }
                    drawPath(path, Color(0xFF303832))
                }
                "fungus" -> {
                    drawLine(Color(0xFF536344), Offset(x, y), Offset(x, y-unit*0.6f), unit*0.1f)
                    drawCircle(Color(0xFF9EBD72).copy(alpha=0.45f), unit*0.34f, Offset(x, y-unit*0.68f))
                    drawCircle(Color(0xFFBDD98A), unit*0.12f, Offset(x, y-unit*0.68f))
                }
                "broken_fence" -> {
                    for (i in 0..2) drawLine(dark, Offset(x-unit*0.65f+i*unit*0.55f,y-unit*0.6f), Offset(x-unit*0.65f+i*unit*0.55f,y+unit*0.45f), unit*0.1f)
                    drawLine(dark, Offset(x-unit*0.8f,y-unit*0.25f), Offset(x+unit*0.8f,y-unit*0.05f), unit*0.1f)
                    drawLine(dark, Offset(x-unit*0.8f,y+unit*0.18f), Offset(x+unit*0.8f,y+unit*0.05f), unit*0.08f)
                }
                "lantern" -> {
                    drawLine(Color(0xFF403A2D), Offset(x,y-unit*0.55f), Offset(x,y-unit*0.25f), unit*0.07f)
                    drawRoundRect(Color(0xFF292A25), Offset(x-unit*0.22f,y-unit*0.25f), androidx.compose.ui.geometry.Size(unit*0.44f,unit*0.58f), androidx.compose.ui.geometry.CornerRadius(unit*0.08f))
                    drawCircle(Color(0xFFFFC66D).copy(alpha=0.35f), unit*0.55f, Offset(x,y+unit*0.02f))
                    drawCircle(Color(0xFFFFD28A), unit*0.12f, Offset(x,y+unit*0.02f))
                }
                "bones" -> {
                    drawLine(Color(0xFFB4AD91), Offset(x-unit*0.55f,y), Offset(x+unit*0.55f,y+unit*0.1f), unit*0.12f, cap=StrokeCap.Round)
                    drawCircle(Color(0xFFB4AD91),unit*0.16f,Offset(x-unit*0.58f,y))
                    drawCircle(Color(0xFFB4AD91),unit*0.16f,Offset(x+unit*0.58f,y+unit*0.1f))
                }
                "hanging_roots" -> for (i in 0..4) {
                    val xx=x-unit*0.55f+i*unit*0.28f
                    val path=Path().apply { moveTo(xx,y-unit); cubicTo(xx-unit*0.12f,y-unit*0.3f,xx+unit*0.18f,y+unit*0.1f,xx+sin(motion * 2f * Math.PI.toFloat()+index+i.toFloat())*unit*0.12f,y+unit*0.7f) }
                    drawPath(path,Color(0xFF353D2E),style=Stroke(width=unit*0.07f,cap=StrokeCap.Round))
                }
                "shrine" -> {
                    drawRect(dark, Offset(x-unit*0.45f,y-unit*0.65f), androidx.compose.ui.geometry.Size(unit*0.9f,unit*0.8f))
                    drawLine(Color(0xFF111511),Offset(x-unit*0.55f,y-unit*0.68f),Offset(x,y-unit*1.15f),unit*0.13f)
                    drawLine(Color(0xFF111511),Offset(x,y-unit*1.15f),Offset(x+unit*0.55f,y-unit*0.68f),unit*0.13f)
                }
                "crates" -> {
                    drawRect(Color(0xFF40372A),Offset(x-unit*0.45f,y-unit*0.45f),androidx.compose.ui.geometry.Size(unit*0.9f,unit*0.8f))
                    drawLine(Color(0xFF756044),Offset(x-unit*0.4f,y-unit*0.4f),Offset(x+unit*0.4f,y+unit*0.3f),unit*0.07f)
                    drawLine(Color(0xFF756044),Offset(x+unit*0.4f,y-unit*0.4f),Offset(x-unit*0.4f,y+unit*0.3f),unit*0.07f)
                }
                "grave" -> {
                    drawRoundRect(Color(0xFF55564D), Offset(x-unit*0.34f,y-unit*0.7f), androidx.compose.ui.geometry.Size(unit*0.68f,unit*0.9f), androidx.compose.ui.geometry.CornerRadius(unit*0.18f))
                    drawLine(Color(0xFF242820),Offset(x,y-unit*0.48f),Offset(x,y+unit*0.02f),unit*0.07f)
                    drawLine(Color(0xFF242820),Offset(x-unit*0.16f,y-unit*0.32f),Offset(x+unit*0.16f,y-unit*0.32f),unit*0.07f)
                }
                "thorn_bush" -> {
                    for(i in 0..5){val bx=x-unit*0.55f+i*unit*0.22f;val by=y+sin(i*1.7f)*unit*0.12f;drawLine(Color(0xFF28352A),Offset(bx,by),Offset(bx+unit*0.12f,by-unit*(0.35f+(i%2)*0.2f)),unit*0.09f,cap=StrokeCap.Round);drawLine(Color(0xFF6A7354),Offset(bx+unit*0.12f,by-unit*0.35f),Offset(bx+unit*0.25f,by-unit*0.4f),unit*0.035f)}
                }
                "broken_cart" -> {
                    drawLine(Color(0xFF4A3829),Offset(x-unit*0.58f,y-unit*0.22f),Offset(x+unit*0.48f,y-unit*0.05f),unit*0.13f)
                    drawCircle(Color(0xFF292B27),unit*0.22f,Offset(x-unit*0.36f,y+unit*0.24f),style=Stroke(width=unit*0.08f))
                    drawCircle(Color(0xFF292B27),unit*0.22f,Offset(x+unit*0.42f,y+unit*0.24f),style=Stroke(width=unit*0.08f))
                    drawLine(Color(0xFF70563A),Offset(x-unit*0.42f,y-unit*0.2f),Offset(x-unit*0.55f,y-unit*0.65f),unit*0.08f)
                }
                "well" -> {
                    drawOval(Color(0xFF343A34),Offset(x-unit*0.62f,y-unit*0.2f),androidx.compose.ui.geometry.Size(unit*1.24f,unit*0.52f))
                    drawOval(Color(0xFF080C0A),Offset(x-unit*0.38f,y-unit*0.13f),androidx.compose.ui.geometry.Size(unit*0.76f,unit*0.28f))
                    drawLine(Color(0xFF3C3428),Offset(x-unit*0.62f,y-unit*0.32f),Offset(x-unit*0.62f,y-unit*0.85f),unit*0.08f)
                    drawLine(Color(0xFF3C3428),Offset(x+unit*0.62f,y-unit*0.32f),Offset(x+unit*0.62f,y-unit*0.85f),unit*0.08f)
                    drawLine(Color(0xFF3C3428),Offset(x-unit*0.68f,y-unit*0.82f),Offset(x+unit*0.68f,y-unit*0.82f),unit*0.08f)
                }
                "hanging_cage" -> {
                    drawLine(Color(0xFF46483F),Offset(x,y-unit*1.0f),Offset(x,y-unit*0.68f),unit*0.06f)
                    drawRect(Color(0xFF292E2A),Offset(x-unit*0.35f,y-unit*0.68f),androidx.compose.ui.geometry.Size(unit*0.7f,unit*0.92f),style=Stroke(width=unit*0.07f))
                    for(i in -1..1)drawLine(Color(0xFF454B42),Offset(x+i*unit*0.16f,y-unit*0.66f),Offset(x+i*unit*0.16f,y+unit*0.22f),unit*0.045f)
                }
                "ruined_door" -> {
                    drawRect(Color(0xFF382F25),Offset(x-unit*0.48f,y-unit*0.82f),androidx.compose.ui.geometry.Size(unit*0.96f,unit*1.1f))
                    drawRect(Color(0xFF101512),Offset(x-unit*0.34f,y-unit*0.65f),androidx.compose.ui.geometry.Size(unit*0.68f,unit*0.93f))
                    drawLine(Color(0xFF6A5137),Offset(x-unit*0.42f,y-unit*0.35f),Offset(x+unit*0.35f,y+unit*0.05f),unit*0.08f)
                }
            }
        }
    }
}
