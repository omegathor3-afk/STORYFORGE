package com.storyforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StoryEngineTest {
    @Test
    fun zipBackupRestoresProjectAndMediaFiles() {
        val root = Files.createTempDirectory("storyforge-test").toFile()
        try {
            val asset = File(root, "storyforge_assets/project-a/scene.png")
            asset.parentFile?.mkdirs()
            asset.writeBytes(byteArrayOf(1, 2, 3, 4))
            val project = StoryProject(
                id = "project-a",
                title = "اختبار",
                scenes = listOf(StoryScene(id = "scene-a", title = "المشهد")),
                assets = listOf(
                    StoryAsset(
                        id = "asset-a",
                        name = "صورة",
                        relativePath = "storyforge_assets/project-a/scene.png",
                        mimeType = "image/png",
                        sceneId = "scene-a"
                    )
                )
            )
            val bytes = ByteArrayOutputStream()
            writeStoryZipBackup(bytes, listOf(project), root)
            val restored = restoreStoryZipBackup(ByteArrayInputStream(bytes.toByteArray()), root)
            assertEquals("اختبار", restored.single().title)
            assertEquals("scene-a", restored.single().assets.single().sceneId)
            assertEquals(byteArrayOf(1, 2, 3, 4).toList(), asset.readBytes().toList())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidZipManifestDoesNotReplaceExistingAssets() {
        val root = Files.createTempDirectory("storyforge-restore-test").toFile()
        try {
            val existing = File(root, "storyforge_assets/old/keep.bin")
            existing.parentFile?.mkdirs()
            existing.writeBytes(byteArrayOf(9, 8, 7))
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("storyforge-projects.json"))
                zip.write("not-json".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/storyforge_assets/new/file.bin"))
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
            org.junit.Assert.assertThrows( Exception::class.java) {
                restoreStoryZipBackup(ByteArrayInputStream(bytes.toByteArray()), root)
            }
            assertEquals(listOf<Byte>(9, 8, 7), existing.readBytes().toList())
            assertFalse(File(root, "storyforge_assets/new/file.bin").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun zipTraversalPathIsRejected() {
        val root = Files.createTempDirectory("storyforge-zip-slip").toFile()
        try {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("storyforge-projects.json"))
                zip.write("[]".toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/storyforge_assets/../../escape.bin"))
                zip.write(byteArrayOf(1))
                zip.closeEntry()
            }
            org.junit.Assert.assertThrows(Exception::class.java) {
                restoreStoryZipBackup(ByteArrayInputStream(bytes.toByteArray()), root)
            }
            assertFalse(File(root.parentFile, "escape.bin").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun blankConditionAlwaysAllowsChoice() {
        assertTrue(evaluateStoryCondition("", mapOf("keys" to 0)))
    }

    @Test
    fun numericConditionsAreEvaluatedCorrectly() {
        val values = mapOf("keys" to 2, "trust" to 1, "alarm" to 0)
        assertTrue(evaluateStoryCondition("keys>=2", values))
        assertTrue(evaluateStoryCondition("trust<3", values))
        assertTrue(evaluateStoryCondition("alarm==0", values))
        assertTrue(evaluateStoryCondition("keys!=1", values))
        assertFalse(evaluateStoryCondition("keys>2", values))
        assertFalse(evaluateStoryCondition("trust<=0", values))
    }

    @Test
    fun arabicVariableNamesAreSupported() {
        assertTrue(evaluateStoryCondition("مفاتيح>=1", mapOf("مفاتيح" to 2)))
    }

    @Test
    fun malformedConditionsFailClosed() {
        assertFalse(evaluateStoryCondition("keys => 1", mapOf("keys" to 2)))
        assertFalse(evaluateStoryCondition("unknown", emptyMap()))
    }

    @Test
    fun effectsCanAssignIncreaseAndDecreaseValues() {
        val start = mapOf("trust" to 2, "keys" to 3)
        val assigned = applyStoryEffect("keys=0", start)
        assertEquals(0, assigned["keys"])
        val increased = applyStoryEffect("trust+=2", assigned)
        assertEquals(4, increased["trust"])
        val decreased = applyStoryEffect("trust-=1", increased)
        assertEquals(3, decreased["trust"])
    }

    @Test
    fun effectsSupportArabicVariableNames() {
        val changed = applyStoryEffect("ثقة+=1", mapOf("ثقة" to 4))
        assertEquals(5, changed["ثقة"])
    }

    @Test
    fun malformedEffectDoesNotChangeValues() {
        val values = mapOf("keys" to 2)
        assertEquals(values, applyStoryEffect("keys++1", values))
        assertEquals(values, applyStoryEffect("", values))
    }
}
