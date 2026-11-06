package com.storyforge.app
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFD6B477),
                    background = Color(0xFF090B0E),
                    surface = Color(0xFF171B20)
                )
            ) {
                var title by remember { mutableStateOf("") }
                var body by remember { mutableStateOf("") }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090B0E)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("STORYFORGE", color = Color(0xFFD6B477), fontSize = 28.sp)
                        Text("اصنع عوالمك… واترك القصة تتكلم", color = Color.LightGray)
                        OutlinedTextField(
                            title, { title = it },
                            label = { Text("عنوان القصة") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            body, { body = it },
                            label = { Text("بداية الحكاية") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5
                        )
                        Button(
                            onClick = {
                                title = title.ifBlank { "قصة جديدة" }
                                body = body.ifBlank { "ابدأ كتابة حكايتك هنا…" }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إنشاء مسودة")
                        }
                        Text("المسودة: $title", color = Color.White)
                    }
                }
            }
        }
    }
}
