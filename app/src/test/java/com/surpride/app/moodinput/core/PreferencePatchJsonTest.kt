package com.surpride.app.moodinput.core

import com.surpride.app.experience.core.*
import org.junit.Assert.*
import org.junit.Test

class PreferencePatchJsonTest {
    private fun valid(json: String) = (PreferencePatchJson.decode(json) as PatchDecodeResult.Success).patch
    @Test fun validAllowlistedPatchRoundTripsAndNullsRemainUnknown() {
        val patch = valid("""{"mood":"REFLECTION","categories":["NATURE","NIGHT"],"excludedCategories":["WATER"],"availableMinutes":90,"returnToStart":false,"randomness":0}""")
        assertEquals(Mood.REFLECTION, patch.mood); assertNull(patch.scope); assertNull(patch.companion)
        assertEquals(false, patch.returnToStart)
        assertEquals(patch, valid(PreferencePatchJson.encode(patch)))
        assertNull(valid("""{"mood":null,"availableMinutes":null}""").mood)
    }
    @Test fun unknownKeysEnumsDestinationsAndCoordinatesAreRejected() {
        listOf("""{"assistantText":"hello"}""", """{"destinationId":"x"}""", """{"latitude":37.5}""",
            """{"mood":"SAD"}""", """{"scope":"AUTO"}""", """{"categories":["BEACH"]}""").forEach {
            assertTrue(it, PreferencePatchJson.decode(it) is PatchDecodeResult.Invalid)
        }
    }
    @Test fun wrongTypesBoundsConflictsAndDuplicateKeysAreRejected() {
        listOf("""{"availableMinutes":"60"}""", """{"availableMinutes":1.5}""", """{"availableMinutes":0}""",
            """{"availableMinutes":10081}""", """{"returnToStart":1}""", """{"randomness":101}""",
            """{"randomness":-1}""", """{"returnByEpochMillis":-1}""", """{"categories":"NATURE"}""",
            """{"categories":["NATURE"],"excludedCategories":["NATURE"]}""", """{"mood":"HEALING","mood":"ENERGY"}""").forEach {
            assertTrue(it, PreferencePatchJson.decode(it) is PatchDecodeResult.Invalid)
        }
    }
    @Test fun malformedJsonAndExtraContentNeverThrowOrLeakRawText() {
        listOf("not-json-private", "[1]", "{", "{} trailing", "{\"mood\":\"HEALING\",}", "{\"mood\":NaN}", "{\"mood\":{}}").forEach {
            val result = PreferencePatchJson.decode(it)
            assertTrue(it, result is PatchDecodeResult.Invalid)
            assertFalse(result.toString().contains(it))
        }
    }
    @Test fun boundaryValuesAndEmptyPatchAreAcceptedAsUnknown() {
        assertEquals(10080, valid("""{"availableMinutes":10080,"randomness":100}""").availableMinutes)
        assertTrue(valid("{}").isEmpty())
        assertTrue(PreferencePatchJson.decode(" ".repeat(4097)) is PatchDecodeResult.Invalid)
    }
}
