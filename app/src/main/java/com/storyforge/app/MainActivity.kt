package com.storyforge.app

import android.content.Context
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.ResponseTypeValues
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val Ink = Color(0xFF090B0E)
private val Panel = Color(0xFF171C22)
private val Gold = Color(0xFFD6B477)
private val Muted = Color(0xFF9BA4AE)

data class StoryScene(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "مشهد جديد",
    val text: String = "",
    val choices: List<String> = listOf("", "", ""),
    val choiceTargets: List<String?> = emptyList(),
    val choiceConditions: List<String> = emptyList(),
    val choiceEffects: List<String> = emptyList(),
    val dialogues: List<DialogueLine> = emptyList(),
    val choiceTimerSeconds: Int = 0,
    val timeoutChoiceIndex: Int = 0,
    val atmosphereEffects: List<String> = emptyList(),
    val atmosphereIntensity: Float = 0.72f,
    val environmentProps: List<SceneProp> = emptyList()
)

data class SceneProp(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val x: Float = 0.5f,
    val y: Float = 0.62f,
    val scale: Float = 1f,
    val foreground: Boolean = false
)

data class DialogueLine(
    val id: String = UUID.randomUUID().toString(),
    val speaker: String = "",
    val text: String = ""
)

data class StoryVariable(
    val name: String,
    val initialValue: Int = 0
)

data class StoryAsset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relativePath: String,
    val mimeType: String,
    val sceneId: String? = null
)

data class StoryProject(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val scenes: List<StoryScene> = listOf(StoryScene()),
    val characters: List<String> = emptyList(),
    val variables: List<StoryVariable> = emptyList(),
    val assets: List<StoryAsset> = emptyList(),
    val visualTheme: String = "darkwood",
    val showSceneNumbers: Boolean = true
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("storyforge_local", Context.MODE_PRIVATE)
        setContent {
            var projects by remember { mutableStateOf(loadProjects(prefs)) }
            val undoStack = remember { mutableStateListOf<List<StoryProject>>() }
            val redoStack = remember { mutableStateListOf<List<StoryProject>>() }
            var selectedId by remember { mutableStateOf<String?>(null) }
            var selectedSceneId by remember { mutableStateOf<String?>(null) }
            var page by remember { mutableIntStateOf(0) }
            var showNewProject by remember { mutableStateOf(false) }
            var librarySearch by remember { mutableStateOf("") }
            var deleteProjectId by remember { mutableStateOf<String?>(null) }
            var newTitle by remember { mutableStateOf("") }
            var newDescription by remember { mutableStateOf("") }
            var newTemplate by remember { mutableStateOf("رعب") }
            var showCharacterDialog by remember { mutableStateOf(false) }
            var showVariableDialog by remember { mutableStateOf(false) }
            var characterName by remember { mutableStateOf("") }
            var variableName by remember { mutableStateOf("") }
            var variableValue by remember { mutableStateOf("0") }
            var showBackup by remember { mutableStateOf(false) }
            var backupText by remember { mutableStateOf("") }
            var importText by remember { mutableStateOf("") }
            var previewIndex by remember { mutableIntStateOf(0) }
            var previewSceneId by remember { mutableStateOf<String?>(null) }
            var previewValues by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
            var showImmersivePlayer by remember { mutableStateOf(false) }
            var showBrandIntro by remember { mutableStateOf(true) }
            var cloudApiBaseUrl by remember { mutableStateOf(prefs.getString("cloud_api_base_url", "") ?: "") }
            var cloudOidcIssuer by remember { mutableStateOf(prefs.getString("cloud_oidc_issuer", "") ?: "") }
            var cloudOidcClientId by remember { mutableStateOf(prefs.getString("cloud_oidc_client_id", "") ?: "") }
            var cloudOidcAudience by remember { mutableStateOf(prefs.getString("cloud_oidc_audience", "") ?: "") }
            var cloudAccessToken by remember { mutableStateOf<String?>(null) }
            var cloudJobId by remember { mutableStateOf(prefs.getString("cloud_job_id", null)) }
            var cloudJobStatus by remember { mutableStateOf(prefs.getString("cloud_job_status", "queued") ?: "queued") }
            var cloudArtifactAvailable by remember { mutableStateOf(prefs.getBoolean("cloud_artifact_available", false)) }
            var cloudBusy by remember { mutableStateOf(false) }
            var cloudMessage by remember { mutableStateOf("") }
            val cloudAuthService = remember { AuthorizationService(this@MainActivity) }
            DisposableEffect(cloudAuthService) { onDispose { cloudAuthService.dispose() } }
            val cloudAuthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val data = result.data
                val authResponse = data?.let { AuthorizationResponse.fromIntent(it) }
                val authError = data?.let { AuthorizationException.fromIntent(it) }
                if (authResponse != null) {
                    cloudAuthService.performTokenRequest(authResponse.createTokenExchangeRequest()) { tokenResponse, tokenError ->
                        runOnUiThread {
                            if (tokenResponse?.accessToken != null) {
                                cloudAccessToken = tokenResponse.accessToken
                                cloudMessage = "تم تسجيل الدخول بنجاح. يمكنك بدء بناء اللعبة."
                            } else {
                                cloudAccessToken = null
                                cloudMessage = "تعذر إكمال تسجيل الدخول: ${tokenError?.errorDescription ?: "استجابة غير صالحة"}"
                            }
                        }
                    }
                } else if (authError != null) {
                    cloudMessage = if (result.resultCode == android.app.Activity.RESULT_CANCELED) "تم إلغاء تسجيل الدخول." else "فشل تسجيل الدخول: ${authError.errorDescription ?: "خطأ في المصادقة"}"
                }
            }
            fun persist(next: List<StoryProject>, recordHistory: Boolean = true) {
                if (recordHistory && projects != next) {
                    undoStack.add(projects.toList())
                    if (undoStack.size > 40) undoStack.removeAt(0)
                    redoStack.clear()
                }
                projects = next
                prefs.edit().putString("projects_json", encodeProjects(next)).apply()
            }
            fun undoEdit() {
                if (undoStack.isNotEmpty()) {
                    redoStack.add(projects.toList())
                    val previous = undoStack.removeAt(undoStack.lastIndex)
                    projects = previous
                    prefs.edit().putString("projects_json", encodeProjects(previous)).apply()
                    if (selectedId != null && previous.none { it.id == selectedId }) {
                        selectedId = null; selectedSceneId = null; page = 0
                    }
                }
            }
            fun redoEdit() {
                if (redoStack.isNotEmpty()) {
                    undoStack.add(projects.toList())
                    val next = redoStack.removeAt(redoStack.lastIndex)
                    projects = next
                    prefs.edit().putString("projects_json", encodeProjects(next)).apply()
                }
            }
            fun updateProject(id: String, transform: (StoryProject) -> StoryProject) {
                persist(projects.map { if (it.id == id) transform(it) else it })
            }
            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(encodeProjects(projects).toByteArray(Charsets.UTF_8)) }
                    Toast.makeText(this@MainActivity, "تم تصدير نسخة STORYFORGE", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this@MainActivity, "تعذر تصدير النسخة", Toast.LENGTH_SHORT).show() }
            }
            val imageAssetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null && selectedId != null) runCatching {
                    val projectId = selectedId!!
                    val mime = contentResolver.getType(uri) ?: "image/*"
                    val ext = when (mime.substringAfter("/", "jpg").lowercase(Locale.ROOT)) {
                        "jpeg" -> "jpg"; "svg+xml" -> "svg"; "png" -> "png"; "webp" -> "webp"; "gif" -> "gif"; else -> "img"
                    }
                    val asset = StoryAsset(name = "صورة ${projects.firstOrNull { it.id == projectId }?.assets?.size?.plus(1) ?: 1}",
                        relativePath = "storyforge_assets/$projectId/${UUID.randomUUID()}.$ext", mimeType = mime, sceneId = selectedSceneId)
                    val destination = File(filesDir, asset.relativePath)
                    destination.parentFile?.mkdirs()
                    contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } } ?: error("تعذر قراءة الملف")
                    updateProject(projectId) { it.copy(assets = it.assets + asset) }
                    Toast.makeText(this@MainActivity, "تمت إضافة الصورة وربطها بالمشهد", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this@MainActivity, "تعذر استيراد الصورة", Toast.LENGTH_LONG).show() }
            }
            val audioAssetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null && selectedId != null) runCatching {
                    val projectId = selectedId!!
                    val mime = contentResolver.getType(uri) ?: "audio/*"
                    val ext = when (mime.substringAfter("/", "mp3").lowercase(Locale.ROOT)) {
                        "mpeg" -> "mp3"; "mp4" -> "m4a"; "x-wav" -> "wav"; "ogg" -> "ogg"; "aac" -> "aac"; else -> "audio"
                    }
                    val asset = StoryAsset(name = "صوت ${projects.firstOrNull { it.id == projectId }?.assets?.size?.plus(1) ?: 1}",
                        relativePath = "storyforge_assets/$projectId/${UUID.randomUUID()}.$ext", mimeType = mime, sceneId = selectedSceneId)
                    val destination = File(filesDir, asset.relativePath)
                    destination.parentFile?.mkdirs()
                    contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { output -> input.copyTo(output) } } ?: error("تعذر قراءة الملف")
                    updateProject(projectId) { it.copy(assets = it.assets + asset) }
                    Toast.makeText(this@MainActivity, "تمت إضافة الملف الصوتي وربطه بالمشهد", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this@MainActivity, "تعذر استيراد الملف الصوتي", Toast.LENGTH_LONG).show() }
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runCatching {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Empty file")
                    val restored = decodeProjects(json)
                    require(restored.isNotEmpty()) { "No projects found" }
                    persist(restored)
                    Toast.makeText(this@MainActivity, "تم استيراد ${restored.size} مشروع", Toast.LENGTH_SHORT).show()
                }.onFailure { Toast.makeText(this@MainActivity, "ملف JSON غير صالح أو تعذر قراءته", Toast.LENGTH_LONG).show() }
            }

            val gamePackageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri != null) runCatching {
                    val project = projects.firstOrNull { it.id == selectedId } ?: error("اختر قصة أولاً")
                    contentResolver.openOutputStream(uri)?.use { output ->
                        writeStandaloneStoryPackage(output, project, filesDir)
                    } ?: error("تعذر إنشاء حزمة اللعبة")
                    Toast.makeText(this@MainActivity, "تم تصدير حزمة اللعبة المستقلة مع الصور والصوت", Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(this@MainActivity, "فشل تصدير اللعبة: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
            }
            val zipExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                if (uri != null) runCatching {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        writeStoryZipBackup(output, projects, filesDir)
                    } ?: error("تعذر فتح ملف النسخة الاحتياطية")
                    Toast.makeText(this@MainActivity, "تم إنشاء نسخة ZIP كاملة بالصور والصوت", Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(this@MainActivity, "فشل إنشاء النسخة الاحتياطية: ${it.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show() }
            }
            val zipImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) runCatching {
                    val restored = contentResolver.openInputStream(uri)?.use { input ->
                        restoreStoryZipBackup(input, filesDir)
                    } ?: error("تعذر قراءة ملف ZIP")
                    require(restored.isNotEmpty()) { "النسخة لا تحتوي على مشاريع" }
                    persist(restored)
                    selectedId = restored.firstOrNull()?.id
                    selectedSceneId = restored.firstOrNull()?.scenes?.firstOrNull()?.id
                    page = 0
                    Toast.makeText(this@MainActivity, "تم استرجاع ${restored.size} مشروع مع ملفات الوسائط", Toast.LENGTH_LONG).show()
                }.onFailure { Toast.makeText(this@MainActivity, "تعذر استعادة ZIP: ${it.message ?: "ملف غير صالح"}", Toast.LENGTH_LONG).show() }
            }

            val current = projects.firstOrNull { it.id == selectedId }
            val visibleProjects = projects.filter { project ->
                librarySearch.isBlank() || project.title.contains(librarySearch.trim(), ignoreCase = true) ||
                    project.description.contains(librarySearch.trim(), ignoreCase = true)
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, secondary = Color(0xFF8CBAA5),
                background = Ink, surface = Panel, onBackground = Color.White,
                onSurface = Color(0xFFE8E4DC)
            )) {
                if (showBrandIntro) androidx.compose.ui.window.Dialog(onDismissRequest = {}, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                    StoryForgeIntro(onFinished = { showBrandIntro = false })
                }
                Scaffold(
                    containerColor = Ink,
                    bottomBar = {
                        NavigationBar(containerColor = Panel, contentColor = Gold) {
                            listOf("المكتبة", "الاستوديو", "المعاينة", "التعلّم", "بناء APK").forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = page == i,
                                    onClick = { page = i },
                                    icon = { Text(listOf("▤", "✎", "▷", "؟", "⬇")[i], fontSize = 20.sp) },
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
                                OutlinedTextField(
                                    value = librarySearch,
                                    onValueChange = { librarySearch = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("ابحث في قصصك") },
                                    placeholder = { Text("اكتب اسم المشروع أو وصفه...") },
                                    trailingIcon = {
                                        if (librarySearch.isNotBlank()) TextButton(onClick = { librarySearch = "" }) { Text("مسح") }
                                    }
                                )
                                Text("${visibleProjects.size} من ${projects.size} مشروع", color = Muted, fontSize = 11.sp)
                                if (projects.isEmpty()) EmptyPanel("لا توجد قصص بعد", "ابدأ مشروعك الأول من الزر بالأعلى.")
                                else if (visibleProjects.isEmpty()) EmptyPanel("لا توجد نتائج", "جرّب كلمة بحث مختلفة أو امسح البحث.")
                                visibleProjects.forEach { project ->
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
                                            TextButton(onClick = { deleteProjectId = project.id }) {
                                                Text("حذف المشروع", color = Color(0xFFE18B83), fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                                Button(onClick = { zipExportLauncher.launch("STORYFORGE-full-backup.zip") },
                                    modifier = Modifier.fillMaxWidth()) { Text("📦 نسخة كاملة ZIP (القصص + الصور + الصوت)") }
                                OutlinedButton(onClick = { zipImportLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                                    modifier = Modifier.fillMaxWidth()) { Text("استعادة نسخة ZIP كاملة") }
                                Button(onClick = { exportLauncher.launch("storyforge-backup.json") },
                                    modifier = Modifier.fillMaxWidth()) { Text("تصدير نسخة احتياطية إلى ملف") }
                                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                                    modifier = Modifier.fillMaxWidth()) { Text("استيراد نسخة JSON من ملف") }
                                TextButton(onClick = {
                                    backupText = encodeProjects(projects)
                                    showBackup = true
                                }, modifier = Modifier.fillMaxWidth()) { Text("أدوات JSON اليدوية") }
                            }
                            1 -> {
                                if (current == null) {
                                    EmptyPanel("اختر مشروعًا أولًا", "افتح مشروعًا من المكتبة أو أنشئ مشروعًا جديدًا.")
                                    OutlinedButton(onClick = { page = 0 }) { Text("العودة للمكتبة") }
                                } else {
                                    Text(current.title, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    val storyIssues = diagnoseStory(current)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Panel),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Text("فحص القصة", color = Gold, fontWeight = FontWeight.Bold)
                                                Text("${storyIssues.count { it.isError }} أخطاء • ${storyIssues.count { !it.isError }} تنبيهات",
                                                    color = if (storyIssues.any { it.isError }) Color(0xFFE18B83) else Color(0xFF8CBAA5),
                                                    fontSize = 11.sp)
                                            }
                                            if (storyIssues.isEmpty()) {
                                                Text("✓ بنية القصة سليمة مبدئيًا.", color = Color(0xFF8CBAA5), fontSize = 12.sp)
                                            } else {
                                                storyIssues.take(10).forEach { issue ->
                                                    Text(
                                                        "${if (issue.isError) "● خطأ" else "● تنبيه"}: ${issue.message}",
                                                        color = if (issue.isError) Color(0xFFE18B83) else Muted,
                                                        fontSize = 12.sp,
                                                        modifier = if (issue.sceneId != null) Modifier.clickable { selectedSceneId = issue.sceneId } else Modifier
                                                    )
                                                }
                                                if (storyIssues.size > 10) {
                                                    Text("هناك ${storyIssues.size - 10} ملاحظات أخرى.", color = Muted, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { undoEdit() }, enabled = undoStack.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                            Text("↶ تراجع")
                                        }
                                        OutlinedButton(onClick = { redoEdit() }, enabled = redoStack.isNotEmpty(), modifier = Modifier.weight(1f)) {
                                            Text("↷ إعادة")
                                        }
                                    }
                                    Text("تراجع وإعادة حتى 40 خطوة تحرير.", color = Muted, fontSize = 11.sp)
                                    Text("محرر القصة", color = Muted)
                                    FieldLabel("عنوان المشروع")
                                    var projectTitle by remember(current.id, current.title) { mutableStateOf(current.title) }
                                    OutlinedTextField(projectTitle, { projectTitle = it; updateProject(current.id) { p -> p.copy(title = it) } },
                                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                                    FieldLabel("ملخص العالم / فكرة القصة")
                                    var description by remember(current.id, current.description) { mutableStateOf(current.description) }
                                    OutlinedTextField(description, { description = it; updateProject(current.id) { p -> p.copy(description = it) } },
                                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                                    FieldLabel("هوية العالم البصرية")
                                    var themeMenu by remember(current.id) { mutableStateOf(false) }
                                    val themeNames = listOf("darkwood" to "غابة Darkwood الميتة", "ashen" to "رماد ما بعد الكارثة", "haunted" to "غابة مسكونة", "fungal" to "غابة الفطريات", "bloodmoon" to "ليلة القمر الدموي", "frozen" to "غابة متجمدة")
                                    Box {
                                        OutlinedButton(onClick = { themeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text(themeNames.firstOrNull { it.first == current.visualTheme }?.second ?: "غابة Darkwood الميتة")
                                        }
                                        DropdownMenu(expanded = themeMenu, onDismissRequest = { themeMenu = false }) {
                                            themeNames.forEach { (id, name) -> DropdownMenuItem(text = { Text(name) }, onClick = {
                                                updateProject(current.id) { p -> p.copy(visualTheme = id) }; themeMenu = false
                                            }) }
                                        }
                                    }
                                    Text("الثيم يحدد طبقات الجو والخلفية العامة، ويمكن تخصيص مؤثرات مستقلة لكل مشهد.", color = Muted, fontSize = 11.sp)
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("إظهار أرقام المشاهد للاعب", fontWeight = FontWeight.SemiBold)
                                            Text("أوقفه لإخفاء رقم المشهد ومؤشر التقدم الرقمي أثناء اللعب.", color = Muted, fontSize = 11.sp)
                                        }
                                        Switch(checked = current.showSceneNumbers, onCheckedChange = { enabled ->
                                            updateProject(current.id) { p -> p.copy(showSceneNumbers = enabled) }
                                        })
                                    }
                                    Divider(color = Color(0xFF343B43))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text("متغيرات القصة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = { variableName = ""; variableValue = "0"; showVariableDialog = true }) { Text("＋ متغير") }
                                    }
                                    if (current.variables.isEmpty()) Text("لا توجد متغيرات. أضف مثلًا: ثقة = 0 أو مفاتيح = 1.", color = Muted, fontSize = 12.sp)
                                    current.variables.forEach { variable ->
                                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(variable.name, color = Gold, modifier = Modifier.weight(1f))
                                            OutlinedTextField(variable.initialValue.toString(), { value ->
                                                val parsed = value.toIntOrNull() ?: return@OutlinedTextField
                                                updateProject(current.id) { p -> p.copy(variables = p.variables.map { v ->
                                                    if (v.name == variable.name) v.copy(initialValue = parsed) else v
                                                }) }
                                            }, modifier = Modifier.width(92.dp), singleLine = true,
                                                label = { Text("القيمة") })
                                            TextButton(onClick = { updateProject(current.id) { p -> p.copy(variables = p.variables - variable) } }) {
                                                Text("حذف", color = Color(0xFFE18B83))
                                            }
                                        }
                                    }
                                    Text("استخدم اسم المتغير في شرط الاختيار أو تأثيره، مثل: keys>=1 أو trust+=1.", color = Muted, fontSize = 12.sp)
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
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        current.scenes.forEachIndexed { index, scene ->
                                            FilterChip(
                                                selected = selectedSceneId == scene.id,
                                                onClick = { selectedSceneId = scene.id },
                                                label = { Text("${index + 1}. ${scene.title.take(12)}") }
                                            )
                                        }
                                    }
                                    Divider(color = Color(0xFF343B43))
                                    Text("خريطة تفرعات القصة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("اضغط على أي عقدة لفتح المشهد وتعديله. الأسهم توضّح مسار كل اختيار.",
                                        color = Muted, fontSize = 12.sp)
                                    current.scenes.forEachIndexed { graphIndex, graphScene ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable { selectedSceneId = graphScene.id },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (selectedSceneId == graphScene.id) Color(0xFF292820) else Panel
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                if (selectedSceneId == graphScene.id) 1.5.dp else 0.5.dp,
                                                if (selectedSceneId == graphScene.id) Gold else Color(0xFF343B43)
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${graphIndex + 1}", color = Gold, fontWeight = FontWeight.Bold)
                                                    Spacer(Modifier.width(9.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(graphScene.title.ifBlank { "مشهد بلا عنوان" }, fontWeight = FontWeight.Bold)
                                                        Text("${graphScene.text.length} حرف • ${graphScene.choices.count { it.isNotBlank() }} اختيارات",
                                                            color = Muted, fontSize = 11.sp)
                                                    }
                                                    Text(if (selectedSceneId == graphScene.id) "محدد" else "فتح",
                                                        color = Gold, fontSize = 11.sp)
                                                }
                                                val graphChoices = graphScene.choices.withIndex().filter { it.value.isNotBlank() }
                                                if (graphChoices.isEmpty()) {
                                                    val nextScene = current.scenes.getOrNull(graphIndex + 1)
                                                    Text(
                                                        if (nextScene != null) "↓ متابعة تلقائية إلى: ${nextScene.title}"
                                                        else "↓ نهاية القصة",
                                                        color = Color(0xFF8CBAA5), fontSize = 12.sp
                                                    )
                                                } else {
                                                    graphChoices.forEach { (choiceIndex, choiceText) ->
                                                        val destination = graphScene.choiceTargets.getOrNull(choiceIndex)
                                                        val destinationLabel = when {
                                                            destination == "__END__" -> "نهاية القصة"
                                                            destination == null -> current.scenes.getOrNull(graphIndex + 1)?.title ?: "نهاية القصة"
                                                            current.scenes.any { it.id == destination } ->
                                                                current.scenes.first { it.id == destination }.title
                                                            else -> "وجهة غير موجودة"
                                                        }
                                                        Row(
                                                            Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(8.dp)).padding(9.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text("↳", color = Gold, fontSize = 17.sp)
                                                            Spacer(Modifier.width(7.dp))
                                                            Column(Modifier.weight(1f)) {
                                                                Text(choiceText, fontSize = 12.sp, maxLines = 2)
                                                                val condition = graphScene.choiceConditions.getOrNull(choiceIndex).orEmpty()
                                                                if (condition.isNotBlank()) Text("شرط: $condition", color = Muted, fontSize = 10.sp)
                                                            }
                                                            Text("→ $destinationLabel", color = Color(0xFF8CBAA5), fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (graphIndex < current.scenes.lastIndex) {
                                            Text("│", color = Color(0xFF65736D), modifier = Modifier.padding(start = 18.dp))
                                        }
                                    }
                                    Divider(color = Color(0xFF343B43))
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
                                        FieldLabel("مؤثرات الجو لهذا المشهد")
                                        Text("اختار طبقات بصرية تظهر أثناء اللعب. تقدر تجمع أكتر من مؤثر في نفس الوقت.", color = Muted, fontSize = 11.sp)
                                        Text("تركيبات أجواء جاهزة", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        val atmospherePresets = listOf(
                                            "غابة ميتة" to setOf("dead_forest", "fog", "ground_fog", "branches", "dead_leaves", "vignette"),
                                            "مطر المطاردة" to setOf("heavy_rain", "fog", "black_shadows", "lightning", "crows", "vignette"),
                                            "مستنقع فطري" to setOf("spores", "dense_fog", "ground_fog", "floating_dust", "moon_glow"),
                                            "ليلة القمر" to setOf("moon_glow", "light_shafts", "fireflies", "watching_eyes", "fog", "vignette")
                                        )
                                        atmospherePresets.chunked(2).forEach { presetRow ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                presetRow.forEach { (presetName, presetEffects) ->
                                                    OutlinedButton(
                                                        onClick = {
                                                            updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { item ->
                                                                if (item.id == scene.id) item.copy(atmosphereEffects = presetEffects.toList(), atmosphereIntensity = 0.86f) else item
                                                            }) }
                                                        },
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                                    ) { Text(presetName, fontSize = 11.sp) }
                                                }
                                            }
                                        }
                                        val effectOptions = listOf("rain" to "🌧 مطر", "heavy_rain" to "⛈ مطر غزير", "fog" to "🌫 ضباب", "dense_fog" to "ضباب كثيف", "ash" to "رماد متساقط", "dust" to "غبار", "fireflies" to "يراعات", "spores" to "أبواغ فطرية", "embers" to "جمر متطاير", "snow" to "ثلج", "black_shadows" to "ظلال عابرة", "blood_mist" to "ضباب دموي", "wind" to "رياح", "dead_leaves" to "أوراق ميتة", "branches" to "ظلال أغصان مكسورة", "dead_forest" to "غابة ميتة متعددة الطبقات", "lightning" to "برق بعيد", "crows" to "غربان عابرة", "watching_eyes" to "عيون تراقب من الظلام", "moths" to "عثّ ليلي", "insects" to "حشرات طائرة", "ground_fog" to "ضباب يزحف على الأرض", "distant_glow" to "وهج بعيد بين الأشجار", "distant_forest" to "غابة بعيدة متعددة الطبقات", "moon_glow" to "وهج قمر خافت", "light_shafts" to "أشعة ضوء بين الأشجار", "vignette" to "ظلام يحيط بأطراف المشهد", "floating_dust" to "غبار مضاء في الهواء")
                                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            effectOptions.chunked(2).forEach { rowOptions ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                                    rowOptions.forEach { (effectId, label) ->
                                                        FilterChip(selected = effectId in scene.atmosphereEffects, onClick = {
                                                            updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { item ->
                                                                if (item.id == scene.id) item.copy(atmosphereEffects = if (effectId in item.atmosphereEffects) item.atmosphereEffects - effectId else item.atmosphereEffects + effectId) else item
                                                            }) }
                                                        }, label = { Text(label, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                                                    }
                                                    if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("شدة الأجواء", color = Color(0xFFE0C58C), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("${(scene.atmosphereIntensity * 100).toInt()}%", color = Muted, fontSize = 12.sp)
                                        }
                                        Slider(
                                            value = scene.atmosphereIntensity.coerceIn(0f, 1f),
                                            onValueChange = { intensity ->
                                                updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { item ->
                                                    if (item.id == scene.id) item.copy(atmosphereIntensity = intensity) else item
                                                }) }
                                            },
                                            valueRange = 0f..1f
                                        )
                                        Text("صفر = بدون طبقات جوية، 100٪ = أجواء كثيفة. الإعداد مستقل لكل مشهد.", color = Muted, fontSize = 10.sp)
                                        Divider(color = Color(0xFF343B43))
                                        Text("عناصر البيئة داخل المشهد", color = Gold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text("أضف تفاصيل ثابتة للمكان: جذوع ميتة، صخور، فطر، فوانيس أو سياج محطّم. كل عنصر له مكان وحجم وطبقة.", color = Muted, fontSize = 11.sp)
                                        val propOptions = listOf(
                                            "dead_tree" to "🌲 شجرة ميتة", "stump" to "🪵 جذع مقطوع",
                                            "rocks" to "🪨 صخور", "fungus" to "🍄 فطر متوهج",
                                            "broken_fence" to "▥ سياج مكسور", "lantern" to "🏮 فانوس",
                                            "bones" to "☠ عظام", "hanging_roots" to "〰 جذور معلّقة",
                                            "shrine" to "♜ ضريح مهجور", "crates" to "▦ صناديق قديمة", "grave" to "† شاهد قبر", "thorn_bush" to "♧ شجيرة شوكية", "broken_cart" to "عربة محطمة", "well" to "بئر مهجور", "hanging_cage" to "قفص معلّق", "ruined_door" to "باب متهالك"
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                            propOptions.chunked(2).forEach { optionRow ->
                                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                                    optionRow.forEach { (type, label) ->
                                                        OutlinedButton(
                                                            onClick = {
                                                                updateProject(current.id) { project -> project.copy(scenes = project.scenes.map { item ->
                                                                    if (item.id == scene.id) item.copy(environmentProps = item.environmentProps + SceneProp(type = type, x = 0.25f + (item.environmentProps.size % 3) * 0.25f, y = 0.68f)) else item
                                                                }) }
                                                            }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 7.dp)
                                                        ) { Text(label, fontSize = 10.sp) }
                                                    }
                                                    if (optionRow.size == 1) Spacer(Modifier.weight(1f))
                                                }
                                            }
                                        }
                                        if (scene.environmentProps.isEmpty()) Text("المشهد لسه فاضي من عناصر البيئة. أضف أول عنصر من القائمة.", color = Muted, fontSize = 11.sp)
                                        scene.environmentProps.forEachIndexed { propIndex, prop ->
                                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
                                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("${propIndex + 1}. " + (propOptions.firstOrNull { it.first == prop.type }?.second ?: prop.type), color = Gold, modifier = Modifier.weight(1f), fontSize = 12.sp)
                                                        TextButton(onClick = {
                                                            updateProject(current.id) { project -> project.copy(scenes = project.scenes.map { item ->
                                                                if (item.id == scene.id) item.copy(environmentProps = item.environmentProps.filterNot { it.id == prop.id }) else item
                                                            }) }
                                                        }) { Text("حذف", color = Color(0xFFE18B83)) }
                                                    }
                                                    Text("الموضع الأفقي: ${(prop.x * 100).toInt()}٪", color = Muted, fontSize = 10.sp)
                                                    Slider(value = prop.x.coerceIn(0f, 1f), onValueChange = { v ->
                                                        updateProject(current.id) { project -> project.copy(scenes = project.scenes.map { item ->
                                                            if (item.id == scene.id) item.copy(environmentProps = item.environmentProps.map { if (it.id == prop.id) it.copy(x = v) else it }) else item
                                                        }) }
                                                    })
                                                    Text("الارتفاع: ${(prop.y * 100).toInt()}٪", color = Muted, fontSize = 10.sp)
                                                    Slider(value = prop.y.coerceIn(0f, 1f), onValueChange = { v ->
                                                        updateProject(current.id) { project -> project.copy(scenes = project.scenes.map { item ->
                                                            if (item.id == scene.id) item.copy(environmentProps = item.environmentProps.map { if (it.id == prop.id) it.copy(y = v) else it }) else item
                                                        }) }
                                                    })
                                                    Text("الحجم: ${(prop.scale * 100).toInt()}٪", color = Muted, fontSize = 10.sp)
                                                    Slider(value = prop.scale.coerceIn(0.4f, 2.2f), onValueChange = { v ->
                                                        updateProject(current.id) { project -> project.copy(scenes = project.scenes.map { item ->
                                                            if (item.id == scene.id) item.copy(environmentProps = item.environmentProps.map { if (it.id == prop.id) it.copy(scale = v) else it }) else item
                                                        }) }
                                                    }, valueRange = 0.4f..2.2f)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(checked = prop.foreground, onCheckedChange = { v ->
                                                            updateProject(current.id) { project -> project.copy(scenes = project.scenes.map { item ->
                                                                if (item.id == scene.id) item.copy(environmentProps = item.environmentProps.map { if (it.id == prop.id) it.copy(foreground = v) else it }) else item
                                                            }) }
                                                        })
                                                        Text("يظهر في مقدمة الصورة", color = Muted, fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Text("حوارات المشهد", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                            TextButton(onClick = {
                                                updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { item ->
                                                    if (item.id == scene.id) item.copy(dialogues = item.dialogues + DialogueLine()) else item
                                                }) }
                                            }) { Text("＋ سطر حوار") }
                                        }
                                        Text("كل سطر مرتبط بهذا المشهد. اكتب اسم المتحدث ونص كلامه، وسيظهر بالترتيب في المعاينة.",
                                            color = Muted, fontSize = 11.sp)
                                        if (scene.dialogues.isEmpty()) Text("لا توجد حوارات في هذا المشهد بعد.", color = Muted, fontSize = 12.sp)
                                        scene.dialogues.forEachIndexed { dialogueIndex, line ->
                                            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(12.dp)) {
                                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("سطر ${dialogueIndex + 1}", color = Gold, modifier = Modifier.weight(1f))
                                                        TextButton(onClick = {
                                                            updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { item ->
                                                                if (item.id == scene.id) item.copy(dialogues = item.dialogues.filterNot { it.id == line.id }) else item
                                                            }) }
                                                        }) { Text("حذف السطر", color = Color(0xFFE18B83)) }
                                                    }
                                                    OutlinedTextField(line.speaker, { value ->
                                                        updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { item ->
                                                            if (item.id == scene.id) item.copy(dialogues = item.dialogues.map { d -> if (d.id == line.id) d.copy(speaker = value) else d }) else item
                                                        }) }
                                                    }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("المتحدث") },
                                                        placeholder = { Text("مثال: ليلى") })
                                                    OutlinedTextField(line.text, { value ->
                                                        updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { item ->
                                                            if (item.id == scene.id) item.copy(dialogues = item.dialogues.map { d -> if (d.id == line.id) d.copy(text = value) else d }) else item
                                                        }) }
                                                    }, modifier = Modifier.fillMaxWidth(), minLines = 2, label = { Text("نص الحوار") },
                                                        placeholder = { Text("اكتب ما تقوله الشخصية...") })
                                                }
                                            }
                                        }
                                        FieldLabel("اختيارات اللاعب")
                                        OutlinedTextField(scene.choiceTimerSeconds.toString(), { value ->
                                            val seconds = value.toIntOrNull()?.coerceIn(0, 300) ?: if (value.isBlank()) 0 else scene.choiceTimerSeconds
                                            updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { s -> if (s.id == scene.id) s.copy(choiceTimerSeconds = seconds) else s }) }
                                        }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("مؤقت القرار بالثواني (0 = بدون مؤقت)") })
                                        if (scene.choiceTimerSeconds > 0) OutlinedTextField(scene.timeoutChoiceIndex.toString(), { value ->
                                            val indexValue = value.toIntOrNull()?.coerceIn(0, (scene.choices.size - 1).coerceAtLeast(0)) ?: scene.timeoutChoiceIndex
                                            updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { s -> if (s.id == scene.id) s.copy(timeoutChoiceIndex = indexValue) else s }) }
                                        }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("رقم الاختيار التلقائي عند انتهاء الوقت (يبدأ من 0)") })
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
                                            val conditionValue = scene.choiceConditions.getOrNull(index).orEmpty()
                                            val effectValue = scene.choiceEffects.getOrNull(index).orEmpty()
                                            OutlinedTextField(conditionValue, { value ->
                                                updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { s ->
                                                    if (s.id == scene.id) {
                                                        val values = s.choiceConditions.toMutableList()
                                                        while (values.size <= index) values.add("")
                                                        values[index] = value
                                                        s.copy(choiceConditions = values)
                                                    } else s
                                                }) }
                                            }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                                label = { Text("شرط الظهور (اختياري)") },
                                                placeholder = { Text("مثال: keys>=1") })
                                            OutlinedTextField(effectValue, { value ->
                                                updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { s ->
                                                    if (s.id == scene.id) {
                                                        val values = s.choiceEffects.toMutableList()
                                                        while (values.size <= index) values.add("")
                                                        values[index] = value
                                                        s.copy(choiceEffects = values)
                                                    } else s
                                                }) }
                                            }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                                label = { Text("تأثير الاختيار (اختياري)") },
                                                placeholder = { Text("مثال: trust+=1 أو keys=0") })
                                            TextButton(onClick = {
                                                updateProject(current.id) { p -> p.copy(scenes = p.scenes.map { s ->
                                                    if (s.id == scene.id) s.copy(
                                                        choices = s.choices.filterIndexed { i, _ -> i != index },
                                                        choiceTargets = s.choiceTargets.filterIndexed { i, _ -> i != index },
                                                        choiceConditions = s.choiceConditions.filterIndexed { i, _ -> i != index },
                                                        choiceEffects = s.choiceEffects.filterIndexed { i, _ -> i != index }
                                                    ) else s
                                                }) }
                                            }) { Text("حذف الاختيار", color = Color(0xFFE18B83), fontSize = 11.sp) }
                                            TextButton(onClick = {
                                                val destinations = current.scenes.map { it.id } + "__END__"
                                                val currentSceneIndex = current.scenes.indexOfFirst { it.id == scene.id }
                                                val next = if (target == null) destinations[(currentSceneIndex + 1).coerceIn(0, destinations.lastIndex)]
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
                                        val scenePosition = current.scenes.indexOfFirst { it.id == scene.id }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedButton(onClick = {
                                                val duplicate = scene.copy(id = UUID.randomUUID().toString(),
                                                    title = "نسخة من ${scene.title}".take(80))
                                                updateProject(current.id) { p ->
                                                    val list = p.scenes.toMutableList()
                                                    list.add((scenePosition + 1).coerceAtMost(list.size), duplicate)
                                                    p.copy(scenes = list)
                                                }
                                                selectedSceneId = duplicate.id
                                            }, modifier = Modifier.weight(1f)) { Text("نسخ المشهد") }
                                            OutlinedButton(onClick = {
                                                updateProject(current.id) { p ->
                                                    val list = p.scenes.toMutableList()
                                                    if (scenePosition > 0) {
                                                        val item = list.removeAt(scenePosition)
                                                        list.add(scenePosition - 1, item)
                                                    }
                                                    p.copy(scenes = list)
                                                }
                                            }, enabled = scenePosition > 0) { Text("↑") }
                                            OutlinedButton(onClick = {
                                                updateProject(current.id) { p ->
                                                    val list = p.scenes.toMutableList()
                                                    if (scenePosition in 0 until list.lastIndex) {
                                                        val item = list.removeAt(scenePosition)
                                                        list.add(scenePosition + 1, item)
                                                    }
                                                    p.copy(scenes = list)
                                                }
                                            }, enabled = scenePosition in 0 until current.scenes.lastIndex) { Text("↓") }
                                        }
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
                                    Divider(color = Color(0xFF343B43))
                                    Text("مكتبة الأصول", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                    Text("استورد صورًا وخلفيات أو ملفات صوتية. تُنسخ الملفات إلى مساحة التطبيق الخاصة وتُحفظ مع بيانات المشروع.", color = Muted, fontSize = 12.sp)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { imageAssetLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text("＋ صورة / خلفية") }
                                        OutlinedButton(onClick = { audioAssetLauncher.launch(arrayOf("audio/*")) }, modifier = Modifier.weight(1f)) { Text("＋ صوت / موسيقى") }
                                    }
                                    if (current.assets.isEmpty()) Text("لم تتم إضافة أصول لهذا المشروع بعد.", color = Muted, fontSize = 12.sp)
                                    current.assets.forEach { asset ->
                                        val linkedScene = current.scenes.firstOrNull { it.id == asset.sceneId }
                                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (asset.mimeType.startsWith("image/")) "▧" else "♫", color = Gold, fontSize = 22.sp)
                                            Column(Modifier.weight(1f)) {
                                                Text(asset.name, fontWeight = FontWeight.SemiBold)
                                                Text("${if (asset.mimeType.startsWith("image/")) "صورة" else "ملف صوتي"} • ${linkedScene?.title ?: "غير مرتبط بمشهد"}", color = Muted, fontSize = 11.sp)
                                            }
                                            TextButton(onClick = {
                                                val destination = current.scenes.firstOrNull { it.id != asset.sceneId }?.id
                                                updateProject(current.id) { p -> p.copy(assets = p.assets.map { a -> if (a.id == asset.id) a.copy(sceneId = destination) else a }) }
                                            }) { Text("نقل للمشهد التالي", fontSize = 10.sp) }
                                            TextButton(onClick = {
                                                File(filesDir, asset.relativePath).delete()
                                                updateProject(current.id) { p -> p.copy(assets = p.assets.filterNot { it.id == asset.id }) }
                                            }) { Text("حذف", color = Color(0xFFE18B83), fontSize = 11.sp) }
                                        }
                                    }
                                    Text("يتم الحفظ تلقائيًا على جهازك عند كل تعديل.", color = Color(0xFF8CBAA5), fontSize = 12.sp)
                                }
                            }
                            2 -> {
                                if (current == null) {
                                    EmptyPanel("لا توجد قصة للمعاينة", "اختر مشروعًا من المكتبة أولًا.")
                                    OutlinedButton(onClick = { page = 0 }) { Text("فتح المكتبة") }
                                } else if (current.scenes.isEmpty()) {
                                    EmptyPanel("القصة فارغة", "أضف مشهدًا من الاستوديو.")
                                } else {
                                    Text("معاينة تفاعلية", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text(current.title, color = Muted)
                                    Button(onClick = { showImmersivePlayer = true }, modifier = Modifier.fillMaxWidth()) { Text("✦ ابدأ التجربة السينمائية الغامرة") }
                                    OutlinedButton(
                                        onClick = { gamePackageLauncher.launch("${current.title.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_")}.storygame") },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("📦 تصدير اللعبة كحزمة مستقلة") }
                                    Text("الحزمة تشمل بيانات القصة والصور والصوت والغلاف، ويمكن نقلها لجهاز آخر. إنشاء APK مستقل مخصص يحتاج خطوة بناء إضافية.", color = Muted, fontSize = 11.sp)

                                    if (showImmersivePlayer) androidx.compose.ui.window.Dialog(onDismissRequest = { showImmersivePlayer = false }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
                                        ImmersiveStoryPlayer(project = current, filesDir = filesDir, onExit = { showImmersivePlayer = false })
                                    }
                                    if (previewSceneId == null || (previewSceneId != "__END__" && current.scenes.none { it.id == previewSceneId })) {
                                        previewSceneId = current.scenes.first().id
                                        previewIndex = 0
                                        previewValues = current.variables.associate { it.name to it.initialValue }
                                    }
                                    val safeIndex = if (previewSceneId == "__END__") current.scenes.lastIndex
                                        else current.scenes.indexOfFirst { it.id == previewSceneId }.coerceAtLeast(0)
                                    val previewScene = current.scenes[safeIndex]
                                    if (previewSceneId == "__END__") {
                                        EmptyPanel("انتهت القصة", "وصل اللاعب إلى نهاية هذا المسار.")
                                    } else Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                            Text("المشهد ${safeIndex + 1} / ${current.scenes.size}", color = Gold, fontSize = 12.sp)
                                            if (current.variables.isNotEmpty()) Text(
                                                current.variables.joinToString("  •  ") { "${it.name}: ${previewValues[it.name] ?: it.initialValue}" },
                                                color = Color(0xFF8CBAA5), fontSize = 12.sp
                                            )
                                            val sceneImageAsset = current.assets.firstOrNull { it.sceneId == previewScene.id && it.mimeType.startsWith("image/") }
                                            val sceneBitmap = remember(sceneImageAsset?.relativePath) {
                                                sceneImageAsset?.let { runCatching { BitmapFactory.decodeFile(File(filesDir, it.relativePath).absolutePath)?.asImageBitmap() }.getOrNull() }
                                            }
                                            Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF101712))) {
                                                if (sceneBitmap != null) Image(bitmap = sceneBitmap, contentDescription = "خلفية المشهد",
                                                    modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                                SceneDepthOverlay(effects = previewScene.atmosphereEffects, theme = current.visualTheme, layer = "far", modifier = Modifier.fillMaxSize())
                                                SceneEnvironmentOverlay(props = previewScene.environmentProps, foreground = false, modifier = Modifier.fillMaxSize())
                                                AtmosphereOverlay(effects = previewScene.atmosphereEffects, theme = current.visualTheme, intensity = previewScene.atmosphereIntensity, modifier = Modifier.fillMaxSize())
                                                SceneEnvironmentOverlay(props = previewScene.environmentProps, foreground = true, modifier = Modifier.fillMaxSize())
                                                SceneDepthOverlay(effects = previewScene.atmosphereEffects, theme = current.visualTheme, layer = "near", modifier = Modifier.fillMaxSize())
                                            }
                                            Text(previewScene.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            Text(previewScene.text.ifBlank { "هذا المشهد لا يحتوي على نص بعد." }, lineHeight = 25.sp)
                                            previewScene.dialogues.filter { it.speaker.isNotBlank() || it.text.isNotBlank() }.forEach { line ->
                                                Column(Modifier.fillMaxWidth().background(Ink, RoundedCornerShape(12.dp)).padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                                    if (line.speaker.isNotBlank()) Text(line.speaker, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    if (line.text.isNotBlank()) Text(line.text, lineHeight = 23.sp)
                                                }
                                            }
                                            current.assets.filter { it.sceneId == previewScene.id && it.mimeType.startsWith("audio/") }.forEach { audioAsset ->
                                                AudioPreviewCard(
                                                    title = audioAsset.name,
                                                    filePath = File(filesDir, audioAsset.relativePath).absolutePath
                                                )
                                            }
                                            previewScene.choices.forEachIndexed { choiceIndex, choice ->
                                                val condition = previewScene.choiceConditions.getOrNull(choiceIndex).orEmpty()
                                                val allowed = evaluateStoryCondition(condition, previewValues)
                                                if (choice.isNotBlank() && allowed) OutlinedButton(onClick = {
                                                    previewValues = applyStoryEffect(previewScene.choiceEffects.getOrNull(choiceIndex).orEmpty(), previewValues)
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
                                            if (previewScene.choices.any { it.isNotBlank() } &&
                                                previewScene.choices.indices.none { i ->
                                                    previewScene.choices[i].isNotBlank() &&
                                                        evaluateStoryCondition(previewScene.choiceConditions.getOrNull(i).orEmpty(), previewValues)
                                                }) {
                                                Text("لا يوجد اختيار متاح حاليًا. شروط هذا المشهد لا تتحقق بالقيم الحالية.", color = Gold, fontSize = 13.sp)
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
                                        previewValues = current.variables.associate { it.name to it.initialValue }
                                    }, modifier = Modifier.fillMaxWidth()) { Text("إعادة من البداية") }
                                    Text("كل اختيار يمكن توجيهه إلى مشهد مستقل أو إنهاء القصة. الوجهة التلقائية تتابع للمشهد التالي.", color = Muted, fontSize = 12.sp)
                                }
                            }
                            3 -> {
                                LearningCenter(onOpenStudio = { page = 1 })
                            }
                            4 -> {
                                BuildCenter(
                                    project = current,
                                    diagnostics = current?.let { diagnoseStory(it) }.orEmpty(),
                                    cloudApiBaseUrl = cloudApiBaseUrl,
                                    oidcIssuer = cloudOidcIssuer,
                                    oidcClientId = cloudOidcClientId,
                                    oidcAudience = cloudOidcAudience,
                                    isAuthenticated = cloudAccessToken != null,
                                    jobId = cloudJobId,
                                    jobStatus = cloudJobStatus,
                                    artifactAvailable = cloudArtifactAvailable,
                                    busy = cloudBusy,
                                    message = cloudMessage,
                                    onSaveSettings = { api, issuer, clientId, audience ->
                                        cloudApiBaseUrl = api.trimEnd('/')
                                        cloudOidcIssuer = issuer
                                        cloudOidcClientId = clientId
                                        cloudOidcAudience = audience
                                        prefs.edit()
                                            .putString("cloud_api_base_url", cloudApiBaseUrl)
                                            .putString("cloud_oidc_issuer", issuer)
                                            .putString("cloud_oidc_client_id", clientId)
                                            .putString("cloud_oidc_audience", audience)
                                            .apply()
                                        cloudMessage = "تم حفظ إعدادات الاتصال على هذا الجهاز."
                                    },
                                    onSignIn = { issuer, clientId, audience ->
                                        if (!issuer.startsWith("https://") || clientId.isBlank()) {
                                            cloudMessage = "أدخل OIDC Issuer وClient ID صحيحين أولًا."
                                        } else {
                                            cloudBusy = true
                                            AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(issuer)) { config, error ->
                                                runOnUiThread {
                                                    cloudBusy = false
                                                    if (config == null) {
                                                        cloudMessage = "تعذر اكتشاف إعدادات تسجيل الدخول: ${error?.localizedMessage ?: "راجع رابط Issuer"}"
                                                    } else {
                                                        val builder = AuthorizationRequest.Builder(
                                                            config, clientId, ResponseTypeValues.CODE,
                                                            Uri.parse("com.storyforge.app:/oauth2redirect")
                                                        ).setScope("openid profile email")
                                                        if (audience.isNotBlank()) builder.setAdditionalParameters(mapOf("audience" to audience))
                                                        cloudAuthLauncher.launch(cloudAuthService.getAuthorizationRequestIntent(builder.build()))
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onStartBuild = { title, appId, version, versionCode, buildType ->
                                        val token = cloudAccessToken
                                        val projectToBuild = current
                                        if (token == null) {
                                            cloudMessage = "سجّل الدخول أولًا."
                                        } else if (projectToBuild == null) {
                                            cloudMessage = "اختر قصة من المكتبة أولًا."
                                        } else {
                                            cloudBusy = true
                                            cloudMessage = "جارٍ تجهيز حزمة القصة ورفعها إلى التخزين الخاص..."
                                            lifecycleScope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        val packageFile = File(cacheDir, "storyforge-${UUID.randomUUID()}.storygame")
                                                        try {
                                                            packageFile.outputStream().use { output ->
                                                                writeStandaloneStoryPackage(output, projectToBuild, filesDir)
                                                            }
                                                            val storyUrl = CloudBuildApi.uploadStoryPackage(cloudApiBaseUrl, token, packageFile)
                                                            CloudBuildApi.start(cloudApiBaseUrl, token, storyUrl, title, appId, version, versionCode, buildType)
                                                        } finally {
                                                            packageFile.delete()
                                                        }
                                                    }
                                                }.onSuccess { job ->
                                                    cloudJobId = job.jobId
                                                    cloudJobStatus = job.status
                                                    cloudArtifactAvailable = false
                                                    prefs.edit().putString("cloud_job_id", job.jobId)
                                                        .putString("cloud_job_status", job.status)
                                                        .putBoolean("cloud_artifact_available", false).apply()
                                                    cloudMessage = "تم رفع القصة وإرسال طلب البناء بنجاح."
                                                }.onFailure { error ->
                                                    cloudMessage = error.message ?: "تعذر بدء البناء."
                                                }
                                                cloudBusy = false
                                            }
                                        }
                                    },
                                    onRefresh = {
                                        val token = cloudAccessToken
                                        val id = cloudJobId
                                        if (token != null && id != null) {
                                            cloudBusy = true
                                            lifecycleScope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) { CloudBuildApi.status(cloudApiBaseUrl, token, id) }
                                                }.onSuccess { status ->
                                                    cloudJobStatus = status.status
                                                    cloudArtifactAvailable = status.artifactAvailable
                                                    prefs.edit().putString("cloud_job_status", status.status)
                                                        .putBoolean("cloud_artifact_available", status.artifactAvailable).apply()
                                                    cloudMessage = when (status.status) {
                                                        "completed" -> if (status.artifactAvailable) "اكتمل البناء والملف جاهز للتنزيل." else "اكتمل البناء، جارٍ تجهيز ملف النتائج."
                                                        "failed" -> "فشل البناء. افتح سجل GitHub Actions لمعرفة التفاصيل."
                                                        else -> "البناء ما زال قيد التنفيذ."
                                                    }
                                                }.onFailure { error -> cloudMessage = error.message ?: "تعذر تحديث الحالة." }
                                                cloudBusy = false
                                            }
                                        }
                                    },
                                    onDownload = {
                                        val token = cloudAccessToken
                                        val id = cloudJobId
                                        if (token != null && id != null) {
                                            cloudBusy = true
                                            lifecycleScope.launch {
                                                runCatching {
                                                    withContext(Dispatchers.IO) { CloudBuildApi.artifactDownloadUrl(cloudApiBaseUrl, token, id) }
                                                }.onSuccess { signedUrl ->
                                                    runCatching {
                                                        val request = DownloadManager.Request(Uri.parse(signedUrl))
                                                            .setTitle("STORYFORGE - APK")
                                                            .setDescription("جارٍ تنزيل ملف اللعبة المبنية")
                                                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "STORYFORGE-$id.zip")
                                                        (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                                                        cloudMessage = "بدأ التنزيل الآمن داخل مجلد Downloads."
                                                    }.onFailure { cloudMessage = "تعذر بدء التنزيل: ${it.message}" }
                                                }.onFailure { cloudMessage = it.message ?: "تعذر الحصول على رابط التنزيل." }
                                                cloudBusy = false
                                            }
                                        }
                                    },
                                    onExport = {
                                        current?.let { project ->
                                            gamePackageLauncher.launch(project.title.replace(Regex("[^A-Za-z0-9._-]+"), "_") + ".storygame")
                                        }
                                    },
                                    onOpenWorkflow = {
                                        startActivity(android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse("https://github.com/omegathor3-afk/STORYFORGE/actions/workflows/standalone-apk.yml")
                                        ))
                                    },
                                    onOpenLibrary = { page = 0 }
                                )
                            }
                        }
                    }
                }
            }

            if (deleteProjectId != null) {
                val deleting = projects.firstOrNull { it.id == deleteProjectId }
                AlertDialog(
                    onDismissRequest = { deleteProjectId = null },
                    title = { Text("حذف المشروع؟") },
                    text = { Text("سيتم حذف «${deleting?.title ?: "هذا المشروع"}» من هذا الجهاز. لا يمكن التراجع عن الحذف.") },
                    confirmButton = {
                        TextButton(onClick = {
                            val id = deleteProjectId
                            persist(projects.filterNot { it.id == id })
                            if (selectedId == id) {
                                selectedId = null
                                selectedSceneId = null
                                previewSceneId = null
                                page = 0
                            }
                            deleteProjectId = null
                        }) { Text("حذف", color = Color(0xFFE18B83)) }
                    },
                    dismissButton = { TextButton(onClick = { deleteProjectId = null }) { Text("إلغاء") } }
                )
            }
            if (showNewProject) {
                AlertDialog(
                    onDismissRequest = { showNewProject = false },
                    title = { Text("مشروع جديد") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("اختر قالب البداية", color = Gold, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("رعب", "غموض", "بقاء").forEach { template ->
                                    FilterChip(
                                        selected = newTemplate == template,
                                        onClick = { newTemplate = template },
                                        label = { Text(template) }
                                    )
                                }
                            }
                            Text("سننشئ لك أربعة مشاهد مترابطة واختيارات جاهزة، ويمكنك تعديل كل شيء لاحقًا.",
                                color = Muted, fontSize = 12.sp)
                            OutlinedTextField(newTitle, { newTitle = it }, label = { Text("اسم القصة") }, singleLine = true)
                            OutlinedTextField(newDescription, { newDescription = it }, label = { Text("وصف مختصر") }, minLines = 2)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val project = StoryProject(title = newTitle.ifBlank { "قصة بلا عنوان" }, description = newDescription, scenes = createStarterScenes(newTemplate))
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
            if (showVariableDialog) {
                AlertDialog(
                    onDismissRequest = { showVariableDialog = false },
                    title = { Text("إضافة متغير للقصة") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(variableName, { variableName = it.filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                                label = { Text("اسم المتغير (بدون مسافات)") }, singleLine = true)
                            OutlinedTextField(variableValue, { variableValue = it.filter { ch -> ch == '-' || ch.isDigit() } },
                                label = { Text("القيمة الابتدائية") }, singleLine = true)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            selectedId?.let { id ->
                                val name = variableName.trim()
                                val value = variableValue.toIntOrNull() ?: 0
                                if (name.isNotBlank()) updateProject(id) { p ->
                                    if (p.variables.any { it.name == name }) p
                                    else p.copy(variables = p.variables + StoryVariable(name, value))
                                }
                            }
                            showVariableDialog = false
                        }) { Text("إضافة") }
                    },
                    dismissButton = { TextButton(onClick = { showVariableDialog = false }) { Text("إلغاء") } }
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
private fun AudioPreviewCard(title: String, filePath: String) {
    var player by remember(filePath) { mutableStateOf<MediaPlayer?>(null) }
    var prepared by remember(filePath) { mutableStateOf(false) }
    var preparing by remember(filePath) { mutableStateOf(false) }
    var playing by remember(filePath) { mutableStateOf(false) }
    var failed by remember(filePath) { mutableStateOf(false) }
    var volume by remember(filePath) { mutableFloatStateOf(0.75f) }

    DisposableEffect(filePath, player) {
        val ownedPlayer = player
        onDispose {
            if (ownedPlayer != null) {
                runCatching { ownedPlayer.setOnPreparedListener(null) }
                runCatching { ownedPlayer.setOnCompletionListener(null) }
                runCatching { ownedPlayer.setOnErrorListener(null) }
                runCatching { ownedPlayer.release() }
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = Ink), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("♫  $title", color = Gold, fontWeight = FontWeight.SemiBold)
                    Text(when {
                        failed -> "تعذر تشغيل الملف الصوتي"
                        preparing -> "جارٍ تجهيز الصوت..."
                        playing -> "يعمل الآن"
                        prepared -> "جاهز للتشغيل"
                        else -> "اضغط لتشغيل الصوت"
                    }, color = Muted, fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        val current = player
                        if (current == null && !preparing && !failed) {
                            preparing = true
                            runCatching {
                                MediaPlayer().also { media ->
                                    player = media
                                    media.setOnPreparedListener { ready ->
                                        if (player === ready) {
                                            prepared = true
                                            preparing = false
                                            runCatching { ready.setVolume(volume, volume); ready.start() }
                                                .onSuccess { playing = true }
                                                .onFailure { failed = true; playing = false }
                                        }
                                    }
                                    media.setOnCompletionListener { completed ->
                                        if (player === completed) playing = false
                                    }
                                    media.setOnErrorListener { broken, _, _ ->
                                        if (player === broken) {
                                            failed = true
                                            preparing = false
                                            prepared = false
                                            playing = false
                                        }
                                        true
                                    }
                                    media.setDataSource(filePath)
                                    media.prepareAsync()
                                }
                            }.onFailure {
                                player?.let { runCatching { it.release() } }
                                player = null
                                preparing = false
                                failed = true
                            }
                        } else if (current != null && prepared && !failed) {
                            runCatching {
                                if (playing) {
                                    current.pause()
                                    playing = false
                                } else {
                                    current.setVolume(volume, volume)
                                    current.start()
                                    playing = true
                                }
                            }.onFailure { failed = true; playing = false }
                        }
                    },
                    enabled = !preparing && !failed && (!prepared || player != null)
                ) { Text(if (preparing) "انتظر..." else if (playing) "إيقاف مؤقت" else "تشغيل") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مستوى الصوت", color = Muted, fontSize = 11.sp)
                Slider(
                    value = volume,
                    onValueChange = { next ->
                        volume = next
                        player?.let { media -> if (prepared) runCatching { media.setVolume(next, next) } }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !failed
                )
                Text("${(volume * 100).toInt()}%", color = Gold, fontSize = 11.sp)
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


/**
 * Portable single-game bundle. It deliberately contains only one project and its
 * referenced media, with a small manifest for a future standalone Android player.
 */
internal fun writeStandaloneStoryPackage(output: OutputStream, project: StoryProject, filesDir: File) {
    val root = File(filesDir, "storyforge_assets").canonicalFile
    val assets = project.assets.filter { asset ->
        asset.relativePath.startsWith("storyforge_assets/") &&
            runCatching {
                val file = File(filesDir, asset.relativePath).canonicalFile
                file.isFile && file.path.startsWith(root.path + File.separator)
            }.getOrDefault(false)
    }
    val packagedProject = project.copy(assets = assets)
    val projectJson = encodeProjects(listOf(packagedProject))
    val manifest = JSONObject()
        .put("format", "storyforge-game")
        .put("formatVersion", 1)
        .put("playerMinimumVersion", "0.2.0")
        .put("title", project.title)
        .put("description", project.description)
        .put("projectId", project.id)
        .put("language", "ar")
        .put("branding", JSONObject()
            .put("displayName", project.title)
            .put("introTitle", project.title)
            .put("introDescription", project.description)
            .put("coverAssetId", assets.firstOrNull { it.mimeType.startsWith("image/") }?.id ?: JSONObject.NULL))
        .put("sceneCount", project.scenes.size)
        .put("assetCount", assets.size)
    ZipOutputStream(output.buffered()).use { zip ->
        zip.putNextEntry(ZipEntry("game-manifest.json"))
        zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.putNextEntry(ZipEntry("storyforge-project.json"))
        zip.write(projectJson.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        assets.forEach { asset ->
            val source = File(filesDir, asset.relativePath).canonicalFile
            zip.putNextEntry(ZipEntry("assets/${asset.relativePath}"))
            source.inputStream().buffered().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.finish()
    }
}

internal fun writeStoryZipBackup(output: OutputStream, projects: List<StoryProject>, filesDir: File) {
    ZipOutputStream(output.buffered()).use { zip ->
        zip.putNextEntry(ZipEntry("storyforge-projects.json"))
        zip.write(encodeProjects(projects).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        projects.forEach { project ->
            project.assets.forEach { asset ->
                if (!asset.relativePath.startsWith("storyforge_assets/")) return@forEach
                val source = File(filesDir, asset.relativePath).canonicalFile
                val root = File(filesDir, "storyforge_assets").canonicalFile
                if (!source.isFile || !source.path.startsWith(root.path + File.separator)) return@forEach
                zip.putNextEntry(ZipEntry("assets/${asset.relativePath}"))
                source.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        zip.finish()
    }
}

private const val ZIP_MAX_ENTRIES = 500
private const val ZIP_MAX_ENTRY_BYTES = 20L * 1024L * 1024L
private const val ZIP_MAX_TOTAL_BYTES = 100L * 1024L * 1024L
private const val ZIP_MAX_JSON_BYTES = 10L * 1024L * 1024L

private fun isSafeStoryAssetPath(path: String): Boolean {
    if (!path.startsWith("storyforge_assets/") || path.startsWith("/") || path.contains(92.toChar())) return false
    val parts = path.split('/')
    return parts.size >= 3 && parts.none { it.isBlank() || it == "." || it == ".." }
}

private fun readZipEntryLimited(input: InputStream, limit: Long): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var count = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        count += read
        require(count <= limit) { "ملف داخل ZIP أكبر من الحد المسموح" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

internal fun restoreStoryZipBackup(input: InputStream, filesDir: File): List<StoryProject> {
    val staging = File(filesDir, ".storyforge_restore_${UUID.randomUUID()}")
    val stagedAssets = File(staging, "storyforge_assets")
    val root = File(filesDir, "storyforge_assets")
    val previousAssets = File(filesDir, ".storyforge_assets_previous_${UUID.randomUUID()}")
    var previousMoved = false
    try {
        require(staging.mkdirs()) { "تعذر تجهيز مساحة الاستعادة" }
        var projectJson: String? = null
        var totalBytes = 0L
        var entryCount = 0
        val seenEntries = mutableSetOf<String>()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= ZIP_MAX_ENTRIES) { "النسخة تحتوي على ملفات أكثر من المسموح" }
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val name = entry.name
                require(!name.startsWith("/") && !name.contains(92.toChar()) &&
                    name.split('/').none { it == "." || it == ".." || it.isBlank() }) { "مسار ملف غير آمن داخل ZIP" }
                require(seenEntries.add(name)) { "النسخة تحتوي على ملف مكرر" }
                when {
                    name == "storyforge-projects.json" -> {
                        val bytes = readZipEntryLimited(zip, ZIP_MAX_JSON_BYTES)
                        totalBytes += bytes.size
                        require(totalBytes <= ZIP_MAX_TOTAL_BYTES) { "حجم النسخة أكبر من المسموح" }
                        projectJson = bytes.toString(Charsets.UTF_8)
                    }
                    name.startsWith("assets/storyforge_assets/") -> {
                        val relative = name.removePrefix("assets/")
                        require(isSafeStoryAssetPath(relative)) { "مسار أصل غير آمن داخل ZIP" }
                        val destination = File(stagedAssets, relative.removePrefix("storyforge_assets/")).canonicalFile
                        require(destination.path.startsWith(stagedAssets.canonicalPath + File.separator)) {
                            "مسار ملف غير آمن داخل ZIP"
                        }
                        destination.parentFile?.mkdirs()
                        val bytes = readZipEntryLimited(zip, ZIP_MAX_ENTRY_BYTES)
                        totalBytes += bytes.size
                        require(totalBytes <= ZIP_MAX_TOTAL_BYTES) { "حجم النسخة أكبر من المسموح" }
                        destination.outputStream().buffered().use { it.write(bytes) }
                    }
                    else -> error("النسخة تحتوي على عنصر غير مدعوم: $name")
                }
                zip.closeEntry()
            }
        }

        val json = projectJson ?: error("ملف بيانات القصة غير موجود داخل ZIP")
        val projects = decodeProjects(json)
        require(projects.isNotEmpty()) { "لا توجد مشاريع داخل النسخة" }
        require(projects.flatMap { it.assets }.all { isSafeStoryAssetPath(it.relativePath) }) {
            "بيانات النسخة تحتوي على مسار أصل غير آمن"
        }

        if (root.exists()) {
            require(root.renameTo(previousAssets)) { "تعذر حفظ ملفات الأصول الحالية مؤقتًا" }
            previousMoved = true
        }
        if (!stagedAssets.exists()) require(stagedAssets.mkdirs()) { "تعذر تجهيز ملفات الأصول المستعادة" }
        if (!stagedAssets.renameTo(root)) {
            if (previousMoved) previousAssets.renameTo(root)
            previousMoved = false
            error("تعذر تثبيت ملفات الأصول المستعادة")
        }
        if (previousMoved) previousAssets.deleteRecursively()
        return projects
    } finally {
        if (previousMoved && !root.exists()) previousAssets.renameTo(root)
        staging.deleteRecursively()
        if (previousAssets.exists() && root.exists()) previousAssets.deleteRecursively()
    }
}

private fun createStarterScenes(template: String): List<StoryScene> {
    val openingId = UUID.randomUUID().toString()
    val leftPathId = UUID.randomUUID().toString()
    val rightPathId = UUID.randomUUID().toString()
    val endingId = UUID.randomUUID().toString()
    val opening = when (template) {
        "غموض" -> StoryScene(
            id = openingId, title = "الرسالة المجهولة",
            text = "تجد رسالة بلا توقيع تحت بابك. الحبر ما زال رطبًا، وفي آخرها عنوان مكان لم تسمع به من قبل.",
            choices = listOf("اذهب إلى العنوان", "مزّق الرسالة وابقَ في منزلك"),
            choiceTargets = listOf(leftPathId, rightPathId)
        )
        "بقاء" -> StoryScene(
            id = openingId, title = "الصباح الأخير",
            text = "انقطعت الكهرباء والماء. حقيبتك شبه فارغة، وصوت خطوات يقترب من المبنى.",
            choices = listOf("ابحث عن الطعام", "أمّن مكانًا للاختباء"),
            choiceTargets = listOf(leftPathId, rightPathId)
        )
        else -> StoryScene(
            id = openingId, title = "الطرقات على الباب",
            text = "الساعة الثالثة بعد منتصف الليل. ثلاث طرقات بطيئة تهز الباب، ثم تسمع صوتًا يناديك باسمك من الخارج.",
            choices = listOf("انظر من العين السحرية", "افتح الباب بحذر"),
            choiceTargets = listOf(leftPathId, rightPathId)
        )
    }
    val left = StoryScene(
        id = leftPathId,
        title = if (template == "غموض") "العنوان القديم" else if (template == "بقاء") "المخزن المهجور" else "عين الباب",
        text = if (template == "غموض") "تصل إلى مبنى مهجور. على الجدار نفس الرمز المرسوم أسفل الرسالة." else if (template == "بقاء") "تعثر على مخزن صغير. الباب مفتوح، لكن شيئًا يتحرك خلف الصناديق." else "لا ترى أحدًا، لكن ظلًا طويلًا يمر أمام الباب رغم أن الممر خالٍ.",
        choices = listOf("تابع ما بدأته"),
        choiceTargets = listOf(endingId)
    )
    val right = StoryScene(
        id = rightPathId,
        title = if (template == "غموض") "الصمت" else if (template == "بقاء") "الغرفة الآمنة" else "الضيف",
        text = if (template == "غموض") "تمزق الرسالة، ثم تلاحظ أن الكلمات تظهر من جديد على قصاصات الورق." else if (template == "بقاء") "تدفع خزانة ثقيلة أمام الباب. لديك وقت قصير لتقرر خطوتك التالية." else "ينفتح الباب ببطء. لا يوجد أحد، وعلى الأرض شيء تركه الزائر.",
        choices = listOf("افحص الدليل"),
        choiceTargets = listOf(endingId)
    )
    val ending = StoryScene(
        id = endingId, title = "بداية الحكاية",
        text = "هنا تبدأ قصتك الحقيقية. غيّر هذا المشهد، أضف شخصياتك، واصنع نهاية لا يتوقعها اللاعب.",
        choices = emptyList(), choiceTargets = emptyList()
    )
    return listOf(opening, left, right, ending)
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
            val conditions = JSONArray(); scene.choiceConditions.forEach { conditions.put(it) }
            val effects = JSONArray(); scene.choiceEffects.forEach { effects.put(it) }
            s.put("choiceConditions", conditions); s.put("choiceEffects", effects)
            s.put("choiceTimerSeconds", scene.choiceTimerSeconds.coerceIn(0, 300))
            s.put("timeoutChoiceIndex", scene.timeoutChoiceIndex.coerceAtLeast(0))
            val atmosphere = JSONArray(); scene.atmosphereEffects.forEach { atmosphere.put(it) }
            s.put("atmosphereEffects", atmosphere)
            s.put("atmosphereIntensity", scene.atmosphereIntensity.coerceIn(0f, 1f).toDouble())
            val props = JSONArray()
            scene.environmentProps.forEach { prop ->
                props.put(JSONObject().put("id", prop.id).put("type", prop.type)
                    .put("x", prop.x.coerceIn(0f, 1f).toDouble()).put("y", prop.y.coerceIn(0f, 1f).toDouble())
                    .put("scale", prop.scale.coerceIn(0.4f, 2.2f).toDouble()).put("foreground", prop.foreground))
            }
            s.put("environmentProps", props)
            val dialogues = JSONArray()
            scene.dialogues.forEach { line ->
                dialogues.put(JSONObject().put("id", line.id).put("speaker", line.speaker).put("text", line.text))
            }
            s.put("dialogues", dialogues)
            scenes.put(s)
        }
        val characters = JSONArray(); project.characters.forEach { characters.put(it) }
        val variables = JSONArray()
        project.variables.forEach { variable ->
            variables.put(JSONObject().put("name", variable.name).put("initialValue", variable.initialValue))
        }
        val assets = JSONArray()
        project.assets.forEach { asset ->
            assets.put(JSONObject().put("id", asset.id).put("name", asset.name)
                .put("relativePath", asset.relativePath).put("mimeType", asset.mimeType)
                .put("sceneId", asset.sceneId ?: JSONObject.NULL))
        }
        p.put("scenes", scenes); p.put("characters", characters); p.put("variables", variables)
        p.put("assets", assets); p.put("visualTheme", project.visualTheme)
        p.put("showSceneNumbers", project.showSceneNumbers); array.put(p)
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
                } ?: emptyList(),
                choiceConditions = s.optJSONArray("choiceConditions")?.let { values ->
                    (0 until values.length()).map { k -> values.optString(k) }
                } ?: emptyList(),
                choiceEffects = s.optJSONArray("choiceEffects")?.let { values ->
                    (0 until values.length()).map { k -> values.optString(k) }
                } ?: emptyList(),
                choiceTimerSeconds = s.optInt("choiceTimerSeconds", 0).coerceIn(0, 300),
                timeoutChoiceIndex = s.optInt("timeoutChoiceIndex", 0).coerceAtLeast(0),
                atmosphereEffects = s.optJSONArray("atmosphereEffects")?.let { values -> (0 until values.length()).map { k -> values.optString(k) } } ?: emptyList(),
                atmosphereIntensity = s.optDouble("atmosphereIntensity", 0.72).toFloat().coerceIn(0f, 1f),
                environmentProps = s.optJSONArray("environmentProps")?.let { values ->
                    (0 until values.length()).map { k ->
                        val item = values.getJSONObject(k)
                        SceneProp(
                            id = item.optString("id", UUID.randomUUID().toString()),
                            type = item.optString("type", "rocks"),
                            x = item.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                            y = item.optDouble("y", 0.62).toFloat().coerceIn(0f, 1f),
                            scale = item.optDouble("scale", 1.0).toFloat().coerceIn(0.4f, 2.2f),
                            foreground = item.optBoolean("foreground", false)
                        )
                    }
                } ?: emptyList(),
                dialogues = s.optJSONArray("dialogues")?.let { values ->
                    (0 until values.length()).map { k ->
                        val d = values.getJSONObject(k)
                        DialogueLine(
                            id = d.optString("id", UUID.randomUUID().toString()),
                            speaker = d.optString("speaker", ""),
                            text = d.optString("text", "")
                        )
                    }
                } ?: emptyList()
            )
        }
        val charsJson = p.optJSONArray("characters") ?: JSONArray()
        val variablesJson = p.optJSONArray("variables") ?: JSONArray()
        StoryProject(
            id = p.optString("id", UUID.randomUUID().toString()),
            title = p.optString("title", "قصة بلا عنوان"),
            description = p.optString("description", ""),
            scenes = scenes.ifEmpty { listOf(StoryScene()) },
            characters = (0 until charsJson.length()).map { charsJson.optString(it) },
            variables = (0 until variablesJson.length()).map { k ->
                val v = variablesJson.getJSONObject(k)
                StoryVariable(v.optString("name"), v.optInt("initialValue", 0))
            },
            visualTheme = p.optString("visualTheme", "darkwood"),
            showSceneNumbers = p.optBoolean("showSceneNumbers", true),
            assets = p.optJSONArray("assets")?.let { values ->
                (0 until values.length()).map { k ->
                    val a = values.getJSONObject(k)
                    StoryAsset(id = a.optString("id", UUID.randomUUID().toString()), name = a.optString("name", "Asset"),
                        relativePath = a.optString("relativePath", ""), mimeType = a.optString("mimeType", "application/octet-stream"),
                        sceneId = if (a.isNull("sceneId")) null else a.optString("sceneId"))
                }
            } ?: emptyList()
        )
    }
}

private fun loadProjects(prefs: android.content.SharedPreferences): List<StoryProject> =
    runCatching { decodeProjects(prefs.getString("projects_json", "[]") ?: "[]") }.getOrDefault(emptyList())


internal fun evaluateStoryCondition(expression: String, values: Map<String, Int>): Boolean {
    if (expression.isBlank()) return true
    val match = Regex("^([\\p{L}A-Za-z0-9_]+)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+)$").matchEntire(expression.trim()) ?: return false
    val actual = values[match.groupValues[1]] ?: 0
    val expected = match.groupValues[3].toIntOrNull() ?: return false
    return when (match.groupValues[2]) {
        ">=" -> actual >= expected
        "<=" -> actual <= expected
        "==" -> actual == expected
        "!=" -> actual != expected
        ">" -> actual > expected
        "<" -> actual < expected
        else -> false
    }
}

internal fun applyStoryEffect(expression: String, values: Map<String, Int>): Map<String, Int> {
    if (expression.isBlank()) return values
    val match = Regex("^([\\p{L}A-Za-z0-9_]+)\\s*(=|\\+=|-=)\\s*(-?\\d+)$").matchEntire(expression.trim()) ?: return values
    val name = match.groupValues[1]
    val amount = match.groupValues[3].toIntOrNull() ?: return values
    val old = values[name] ?: 0
    val next = when (match.groupValues[2]) {
        "=" -> amount
        "+=" -> old + amount
        "-=" -> old - amount
        else -> old
    }
    return values + (name to next)
}
