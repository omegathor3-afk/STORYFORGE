package com.storyforge.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
internal fun StoryForgeIntro(onFinished: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(1850)
        onFinished()
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF090511), Color(0xFF21103C), Color(0xFF05070B)))),
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(17.dp)) {
            AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 3 }) {
                Image(painterResource(R.drawable.ic_storyforge), "STORYFORGE",
                    Modifier.size(132.dp))
            }
            AnimatedVisibility(visible, enter = fadeIn()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("STORYFORGE", color = Color(0xFFE4C8FF), fontSize = 30.sp, fontWeight = FontWeight.Black)
                    Text("اصنع عوالمك… واترك القصة تتكلم", color = Color(0xFFD6B477), fontSize = 14.sp)
                }
            }
            LinearProgressIndicator(
                progress = { if (visible) 0.78f else 0.08f },
                modifier = Modifier.width(175.dp).height(3.dp),
                color = Color(0xFFB86CFF), trackColor = Color.White.copy(alpha = 0.12f)
            )
            Text("A WORLD BEYOND WORDS", color = Color(0xFF9BA4AE), fontSize = 9.sp, letterSpacing = 2.sp)
        }
    }
}
