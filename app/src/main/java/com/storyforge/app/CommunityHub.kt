package com.storyforge.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CommunityPanel = Color(0xFF171C22)
private val CommunityGold = Color(0xFFD6B477)
private val CommunityMuted = Color(0xFF9BA4AE)
private val CommunityGreen = Color(0xFF8CBAA5)
private val CommunityRed = Color(0xFFE18B83)

private data class CommunityPost(
    val id: String,
    val userId: String,
    val title: String,
    val body: String,
    val genre: String,
    val author: String,
    val likes: Int,
    val comments: Int,
    val liked: Boolean,
    val hasStory: Boolean
)

private data class CommunityComment(val id: String, val author: String, val body: String)
private data class CommunityMember(val userId: String, val name: String, val bio: String, val followers: Int, val following: Boolean)

private suspend fun communityRequest(
    baseUrl: String,
    token: String,
    path: String,
    method: String = "GET",
    payload: JSONObject? = null
): JSONObject = withContext(Dispatchers.IO) {
    require(baseUrl.startsWith("https://")) { "رابط المجتمع يجب أن يبدأ بـ https://" }
    val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        if (payload != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(response).optString("message") }.getOrNull()
            throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "تعذر الاتصال بالمجتمع (HTTP $code).")
        }
        if (response.isBlank()) JSONObject() else JSONObject(response)
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun CommunityHub(
    apiBaseUrl: String,
    accessToken: String?,
    projectJson: String?,
    onImportStory: (String) -> Unit,
    onOpenBuildCenter: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<CommunityPost>() }
    var profileName by remember { mutableStateOf("") }
    var profileBio by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("horror") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var expandedComments by remember { mutableStateOf<String?>(null) }
    var commentDraft by remember { mutableStateOf("") }
    var comments by remember { mutableStateOf<List<CommunityComment>>(emptyList()) }
    var commentsBusy by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("all") }
    var attachCurrentStory by remember { mutableStateOf(true) }
    var memberQuery by remember { mutableStateOf("") }
    var members by remember { mutableStateOf<List<CommunityMember>>(emptyList()) }
    var membersBusy by remember { mutableStateOf(false) }

    fun loadFeed(showSpinner: Boolean = true) {
        val token = accessToken ?: return
        if (apiBaseUrl.isBlank()) { message = "أدخل رابط خدمة المجتمع في إعدادات مركز البناء أولًا."; return }
        scope.launch {
            if (showSpinner) loading = true
            runCatching {
                val query = if (selectedFilter == "all") "" else "?genre=$selectedFilter"
                val response = communityRequest(apiBaseUrl, token, "/api/community/feed$query")
                val array = response.optJSONArray("posts") ?: JSONArray()
                val fresh = (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    CommunityPost(
                        id = item.optString("id"), userId = item.optString("user_id"),
                        title = item.optString("title"), body = item.optString("body"),
                        genre = item.optString("genre"), author = item.optString("author_name", "كاتب"),
                        likes = item.optInt("likes_count"), comments = item.optInt("comments_count"),
                        liked = item.optBoolean("liked"),
                        hasStory = item.optBoolean("has_story")
                    )
                }
                posts.clear()
                posts.addAll(fresh)
                message = if (fresh.isEmpty()) "لسه مفيش منشورات هنا. كن أول من يشارك قصة!" else "تم تحديث المجتمع."
            }.onFailure { message = it.message ?: "تعذر تحميل المنشورات." }
            loading = false
        }
    }

    LaunchedEffect(apiBaseUrl, accessToken, selectedFilter) {
        if (!accessToken.isNullOrBlank() && apiBaseUrl.isNotBlank()) {
            runCatching {
                val profile = communityRequest(apiBaseUrl, accessToken, "/api/community/profile").optJSONObject("profile")
                if (profile != null) {
                    profileName = profile.optString("display_name")
                    profileBio = profile.optString("bio")
                }
            }
            loadFeed()
            while (true) {
                delay(30000)
                loadFeed(false)
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مجتمع STORYFORGE", color = CommunityGold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("مساحة يشارك فيها الكتّاب عوالمهم، يتناقشون، ويتابعون بعضهم.", color = CommunityMuted, fontSize = 13.sp)
        Card(colors = CardDefaults.cardColors(containerColor = CommunityPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("حالة الاتصال", color = CommunityGold, fontWeight = FontWeight.Bold)
                    Text(if (accessToken != null && apiBaseUrl.startsWith("https://")) "● متصل" else "○ غير متصل",
                        color = if (accessToken != null && apiBaseUrl.startsWith("https://")) CommunityGreen else CommunityRed)
                }
                if (accessToken == null) {
                    Text("سجّل الدخول من مركز بناء APK لاستخدام حساب المجتمع.", color = CommunityMuted, fontSize = 12.sp)
                    Button(onClick = onOpenBuildCenter, modifier = Modifier.fillMaxWidth()) { Text("إعداد الحساب والاتصال") }
                } else if (apiBaseUrl.isBlank()) {
                    Text("خدمة المجتمع لم تُضبط بعد. أدخل رابط الخدمة بعد نشرها.", color = CommunityMuted, fontSize = 12.sp)
                    Button(onClick = onOpenBuildCenter, modifier = Modifier.fillMaxWidth()) { Text("فتح إعدادات الاتصال") }
                } else {
                    Text("تتحدث المنشورات تلقائيًا كل 30 ثانية.", color = CommunityGreen, fontSize = 11.sp)
                    OutlinedButton(onClick = { loadFeed() }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                        Text(if (loading) "جارٍ التحديث…" else "↻ تحديث المجتمع")
                    }
                }
            }
        }

        if (accessToken != null && apiBaseUrl.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = CommunityPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ملفك في المجتمع", color = CommunityGold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = profileName, onValueChange = { profileName = it }, label = { Text("اسم العرض") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = profileBio, onValueChange = { profileBio = it }, label = { Text("نبذة قصيرة") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                communityRequest(apiBaseUrl, accessToken, "/api/community/profile", "POST",
                                    JSONObject().put("displayName", profileName).put("bio", profileBio))
                                message = "تم حفظ ملفك."
                            }.onFailure { message = it.message ?: "تعذر حفظ الملف." }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الملف") }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CommunityPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("انشر حكاية أو فكرة", color = CommunityGold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = title, onValueChange = { title = it.take(80) }, label = { Text("عنوان المنشور") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = body, onValueChange = { body = it.take(4000) }, label = { Text("احكِ للمجتمع عن قصتك…") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("horror" to "رعب","fantasy" to "فانتازيا","mystery" to "غموض","adventure" to "مغامرة","scifi" to "خيال علمي","other" to "أخرى").forEach { (key,label) ->
                            Text(label, color = if (genre == key) CommunityGold else CommunityMuted, modifier = Modifier.clickable { genre = key }.padding(5.dp), fontSize = 11.sp)
                        }
                    }
                    if (!projectJson.isNullOrBlank()) {
                        TextButton(onClick = { attachCurrentStory = !attachCurrentStory }) {
                            Text(if (attachCurrentStory) "✓ إرفاق المشروع الحالي" else "＋ إرفاق المشروع الحالي",
                                color = CommunityGreen)
                        }
                    }
                    Button(onClick = {
                        scope.launch {
                            runCatching {
                                val payload = JSONObject().put("title", title.trim()).put("body", body.trim()).put("genre", genre)
                                if (attachCurrentStory && !projectJson.isNullOrBlank()) {
                                    payload.put("story", JSONArray(projectJson).optJSONObject(0))
                                }
                                communityRequest(apiBaseUrl, accessToken, "/api/community/feed", "POST", payload)
                                title = ""; body = ""; message = "تم نشر منشورك في المجتمع."; loadFeed(false)
                            }.onFailure { message = it.message ?: "تعذر النشر." }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = title.isNotBlank()) { Text("نشر في المجتمع") }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = CommunityPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اكتشف الكتّاب", color = CommunityGold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = memberQuery, onValueChange = { memberQuery = it.take(40) },
                        label = { Text("ابحث باسم الكاتب") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton(onClick = {
                        scope.launch {
                            membersBusy = true
                            runCatching {
                                val query = java.net.URLEncoder.encode(memberQuery.trim(), "UTF-8")
                                val response = communityRequest(apiBaseUrl, accessToken, "/api/community/members?q=$query")
                                val array = response.optJSONArray("members") ?: JSONArray()
                                members = (0 until array.length()).mapNotNull { i ->
                                    array.optJSONObject(i)?.let { CommunityMember(
                                        it.optString("user_id"), it.optString("display_name"), it.optString("bio"),
                                        it.optInt("followers_count"), it.optBoolean("following")
                                    ) }
                                }
                            }.onFailure { message = it.message ?: "تعذر البحث عن الكتّاب." }
                            membersBusy = false
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !membersBusy) { Text(if (membersBusy) "جارٍ البحث…" else "بحث عن كتّاب") }
                    members.forEach { member ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(member.name, color = Color(0xFFE8E4DC), fontWeight = FontWeight.Bold)
                                if (member.bio.isNotBlank()) Text(member.bio, color = CommunityMuted, fontSize = 11.sp)
                                Text("${member.followers} متابع", color = CommunityMuted, fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        communityRequest(apiBaseUrl, accessToken, "/api/community/follow", "POST", JSONObject().put("userId",member.userId))
                                        members = members.map { if (it.userId == member.userId) it.copy(following = !it.following) else it }
                                    }.onFailure { message = it.message ?: "تعذر تحديث المتابعة." }
                                }
                            }) { Text(if (member.following) "تتابعه ✓" else "＋ متابعة", color = CommunityGreen) }
                        }
                    }
                }
            }

            Text("استكشف القصص", color = CommunityGold, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "الكل","horror" to "رعب","fantasy" to "فانتازيا","mystery" to "غموض","adventure" to "مغامرة").forEach { (key,label) ->
                    Text(label, color = if (selectedFilter == key) CommunityGold else CommunityMuted,
                        modifier = Modifier.clickable { selectedFilter = key }.padding(5.dp), fontSize = 12.sp)
                }
            }
            if (posts.isEmpty()) {
                Text(if (loading) "جارٍ تحميل القصص…" else "لا توجد منشورات ظاهرة حاليًا.", color = CommunityMuted, fontSize = 13.sp)
            }
            posts.forEach { post ->
                Card(colors = CardDefaults.cardColors(containerColor = CommunityPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(post.author, color = CommunityGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(post.genre, color = CommunityMuted, fontSize = 11.sp)
                        }
                        Text(post.title, color = CommunityGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (post.body.isNotBlank()) Text(post.body, color = Color(0xFFE8E4DC), fontSize = 13.sp)
                        if (post.hasStory) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        val detail = communityRequest(apiBaseUrl, accessToken, "/api/community/posts/${post.id}")
                                        val story = detail.optJSONObject("post")?.optJSONObject("story_json")
                                            ?: error("المنشور لا يحتوي على ملف قصة صالح.")
                                        onImportStory(JSONArray().put(story).toString())
                                        message = "تم استيراد نسخة من القصة إلى المكتبة."
                                    }.onFailure { message = it.message ?: "تعذر تنزيل القصة." }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("↓ استيراد القصة إلى مكتبتي") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        communityRequest(apiBaseUrl, accessToken, "/api/community/posts/${post.id}/reactions", "POST",
                                            JSONObject().put("reaction","like"))
                                        loadFeed(false)
                                    }.onFailure { message = it.message ?: "تعذر تسجيل الإعجاب." }
                                }
                            }) { Text((if (post.liked) "♥" else "♡") + " ${post.likes}", color = CommunityGold) }
                            TextButton(onClick = {
                                expandedComments = if (expandedComments == post.id) null else post.id
                                if (expandedComments != post.id) scope.launch {
                                    commentsBusy = true
                                    runCatching {
                                        val response = communityRequest(apiBaseUrl, accessToken, "/api/community/posts/${post.id}/comments")
                                        val array = response.optJSONArray("comments") ?: JSONArray()
                                        comments = (0 until array.length()).mapNotNull { i ->
                                            array.optJSONObject(i)?.let { CommunityComment(it.optString("id"),it.optString("author_name","كاتب"),it.optString("body")) }
                                        }
                                    }.onFailure { message = it.message ?: "تعذر تحميل التعليقات." }
                                    commentsBusy = false
                                }
                            }) { Text("تعليقات ${post.comments}", color = CommunityMuted) }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        communityRequest(apiBaseUrl, accessToken, "/api/community/follow", "POST", JSONObject().put("userId",post.userId))
                                        message = "تم تحديث قائمة المتابعة."
                                    }.onFailure { message = it.message ?: "تعذر متابعة الكاتب." }
                                }
                            }) { Text("＋ متابعة", color = CommunityGreen) }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching {
                                        communityRequest(apiBaseUrl, accessToken, "/api/community/reports", "POST",
                                            JSONObject().put("targetType","post").put("targetId",post.id).put("reason","other"))
                                        message = "تم استلام البلاغ."
                                    }.onFailure { message = it.message ?: "تعذر إرسال البلاغ." }
                                }
                            }) { Text("إبلاغ", color = CommunityRed) }
                        }
                        if (expandedComments == post.id) {
                            if (commentsBusy) Text("جارٍ تحميل التعليقات…", color = CommunityMuted)
                            comments.forEach { comment ->
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(comment.author, color = CommunityGreen, fontSize = 11.sp)
                                    Text(comment.body, color = Color(0xFFE8E4DC), fontSize = 12.sp)
                                }
                            }
                            OutlinedTextField(value = commentDraft, onValueChange = { commentDraft = it.take(1200) },
                                label = { Text("اكتب تعليقًا") }, modifier = Modifier.fillMaxWidth())
                            Button(onClick = {
                                scope.launch {
                                    runCatching {
                                        communityRequest(apiBaseUrl, accessToken, "/api/community/posts/${post.id}/comments", "POST",
                                            JSONObject().put("body",commentDraft.trim()))
                                        commentDraft = ""
                                        val response = communityRequest(apiBaseUrl, accessToken, "/api/community/posts/${post.id}/comments")
                                        val array = response.optJSONArray("comments") ?: JSONArray()
                                        comments = (0 until array.length()).mapNotNull { i ->
                                            array.optJSONObject(i)?.let { CommunityComment(it.optString("id"),it.optString("author_name","كاتب"),it.optString("body")) }
                                        }
                                        loadFeed(false)
                                    }.onFailure { message = it.message ?: "تعذر إضافة التعليق." }
                                }
                            }, enabled = commentDraft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("إرسال التعليق") }
                        }
                    }
                }
            }
        }
        if (message.isNotBlank()) Text(message, color = CommunityMuted, fontSize = 12.sp)
        Text("المجتمع يحتاج نشر خدمة Cloud Builder وتطبيق مخطط قاعدة البيانات قبل استقبال أعضاء حقيقيين.", color = CommunityMuted, fontSize = 11.sp)
    }
}
