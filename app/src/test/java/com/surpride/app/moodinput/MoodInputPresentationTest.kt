package com.surpride.app.moodinput

import com.surpride.app.experience.core.DrivePreferences
import com.surpride.app.experience.core.Mood
import com.surpride.app.experience.core.PlaceCategory
import com.surpride.app.moodinput.core.InterpretationIssue
import com.surpride.app.moodinput.core.MoodInterpretation
import com.surpride.app.moodinput.core.PreferencePatch
import org.junit.Assert.*
import org.junit.Test

class MoodInputPresentationTest {
    @Test fun unmentionedFieldsDoNotBecomeDefaultsInExtraction() {
        val text = MoodInputPresentation.extraction(MoodInterpretation(PreferencePatch(mood = Mood.COMFORT)))
        assertTrue(text.contains("위로"))
        assertFalse(text.contains("60분"))
        assertFalse(text.contains("혼자"))
        assertFalse(text.contains("동네 한 바퀴"))
        assertFalse(text.contains("UNKNOWN"))
    }

    @Test fun extractedFieldsExplainChangesWithoutProviderVocabulary() {
        val text = MoodInputPresentation.extraction(MoodInterpretation(PreferencePatch(
            categories = setOf(PlaceCategory.WATER), availableMinutes = 90, returnToStart = true)))
        assertTrue(text.contains("90분"))
        assertTrue(text.contains("왕복"))
        assertTrue(text.contains(PlaceCategory.WATER.label))
        assertFalse(text.contains("RULE_BASED"))
        assertFalse(text.contains("AUTO"))
    }

    @Test fun invalidInterpretationShowsActionableIssueInsteadOfAcceptedChanges() {
        val text = MoodInputPresentation.extraction(MoodInterpretation(PreferencePatch(availableMinutes = 90),
            setOf(InterpretationIssue.CONTRADICTORY_CONDITIONS)))
        assertTrue(text.contains("서로 다른"))
        assertFalse(text.contains("사용 시간 90분"))
    }

    @Test fun everyFiniteIssueHasHumanReadableCopy() {
        InterpretationIssue.entries.forEach {
            val text = MoodInputPresentation.issue(it)
            assertTrue(text.isNotBlank())
            assertFalse(text.contains(it.name))
        }
    }

    @Test fun categoryTriStateRemainsExplicitWithoutRelyingOnColor() {
        val neutral = DrivePreferences()
        assertEquals("바다 · 호수 · 강 · 미지정", MoodInputPresentation.categoryLabel(PlaceCategory.WATER, neutral))
        assertTrue(MoodInputPresentation.categoryLabel(PlaceCategory.WATER,
            neutral.copy(categories = setOf(PlaceCategory.WATER))).endsWith("선호"))
        assertTrue(MoodInputPresentation.categoryLabel(PlaceCategory.WATER,
            neutral.copy(excludedCategories = setOf(PlaceCategory.WATER))).endsWith("제외"))
    }

    @Test fun nullTimeIsNotPresentedAsAnInventedBudget() {
        val text = MoodInputPresentation.summary(DrivePreferences(availableMinutes = null))
        assertTrue(text.contains("시간 미지정"))
        assertFalse(text.contains("60분"))
    }

    @Test fun dirtyOrInvalidTextCannotBeApplied() {
        assertFalse(ready(dirty = true))
        assertFalse(ready(result = MoodInterpretation(PreferencePatch(), setOf(InterpretationIssue.EMPTY_INPUT))))
    }

    @Test fun runningSpeechAndInterpretationBlockApply() {
        assertFalse(ready(working = true))
        assertFalse(ready(speechBusy = true))
    }

    @Test fun parkedPositiveBudgetIsRequiredEvenAfterManualCorrection() {
        assertFalse(ready(allowed = false))
        assertFalse(ready(budget = 0))
        assertFalse(ready(budget = null))
        assertTrue(ready())
    }

    private fun ready(allowed: Boolean = true, dirty: Boolean = false, result: MoodInterpretation? = null,
        working: Boolean = false, speechBusy: Boolean = false, budget: Int? = 90) =
        MoodInputPresentation.readyToApply(allowed, dirty, result, working, speechBusy, budget)
}
