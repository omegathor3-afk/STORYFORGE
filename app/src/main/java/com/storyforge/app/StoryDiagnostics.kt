package com.storyforge.app

data class StoryDiagnostic(
    val code: String,
    val message: String,
    val sceneId: String? = null,
    val isError: Boolean = true
)

private val diagnosticConditionRegex =
    Regex("^([\\p{L}A-Za-z0-9_]+)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+)$")
private val diagnosticEffectRegex =
    Regex("^([\\p{L}A-Za-z0-9_]+)\\s*(=|\\+=|-=)\\s*(-?\\d+)$")

internal fun diagnoseStory(project: StoryProject): List<StoryDiagnostic> {
    val issues = mutableListOf<StoryDiagnostic>()
    val scenes = project.scenes
    if (scenes.isEmpty()) {
        issues += StoryDiagnostic("no-scenes", "المشروع لا يحتوي على مشاهد.")
        return issues
    }

    fun reportDuplicates(values: List<String>, code: String, label: String) {
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach { value ->
            issues += StoryDiagnostic(code, "$label مكرر: $value")
        }
    }

    reportDuplicates(scenes.map { it.id }, "duplicate-scene-id", "معرّف مشهد")
    reportDuplicates(project.variables.map { it.name }, "duplicate-variable", "اسم متغير")
    reportDuplicates(project.assets.map { it.id }, "duplicate-asset-id", "معرّف أصل")
    reportDuplicates(scenes.flatMap { scene -> scene.dialogues.map { it.id } }, "duplicate-dialogue-id", "معرّف حوار")

    val sceneIds = scenes.map { it.id }.toSet()
    val variableNames = project.variables.map { it.name }.toSet()
    scenes.forEach { scene ->
        scene.choices.forEachIndexed { index, choice ->
            if (choice.isBlank()) return@forEachIndexed
            val condition = scene.choiceConditions.getOrNull(index).orEmpty().trim()
            if (condition.isNotEmpty()) {
                val match = diagnosticConditionRegex.matchEntire(condition)
                if (match == null) {
                    issues += StoryDiagnostic("malformed-condition", "شرط الاختيار ${index + 1} غير صالح.", scene.id)
                } else if (match.groupValues[1] !in variableNames) {
                    issues += StoryDiagnostic("unknown-condition-variable", "متغير الشرط غير معرّف: ${match.groupValues[1]}", scene.id)
                }
            }
            val effect = scene.choiceEffects.getOrNull(index).orEmpty().trim()
            if (effect.isNotEmpty()) {
                val match = diagnosticEffectRegex.matchEntire(effect)
                if (match == null) {
                    issues += StoryDiagnostic("malformed-effect", "تأثير الاختيار ${index + 1} غير صالح.", scene.id)
                } else if (match.groupValues[1] !in variableNames) {
                    issues += StoryDiagnostic("unknown-effect-variable", "متغير التأثير غير معرّف: ${match.groupValues[1]}", scene.id)
                }
            }
            val target = scene.choiceTargets.getOrNull(index)
            if (!target.isNullOrBlank() && target != "__END__" && target !in sceneIds) {
                issues += StoryDiagnostic("missing-target", "الاختيار ${index + 1} يشير إلى مشهد غير موجود.", scene.id)
            }
        }
    }

    project.assets.forEach { asset ->
        if (asset.sceneId != null && asset.sceneId !in sceneIds) {
            issues += StoryDiagnostic("missing-asset-scene", "الأصل «${asset.name}» مرتبط بمشهد غير موجود.")
        }
    }

    val visited = mutableSetOf<String>()
    val queue = ArrayDeque<String>()
    queue.add(scenes.first().id)
    var hasReachableEnding = false
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!visited.add(id)) continue
        val index = scenes.indexOfFirst { it.id == id }
        if (index < 0) continue
        val scene = scenes[index]
        val choices = scene.choices.withIndex().filter { it.value.isNotBlank() }
        if (choices.isEmpty()) {
            val next = scenes.getOrNull(index + 1)
            if (next != null) queue.add(next.id) else hasReachableEnding = true
        } else {
            choices.forEach { (choiceIndex, _) ->
                val target = scene.choiceTargets.getOrNull(choiceIndex)
                when {
                    target == "__END__" -> hasReachableEnding = true
                    !target.isNullOrBlank() && target in sceneIds -> queue.add(target)
                    target.isNullOrBlank() -> {
                        val next = scenes.getOrNull(index + 1)
                        if (next != null) queue.add(next.id) else hasReachableEnding = true
                    }
                }
            }
        }
    }

    scenes.filter { it.id !in visited }.forEach { scene ->
        issues += StoryDiagnostic("unreachable-scene", "المشهد «${scene.title}» غير قابل للوصول من البداية.", scene.id, false)
    }
    if (!hasReachableEnding) {
        issues += StoryDiagnostic("missing-ending", "لا توجد نهاية يمكن الوصول إليها من أول مشهد.")
    }
    return issues
}
