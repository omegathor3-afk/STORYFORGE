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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onExport: () -> Unit,
    onOpenWorkflow: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("مركز بناء الألعاب", color = BuildGold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("حوّل قصتك إلى حزمة لعبة، ثم ابنِ منها تطبيق Android مستقل باسم وأيقونة خاصين.", color = BuildMuted, fontSize = 13.sp)
        Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("حالة البناء السحابي", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("إعداد يدوي", color = BuildGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text("البناء من داخل التطبيق بنقرة واحدة قيد التطوير. حاليًا نستخدم GitHub Actions كخدمة بناء آمنة؛ مفاتيح التوقيع لا توضع داخل التطبيق.", color = BuildMuted, fontSize = 12.sp)
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
                    Spacer(Modifier.height(3.dp))
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
            if (diagnostics.any { it.isError }) {
                Text("أصلح الأخطاء أولًا من الاستوديو حتى لا تبني نسخة بها روابط أو شروط غير صالحة.", color = BuildRed, fontSize = 12.sp)
            } else {
                Text("سيتم حفظ ملف القصة مع الصور والصوت المرتبطين بها. احتفظ به لاستخدامه في خطوة البناء.", color = BuildMuted, fontSize = 11.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = BuildPanel), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("② إنشاء APK من GitHub Actions", color = BuildGold, fontWeight = FontWeight.Bold)
                Text("بعد تصدير ملف .storygame، افتح صفحة البناء واتبع الخطوات:", color = BuildMuted, fontSize = 12.sp)
                Text("1. اضغط Run workflow.", color = Color.White, fontSize = 12.sp)
                Text("2. أدخل رابط HTTPS مباشر لملف .storygame مستضاف على الإنترنت.", color = Color.White, fontSize = 12.sp)
                Text("3. اكتب اسم اللعبة ومعرّف التطبيق والإصدار، ثم اختر Debug أو Release.", color = Color.White, fontSize = 12.sp)
                Text("4. نزّل APK من Artifacts بعد نجاح البناء.", color = Color.White, fontSize = 12.sp)
                OutlinedButton(onClick = onOpenWorkflow, modifier = Modifier.fillMaxWidth()) { Text("فتح صفحة بناء APK ↗") }
                Text("ملاحظة: Release يحتاج إعداد أسرار التوقيع مرة واحدة في إعدادات المستودع. لا تشارك ملف keystore أو كلمات مروره.", color = BuildMuted, fontSize = 11.sp)
            }
        }
    }
}
