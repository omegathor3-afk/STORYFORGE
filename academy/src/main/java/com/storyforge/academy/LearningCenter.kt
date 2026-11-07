package com.storyforge.academy

import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class GuideLesson(
    val title: String,
    val time: String,
    val summary: String,
    val steps: List<String>,
    val task: String,
    val tip: String
)

private data class GuideQuiz(val question: String, val options: List<String>, val correct: Int)

private val guideQuizzes = listOf(
    GuideQuiz("ما أفضل بداية لمشروع قصصي؟", listOf("كتابة كل النهايات فورًا", "تحديد فكرة العالم ثم إنشاء مشاهد قليلة", "إضافة مؤثرات كثيرة"), 1),
    GuideQuiz("ما وظيفة المشهد؟", listOf("تمثيل مكان أو لحظة في القصة", "حفظ كلمة المرور", "تغيير لغة الهاتف"), 0),
    GuideQuiz("كيف تجعل الحوار أوضح؟", listOf("جمع كل الكلام في فقرة", "حذف أسماء المتحدثين", "إضافة سطر مستقل لكل متحدث"), 2),
    GuideQuiz("ماذا يفعل الاختيار المرتبط بوجهة؟", listOf("ينقل اللاعب إلى مشهد مستهدف", "يحذف المشروع", "يعيد ضبط الهاتف"), 0),
    GuideQuiz("متى يظهر اختيار عليه شرط trust>=1؟", listOf("دائمًا", "عندما تكون قيمة trust واحدًا أو أكثر", "عندما تكون القيمة صفرًا فقط"), 1),
    GuideQuiz("ما الذي يضيفه ملف الصوت للمشهد؟", listOf("يغير وجهة الاختيار", "يصنع خلفية صوتية أو مؤثرًا", "ينشئ متغيرًا تلقائيًا"), 1),
    GuideQuiz("ما أفضل طريقة لاكتشاف مسار مكسور؟", listOf("تجربة الاختيارات والنهايات من المعاينة", "تغيير لون التطبيق", "إغلاق المشروع دون حفظ"), 0),
    GuideQuiz("هل ملف .storygame هو APK مستقل؟", listOf("نعم، دائمًا", "لا، هو حزمة محتوى تُفتح في Player", "هو ملف صورة"), 1)
)

private val guideLessons = listOf(
    GuideLesson("ابدأ مشروعك الأول", "دقيقتان", "أنشئ عالم قصتك وحدد فكرته قبل كتابة التفاصيل.",
        listOf("افتح تبويب المكتبة واضغط إنشاء مشروع جديد.", "اكتب عنوانًا واضحًا ووصفًا قصيرًا للعالم.", "افتح المشروع من المكتبة للانتقال إلى الاستوديو."),
        "أنشئ قصة قصيرة بعنوان تختاره، واكتب جملة واحدة تصف عالمها.", "ابدأ صغيرًا: ثلاثة مشاهد كافية لتجربة أولى."),
    GuideLesson("ابنِ المشاهد", "3 دقائق", "كل مشهد مكان أو لحظة في القصة، وله نص وصورة وصوت اختياري.",
        listOf("من الاستوديو اضغط إضافة مشهد.", "اكتب اسم المشهد ثم وصف ما يراه اللاعب وما يحدث.", "اربط صورة أو ملفًا صوتيًا بالمشهد من أدوات الوسائط.", "رتّب المشاهد من البداية إلى النهاية."),
        "أنشئ مشهد بداية ومشهدًا ثانيًا، واجعل لكل واحد عنوانًا ونصًا.", "اكتب وصفًا حسيًا: صوت، إضاءة، رائحة، وحركة."),
    GuideLesson("اكتب حوار الشخصيات", "دقيقتان", "اجعل الشخصيات تتحدث بأصوات مختلفة بدل سرد كل شيء في فقرة واحدة.",
        listOf("افتح المشهد المطلوب في الاستوديو.", "انتقل إلى قسم حوارات المشهد واضغط سطر حوار.", "اكتب اسم المتحدث ونص كلامه.", "أضف سطرًا جديدًا لكل رد حتى يظهر الحوار بالترتيب."),
        "أضف حوارًا من ثلاثة أسطر بين شخصيتين.", "اجعل لكل شخصية طريقة كلام ومعلومة لا يعرفها الآخر."),
    GuideLesson("اصنع اختيارات وتفرعات", "4 دقائق", "الاختيار ينقل اللاعب إلى مشهد آخر، ويمكن أن يفتح أكثر من مسار.",
        listOf("اكتب نص الاختيار في أحد حقول اختيارات اللاعب.", "اضغط زر الوجهة لتدوير المشاهد المستهدفة حتى تصل للمشهد المطلوب.", "استخدم وجهة نهاية القصة لإنهاء المسار.", "أضف اختيارين مختلفين ليشعر اللاعب أن قراره مهمًا."),
        "أنشئ اختيارًا شجاعًا ينقل إلى مشهد، واختيارًا حذرًا ينقل إلى مشهد آخر.", "لا تجعل الاختيارين يؤديان إلى النتيجة نفسها دائمًا."),
    GuideLesson("استخدم المتغيرات والشروط", "5 دقائق", "المتغيرات تحفظ آثار القرارات مثل الثقة أو المفاتيح أو الخطر.",
        listOf("أضف متغيرًا من قسم متغيرات القصة، مثل trust بقيمة 0.", "في تأثير اختيار اكتب trust+=1 لزيادة الثقة.", "في شرط الظهور اكتب trust>=1 لإظهار الاختيار عند تحقق الشرط.", "جرّب المسارات من المعاينة ولاحظ تغير القيم."),
        "أنشئ متغيرًا اسمه keys، واجعل اختيارًا يزيده ثم اجعل بابًا يتطلب keys>=1.", "استخدم أسماء قصيرة بلا مسافات لتسهيل كتابة الشروط."),
    GuideLesson("أضف الصور والأصوات", "3 دقائق", "الصورة والصوت يصنعان الجو، لكن وضوح النص يظل مهمًا.",
        listOf("حدد المشهد الذي تريد تزيينه.", "استورد صورة للمشهد من قسم الوسائط.", "استورد صوتًا مناسبًا مثل رياح أو خطوات أو همس.", "عاين المشهد وتأكد أن النص واضح فوق الخلفية."),
        "أضف صورة واحدة وصوتًا واحدًا لمشهدك الأول.", "اختر صوتًا هادئًا ومتكررًا بدل مؤثر مرتفع يزعج اللاعب."),
    GuideLesson("اختبر القصة وأصلحها", "3 دقائق", "المعاينة تكشف المسارات المكسورة والشروط التي تمنع الاختيارات.",
        listOf("افتح تبويب المعاينة.", "ابدأ التجربة واتخذ كل اختيار مرة.", "ارجع للاستوديو وافحص تنبيهات وأخطاء القصة.", "تأكد أن كل وجهة تشير إلى مشهد موجود أو إلى نهاية القصة."),
        "العب قصتك من البداية حتى كل نهاية صنعتها.", "اختبر القصة كأنك لاعب لا يعرف ما كنت تقصده."),
    GuideLesson("صدّر لعبتك وشاركها", "دقيقتان", "احتفظ بنسخة قابلة للنقل، أو صدّر حزمة اللعبة لتشغيلها في Player.",
        listOf("من تبويب المعاينة اختر تصدير اللعبة كحزمة مستقلة.", "احفظ ملف storygame في مكان يسهل الوصول إليه.", "افتح STORYFORGE PLAYER واستورد الحزمة.", "استخدم النسخة الاحتياطية ZIP لحفظ المشاريع والوسائط كاملة."),
        "صدّر مشروعك التجريبي وافتحه في STORYFORGE PLAYER.", "ملف storygame حزمة محتوى؛ ليس APK مستقلًا بحد ذاته.")
)

@Composable
fun LearningCenter(onOpenStudio: (() -> Unit)? = null) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("storyforge_learning", Context.MODE_PRIVATE) }
    val completed = remember { mutableStateListOf<Int>().apply {
        val saved = prefs.getStringSet("completed_lessons", emptySet()).orEmpty()
        saved.mapNotNull { it.toIntOrNull() }.filter { it in guideLessons.indices }.forEach { add(it) }
    } }
    var selected by remember { mutableIntStateOf(-1) }
    var quizAnswer by remember(selected) { mutableIntStateOf(-1) }
    var quizChecked by remember(selected) { mutableStateOf(false) }
    var showGlossary by remember { mutableStateOf(false) }
    val progress = completed.size.toFloat() / guideLessons.size.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("أكاديمية STORYFORGE", color = Color(0xFFD6B477), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("دليل عملي تفاعلي يعلمك صناعة لعبة قصصية خطوة بخطوة، من أول مشروع حتى تصدير اللعبة.", color = Color(0xFF9BA4AE), lineHeight = 22.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171C22)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("تقدمك في التعلم", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${completed.size} / ${guideLessons.size}", color = Color(0xFFD6B477))
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(7.dp),
                    color = Color(0xFFD6B477), trackColor = Color(0xFF343B43))
                Text(if (completed.size == guideLessons.size) "أحسنت! أنهيت المسار الأساسي." else "كل درس قصير وينتهي بمهمة تطبقها داخل الاستوديو.", color = Color(0xFF9BA4AE), fontSize = 12.sp)
            }
        }
        if (selected in guideLessons.indices) {
            val lesson = guideLessons[selected]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selected = -1 }) { Text("← كل الدروس", color = Color(0xFFD6B477)) }
                Text("الدرس ${selected + 1} من ${guideLessons.size}", color = Color(0xFF9BA4AE), fontSize = 11.sp)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171C22)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(lesson.title, color = Color(0xFFD6B477), fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(lesson.summary, color = Color.White, lineHeight = 23.sp)
                    lesson.steps.forEachIndexed { index, step ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("${index + 1}.", color = Color(0xFF91C8D7), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(step, color = Color(0xFFE1E4EA), lineHeight = 22.sp)
                        }
                    }
                    Divider(color = Color(0xFF343B43))
                    Text("مهمتك الآن", color = Color(0xFFD6B477), fontWeight = FontWeight.Bold)
                    Text(lesson.task, color = Color.White, lineHeight = 22.sp)
                    Text("نصيحة: ${lesson.tip}", color = Color(0xFF91C8D7), fontSize = 12.sp, lineHeight = 20.sp)
                    val quiz = guideQuizzes[selected]
                    Divider(color = Color(0xFF343B43))
                    Text("اختبر فهمك", color = Color(0xFFD6B477), fontWeight = FontWeight.Bold)
                    Text(quiz.question, color = Color.White, lineHeight = 22.sp)
                    quiz.options.forEachIndexed { optionIndex, option ->
                        Row(
                            Modifier.fillMaxWidth().clickable { quizAnswer = optionIndex; quizChecked = false },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = quizAnswer == optionIndex, onClick = { quizAnswer = optionIndex; quizChecked = false })
                            Text(option, color = Color(0xFFE1E4EA), lineHeight = 20.sp)
                        }
                    }
                    if (quizChecked) {
                        Text(
                            if (quizAnswer == quiz.correct) "إجابة صحيحة! أحسنت." else "ليست الإجابة الصحيحة. راجع الدرس وحاول مرة أخرى.",
                            color = if (quizAnswer == quiz.correct) Color(0xFF8CBAA5) else Color(0xFFE18B83),
                            fontSize = 12.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { quizChecked = true },
                        enabled = quizAnswer >= 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("تحقق من الإجابة") }
                    if (!completed.contains(selected) && !(quizChecked && quizAnswer == quiz.correct)) {
                        Text("أجب عن سؤال الدرس إجابة صحيحة لفتح زر إكمال الدرس.", color = Color(0xFF9BA4AE), fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            if (!completed.contains(selected)) completed.add(selected)
                            prefs.edit().putStringSet("completed_lessons", completed.map { it.toString() }.toSet()).apply()
                            if (selected < guideLessons.lastIndex) selected++ else selected = -1
                        },
                        enabled = completed.contains(selected) || (quizChecked && quizAnswer == quiz.correct),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (completed.contains(selected)) "تم الدرس — التالي" else "أنهيت الدرس، التالي")
                    }
                    if (onOpenStudio != null) OutlinedButton(onClick = onOpenStudio, modifier = Modifier.fillMaxWidth()) { Text("افتح الاستوديو وطبّق الآن") }
                }
            }
        } else {
            guideLessons.forEachIndexed { index, lesson ->
                Card(modifier = Modifier.fillMaxWidth().clickable { selected = index },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171C22)),
                    shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (completed.contains(index)) "✓" else "${index + 1}", color = if (completed.contains(index)) Color(0xFF8CBAA5) else Color(0xFFD6B477), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(lesson.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(lesson.summary, color = Color(0xFF9BA4AE), fontSize = 11.sp, lineHeight = 17.sp)
                        }
                        Text(lesson.time, color = Color(0xFF91C8D7), fontSize = 10.sp)
                    }
                }
            }
            OutlinedButton(onClick = { showGlossary = !showGlossary }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showGlossary) "إخفاء قاموس المصطلحات" else "افتح قاموس المصطلحات")
            }
            if (showGlossary) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF171C22)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Text("قاموس STORYFORGE", color = Color(0xFFD6B477), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Scene — المشهد", color = Color(0xFF91C8D7), fontWeight = FontWeight.SemiBold)
                            Text("جزء مستقل من القصة يمثل مكانًا أو لحظة، ويحتوي على وصف وحوار واختيارات.", color = Color(0xFFE1E4EA), fontSize = 12.sp, lineHeight = 19.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Choice — الاختيار", color = Color(0xFF91C8D7), fontWeight = FontWeight.SemiBold)
                            Text("قرار يراه اللاعب، ويمكن أن ينقله إلى مشهد آخر.", color = Color(0xFFE1E4EA), fontSize = 12.sp, lineHeight = 19.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Branch — التفرّع", color = Color(0xFF91C8D7), fontWeight = FontWeight.SemiBold)
                            Text("مسار مختلف ينتج عن قرار اللاعب، وقد يقوده إلى أحداث أو نهايات مختلفة.", color = Color(0xFFE1E4EA), fontSize = 12.sp, lineHeight = 19.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Variable — المتغير", color = Color(0xFF91C8D7), fontWeight = FontWeight.SemiBold)
                            Text("قيمة تحفظ أثرًا في القصة، مثل الثقة أو عدد المفاتيح.", color = Color(0xFFE1E4EA), fontSize = 12.sp, lineHeight = 19.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Condition — الشرط", color = Color(0xFF91C8D7), fontWeight = FontWeight.SemiBold)
                            Text("قاعدة تحدد متى يظهر اختيار أو يصبح متاحًا، مثل keys>=1.", color = Color(0xFFE1E4EA), fontSize = 12.sp, lineHeight = 19.sp)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Asset — الوسائط", color = Color(0xFF91C8D7), fontWeight = FontWeight.SemiBold)
                            Text("ملفات الصور والأصوات التي تضيف شكلًا وصوتًا إلى المشاهد.", color = Color(0xFFE1E4EA), fontSize = 12.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
            if (onOpenStudio != null) Button(onClick = onOpenStudio, modifier = Modifier.fillMaxWidth()) { Text("ابدأ التطبيق داخل الاستوديو") }
        }
    }
}
