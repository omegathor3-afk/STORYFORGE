package com.storyforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryEngineTest {
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
