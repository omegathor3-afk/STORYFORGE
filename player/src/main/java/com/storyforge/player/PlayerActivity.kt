package com.storyforge.player

import android.graphics.BitmapFactory
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
import java.util.zip.ZipInputStream

private val Night = Color(0xFF05070B)
private val Gold = Color(0xFFE0C58C)
private val Blue = Color(0xFF91C8D7)
private data class Choice(val text:String,val target:String?,val condition:String,val effect:String)
private data class Scene(val id:String,val title:String,val text:String,val choices:List<Choice>,val speakerLines:List<Pair<String,String>>)
private data class Game(val title:String,val description:String,val scenes:List<Scene>,val variables:Map<String,Int>,val cover:File?)

class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PlayerScreen() }
    }

    @Composable private fun PlayerScreen() {
        var game by remember { mutableStateOf<Game?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) runCatching {
                val root = File(filesDir, "installed_game").apply { deleteRecursively(); mkdirs() }
                contentResolver.openInputStream(uri)?.use { raw ->
                    ZipInputStream(raw.buffered()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            require(!entry.name.startsWith("/") && entry.name.split('/').none { it == ".." || it.isBlank() }) { "مسار غير آمن داخل الحزمة" }
                            if (entry.isDirectory) continue
                            val relative = if (entry.name.startsWith("assets/")) entry.name.removePrefix("assets/") else entry.name
                            require(relative == "game-manifest.json" || relative == "storyforge-project.json" || relative.startsWith("storyforge_assets/")) { "ملف غير مدعوم داخل الحزمة" }
                            val out = File(root, relative).canonicalFile
                            require(out.path.startsWith(root.canonicalPath + File.separator))
                            out.parentFile?.mkdirs()
                            out.outputStream().buffered().use { zip.copyTo(it) }
                        }
                    }
                } ?: error("تعذر قراءة الملف")
                val manifest = JSONObject(File(root,"game-manifest.json").readText())
                require(manifest.optString("format") == "storyforge-game" && manifest.optInt("formatVersion") == 1) { "إصدار حزمة اللعبة غير مدعوم" }
                val payload = org.json.JSONArray(File(root,"storyforge-project.json").readText())
                require(payload.length() == 1) { "يجب أن تحتوي الحزمة على قصة واحدة" }
                val p = payload.getJSONObject(0)
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
                    Scene(s.optString("id"),s.optString("title"),s.optString("text"),choices,lines)
                }
                val vars = linkedMapOf<String,Int>()
                val varsJson = p.optJSONArray("variables")
                for (i in 0 until (varsJson?.length() ?: 0)) {
                    val v = varsJson!!.getJSONObject(i); vars[v.optString("name")] = v.optInt("initialValue")
                }
                val assets = p.optJSONArray("assets")
                var cover: File? = null
                for (i in 0 until (assets?.length() ?: 0)) {
                    val a = assets!!.getJSONObject(i)
                    if (a.optString("mimeType").startsWith("image/") && a.isNull("sceneId")) {
                        cover = File(root,a.optString("relativePath")); break
                    }
                }
                if (cover == null) {
                    for (i in 0 until (assets?.length() ?: 0)) {
                        val a = assets!!.getJSONObject(i)
                        if (a.optString("mimeType").startsWith("image/")) { cover = File(root,a.optString("relativePath")); break }
                    }
                }
                game = Game(p.optString("title",manifest.optString("title")),p.optString("description"),scenes,vars,cover)
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
        var intro by remember(game.title) { mutableStateOf(true) }
        var sceneId by remember(game.title) { mutableStateOf(game.scenes.firstOrNull()?.id) }
        var values by remember(game.title) { mutableStateOf(game.variables) }
        var revealed by remember { mutableIntStateOf(0) }
        val scene = game.scenes.firstOrNull { it.id == sceneId }
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
                Button(onClick={sceneId=game.scenes.firstOrNull()?.id;values=game.variables},modifier=Modifier.fillMaxWidth()) { Text("إعادة اللعب") }
                Spacer(Modifier.weight(1f))
            } else {
                LinearProgressIndicator(progress={ (game.scenes.indexOf(scene!!)+1).toFloat()/game.scenes.size.coerceAtLeast(1) },modifier=Modifier.fillMaxWidth())
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.Bottom) {
                    val image = remember(scene?.id) { scene?.let { current ->
                        val root=File(filesDir,"installed_game")
                        val files=root.walkTopDown().filter { it.isFile && it.extension.lowercase() in listOf("png","jpg","jpeg","webp") }.toList()
                        val p=game.title
                        files.firstOrNull { it.nameWithoutExtension.contains(current.id.take(8)) }
                    } }
                    image?.let { file -> val bitmap=remember(file.absolutePath){BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()}; bitmap?.let { Image(it,null,Modifier.fillMaxWidth().height(210.dp),contentScale=ContentScale.Crop) } }
                    Text(scene!!.title,color=Gold,fontSize=23.sp,fontWeight=FontWeight.Bold)
                    Text(scene.text.take(revealed),color=Color.White,fontSize=16.sp,lineHeight=27.sp,modifier=Modifier.clickable{revealed=scene.text.length})
                    scene.speakerLines.forEach { (speaker,line) ->
                        Column(Modifier.fillMaxWidth().padding(top=8.dp).background(Color(0xFF202B38),RoundedCornerShape(12.dp)).padding(12.dp)) {
                            Text(speaker,color=Gold,fontWeight=FontWeight.Bold); Text(line,color=Color.White)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if(revealed>=scene.text.length) {
                        val available=scene.choices.filter { it.text.isNotBlank() && condition(it.condition) }
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
