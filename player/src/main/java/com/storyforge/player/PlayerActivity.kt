package com.storyforge.player

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

private val Night = Color(0xFF05070B)
private val Gold = Color(0xFFE0C58C)
private val Blue = Color(0xFF91C8D7)
private data class Choice(val text:String,val target:String?,val condition:String,val effect:String)
internal data class SceneProp(val type:String,val x:Float,val y:Float,val scale:Float,val foreground:Boolean)
private data class Scene(val id:String,val title:String,val text:String,val choices:List<Choice>,val speakerLines:List<Pair<String,String>>,val image:File?,val audio:File?,val choiceTimerSeconds:Int,val timeoutChoiceIndex:Int,val atmosphereEffects:List<String>,val atmosphereIntensity:Float,val environmentProps:List<SceneProp>)
private data class Game(val projectId:String,val title:String,val description:String,val scenes:List<Scene>,val variables:Map<String,Int>,val cover:File?,val visualTheme:String,val showSceneNumbers:Boolean)

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlayerScreen() }
    }

    private fun loadBundledGame(): Game? {
        val bundled = runCatching { assets.open("default.storygame") }.getOrNull() ?: return null
        val root = File(filesDir, "bundled_story").apply { deleteRecursively(); mkdirs() }
        bundled.use { raw -> ZipInputStream(raw.buffered()).use { zip ->
            var count = 0; var total = 0L
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= 500) { "حزمة اللعبة تحتوي على ملفات كثيرة" }
                val name = entry.name
                require(!name.startsWith("/") && !name.contains(92.toChar()) && name.split('/').none { it.isBlank() || it == "." || it == ".." }) { "مسار غير آمن" }
                if (entry.isDirectory) { zip.closeEntry(); continue }
                val relative = if (name.startsWith("assets/")) name.removePrefix("assets/") else name
                require(relative == "game-manifest.json" || relative == "storyforge-project.json" || relative.startsWith("storyforge_assets/")) { "ملف غير مدعوم" }
                val target = File(root, relative).canonicalFile
                require(target.path.startsWith(root.canonicalPath + File.separator))
                target.parentFile?.mkdirs()
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192); var entryBytes = 0L
                    while (true) { val n = zip.read(buffer); if (n < 0) break; entryBytes += n; total += n; require(entryBytes <= 20L*1024*1024 && total <= 100L*1024*1024) { "حجم الحزمة أكبر من الحد الآمن" }; output.write(buffer,0,n) }
                }
                zip.closeEntry()
            }
        } }
        val manifest = JSONObject(File(root, "game-manifest.json").readText())
        require(manifest.optString("format") == "storyforge-game" && manifest.optInt("formatVersion") == 1) { "إصدار الحزمة غير مدعوم" }
        val projects = org.json.JSONArray(File(root, "storyforge-project.json").readText())
        require(projects.length() == 1) { "الحزمة يجب أن تحتوي قصة واحدة" }
        val project = projects.getJSONObject(0)
        val assetsJson = project.optJSONArray("assets")
        data class AssetFile(val path: String, val mime: String, val sceneId: String?)
        val assetFiles = (0 until (assetsJson?.length() ?: 0)).mapNotNull { i ->
            val a = assetsJson!!.getJSONObject(i); val path = a.optString("relativePath")
            if (!path.startsWith("storyforge_assets/") || path.split('/').any { it.isBlank() || it == "." || it == ".." }) null
            else AssetFile(path, a.optString("mimeType"), if (a.isNull("sceneId")) null else a.optString("sceneId"))
        }.filter { val f = File(root, it.path).canonicalFile; f.path.startsWith(root.canonicalPath + File.separator) && f.isFile }
        val sceneArray = project.optJSONArray("scenes") ?: org.json.JSONArray()
        val scenes = (0 until sceneArray.length()).map { index ->
            val s = sceneArray.getJSONObject(index); val choicesJson = s.optJSONArray("choices") ?: org.json.JSONArray()
            val targets = s.optJSONArray("choiceTargets"); val conditions = s.optJSONArray("choiceConditions"); val effects = s.optJSONArray("choiceEffects")
            val choices = (0 until choicesJson.length()).map { i -> Choice(choicesJson.optString(i), targets?.let { if (it.isNull(i)) null else it.optString(i) }, conditions?.optString(i).orEmpty(), effects?.optString(i).orEmpty()) }
            val dialogue = s.optJSONArray("dialogues")
            val lines = (0 until (dialogue?.length() ?: 0)).map { i -> val d = dialogue!!.getJSONObject(i); d.optString("speaker") to d.optString("text") }
            val id = s.optString("id")
            val image = assetFiles.firstOrNull { it.sceneId == id && it.mime.startsWith("image/") }?.let { File(root, it.path) }
            val audio = assetFiles.firstOrNull { it.sceneId == id && it.mime.startsWith("audio/") }?.let { File(root, it.path) }
            Scene(id, s.optString("title"), s.optString("text"), choices, lines, image, audio, s.optInt("choiceTimerSeconds", 0).coerceIn(0,300), s.optInt("timeoutChoiceIndex",0).coerceAtLeast(0), s.optJSONArray("atmosphereEffects")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(), s.optDouble("atmosphereIntensity",0.72).toFloat().coerceIn(0f,1f), s.optJSONArray("environmentProps")?.let { arr -> (0 until arr.length()).map { k -> val item=arr.getJSONObject(k); SceneProp(item.optString("type","rocks"),item.optDouble("x",0.5).toFloat().coerceIn(0f,1f),item.optDouble("y",0.62).toFloat().coerceIn(0f,1f),item.optDouble("scale",1.0).toFloat().coerceIn(0.4f,2.2f),item.optBoolean("foreground",false)) } } ?: emptyList())
        }
        val variables = linkedMapOf<String, Int>(); val vars = project.optJSONArray("variables")
        for (i in 0 until (vars?.length() ?: 0)) { val v = vars!!.getJSONObject(i); variables[v.optString("name")] = v.optInt("initialValue") }
        val cover = assetFiles.firstOrNull { it.mime.startsWith("image/") && it.sceneId == null }?.let { File(root,it.path) } ?: assetFiles.firstOrNull { it.mime.startsWith("image/") }?.let { File(root,it.path) }
        return Game(project.optString("id", manifest.optString("projectId")), project.optString("title", manifest.optString("title")), project.optString("description"), scenes, variables, cover, project.optString("visualTheme", "darkwood"), project.optBoolean("showSceneNumbers", true))
    }

    @Composable private fun PlayerScreen() {
        var game by remember { mutableStateOf(runCatching { loadBundledGame() }.getOrNull()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runCatching {
                val root = File(filesDir, "installed_game").apply { deleteRecursively(); mkdirs() }
                contentResolver.openInputStream(uri)?.use { raw ->
                    ZipInputStream(raw.buffered()).use { zip ->
                        val seen = mutableSetOf<String>()
                        var entries = 0
                        var totalBytes = 0L
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            entries++
                            require(entries <= 500) { "الحزمة تحتوي على ملفات كثيرة جدًا" }
                            val name = entry.name
                            require(!name.startsWith("/") && !name.contains(92.toChar()) &&
                                name.split('/').none { it == ".." || it == "." || it.isBlank() }) { "مسار غير آمن داخل الحزمة" }
                            require(seen.add(name)) { "الحزمة تحتوي على ملف مكرر" }
                            if (entry.isDirectory) { zip.closeEntry(); continue }
                            val relative = if (name.startsWith("assets/")) name.removePrefix("assets/") else name
                            require(relative == "game-manifest.json" || relative == "storyforge-project.json" ||
                                relative.startsWith("storyforge_assets/")) { "ملف غير مدعوم داخل الحزمة" }
                            val out = File(root, relative).canonicalFile
                            require(out.path.startsWith(root.canonicalPath + File.separator))
                            out.parentFile?.mkdirs()
                            val limit = if (relative.endsWith(".json")) 10L * 1024 * 1024 else 20L * 1024 * 1024
                            out.outputStream().buffered().use { output ->
                                val buffer = ByteArray(8192)
                                var entryBytes = 0L
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    entryBytes += count
                                    totalBytes += count
                                    require(entryBytes <= limit && totalBytes <= 100L * 1024 * 1024) { "حجم الحزمة يتجاوز الحد الآمن" }
                                    output.write(buffer, 0, count)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                } ?: error("تعذر قراءة الملف")
                val manifest = JSONObject(File(root,"game-manifest.json").readText())
                require(manifest.optString("format") == "storyforge-game" && manifest.optInt("formatVersion") == 1) { "إصدار حزمة اللعبة غير مدعوم" }
                val payload = org.json.JSONArray(File(root,"storyforge-project.json").readText())
                require(payload.length() == 1) { "يجب أن تحتوي الحزمة على قصة واحدة" }
                val p = payload.getJSONObject(0)
                val assets = p.optJSONArray("assets")
                data class AssetFile(val path:String,val mime:String,val sceneId:String?)
                val assetFiles = (0 until (assets?.length() ?: 0)).mapNotNull { i ->
                    val a = assets!!.getJSONObject(i)
                    val relative = a.optString("relativePath")
                    if (!relative.startsWith("storyforge_assets/") || relative.split('/').any { it == ".." || it == "." || it.isBlank() }) null
                    else AssetFile(relative,a.optString("mimeType"),if(a.isNull("sceneId")) null else a.optString("sceneId"))
                }.filter { asset ->
                    val file = File(root,asset.path).canonicalFile
                    file.path.startsWith(root.canonicalPath + File.separator) && file.isFile
                }
                val scenesJson = p.optJSONArray("scenes") ?: org.json.JSONArray()
                val scenes = (0 until scenesJson.length()).map { ix ->
                    val s = scenesJson.getJSONObject(ix)
                    val choicesJson = s.optJSONArray("choices") ?: org.json.JSONArray()
                    val targets = s.optJSONArray("choiceTargets")
                    val conditions = s.optJSONArray("choiceConditions")
                    val effects = s.optJSONArray("choiceEffects")
                    val choices = (0 until choicesJson.length()).map { ci ->
                        Choice(choicesJson.optString(ci), targets?.let { if(it.isNull(ci)) null else it.optString(ci) },
                            conditions?.optString(ci).orEmpty(), effects?.optString(ci).orEmpty())
                    }
                    val dialogue = s.optJSONArray("dialogues")
                    val lines = (0 until (dialogue?.length() ?: 0)).map { di ->
                        val line = dialogue!!.getJSONObject(di)
                        line.optString("speaker") to line.optString("text")
                    }
                    val sceneId = s.optString("id")
                    val image = assetFiles.firstOrNull { it.sceneId == sceneId && it.mime.startsWith("image/") }?.let { File(root,it.path) }
                    val audio = assetFiles.firstOrNull { it.sceneId == sceneId && it.mime.startsWith("audio/") }?.let { File(root,it.path) }
                    Scene(sceneId,s.optString("title"),s.optString("text"),choices,lines,image,audio,s.optInt("choiceTimerSeconds",0).coerceIn(0,300),s.optInt("timeoutChoiceIndex",0).coerceAtLeast(0),s.optJSONArray("atmosphereEffects")?.let { arr -> (0 until arr.length()).map { arr.optString(it) } } ?: emptyList(),s.optDouble("atmosphereIntensity",0.72).toFloat().coerceIn(0f,1f),s.optJSONArray("environmentProps")?.let { arr -> (0 until arr.length()).map { k -> val item=arr.getJSONObject(k); SceneProp(item.optString("type","rocks"),item.optDouble("x",0.5).toFloat().coerceIn(0f,1f),item.optDouble("y",0.62).toFloat().coerceIn(0f,1f),item.optDouble("scale",1.0).toFloat().coerceIn(0.4f,2.2f),item.optBoolean("foreground",false)) } } ?: emptyList())
                }
                val vars = linkedMapOf<String,Int>()
                val varsJson = p.optJSONArray("variables")
                for (i in 0 until (varsJson?.length() ?: 0)) {
                    val v = varsJson!!.getJSONObject(i); vars[v.optString("name")] = v.optInt("initialValue")
                }
                val cover = assetFiles.firstOrNull { it.mime.startsWith("image/") && it.sceneId == null }?.let { File(root,it.path) }
                    ?: assetFiles.firstOrNull { it.mime.startsWith("image/") }?.let { File(root,it.path) }
                game = Game(p.optString("id",manifest.optString("projectId")),p.optString("title",manifest.optString("title")),p.optString("description"),scenes,vars,cover,p.optString("visualTheme","darkwood"),p.optBoolean("showSceneNumbers",true))
                errorMessage = null
            }.onFailure { errorMessage = it.message ?: "تعذر فتح اللعبة" }
        }
        MaterialTheme(colorScheme = darkColorScheme(primary=Gold,background=Night,surface=Color(0xFF151A23))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF101B2A),Night)))) {
                if (game == null) {
                    Column(Modifier.fillMaxSize().padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text("✦",color=Gold,fontSize=54.sp)
                        Text("STORYFORGE PLAYER",color=Gold,fontSize=24.sp,fontWeight=FontWeight.Black)
                        Text("حوّل حكايتك إلى تجربة تعيشها",color=Color.LightGray)
                        Spacer(Modifier.height(26.dp))
                        Button(onClick={picker.launch(arrayOf("application/zip","application/octet-stream"))},modifier=Modifier.fillMaxWidth()) { Text("استيراد لعبة .storygame") }
                        Text("اختر حزمة لعبة صدّرتها من STORYFORGE.",color=Color.Gray,fontSize=12.sp,textAlign=TextAlign.Center)
                        errorMessage?.let { Text(it,color=Color(0xFFFF8888),modifier=Modifier.padding(top=16.dp)) }
                    }
                } else GameExperience(game!!)
            }
        }
    }

    @Composable private fun GameExperience(game: Game) {
        val progressPrefs = remember(game.projectId) { getSharedPreferences("storyforge_player_progress", MODE_PRIVATE) }
        val savedProgress = remember(game.projectId) {
            runCatching { JSONObject(progressPrefs.getString(game.projectId, "{}") ?: "{}") }.getOrDefault(JSONObject())
        }
        var intro by remember(game.projectId) { mutableStateOf(true) }
        var sceneId by remember(game.projectId) {
            val savedScene = savedProgress.optString("sceneId", game.scenes.firstOrNull()?.id)
            mutableStateOf(if (savedScene == "__END__" || game.scenes.any { it.id == savedScene }) savedScene else game.scenes.firstOrNull()?.id)
        }
        var values by remember(game.projectId) {
            val savedValues = savedProgress.optJSONObject("values")
            val restored = game.variables.toMutableMap()
            if (savedValues != null) savedValues.keys().forEach { key -> restored[key] = savedValues.optInt(key, restored[key] ?: 0) }
            mutableStateOf(restored.toMap())
        }
        var revealed by remember { mutableIntStateOf(0) }
        LaunchedEffect(game.projectId, sceneId, values) {
            val savedValues = JSONObject()
            values.forEach { (key,value) -> savedValues.put(key,value) }
            progressPrefs.edit().putString(game.projectId, JSONObject()
                .put("sceneId", sceneId ?: JSONObject.NULL)
                .put("values", savedValues).toString()).apply()
        }
        var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
        var audioPlaying by remember { mutableStateOf(false) }
        val scene = game.scenes.firstOrNull { it.id == sceneId }
        var remainingSeconds by remember(sceneId) { mutableIntStateOf(scene?.choiceTimerSeconds ?: 0) }
        DisposableEffect(scene?.audio?.absolutePath) {
            onDispose {
                audioPlayer?.let { runCatching { if (it.isPlaying) it.stop(); it.release() } }
                audioPlayer = null
                audioPlaying = false
            }
        }
        val ended = scene == null || sceneId == "__END__"
        LaunchedEffect(scene?.id) {
            revealed = 0
            val size = scene?.text?.length ?: 0
            for (i in 1..size) { revealed=i; delay(22) }
        }
        fun condition(exp:String):Boolean {
            if(exp.isBlank()) return true
            val m=Regex("^([\\p{L}A-Za-z0-9_]+)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+)$").matchEntire(exp.trim()) ?: return false
            val a=values[m.groupValues[1]] ?: 0; val b=m.groupValues[3].toIntOrNull() ?: return false
            return when(m.groupValues[2]) { ">="->a>=b; "<="->a<=b; "=="->a==b; "!="->a!=b; ">"->a>b; "<"->a<b; else->false }
        }
        fun choose(choice:Choice) {
            val m=Regex("^([\\p{L}A-Za-z0-9_]+)\\s*(=|\\+=|-=)\\s*(-?\\d+)$").matchEntire(choice.effect.trim())
            if(m!=null) {
                val name=m.groupValues[1]; val n=m.groupValues[3].toIntOrNull() ?: 0; val old=values[name] ?: 0
                values=values+(name to when(m.groupValues[2]) { "="->n; "+="->old+n; "-="->old-n; else->old })
            }
            sceneId=when {
                choice.target=="__END__" -> "__END__"
                !choice.target.isNullOrBlank() && game.scenes.any { it.id==choice.target } -> choice.target
                else -> game.scenes.getOrNull(game.scenes.indexOfFirst { it.id==scene?.id }+1)?.id ?: "__END__"
            }
        }
        LaunchedEffect(scene?.id, revealed) {
            val current = scene ?: return@LaunchedEffect
            if (current.choiceTimerSeconds <= 0 || revealed < current.text.length) return@LaunchedEffect
            val available = current.choices.filter { it.text.isNotBlank() && condition(it.condition) }
            if (available.isEmpty()) return@LaunchedEffect
            remainingSeconds = current.choiceTimerSeconds
            while (remainingSeconds > 0) { delay(1000); remainingSeconds-- }
            val timedChoice = available.getOrNull(current.timeoutChoiceIndex.coerceIn(0, available.lastIndex))
            if (timedChoice != null && sceneId == current.id) choose(timedChoice)
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("STORYFORGE PLAYER",color=Blue,fontSize=10.sp,fontWeight=FontWeight.Bold)
                Text(game.title,color=Gold,fontSize=12.sp,maxLines=1)
            }
            if(intro) {
                Spacer(Modifier.weight(1f))
                game.cover?.takeIf { it.isFile }?.let { file ->
                    val bitmap=remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
                    bitmap?.let { Image(it,null,Modifier.fillMaxWidth().height(260.dp),contentScale=androidx.compose.ui.layout.ContentScale.Crop) }
                }
                Text("✦",color=Gold,fontSize=38.sp,modifier=Modifier.align(Alignment.CenterHorizontally))
                Text(game.title,color=Gold,fontSize=27.sp,fontWeight=FontWeight.Black,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                Text(game.description,color=Color.LightGray,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                Button(onClick={intro=false},modifier=Modifier.fillMaxWidth()) { Text("ابدأ اللعبة  ▶") }
                Spacer(Modifier.weight(1f))
            } else if(ended) {
                Spacer(Modifier.weight(1f))
                Text("انتهت الحكاية",color=Gold,fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.CenterHorizontally))
                Button(onClick={sceneId=game.scenes.firstOrNull()?.id;values=game.variables;progressPrefs.edit().remove(game.projectId).apply()},modifier=Modifier.fillMaxWidth()) { Text("إعادة اللعب من البداية") }
                Spacer(Modifier.weight(1f))
            } else {
                if (game.showSceneNumbers) {
                    Text("المشهد " + (game.scenes.indexOf(scene!!) + 1) + " / " + game.scenes.size, color = Gold, fontSize = 11.sp)
                    LinearProgressIndicator(progress={ (game.scenes.indexOf(scene!!)+1).toFloat()/game.scenes.size.coerceAtLeast(1) },modifier=Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Bottom) {
                    if (scene.image != null || scene.environmentProps.isNotEmpty() || scene.atmosphereEffects.isNotEmpty()) {
                        Box(Modifier.fillMaxWidth().height(230.dp)) {
                            scene.image?.let { file ->
                                val bitmap=remember(file.absolutePath){BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()}
                                bitmap?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) }
                            } ?: Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17211B), Night))))
                            SceneDepthOverlay(effects=scene.atmosphereEffects,theme=game.visualTheme,layer="far",modifier=Modifier.fillMaxSize())
                            SceneEnvironmentOverlay(props=scene.environmentProps,foreground=false,modifier=Modifier.fillMaxSize())
                            AtmosphereOverlay(effects=scene.atmosphereEffects,theme=game.visualTheme,intensity=scene.atmosphereIntensity,modifier=Modifier.fillMaxSize())
                            SceneEnvironmentOverlay(props=scene.environmentProps,foreground=true,modifier=Modifier.fillMaxSize())
                            SceneDepthOverlay(effects=scene.atmosphereEffects,theme=game.visualTheme,layer="near",modifier=Modifier.fillMaxSize())
                        }
                    }
                    Text(scene!!.title,color=Gold,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    Text(scene.text.take(revealed),color=Color.White,fontSize=16.sp,lineHeight=27.sp,modifier=Modifier.clickable{revealed=scene.text.length})
                    scene.speakerLines.forEach { (speaker,line) ->
                        Column(Modifier.fillMaxWidth().padding(top=8.dp).background(Color(0xFF202B38),RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(speaker,color=Gold,fontWeight=FontWeight.Bold); Text(line,color=Color.White)
                        }
                    }
                    scene.audio?.let { audioFile ->
                        OutlinedButton(onClick = {
                            runCatching {
                                val existing = audioPlayer
                                if (existing == null) {
                                    val created = MediaPlayer()
                                    created.setDataSource(this@PlayerActivity, Uri.fromFile(audioFile))
                                    created.isLooping = true
                                    created.setOnPreparedListener { it.start(); audioPlaying = true }
                                    created.setOnErrorListener { mp, _, _ -> runCatching { mp.release() }; audioPlayer = null; audioPlaying = false; true }
                                    created.prepareAsync()
                                    audioPlayer = created
                                } else if (existing.isPlaying) {
                                    existing.pause(); audioPlaying = false
                                } else {
                                    existing.start(); audioPlaying = true
                                }
                            }.onFailure { audioPlaying = false }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (audioPlaying) "Ⅱ إيقاف صوت المشهد" else "♫ تشغيل صوت المشهد")
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if(revealed>=scene.text.length) {
                        val available=scene.choices.filter { it.text.isNotBlank() && condition(it.condition) }
                        if (scene.choiceTimerSeconds > 0 && available.isNotEmpty()) Text("سيتم اختيار القرار تلقائيًا خلال $remainingSeconds ثانية", color=Gold, fontSize=12.sp)
                        if(available.isEmpty()) {
                            Button(onClick={sceneId=game.scenes.getOrNull(game.scenes.indexOf(scene)+1)?.id ?: "__END__"},modifier=Modifier.fillMaxWidth()) { Text("متابعة") }
                        } else available.forEach { choice ->
                            AnimatedVisibility(true,enter=fadeIn()+slideInVertically { it/3 }) {
                                OutlinedButton(onClick={choose(choice)},modifier=Modifier.fillMaxWidth().padding(bottom=6.dp)) { Text(choice.text,modifier=Modifier.fillMaxWidth()) }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    values.forEach { (name,value) -> Text("$name: $value",color=Blue,fontSize=11.sp) }
                }
            }
        }
    }
}
