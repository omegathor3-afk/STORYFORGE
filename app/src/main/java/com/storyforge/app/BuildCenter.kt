package com.storyforge.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BuildPanel = Color(0xFF171C22)
private val BuildGold = Color(0xFFD6B477)
private val BuildMuted = Color(0xFF9BA4AE)
private val BuildGreen = Color(0xFF8CBAA5)
private val BuildRed = Color(0xFFE18B83)

@Composable
internal fun BuildCenter(
    project: StoryProject?,
    diagnostics: List<StoryDiagnostic>,
    cloudApiBaseUrl: String,
    oidcIssuer: String,
    oidcClientId: String,
    oidcAudience: String,
    isAuthenticated: Boolean,
    jobId: String?,
    jobStatus: String,
    artifactAvailable: Boolean,
    busy: Boolean,
    message: String,
    onSaveSettings: (String, String, String, String) -> Unit,
    onSignIn: (String, String, String) -> Unit,
    onStartBuild: (String, String, String, String, String) -> Unit,
    onRefresh: () -> Unit,
    onDownload: () -> Unit,
    onExport: () -> Unit,
    onOpenWorkflow: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    var apiUrl by remember(cloudApiBaseUrl) { mutableStateOf(cloudApiBaseUrl) }
    var issuer by remember(oidcIssuer) { mutableStateOf(oidcIssuer) }
    var clientId by remember(oidcClientId) { mutableStateOf(oidcClientId) }
    var audience by remember(oidcAudience) { mutableStateOf(oidcAudience) }
    var gameTitle by remember(project?.id) { mutableStateOf(project?.title ?: "") }
    var applicationId by remember(project?.id) { mutableStateOf("") }
    var versionName by remember { mutableStateOf("1.0.0") }
    var versionCode by remember { mutableStateOf("1") }
    var buildType by remember { mutableStateOf("debug") }
    val localizedJobStatus = when (jobStatus) {
        "queued" -> "في قائمة الانتظار"
        "in_progress" -> "قيد التنفيذ"
        "completed" -> "اكتمل البناء"
        "failed" -> "فشل البناء"
        else -> jobStatus
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("مركز بناء الألعاب", color = BuildGold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("حوّل قصتك إلى حزمة لعبة، ثم اطلب إنشاء APK مستقل ومتابعة النتيجة من داخل التطبيق.", color = BuildMuted, fontSize = 13.sp)

        Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("الاتصال بخدمة البناء", color = BuildGold, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = apiUrl, onValueChange = { apiUrl = it }, label = { Text("رابط خدمة Cloud Builder") }, placeholder = { Text("https://your-service.vercel.app") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = issuer, onValueChange = { issuer = it }, label = { Text("OIDC Issuer") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = clientId, onValueChange = { clientId = it }, label = { Text("OIDC Client ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = audience, onValueChange = { audience = it }, label = { Text("API Audience") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { onSaveSettings(apiUrl.trim(), issuer.trim(), clientId.trim(), audience.trim()) }, modifier = Modifier.weight(1f)) { Text("حفظ الإعدادات") }
                    Button(onClick = { onSignIn(issuer.trim(), clientId.trim(), audience.trim()) }, enabled = !busy && apiUrl.startsWith("https://") && issuer.startsWith("https://") && clientId.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text(if (isAuthenticated) "تجديد الدخول" else "تسجيل الدخول")
                    }
                }
                Text(if (isAuthenticated) "✓ تم تسجيل الدخول لهذه الجلسة" else "لم يتم تسجيل الدخول بعد. أدخل بيانات مزود OIDC الذي أعددته للخدمة.", color = if (isAuthenticated) BuildGreen else BuildMuted, fontSize = 12.sp)
            }
        }

        if (project == null) {
            Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("لا توجد قصة محددة", color = BuildGold, fontWeight = FontWeight.Bold)
                    Text("افتح قصة من المكتبة أولًا، وبعدها ارجع لمركز البناء.", color = BuildMuted)
                    Button(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) { Text("فتح مكتبة القصص") }
                }
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(project.title, color = BuildGold, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text(project.description.ifBlank { "لا يوجد وصف مضاف." }, color = BuildMuted, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("المشاهد: ${project.scenes.size}", color = Color.White, fontSize = 12.sp)
                        Text("الشخصيات: ${project.characters.size}", color = Color.White, fontSize = 12.sp)
                        Text("الوسائط: ${project.assets.size}", color = Color.White, fontSize = 12.sp)
                    }
                    val errors = diagnostics.count { it.isError }
                    val warnings = diagnostics.size - errors
                    Text(if (errors == 0) "✓ لا توجد أخطاء بنيوية مكتشفة" else "● يوجد ${errors} أخطاء تحتاج مراجعة",
                        color = if (errors == 0) BuildGreen else BuildRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (warnings > 0) Text("تنبيهات: $warnings", color = BuildGold, fontSize = 12.sp)
                    diagnostics.take(5).forEach { issue ->
                        Text("• ${issue.message}", color = if (issue.isError) BuildRed else BuildMuted, fontSize = 11.sp)
                    }
                    if (diagnostics.size > 5) Text("و ${diagnostics.size - 5} ملاحظات أخرى في فحص القصة داخل الاستوديو.", color = BuildMuted, fontSize = 11.sp)
                }
            }
            Button(onClick = onExport, modifier = Modifier.fillMaxWidth(), enabled = diagnostics.none { it.isError }) {
                Text("① تصدير حزمة القصة .storygame")
            }
            Text("يمكنك تصدير نسخة احتياطية يدويًا. عند بدء البناء السحابي، سيُنشئ التطبيق ملف .storygame تلقائيًا ويرفعه إلى التخزين الخاص دون الحاجة إلى رابط خارجي.", color = BuildMuted, fontSize = 11.sp)

            Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("② إنشاء APK سحابي", color = BuildGold, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = gameTitle, onValueChange = { gameTitle = it.take(40) }, label = { Text("اسم اللعبة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = applicationId, onValueChange = { applicationId = it }, label = { Text("Application ID (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = versionName, onValueChange = { versionName = it }, label = { Text("الإصدار") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(value = versionCode, onValueChange = { versionCode = it }, label = { Text("Version Code") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { buildType = "debug" }) { Text(if (buildType == "debug") "✓ Debug" else "Debug") }
                        TextButton(onClick = { buildType = "release" }) { Text(if (buildType == "release") "✓ Release" else "Release") }
                    }
                    Button(
                        onClick = { onStartBuild(gameTitle.trim(), applicationId.trim(), versionName.trim(), versionCode.trim(), buildType) },
                        enabled = !busy && isAuthenticated && diagnostics.none { it.isError } && gameTitle.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (busy) "جارٍ إرسال الطلب..." else "③ ابدأ بناء APK") }
                    if (message.isNotBlank()) Text(message, color = BuildMuted, fontSize = 12.sp)
                }
            }
        }

        if (jobId != null) {
            Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("حالة طلب البناء", color = BuildGold, fontWeight = FontWeight.Bold)
                    Text("رقم الطلب: $jobId", color = BuildMuted, fontSize = 11.sp)
                    Text("الحالة: $localizedJobStatus", color = when (jobStatus) { "completed" -> BuildGreen; "failed" -> BuildRed; else -> BuildMuted }, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRefresh, enabled = !busy) { Text("تحديث الحالة") }
                        if (artifactAvailable) Button(onClick = onDownload, enabled = !busy) { Text("تنزيل APK ZIP") }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("البناء اليدوي الاحتياطي", color = BuildGold, fontWeight = FontWeight.Bold)
                Text("إذا لم تُعد خدمة Cloud Builder بعد، يمكنك فتح GitHub Actions وتشغيل البناء يدويًا.", color = BuildMuted, fontSize = 12.sp)
                OutlinedButton(onClick = onOpenWorkflow, modifier = Modifier.fillMaxWidth()) { Text("فتح صفحة بناء APK ↗") }
            }
        }
    }
}
