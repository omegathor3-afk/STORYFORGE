package com.storyforge.academy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class AcademyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFD6B477),
                background = Color(0xFF090B0E),
                surface = Color(0xFF171C22)
            )) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF090B0E))
                        .statusBarsPadding().navigationBarsPadding()
                        .verticalScroll(rememberScrollState()).padding(18.dp)
                ) {
                    Text("STORYFORGE • LEARN", color = Color(0xFF91C8D7))
                    Spacer(Modifier.height(12.dp))
                    LearningCenter()
                }
            }
        }
    }
}
