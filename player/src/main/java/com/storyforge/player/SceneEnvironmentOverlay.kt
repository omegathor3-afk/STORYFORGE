package com.storyforge.player

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
    Canvas(modifier.fillMaxSize()) {
        props.filter { it.foreground == foreground }.forEachIndexed { index, prop ->
            val x=size.width*prop.x.coerceIn(0f,1f); val y=size.height*prop.y.coerceIn(0f,1f)
            val u=(size.width*0.12f*prop.scale.coerceIn(0.4f,2.2f)).coerceAtLeast(12f)
            val dark=if(foreground) Color(0xEE080B09) else Color(0xBB171D18)
            when(prop.type) {
                "dead_tree" -> {
                    drawLine(dark,Offset(x,y+u*1.6f),Offset(x-u*0.08f,y-u*1.8f),u*0.22f,cap=StrokeCap.Round)
                    for(b in 0..3){val by=y-u*(0.45f+b*0.38f);val d=if(b%2==0)-1f else 1f;drawLine(dark,Offset(x,by),Offset(x+d*u*(0.65f+b*0.12f),by-u*0.55f),u*0.09f,cap=StrokeCap.Round)}
                }
                "stump" -> { drawLine(dark,Offset(x,y),Offset(x,y+u*0.55f),u*0.42f,cap=StrokeCap.Round);drawOval(Color(0xFF514B3C),Offset(x-u*0.22f,y-u*0.1f),Size(u*0.44f,u*0.18f)) }
                "rocks" -> { val path=Path().apply{moveTo(x-u*0.65f,y);lineTo(x-u*0.4f,y-u*0.55f);lineTo(x+u*0.15f,y-u*0.7f);lineTo(x+u*0.68f,y-u*0.15f);lineTo(x+u*0.55f,y+u*0.05f);close()};drawPath(path,Color(0xFF303832)) }
                "fungus" -> { drawLine(Color(0xFF536344),Offset(x,y),Offset(x,y-u*0.6f),u*0.1f);drawCircle(Color(0xFF9EBD72).copy(alpha=0.45f),u*0.34f,Offset(x,y-u*0.68f));drawCircle(Color(0xFFBDD98A),u*0.12f,Offset(x,y-u*0.68f)) }
                "broken_fence" -> { for(i in 0..2)drawLine(dark,Offset(x-u*0.65f+i*u*0.55f,y-u*0.6f),Offset(x-u*0.65f+i*u*0.55f,y+u*0.45f),u*0.1f);drawLine(dark,Offset(x-u*0.8f,y-u*0.25f),Offset(x+u*0.8f,y-u*0.05f),u*0.1f);drawLine(dark,Offset(x-u*0.8f,y+u*0.18f),Offset(x+u*0.8f,y+u*0.05f),u*0.08f) }
                "lantern" -> { drawLine(Color(0xFF403A2D),Offset(x,y-u*0.55f),Offset(x,y-u*0.25f),u*0.07f);drawRoundRect(Color(0xFF292A25),Offset(x-u*0.22f,y-u*0.25f),Size(u*0.44f,u*0.58f),CornerRadius(u*0.08f));drawCircle(Color(0xFFFFC66D).copy(alpha=0.35f),u*0.55f,Offset(x,y+u*0.02f));drawCircle(Color(0xFFFFD28A),u*0.12f,Offset(x,y+u*0.02f)) }
                "bones" -> { drawLine(Color(0xFFB4AD91),Offset(x-u*0.55f,y),Offset(x+u*0.55f,y+u*0.1f),u*0.12f,cap=StrokeCap.Round);drawCircle(Color(0xFFB4AD91),u*0.16f,Offset(x-u*0.58f,y));drawCircle(Color(0xFFB4AD91),u*0.16f,Offset(x+u*0.58f,y+u*0.1f)) }
                "hanging_roots" -> for(i in 0..4){val xx=x-u*0.55f+i*u*0.28f;val path=Path().apply{moveTo(xx,y-u);cubicTo(xx-u*0.12f,y-u*0.3f,xx+u*0.18f,y+u*0.1f,xx+sin(index+i.toFloat())*u*0.08f,y+u*0.7f)};drawPath(path,Color(0xFF353D2E),style=Stroke(width=u*0.07f,cap=StrokeCap.Round))}
                "shrine" -> { drawRect(dark,Offset(x-u*0.45f,y-u*0.65f),Size(u*0.9f,u*0.8f));drawLine(Color(0xFF111511),Offset(x-u*0.55f,y-u*0.68f),Offset(x,y-u*1.15f),u*0.13f);drawLine(Color(0xFF111511),Offset(x,y-u*1.15f),Offset(x+u*0.55f,y-u*0.68f),u*0.13f) }
                "crates" -> { drawRect(Color(0xFF40372A),Offset(x-u*0.45f,y-u*0.45f),Size(u*0.9f,u*0.8f));drawLine(Color(0xFF756044),Offset(x-u*0.4f,y-u*0.4f),Offset(x+u*0.4f,y+u*0.3f),u*0.07f);drawLine(Color(0xFF756044),Offset(x+u*0.4f,y-u*0.4f),Offset(x-u*0.4f,y+u*0.3f),u*0.07f) }
            }
        }
    }
}
