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
                        val trunk=Path().apply {
                            moveTo(x-w*0.010f,baseY)
                            cubicTo(x-w*0.020f,baseY-treeHeight*0.28f,x+sway-w*0.006f,baseY-treeHeight*0.72f,x+sway,baseY-treeHeight)
                            cubicTo(x+sway+w*0.010f,baseY-treeHeight*0.68f,x+w*0.012f,baseY-treeHeight*0.25f,x+w*0.021f,baseY)
                            close()
                        }
                        drawPath(trunk,color)
                        // Broken, curved limbs grow at uneven angles from the trunk.
                        for(b in 0..3) {
                            val by=baseY-treeHeight*(0.25f+b*0.17f)
                            val dir=if((i+b)%2==0)-1f else 1f
                            val limb=Path().apply {
                                moveTo(x+sway*0.45f,by)
                                cubicTo(x+dir*w*0.012f,by-h*0.012f,x+dir*w*0.020f,by-h*0.045f,x+dir*w*(0.030f+(b%2)*0.008f),by-h*(0.065f+(b%2)*0.012f))
                            }
                            drawPath(limb,color,style=androidx.compose.ui.graphics.drawscope.Stroke(width=(row+1)*1.45f,cap=StrokeCap.Round))
                        }
                        val root=Path().apply {
                            moveTo(x,baseY-treeHeight*0.03f)
                            cubicTo(x-w*0.018f,baseY+h*0.012f,x-w*0.025f,baseY+h*0.018f,x-w*0.038f,baseY+h*0.022f)
                            moveTo(x+w*0.003f,baseY-treeHeight*0.02f)
                            cubicTo(x+w*0.018f,baseY+h*0.012f,x+w*0.027f,baseY+h*0.015f,x+w*0.040f,baseY+h*0.019f)
                        }
                        drawPath(root,color.copy(alpha=alpha*0.72f),style=androidx.compose.ui.graphics.drawscope.Stroke(width=(row+1)*1.2f,cap=StrokeCap.Round))
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
            if ("dead_forest" in active || "branches" in active) {
                // Foreground roots flex slowly at a different speed from the distant tree line.
                for (i in 0..6) {
                    val anchorX = w * (i / 6f)
                    val wave = sin((drift * 2f * PI + i * 1.31f).toFloat())
                    val reach = w * (0.045f + (i % 3) * 0.018f)
                    val root = Path().apply {
                        moveTo(anchorX, h * (0.91f + (i % 2) * 0.035f))
                        cubicTo(anchorX + reach * (if (i % 2 == 0) -0.25f else 0.25f), h * 0.86f,
                            anchorX + reach * (0.42f + wave * 0.10f), h * 0.94f,
                            anchorX + reach * (0.78f + wave * 0.18f), h * 0.985f)
                    }
                    drawPath(root, Color(0xFF171D16).copy(alpha = 0.72f * strength),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f + (i % 3) * 0.8f, cap = StrokeCap.Round))
                    val hairline = Path().apply {
                        moveTo(anchorX + 1.5f, h * 0.925f)
                        quadraticTo(anchorX + reach * 0.42f, h * 0.94f,
                            anchorX + reach * (0.70f + wave * 0.14f), h * 0.975f)
                    }
                    drawPath(hairline, Color(0xFF414638).copy(alpha = 0.32f * strength),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.75f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
