package com.storyforge.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Ink = Color(0xFF090B0E)
private val Panel = Color(0xFF171C22)
private val Gold = Color(0xFFD6B477)
private val Muted = Color(0xFF9BA4AE)

data class StoryScene(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "مشهد جديد",
    val text: String = "",
    val choices: List<String> = listOf("", "", ""),
    val choiceTargets: List<String?> = emptyList()
)

data class StoryProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val scenes: List<StoryScene> = listOf(StoryScene()),
    val characters: List<String> = emptyList()
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("storyforge_local", Context.MODE_PRIVATE)
        setContent {
            var projects by remember { mutableStateOf(loadProjects(prefs)) }
            var selectedId by remember { mutableStateOf<String?>(null) }
            var selectedSceneId by remember { mutableStateOf<String?>(null) }
            var page by remember { mutableIntStateOf(0) }
            var showNewProject by remember { mutableStateOf(false) }
            var newTitle by remember { mutableStateOf("") }
            var newDescription by remember { mutableStateOf("") }
            var showCharacterDialog by remember { mutableStateOf(false) }
            var characterName by remember { mutableStateOf("") }
            var showBackup by remember { mutableStateOf(false) }
            var backupText by remember { mutableStateOf("") }
            var importText by remember { mutableStateOf("") }
            var previewIndex by remember { mutableIntStateOf(0) }
            var previewSceneId by remember { mutableStateOf<String?>(null) }

            fun persist(next: List<StoryProject>) {
                projects = next
                prefs.edit().putString("projects_json", encodeProjects(next)).apply()
            }
            fun updateProject(id: String, transform: (StoryProject) -> StoryProject) {
                persist(projects.map { if (it.id == id) transform(it) else it })
            }
            val current = projects.firstOrNull { it.id == selectedId }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, secondary = Color(0xFF8CBAA5),
                background = Ink, surface = Panel, onBackground = Color.White,
                onSurface = Color(0xFFE8E4DC)
            )) {
                Scaffold(
                    containerColor = Ink,
                    bottomBar = {
                        NavigationBar(containerColor = Panel, contentColor = Gold) {
                            listOf("المكتبة", "الاستوديو", "المعاينة").forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = page == i,
                                    onClick = { page = i },
                                    icon = { Text(listOf("▤", "✎", "▷")[i], fontSize = 20.sp) },
                                    label = { Text(label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Gold, selectedTextColor = Gold,
                                        indicatorColor = Color(0xFF34302A),
                                        unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    Column(
                        Modifier.fillMaxSize().padding(padding).background(Ink)
                            .verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("STORYFORGE", color = Gold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                Text("ورشة صناعة الحكايات", color = Muted, fontSize = 12.sp)
                            }
                            Text("OFFLINE", color = Color(0xFF8CBAA5), fontSize = 11.sp)
                        }
                        when (page) {
                            0 -> {
                                Text("مكتبة القصص", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("مشاريعك محفوظة على هذا الجهاز تلقائيًا.", color = Muted)
                                Button(onClick = { showNewProject = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("＋ إنشاء مشروع جديد")
                                }
                                if (projects.isEmpty()) EmptyPanel("لا توجد قصص بعد", "ابدأ مشروعك الأول من الزر بالأعلى.")
                                projects.forEach { project ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedId = project.id
                                            selectedSceneId = project.scenes.firstOrNull()?.id
                                            page = 1
                                        },
                                        colors = CardDefaults.cardColors(containerColor = Panel),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Text(project.title, color = Gold, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                            Text(project.description.ifBlank { "لا يوجد وصف بعد." }, color = Muted)
                                            Text("${project.scenes.size} مشاهد  •  ${project.characters.size} شخصيات",
                                                color = Color(0xFF8CBAA5), fontSize = 12.sp)
                                            Text("فتح الاستوديو ←", color = Gold, fontSize = 13.sp)
                                        }
                                    }
                                }
                                OutlinedButton(onClick = {
                                    backupText = encodeProjects(projects)
                                    showBackup = true
                                }, modifier = Modifier.fillMaxWidth()) { Text("نسخ احتياطي / استعادة JSON") }
                            }
                            1 -> {
                                if (current == null) {
                                    EmptyPanel("اختر مشروعًا أولًا", "افتح مشروعًا من المكتبة أو أنشئ مشروعًا جديدًا.")
                                    OutlinedButton(onClick = { page = 0 }) { Text("العودة للمكتبة") }
                                } else {
                                    Text(current.title, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("محرر القصة", color = Muted)
                                    FieldLabel("عنوان المشروع")
                                    var projectTitle by remember(current.id, current.title) { mutableStateOf(current.title) }
                                    OutlinedTextField(projectTitle, { projectTitle = it; updateProject(current.id) { p -> p.copy(title = it) } },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    FieldLabel("ملخص العالم / فكرة القصة")
                                    var description by remember(current.id, current.description) { mutableStateOf(current.description) }
                                    OutlinedTextField(description, { description = it; updateProject(current.id) { p -> p.copy(description = it) } },
                                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                                    Divider(color = Color(0xFF343B43))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("المشاهد", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = {
                                            val scene = StoryScene(title = "مشهد ${current.scenes.size + 1}")
                                            updateProject(current.id) { it.copy(scenes = it.scenes + scene) }
                                            selectedSceneId = scene.id
                                        }) { Text("＋ إضافة مشهد") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        current.scenes.forEachIndexed { index, scene ->
                                            FilterChip(
                                                selected = selectedSceneId == scene.id,
                                                onClick = { selectedSceneId = scene.id },
                                                label = { Text("${index + 1}. ${scene.title.take(12)}") }
                                            )
                                        }
                                    }
                                    val scene = current.scenes.firstOrNull { it.id == selectedSceneId }
                                        ?: current.scenes.firstOrNull()
                                    if (scene != null) {
                                        FieldLabel("اسم المشهد")
                                        OutlinedTextField(scene.title, { value ->
                                            updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { if (it.id == scene.id) it.copy(title = value) else it }) }
                                        }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                                        FieldLabel("نص المشهد")
                                        OutlinedTextField(scene.text, { value ->
                                            updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { if (it.id == scene.id) it.copy(text = value) else it }) }
                                        }, modifier = Modifier.fillMaxWidth(), minLines = 6,
                                            placeholder = { Text("اكتب وصف المكان والأحداث والحوار...") })
                                        FieldLabel("اختيارات اللاعب")
                                        scene.choices.forEachIndexed { index, choice ->
                                            OutlinedTextField(choice, { value ->
                                                updateProject(current.id) { p ->
                                                    p.copy(scenes = p.scenes.map { s ->
                                                        if (s.id == scene.id) s.copy(choices = s.choices.toMutableList().also { it[index] = value }) else s
                                                    })
                                                }
                                            }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                                label = { Text("الاختيار ${index + 1}") })
                                            val target = scene.choiceTargets.getOrNull(index)
                                            val targetLabel = when (target) {
                                                "__END__" -> "نهاية القصة"
                                                null -> "المشهد التالي تلقائيًا"
                                                else -> current.scenes.firstOrNull { it.id == target }?.title ?: "وجهة غير موجودة"
                                            }
                                            TextButton(onClick = {
                                                val destinations = current.scenes.map { it.id } + "__END__"
                                                val next = if (target == null) destinations.first()
                                                    else destinations[(destinations.indexOf(target).coerceAtLeast(-1) + 1) % destinations.size]
                                                updateProject(current.id) { p ->
                                                    p.copy(scenes = p.scenes.map { s ->
                                                        if (s.id == scene.id) {
                                                            val targets = s.choiceTargets.toMutableList()
                                                            while (targets.size < s.choices.size) targets.add(null)
                                                            targets[index] = next
                                                            s.copy(choiceTargets = targets)
                                                        } else s
                                                    })
                                                }
                                            }) { Text("الوجهة: $targetLabel  ↻", color = Gold, fontSize = 12.sp) }
                                        }
                                        TextButton(onClick = {
                                            updateProject(current.id) { p ->
                                                p.copy(scenes = p.scenes.map { s ->
                                                    if (s.id == scene.id) s.copy(choices = s.choices + "") else s
                                                })
                                            }
                                        }) { Text("＋ إضافة اختيار") }
                                        if (current.scenes.size > 1) {
                                            TextButton(onClick = {
                                                val remaining = current.scenes.filterNot { it.id == scene.id }
                                                updateProject(current.id) { it.copy(scenes = remaining) }
                                                selectedSceneId = remaining.first().id
                                            }) { Text("حذف هذا المشهد", color = Color(0xFFE18B83)) }
                                        }
                                    }
                                    Divider(color = Color(0xFF343B43))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("الشخصيات", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = { showCharacterDialog = true }) { Text("＋ إضافة") }
                                    }
                                    if (current.characters.isEmpty()) Text("لم تضف شخصيات بعد.", color = Muted)
                                    current.characters.forEach { name ->
                                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("♙  $name")
                                            TextButton(onClick = { updateProject(current.id) { p -> p.copy(characters = p.characters - name) } }) {
                                                Text("حذف", color = Color(0xFFE18B83))
                                            }
                                        }
                                    }
                                    Text("يتم الحفظ تلقائيًا على جهازك عند كل تعديل.", color = Color(0xFF8CBAA5), fontSize = 12.sp)
                                }
                            }
                            else -> {
                                if (current == null) {
                                    EmptyPanel("لا توجد قصة للمعاينة", "اختر مشروعًا من المكتبة أولًا.")
                                    OutlinedButton(onClick = { page = 0 }) { Text("فتح المكتبة") }
                                } else if (current.scenes.isEmpty()) {
                                    EmptyPanel("القصة فارغة", "أضف مشهدًا من الاستوديو.")
                                } else {
                                    Text("معاينة تفاعلية", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(current.title, color = Muted)
                                    if (previewSceneId == null || (previewSceneId != "__END__" && current.scenes.none { it.id == previewSceneId })) {
                                        previewSceneId = current.scenes.first().id
                                        previewIndex = 0
                                    }
                                    val safeIndex = if (previewSceneId == "__END__") current.scenes.lastIndex
                                        else current.scenes.indexOfFirst { it.id == previewSceneId }.coerceAtLeast(0)
                                    val previewScene = current.scenes[safeIndex]
                                    if (previewSceneId == "__END__") {
                                        EmptyPanel("انتهت القصة", "وصل اللاعب إلى نهاية هذا المسار.")
                                    } else Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Text("المشهد ${safeIndex + 1} / ${current.scenes.size}", color = Gold, fontSize = 12.sp)
                                            Text(previewScene.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            Text(previewScene.text.ifBlank { "هذا المشهد لا يحتوي على نص بعد." }, lineHeight = 25.sp)
                                            previewScene.choices.forEachIndexed { choiceIndex, choice ->
                                                if (choice.isNotBlank()) OutlinedButton(onClick = {
                                                    val target = previewScene.choiceTargets.getOrNull(choiceIndex)
                                                    when {
                                                        target == "__END__" -> { previewSceneId = "__END__"; previewIndex = current.scenes.size }
                                                        target != null && current.scenes.any { it.id == target } -> {
                                                            previewSceneId = target
                                                            previewIndex = current.scenes.indexOfFirst { it.id == target }
                                                        }
                                                        previewIndex < current.scenes.lastIndex -> {
                                                            previewIndex++
                                                            previewSceneId = current.scenes[previewIndex].id
                                                        }
                                                        else -> { previewSceneId = "__END__"; previewIndex = current.scenes.size }
                                                    }
                                                }, modifier = Modifier.fillMaxWidth()) { Text(choice) }
                                            }
                                            if (previewScene.choices.none { it.isNotBlank() }) {
                                                Button(onClick = {
                                                    if (previewIndex < current.scenes.lastIndex) {
                                                        previewIndex++
                                                        previewSceneId = current.scenes[previewIndex].id
                                                    } else {
                                                        previewIndex = current.scenes.size
                                                        previewSceneId = "__END__"
                                                    }
                                                }, modifier = Modifier.fillMaxWidth()) {
                                                    Text(if (previewIndex < current.scenes.lastIndex) "متابعة" else "إنهاء القصة")
                                                }
                                            }
                                            if (previewIndex >= current.scenes.lastIndex) Text("وصلت إلى آخر مشهد في النسخة الحالية.", color = Gold)
                                        }
                                    }
                                    OutlinedButton(onClick = {
                                        previewIndex = 0
                                        previewSceneId = current.scenes.firstOrNull()?.id
                                    }, modifier = Modifier.fillMaxWidth()) { Text("إعادة من البداية") }
                                    Text("كل اختيار يمكن توجيهه إلى مشهد مستقل أو إنهاء القصة. الوجهة التلقائية تتابع للمشهد التالي.", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (showNewProject) {
                AlertDialog(
                    onDismissRequest = { showNewProject = false },
                    title = { Text("مشروع جديد") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(newTitle, { newTitle = it }, label = { Text("اسم القصة") }, singleLine = true)
                            OutlinedTextField(newDescription, { newDescription = it }, label = { Text("وصف مختصر") }, minLines = 2)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val project = StoryProject(title = newTitle.ifBlank { "قصة بلا عنوان" }, description = newDescription)
                            persist(projects + project)
                            selectedId = project.id
                            selectedSceneId = project.scenes.first().id
                            page = 1
                            newTitle = ""; newDescription = ""; showNewProject = false
                        }) { Text("إنشاء") }
                    },
                    dismissButton = { TextButton(onClick = { showNewProject = false }) { Text("إلغاء") } }
                )
            }
            if (showCharacterDialog) {
                AlertDialog(
                    onDismissRequest = { showCharacterDialog = false },
                    title = { Text("إضافة شخصية") },
                    text = { OutlinedTextField(characterName, { characterName = it }, label = { Text("اسم الشخصية") }) },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedId?.let { id -> updateProject(id) { p -> if (characterName.isBlank()) p else p.copy(characters = p.characters + characterName.trim()) } }
                            characterName = ""; showCharacterDialog = false
                        }) { Text("إضافة") }
                    },
                    dismissButton = { TextButton(onClick = { showCharacterDialog = false }) { Text("إلغاء") } }
                )
            }
            if (showBackup) {
                AlertDialog(
                    onDismissRequest = { showBackup = false },
                    title = { Text("النسخ الاحتياطي والاستعادة") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("انسخ بيانات مشاريعك بصيغة JSON واحتفظ بها. لاستعادة نسخة، الصق محتواها هنا ثم اضغط استعادة.", fontSize = 13.sp)
                            OutlinedTextField(backupText, { backupText = it }, label = { Text("نسخة التصدير") }, minLines = 3)
                            OutlinedTextField(importText, { importText = it }, label = { Text("الصق نسخة للاستعادة") }, minLines = 3)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            runCatching {
                                val restored = decodeProjects(importText)
                                if (restored.isNotEmpty()) persist(restored)
                            }
                            showBackup = false
                        }) { Text("استعادة JSON") }
                    },
                    dismissButton = { TextButton(onClick = { showBackup = false }) { Text("إغلاق") } }
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(value: String) {
    Text(value, color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EmptyPanel(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("✦", color = Gold, fontSize = 30.sp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = Muted)
        }
    }
}

private fun encodeProjects(projects: List<StoryProject>): String {
    val array = JSONArray()
    projects.forEach { project ->
        val p = JSONObject()
        p.put("id", project.id); p.put("title", project.title); p.put("description", project.description)
        val scenes = JSONArray()
        project.scenes.forEach { scene ->
            val s = JSONObject()
            s.put("id", scene.id); s.put("title", scene.title); s.put("text", scene.text)
            val choices = JSONArray(); scene.choices.forEach { choices.put(it) }
            s.put("choices", choices)
            val targets = JSONArray()
            scene.choiceTargets.forEach { targets.put(it ?: JSONObject.NULL) }
            s.put("choiceTargets", targets)
            scenes.put(s)
        }
        val characters = JSONArray(); project.characters.forEach { characters.put(it) }
        p.put("scenes", scenes); p.put("characters", characters); array.put(p)
    }
    return array.toString(2)
}

private fun decodeProjects(json: String): List<StoryProject> {
    val array = JSONArray(json)
    return (0 until array.length()).map { i ->
        val p = array.getJSONObject(i)
        val scenesJson = p.optJSONArray("scenes") ?: JSONArray()
        val scenes = (0 until scenesJson.length()).map { j ->
            val s = scenesJson.getJSONObject(j)
            val choicesJson = s.optJSONArray("choices") ?: JSONArray()
            StoryScene(
                id = s.optString("id", UUID.randomUUID().toString()),
                title = s.optString("title", "مشهد جديد"),
                text = s.optString("text", ""),
                choices = (0 until choicesJson.length()).map { choicesJson.optString(it) },
                choiceTargets = s.optJSONArray("choiceTargets")?.let { targets ->
                    (0 until targets.length()).map { k -> if (targets.isNull(k)) null else targets.optString(k) }
                } ?: emptyList()
            )
        }
        val charsJson = p.optJSONArray("characters") ?: JSONArray()
        StoryProject(
            id = p.optString("id", UUID.randomUUID().toString()),
            title = p.optString("title", "قصة بلا عنوان"),
            description = p.optString("description", ""),
            scenes = scenes.ifEmpty { listOf(StoryScene()) },
            characters = (0 until charsJson.length()).map { charsJson.optString(it) }
        )
    }
}

private fun loadProjects(prefs: android.content.SharedPreferences): List<StoryProject> =
    runCatching { decodeProjects(prefs.getString("projects_json", "[]") ?: "[]") }.getOrDefault(emptyList())
