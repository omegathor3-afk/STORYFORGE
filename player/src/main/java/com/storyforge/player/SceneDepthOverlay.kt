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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun SceneDepthOverlay(effects: List<String>, theme: String, layer: String, intensity: Float = 0.72f, modifier: Modifier = Modifier) {
    val motion=rememberInfiniteTransition(label="scene-depth")
    val drift by motion.animateFloat(0f,1f,infiniteRepeatable(tween(18000,easing=LinearEasing),RepeatMode.Restart),label="mist-drift")
    val pulse by motion.animateFloat(0f,1f,infiniteRepeatable(tween(6200,easing=LinearEasing),RepeatMode.Reverse),label="moon-pulse")
    val active=effects.toSet()
    val strength=intensity.coerceIn(0f,1f)
    Canvas(modifier.fillMaxSize()) {
        val w=size.width; val h=size.height
        if(layer=="far") {
            if("distant_forest" in active || theme=="darkwood" || theme=="haunted" || theme=="ashen") {
                for(row in 0..2) {
                    val baseY=h*(0.52f+row*0.105f); val alpha=(0.22f+row*0.10f)*strength
                    val color=Color(0xFF101812).copy(alpha=alpha)
                    val step=if(row==0) 0.105f else if(row==1) 0.13f else 0.16f
                    var x=-w*0.08f+row*37f; var i=0
                    while(x<w*1.1f) {
                        val treeHeight=h*(0.10f+((i*17+row*11)%9)*0.012f)
                        val sway=sin((drift*2f*PI+i*0.7f+row).toFloat())*w*0.003f
                        val trunk=Path().apply { moveTo(x-w*0.009f,baseY);lineTo(x+sway,baseY-treeHeight);lineTo(x+w*0.012f,baseY);close() }
                        drawPath(trunk,color)
                        for(b in 0..2) {
                            val by=baseY-treeHeight*(0.35f+b*0.2f);val dir=if((i+b)%2==0)-1f else 1f
                            drawLine(color,Offset(x+sway*0.5f,by),Offset(x+sway*0.5f+dir*w*0.025f,by-h*0.035f),strokeWidth=(row+1)*1.3f,cap=StrokeCap.Round)
                        }
                        x+=w*step;i++
                    }
                }
            }
            if("moon_glow" in active || "distant_glow" in active) {
                val c=Offset(w*0.78f,h*0.18f);val r=w*0.38f
                drawCircle(Brush.radialGradient(listOf(Color(0x55C5D0B0).copy(alpha=(0.35f+0.2f*pulse)*strength),Color(0x1C9CA88F).copy(alpha=0.11f*strength),Color.Transparent),center=c,radius=r),radius=r,center=c)
                drawCircle(Color(0x99C5C9AE).copy(alpha=strength),radius=w*0.025f,center=c)
            }
            if("light_shafts" in active) for(i in 0..2) {
                val x=w*(0.22f+i*0.29f)
                val beam=Path().apply { moveTo(x-w*0.025f,0f);lineTo(x+w*0.09f,h*0.67f);lineTo(x+w*0.16f,h*0.67f);lineTo(x+w*0.015f,0f);close() }
                drawPath(beam,Brush.verticalGradient(listOf(Color(0x1DC5CBA7).copy(alpha=strength),Color.Transparent),startY=0f,endY=h*0.68f))
            }
        } else {
            if("ground_fog" in active) {
                val fogStrength=0.11f*strength
                for(i in 0..5) {
                    val x=((drift*(if(i%2==0)1f else -0.72f)+i*0.21f)%1.5f-0.2f)*w
                    val y=h*(0.73f+(i%3)*0.065f);val r=w*(0.22f+(i%2)*0.09f)
                    val fog=Brush.radialGradient(listOf(Color(0x99AAB4A0).copy(alpha=fogStrength),Color(0x4495A18E).copy(alpha=fogStrength*0.5f),Color.Transparent),center=Offset(x,y),radius=r)
                    drawCircle(fog,radius=r,center=Offset(x,y))
                }
                drawRect(Brush.verticalGradient(0f to Color.Transparent,0.72f to Color.Transparent,1f to Color(0x263D493F).copy(alpha=strength)))
            }
            if("vignette" in active || theme=="darkwood" || theme=="haunted")
                drawRect(Brush.radialGradient(listOf(Color.Transparent,Color(0x22020403).copy(alpha=strength),Color(0x99010302).copy(alpha=strength)),center=Offset(w*0.5f,h*0.48f),radius=maxOf(w,h)*0.78f))
            if("floating_dust" in active) for(i in 0..35) {
                val x=(i*83f+sin((drift*2f*PI+i).toFloat())*w*0.012f)%w
                val y=(i*47f+h*0.12f)%h
                drawCircle(Color(0x77C7C6A4).copy(alpha=strength),radius=if(i%5==0)2.2f else 1.1f,center=Offset(x,y))
            }
        }
    }
}
