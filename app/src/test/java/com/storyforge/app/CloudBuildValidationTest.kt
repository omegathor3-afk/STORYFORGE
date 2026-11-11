package com.storyforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloudBuildValidationTest {
    @Test
    fun acceptsValidBuildInputsAndOptionalApplicationId() {
        validateCloudBuildInputs(
            gameTitle = "The Ashen Bell",
            applicationId = "com.example.ashenbell",
            versionName = "1.2.3",
            versionCode = "42",
            buildType = "release"
        )
        validateCloudBuildInputs(
            gameTitle = "A Story",
            applicationId = "",
            versionName = "0.1.0",
            versionCode = "1",
            buildType = "debug"
        )
    }

    @Test
    fun trimsUserFacingValuesBeforeValidation() {
        validateCloudBuildInputs(
            gameTitle = "  The Ashen Bell  ",
            applicationId = " com.example.ashenbell ",
            versionName = " 1.2.3 ",
            versionCode = " 42 ",
            buildType = "debug"
        )
        assertEquals(42, parseCloudBuildVersionCode(" 42 "))
    }

    @Test
    fun rejectsBlankAndOverlongGameTitles() {
        assertThrows(IllegalArgumentException::class.java) {
            validateCloudBuildInputs("", "", "1.0.0", "1", "debug")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCloudBuildInputs("x".repeat(41), "", "1.0.0", "1", "debug")
        }
    }

    @Test
    fun rejectsMalformedAndroidApplicationIds() {
        listOf(
            "example",
            "com..game",
            "com.example.1game",
            "com.example.game-name",
            "com.example.game.extra.more.parts.too.many"
        ).forEach { invalid ->
            assertThrows("Expected invalid application ID: $invalid", IllegalArgumentException::class.java) {
                validateCloudBuildInputs("Game", invalid, "1.0.0", "1", "debug")
            }
        }
    }

    @Test
    fun rejectsNonSemanticVersionNames() {
        listOf("", "1", "1.0", "1.0.0-beta", "v1.0.0", "1.0.0.1").forEach { invalid ->
            assertThrows("Expected invalid version name: $invalid", IllegalArgumentException::class.java) {
                validateCloudBuildInputs("Game", "", invalid, "1", "debug")
            }
        }
    }

    @Test
    fun enforcesPositiveAndroidVersionCodeRange() {
        assertEquals(1, parseCloudBuildVersionCode("1"))
        assertEquals(2_100_000_000, parseCloudBuildVersionCode("2100000000"))
        listOf("", "0", "-1", "2100000001", "2147483648", "1.0").forEach { invalid ->
            assertThrows("Expected invalid version code: $invalid", IllegalArgumentException::class.java) {
                parseCloudBuildVersionCode(invalid)
            }
        }
    }

    @Test
    fun rejectsUnknownBuildTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            validateCloudBuildInputs("Game", "", "1.0.0", "1", "production")
        }
    }
}
