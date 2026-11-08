package com.storyforge.player

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
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
            val x=size.width*prop.x.coerceIn(0f,1f); val y=size.height*prop.y.coerceIn(0f,1f)
            val u=(size.width*0.12f*prop.scale.coerceIn(0.4f,2.2f)).coerceAtLeast(12f)
            val dark=if(foreground) Color(0xEE080B09) else Color(0xBB171D18)
            when(prop.type) {
                "dead_tree" -> {
                    // Uneven trunk, exposed roots and broken branch forks create a less geometric silhouette.
                    val trunk = Path().apply {
                        moveTo(x-u*0.19f,y+u*1.55f)
                        cubicTo(x-u*0.30f,y+u*0.75f,x-u*0.03f,y+u*0.10f,x-u*0.11f,y-u*0.55f)
                        cubicTo(x-u*0.19f,y-u*1.12f,x+u*0.12f,y-u*1.48f,x+u*0.04f,y-u*1.92f)
                        lineTo(x+u*0.25f,y-u*1.95f)
                        cubicTo(x+u*0.22f,y-u*1.30f,x+u*0.09f,y-u*0.78f,x+u*0.19f,y-u*0.18f)
                        cubicTo(x+u*0.28f,y+u*0.52f,x+u*0.12f,y+u*1.10f,x+u*0.22f,y+u*1.55f)
                        close()
                    }
                    drawPath(trunk, dark)
                    for (b in 0..3) {
                        val by=y-u*(0.40f+b*0.38f); val dir=if(b%2==0)-1f else 1f
                        val sway=sin(motion*2f*Math.PI.toFloat()+index*0.7f+b)*u*0.045f
                        val branch=Path().apply {
                            moveTo(x+u*0.02f,by)
                            cubicTo(x+dir*u*0.25f,by-u*0.10f,x+dir*u*0.40f+sway,by-u*0.34f,x+dir*u*(0.72f+b*0.10f)+sway,by-u*0.62f)
                            lineTo(x+dir*u*(0.65f+b*0.10f)+sway,by-u*0.43f)
                            cubicTo(x+dir*u*0.38f+sway,by-u*0.20f,x+dir*u*0.22f,by+u*0.04f,x+u*0.02f,by+u*0.12f)
                            close()
                        }
                        drawPath(branch,dark)
                        drawLine(Color(0xFF343A30).copy(alpha=0.55f),Offset(x+dir*u*0.22f,by-u*0.10f),Offset(x+dir*u*0.58f+sway,by-u*0.47f),u*0.025f)
                    }
                    // Fine bark scars and roots remain subtle at small screen sizes.
                    for (r in 0..2) {
                        val rx=x+u*(-0.08f+r*0.07f)
                        drawLine(Color(0xFF3A4034).copy(alpha=0.48f),Offset(rx,y+u*(0.35f+r*0.18f)),Offset(rx-u*0.035f,y+u*(0.62f+r*0.18f)),u*0.025f)
                    }
                    val roots= listOf(
                        Offset(x-u*0.10f,y+u*1.22f) to Offset(x-u*0.82f,y+u*1.72f),
                        Offset(x+u*0.10f,y+u*1.28f) to Offset(x+u*0.72f,y+u*1.66f),
                        Offset(x-u*0.02f,y+u*1.38f) to Offset(x-u*0.35f,y+u*1.88f)
                    )
                    roots.forEach { (a,b) ->
                        val p=Path().apply { moveTo(a.x,a.y); cubicTo(a.x+(b.x-a.x)*0.28f,a.y+u*0.12f,b.x-u*0.10f,b.y-u*0.12f,b.x,b.y) }
                        drawPath(p,Color(0xFF111712),style=Stroke(width=u*0.13f,cap=StrokeCap.Round))
                    }
                }
                "stump" -> { drawLine(dark,Offset(x,y),Offset(x,y+u*0.55f),u*0.42f,cap=StrokeCap.Round);drawOval(Color(0xFF514B3C),Offset(x-u*0.22f,y-u*0.1f),Size(u*0.44f,u*0.18f)) }
                "rocks" -> { val path=Path().apply{moveTo(x-u*0.65f,y);lineTo(x-u*0.4f,y-u*0.55f);lineTo(x+u*0.15f,y-u*0.7f);lineTo(x+u*0.68f,y-u*0.15f);lineTo(x+u*0.55f,y+u*0.05f);close()};drawPath(path,Color(0xFF303832)) }
                "fungus" -> { drawLine(Color(0xFF536344),Offset(x,y),Offset(x,y-u*0.6f),u*0.1f);drawCircle(Color(0xFF9EBD72).copy(alpha=0.45f),u*0.34f,Offset(x,y-u*0.68f));drawCircle(Color(0xFFBDD98A),u*0.12f,Offset(x,y-u*0.68f)) }
                "broken_fence" -> { for(i in 0..2)drawLine(dark,Offset(x-u*0.65f+i*u*0.55f,y-u*0.6f),Offset(x-u*0.65f+i*u*0.55f,y+u*0.45f),u*0.1f);drawLine(dark,Offset(x-u*0.8f,y-u*0.25f),Offset(x+u*0.8f,y-u*0.05f),u*0.1f);drawLine(dark,Offset(x-u*0.8f,y+u*0.18f),Offset(x+u*0.8f,y+u*0.05f),u*0.08f) }
                "lantern" -> { drawLine(Color(0xFF403A2D),Offset(x,y-u*0.55f),Offset(x,y-u*0.25f),u*0.07f);drawRoundRect(Color(0xFF292A25),Offset(x-u*0.22f,y-u*0.25f),Size(u*0.44f,u*0.58f),CornerRadius(u*0.08f));val flicker=0.24f+(sin(motion*6f*Math.PI.toFloat()+index)*0.5f+0.5f)*0.2f;drawCircle(Color(0xFFFFC66D).copy(alpha=flicker),u*0.62f,Offset(x,y+u*0.02f));drawCircle(Color(0xFFFFD28A),u*0.12f,Offset(x,y+u*0.02f)) }
                "bones" -> { drawLine(Color(0xFFB4AD91),Offset(x-u*0.55f,y),Offset(x+u*0.55f,y+u*0.1f),u*0.12f,cap=StrokeCap.Round);drawCircle(Color(0xFFB4AD91),u*0.16f,Offset(x-u*0.58f,y));drawCircle(Color(0xFFB4AD91),u*0.16f,Offset(x+u*0.58f,y+u*0.1f)) }
                "hanging_roots" -> for(i in 0..4){val xx=x-u*0.55f+i*u*0.28f;val path=Path().apply{moveTo(xx,y-u);cubicTo(xx-u*0.12f,y-u*0.3f,xx+u*0.18f,y+u*0.1f,xx+sin(motion*2f*Math.PI.toFloat()+index+i.toFloat())*u*0.12f,y+u*0.7f)};drawPath(path,Color(0xFF353D2E),style=Stroke(width=u*0.07f,cap=StrokeCap.Round))}
                "shrine" -> { drawRect(dark,Offset(x-u*0.45f,y-u*0.65f),Size(u*0.9f,u*0.8f));drawLine(Color(0xFF111511),Offset(x-u*0.55f,y-u*0.68f),Offset(x,y-u*1.15f),u*0.13f);drawLine(Color(0xFF111511),Offset(x,y-u*1.15f),Offset(x+u*0.55f,y-u*0.68f),u*0.13f) }
                "crates" -> { drawRect(Color(0xFF40372A),Offset(x-u*0.45f,y-u*0.45f),Size(u*0.9f,u*0.8f));drawLine(Color(0xFF756044),Offset(x-u*0.4f,y-u*0.4f),Offset(x+u*0.4f,y+u*0.3f),u*0.07f);drawLine(Color(0xFF756044),Offset(x+u*0.4f,y-u*0.4f),Offset(x-u*0.4f,y+u*0.3f),u*0.07f) }
                "grave" -> { drawRoundRect(Color(0xFF55564D),Offset(x-u*0.34f,y-u*0.7f),Size(u*0.68f,u*0.9f),CornerRadius(u*0.18f));drawLine(Color(0xFF242820),Offset(x,y-u*0.48f),Offset(x,y+u*0.02f),u*0.07f);drawLine(Color(0xFF242820),Offset(x-u*0.16f,y-u*0.32f),Offset(x+u*0.16f,y-u*0.32f),u*0.07f) }
                "thorn_bush" -> for(i in 0..5){val bx=x-u*0.55f+i*u*0.22f;val by=y+sin(i*1.7f)*u*0.12f;drawLine(Color(0xFF28352A),Offset(bx,by),Offset(bx+u*0.12f,by-u*(0.35f+(i%2)*0.2f)),u*0.09f,cap=StrokeCap.Round);drawLine(Color(0xFF6A7354),Offset(bx+u*0.12f,by-u*0.35f),Offset(bx+u*0.25f,by-u*0.4f),u*0.035f)}
                "broken_cart" -> { drawLine(Color(0xFF4A3829),Offset(x-u*0.58f,y-u*0.22f),Offset(x+u*0.48f,y-u*0.05f),u*0.13f);drawCircle(Color(0xFF292B27),u*0.22f,Offset(x-u*0.36f,y+u*0.24f),style=Stroke(width=u*0.08f));drawCircle(Color(0xFF292B27),u*0.22f,Offset(x+u*0.42f,y+u*0.24f),style=Stroke(width=u*0.08f));drawLine(Color(0xFF70563A),Offset(x-u*0.42f,y-u*0.2f),Offset(x-u*0.55f,y-u*0.65f),u*0.08f) }
                "well" -> { drawOval(Color(0xFF343A34),Offset(x-u*0.62f,y-u*0.2f),Size(u*1.24f,u*0.52f));drawOval(Color(0xFF080C0A),Offset(x-u*0.38f,y-u*0.13f),Size(u*0.76f,u*0.28f));drawLine(Color(0xFF3C3428),Offset(x-u*0.62f,y-u*0.32f),Offset(x-u*0.62f,y-u*0.85f),u*0.08f);drawLine(Color(0xFF3C3428),Offset(x+u*0.62f,y-u*0.32f),Offset(x+u*0.62f,y-u*0.85f),u*0.08f);drawLine(Color(0xFF3C3428),Offset(x-u*0.68f,y-u*0.82f),Offset(x+u*0.68f,y-u*0.82f),u*0.08f) }
                "hanging_cage" -> { drawLine(Color(0xFF46483F),Offset(x,y-u*1.0f),Offset(x,y-u*0.68f),u*0.06f);drawRect(Color(0xFF292E2A),Offset(x-u*0.35f,y-u*0.68f),Size(u*0.7f,u*0.92f),style=Stroke(width=u*0.07f));for(i in -1..1)drawLine(Color(0xFF454B42),Offset(x+i*u*0.16f,y-u*0.66f),Offset(x+i*u*0.16f,y+u*0.22f),u*0.045f) }
                "ruined_door" -> { drawRect(Color(0xFF382F25),Offset(x-u*0.48f,y-u*0.82f),Size(u*0.96f,u*1.1f));drawRect(Color(0xFF101512),Offset(x-u*0.34f,y-u*0.65f),Size(u*0.68f,u*0.93f));drawLine(Color(0xFF6A5137),Offset(x-u*0.42f,y-u*0.35f),Offset(x+u*0.35f,y+u*0.05f),u*0.08f) }
            }
        }
    }
}
