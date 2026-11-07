package com.storyforge.app

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File

private val PlayerNight = Color(0xFF05070B)
private val PlayerPanel = Color(0xE9161B25)
private val PlayerGold = Color(0xFFE0C58C)
private val PlayerBlue = Color(0xFF91C8D7)
private val PlayerDanger = Color(0xFFE17D83)

@Composable
internal fun ImmersiveStoryPlayer(project: StoryProject, filesDir: File, onExit: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var sceneId by remember(project.id) { mutableStateOf(project.scenes.firstOrNull()?.id) }
    var values by remember(project.id) { mutableStateOf(project.variables.associate { it.name to it.initialValue }) }
    var history by remember(project.id) { mutableStateOf(listOfNotNull(project.scenes.firstOrNull()?.id)) }
    var revealedChars by remember { mutableIntStateOf(0) }
    var callAnswered by remember { mutableStateOf(false) }
    var soundPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var soundPlaying by remember { mutableStateOf(false) }
    var showGameIntro by remember(project.id) { mutableStateOf(true) }
    val scene = project.scenes.firstOrNull { it.id == sceneId }
    val ended = sceneId == "__END__" || scene == null
    val fullText = scene?.text.orEmpty()
    val sceneImage = scene?.let { s -> project.assets.firstOrNull { it.sceneId == s.id && it.mimeType.startsWith("image/") } }
    val imageBitmap = remember(sceneImage?.relativePath) {
        sceneImage?.let { runCatching { BitmapFactory.decodeFile(File(filesDir, it.relativePath).absolutePath)?.asImageBitmap() }.getOrNull() }
    }
    val sceneAudio = scene?.let { s -> project.assets.firstOrNull { it.sceneId == s.id && it.mimeType.startsWith("audio/") } }
    val coverAsset = project.assets.firstOrNull { it.mimeType.startsWith("image/") }
    val coverBitmap = remember(coverAsset?.relativePath) {
        coverAsset?.let { runCatching { BitmapFactory.decodeFile(File(filesDir, it.relativePath).absolutePath)?.asImageBitmap() }.getOrNull() }
    }
    DisposableEffect(sceneAudio?.relativePath) {
        onDispose {
            soundPlayer?.let { runCatching { if (it.isPlaying) it.stop(); it.release() } }
            soundPlayer = null
            soundPlaying = false
        }
    }
    LaunchedEffect(scene?.id, fullText) {
        revealedChars = 0
        callAnswered = false
        for (index in 1..fullText.length) { revealedChars = index; delay(19) }
    }
    fun moveTo(target: String?) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        val next = when {
            target == "__END__" -> "__END__"
            !target.isNullOrBlank() && project.scenes.any { it.id == target } -> target
            scene != null -> project.scenes.getOrNull(project.scenes.indexOfFirst { it.id == scene.id } + 1)?.id ?: "__END__"
            else -> "__END__"
        }
        sceneId = next
        if (next != "__END__") history = history + next
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF111A28), PlayerNight, Color(0xFF020305))))) {
        if (showGameIntro) {
            if (coverBitmap != null) Image(coverBitmap, "غلاف " + project.title,
                Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0xCC03050A), Color(0x99101825), Color(0xF805070B)))))
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("✦", color = PlayerGold, fontSize = 48.sp)
                Text(project.title, color = PlayerGold, fontSize = 29.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(project.description.ifBlank { "هناك حكاية تنتظر أن تُكتشف… لكن ليس كل سر يريد أن يُعرف." },
                    color = Color(0xFFE5E8EF), fontSize = 15.sp, lineHeight = 25.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(34.dp))
                Button(onClick = { showGameIntro = false }, modifier = Modifier.fillMaxWidth()) { Text("ابدأ المغامرة  ▶") }
                TextButton(onClick = onExit) { Text("العودة", color = Color.LightGray) }
            }
        } else {
            if (imageBitmap != null) {
                Image(imageBitmap, "مشهد " + (scene?.title ?: ""),
                    Modifier.fillMaxWidth().fillMaxHeight(0.58f), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxWidth().fillMaxHeight(0.76f).background(
                    Brush.verticalGradient(listOf(Color.Transparent, PlayerNight.copy(alpha = 0.3f), PlayerNight))))
            } else Box(Modifier.fillMaxWidth().height(300.dp).background(
                Brush.radialGradient(listOf(Color(0xFF24394D), Color(0xFF101723), PlayerNight))))

            AtmosphereOverlay(effects = scene?.atmosphereEffects.orEmpty(), theme = project.visualTheme, intensity = scene?.atmosphereIntensity ?: 0.72f, modifier = Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 17.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onExit) { Text("✕ خروج", color = Color.White) }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("STORYFORGE • STORY MODE", color = PlayerBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(project.title, color = PlayerGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
                if (!ended && scene != null) {
                    if (project.showSceneNumbers) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المشهد " + (project.scenes.indexOf(scene) + 1) + " / " + project.scenes.size, color = Color.LightGray, fontSize = 11.sp)
                        Text("◆ " + history.size + " خطوة", color = PlayerBlue, fontSize = 11.sp)
                    }
                    if (project.showSceneNumbers) LinearProgressIndicator(
                        progress = { (project.scenes.indexOf(scene) + 1).toFloat() / project.scenes.size.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(8.dp)),
                        color = PlayerGold, trackColor = Color.White.copy(alpha = 0.14f))
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
                        Spacer(Modifier.height(90.dp))
                        Card(Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(containerColor = PlayerPanel), shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))) {
                            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(scene.title, color = PlayerGold, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                Text(fullText.take(revealedChars).ifBlank { if (fullText.isBlank()) "..." else "" },
                                    color = Color(0xFFE8EAF0), fontSize = 16.sp, lineHeight = 27.sp,
                                    modifier = Modifier.clickable { revealedChars = fullText.length })
                                if (revealedChars < fullText.length) Text("اضغط على النص لتسريع ظهوره ▸", color = PlayerBlue, fontSize = 10.sp)
                                scene.dialogues.filter { it.speaker.isNotBlank() || it.text.isNotBlank() }.forEach { line ->
                                    val isCall = line.speaker.contains("مكالمة") || line.speaker.contains("اتصال") || line.speaker.contains("هاتف")
                                    if (isCall && !callAnswered) {
                                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF202B3A)), shape = RoundedCornerShape(16.dp)) {
                                            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("☎", color = PlayerBlue, fontSize = 30.sp)
                                                Text("مكالمة واردة", color = Color.White, fontWeight = FontWeight.Bold)
                                                Text(line.speaker, color = PlayerGold, fontSize = 12.sp)
                                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    Button(onClick = { callAnswered = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF287D58))) { Text("الرد") }
                                                    OutlinedButton(onClick = { callAnswered = true }) { Text("رفض") }
                                                }
                                            }
                                        }
                                    } else if (!isCall || callAnswered) {
                                        Column(Modifier.fillMaxWidth().background(Color(0xFF222B38), RoundedCornerShape(13.dp)).padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                            if (line.speaker.isNotBlank()) Text(line.speaker, color = PlayerGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            if (line.text.isNotBlank()) Text(line.text, color = Color.White, lineHeight = 23.sp)
                                        }
                                    }
                                }
                                if (values.isNotEmpty()) {
                                    Divider(color = Color.White.copy(alpha = 0.12f))
                                    Text(values.entries.joinToString("   •   ") { it.key + ": " + it.value }, color = PlayerBlue, fontSize = 11.sp)
                                }
                                if (sceneAudio != null) {
                                    OutlinedButton(onClick = {
                                        runCatching {
                                            val existing = soundPlayer
                                            if (existing == null) {
                                                val player = MediaPlayer.create(context, Uri.fromFile(File(filesDir, sceneAudio.relativePath))) ?: error("تعذر تشغيل الصوت")
                                                player.isLooping = true; player.start(); soundPlayer = player; soundPlaying = true
                                            } else if (existing.isPlaying) { existing.pause(); soundPlaying = false }
                                            else { existing.start(); soundPlaying = true }
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (soundPlaying) "Ⅱ إيقاف الأجواء الصوتية" else "♫ تشغيل الأجواء الصوتية")
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        if (revealedChars >= fullText.length) {
                            val available = scene.choices.withIndex().filter { (index, choice) ->
                                choice.isNotBlank() && evaluateStoryCondition(scene.choiceConditions.getOrNull(index).orEmpty(), values)
                            }
                            if (available.isNotEmpty()) {
                                Text("ماذا ستفعل؟", color = PlayerGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Spacer(Modifier.height(5.dp))
                                available.forEach { (index, choice) ->
                                    AnimatedVisibility(true, enter = fadeIn() + slideInVertically { it / 3 }) {
                                        OutlinedButton(onClick = {
                                            values = applyStoryEffect(scene.choiceEffects.getOrNull(index).orEmpty(), values)
                                            moveTo(scene.choiceTargets.getOrNull(index))
                                        }, modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp), shape = RoundedCornerShape(14.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, PlayerGold.copy(alpha = 0.55f))) {
                                            Text(choice, Modifier.fillMaxWidth().padding(vertical = 5.dp), textAlign = TextAlign.Start, lineHeight = 21.sp)
                                        }
                                    }
                                }
                            } else if (scene.choices.any { it.isNotBlank() }) {
                                Text("لا يوجد طريق متاح… شيء ما يمنعك من المتابعة.", color = PlayerDanger, fontSize = 13.sp)
                            } else Button(onClick = { moveTo(null) }, modifier = Modifier.fillMaxWidth()) { Text("متابعة  →") }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PlayerPanel).padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("✦", color = PlayerGold, fontSize = 42.sp)
                        Text("انتهت الحكاية", color = PlayerGold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("كل اختيار ترك أثرًا في هذا العالم.", color = Color.LightGray, textAlign = TextAlign.Center)
                        Text("عدد الخطوات: " + history.size, color = PlayerBlue, fontSize = 12.sp)
                        Button(onClick = {
                            sceneId = project.scenes.firstOrNull()?.id
                            values = project.variables.associate { it.name to it.initialValue }
                            history = listOfNotNull(sceneId)
                        }, modifier = Modifier.fillMaxWidth()) { Text("ابدأ من جديد") }
                        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("العودة للاستوديو") }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
