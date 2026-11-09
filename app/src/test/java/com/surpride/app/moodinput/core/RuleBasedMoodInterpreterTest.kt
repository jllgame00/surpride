package com.surpride.app.moodinput.core

import com.surpride.app.experience.core.*
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class RuleBasedMoodInterpreterTest {
    @Test fun unsupportedClockPhrasingNeverBecomesAvailableDuration() {
        listOf("오늘 오후 6시 30분에 집에 돌아와야 해", "오늘 18:30에 귀가해야 해").forEach {
            val result = parse(it)
            assertFalse(result.canApply)
            assertNull(result.patch.availableMinutes)
        }
    }
    @Test fun everydayRefreshExpressionMapsToHealingWithoutInventingCompanion() {
        val result = parse("오늘 좀 답답해서 바람 쐬고 싶어. 바다나 강이 보이는 곳으로 한 시간 반만 쓰고 싶어. 집으로 돌아와야 해.")
        assertTrue(result.canApply)
        assertEquals(Mood.HEALING, result.patch.mood)
        assertEquals(90, result.patch.availableMinutes)
        assertNull(result.patch.companion)
    }
    private val now = ZonedDateTime.parse("2026-11-09T14:00:00+09:00[Asia/Seoul]").toInstant().toEpochMilli()
    private fun parse(text: String) = RuleBasedMoodInterpreter().extract(text, now)

    @Test fun unmentionedFieldsStayUnknownAndExistingValuesSurviveReview() {
        val result = parse("숲을 보고 싶어")
        assertNull(result.patch.mood); assertNull(result.patch.availableMinutes)
        assertNull(result.patch.companion); assertNull(result.patch.returnToStart)
        val existing = DrivePreferences(mood = Mood.ADVENTURE, companion = Companion.FRIENDS, availableMinutes = 180, randomness = 71)
        val applied = result.patch.applyTo(existing)
        assertEquals(Mood.ADVENTURE, applied.mood); assertEquals(180, applied.availableMinutes)
        assertEquals(Companion.FRIENDS, applied.companion); assertEquals(71, applied.randomness)
        assertEquals(setOf(PlaceCategory.NATURE), applied.categories)
    }
    @Test fun sixMoodsUseOrdinaryKoreanExpressionsWithoutDiagnosis() {
        listOf("힐링하고 쉬고 싶어" to Mood.HEALING, "설레는 새로운 풍경" to Mood.EXCITEMENT,
            "신나고 활기찬 기분" to Mood.ENERGY, "생각 정리하고 싶어" to Mood.REFLECTION,
            "위로가 필요해" to Mood.COMFORT, "모험하고 싶어" to Mood.ADVENTURE).forEach { (text, mood) ->
            assertEquals(mood, parse(text).patch.mood)
        }
        assertNull(parse("우울증 진단해줘").patch.mood)
    }
    @Test fun multipleSceneryAndExplicitExclusionsAreKept() {
        val result = parse("바다와 야경을 보고 싶고 캠핑은 빼줘")
        assertEquals(setOf(PlaceCategory.WATER, PlaceCategory.NIGHT), result.patch.categories)
        assertEquals(setOf(PlaceCategory.CAMPING), result.patch.excludedCategories)
        assertTrue(result.canApply)
    }
    @Test fun correctionReplacesSceneryAndPreservesUnmentionedTime() {
        val first = parse("바다 보고 한 시간 반 다녀올래").patch.applyTo(DrivePreferences(categories = setOf(PlaceCategory.PET)))
        val second = parse("아니 바다보다는 숲").patch.applyTo(first)
        assertEquals(setOf(PlaceCategory.NATURE, PlaceCategory.PET), second.categories)
        assertEquals(emptySet<PlaceCategory>(), second.excludedCategories)
        assertEquals(90, second.availableMinutes)
        assertEquals(120, parse("시간은 두시간으로 바꿔줘").patch.availableMinutes)
        assertEquals(Companion.SOLO, parse("혼자 갈거야").patch.companion)
    }
    @Test fun koreanAndNumericDurationsAreConvertedWithoutDistanceInvention() {
        listOf("한시간반" to 90, "두 시간" to 120, "1시간 30분" to 90, "삼십분" to 30,
            "45분" to 45, "세시간" to 180, "반 시간" to 30).forEach { (text, minutes) ->
            val patch = parse(text).patch
            assertEquals(text, minutes, patch.availableMinutes)
            assertNull(patch.scope)
        }
        assertEquals(TravelScope.NEARBY, parse("한시간반").patch.suggestedScope())
        assertEquals(TravelScope.DAY, parse("두시간").patch.suggestedScope())
    }
    @Test fun explicitScopesAndCompanionsAndReturnAreParsed() {
        assertEquals(TravelScope.LOCAL, parse("동네에서 가족과 왕복으로").patch.scope)
        assertEquals(Companion.FAMILY, parse("가족과").patch.companion)
        assertEquals(Companion.PARTNER, parse("연인이랑").patch.companion)
        assertEquals(Companion.FRIENDS, parse("친구와").patch.companion)
        assertEquals(TravelScope.OVERNIGHT, parse("1박 여행").patch.scope)
        assertEquals(false, parse("돌아오지 않아도 돼 편도로").patch.returnToStart)
        assertEquals(true, parse("왕복으로 출발지에 돌아올래").patch.returnToStart)
        assertEquals(75, parse("랜덤 75").patch.randomness)
    }
    @Test fun explicitFutureDeadlineUsesSeoulAndNeverSilentlyTomorrow() {
        val result = parse("오늘 오후 6시 30분까지 돌아와야 해")
        val expected = ZonedDateTime.parse("2026-11-09T18:30:00+09:00[Asia/Seoul]").toInstant().toEpochMilli()
        assertEquals(expected, result.patch.returnByEpochMillis)
        assertNull(result.patch.availableMinutes)
        assertTrue(result.canApply)
        assertFalse(parse("오늘 오전 9시까지 복귀").canApply)
        assertNull(parse("오늘 오전 9시까지 복귀").patch.returnByEpochMillis)
        assertTrue(InterpretationIssue.AMBIGUOUS_TIME in parse("6시까지 복귀").issues)
        val tomorrow = parse("내일 오전 9시까지 돌아올래")
        assertEquals(now + 19 * 60 * 60_000, tomorrow.patch.returnByEpochMillis)
    }
    @Test fun contradictoryAndUnsupportedHardConditionsBlockApplication() {
        listOf("바다는 꼭 보고 바다는 빼줘", "혼자 친구랑 갈래", "왕복 편도", "30분 또는 두시간",
            "힐링 모험", "사람 없는 숲", "붐비지 않는 바다", "랜덤 101", "0분", "10000시간").forEach {
            assertFalse(it, parse(it).canApply)
            assertTrue(it, parse(it).issues.isNotEmpty())
        }
        assertTrue(InterpretationIssue.UNSUPPORTED_CONDITION in parse("사람 적은 곳").issues)
    }
    @Test fun categoryNegationDoesNotInferPositiveConditionsOrMood() {
        val patch = parse("바다 말고 숲, 힐링 말고").patch
        assertEquals(setOf(PlaceCategory.NATURE), patch.categories)
        assertEquals(setOf(PlaceCategory.WATER), patch.excludedCategories)
        assertNull(patch.mood)
    }
    @Test fun inputBoundsUnknownAndPlaceRequestsFailClosedWithoutTextInResult() {
        assertFalse(parse("").canApply)
        assertTrue(InterpretationIssue.INPUT_TOO_LONG in parse("가".repeat(1001)).issues)
        assertFalse(parse("북위 37.5 동경 127.0으로 가줘").canApply)
        assertFalse(parse("서울역으로 데려다줘").canApply)
        val secret = "개인비밀문장"
        assertFalse(parse(secret).toString().contains(secret))
        assertEquals(InterpretationSource.RULE_BASED, parse("힐링").source)
    }
    @Test fun unsupportedLocationOrCrowdIsNotDroppedWhenOtherConditionsExist() {
        listOf("힐링하고 서울역으로 가줘", "숲에서 위도 37.5 경도 127.0", "한시간 사람 안 붐비는 곳").forEach {
            assertFalse(it, parse(it).canApply)
        }
    }
    @Test fun alternativesAcrossCategoriesRequireReviewInsteadOfBecomingAllOf() {
        assertFalse(parse("바다나 숲").canApply)
        assertTrue(parse("바다나 강가").canApply)
        val applied = parse("숲으로 바꿔줘").patch.applyTo(DrivePreferences(categories = setOf(PlaceCategory.CHILD, PlaceCategory.PET)))
        assertTrue(applied.categories.containsAll(setOf(PlaceCategory.CHILD, PlaceCategory.PET, PlaceCategory.NATURE)))
    }
    @Test fun preparationHintsAreFiniteAndDoNotClaimForecastKnowledge() {
        val hints = SafePreparationPolicy.hints(DrivePreferences(scope = TravelScope.OVERNIGHT, categories = setOf(PlaceCategory.NATURE)))
        assertTrue(PreparationHint.CHECK_FORECAST in hints)
        assertTrue(PreparationHint.OUTER_LAYER in hints)
        assertTrue(PreparationHint.CONFIRM_LODGING in hints)
        assertFalse(hints.joinToString { it.label }.contains("비가 옵니다"))
    }
    @Test fun genericPreparationAndLongDriveRestAdviceAreConditional() {
        val short = SafePreparationPolicy.hints(DrivePreferences(availableMinutes = 30))
        assertTrue(short.any { it.label.contains("물") && it.label.contains("신발") })
        assertFalse(short.any { it.label.contains("휴식") })
        val long = SafePreparationPolicy.hints(DrivePreferences(availableMinutes = 180))
        assertTrue(long.any { it.label.contains("휴식") })
    }
    @Test fun commonClockNotationAndMidnightAmbiguityNeverInventTime() {
        val expected = ZonedDateTime.parse("2026-11-09T18:30:00+09:00[Asia/Seoul]").toInstant().toEpochMilli()
        val result = parse("오늘 18:30까지 돌아와야 해")
        assertEquals(expected, result.patch.returnByEpochMillis)
        assertNull(result.patch.availableMinutes)
        assertFalse(parse("힐링하고 밤 12시까지 돌아올래").canApply)
        assertFalse(parse("힐링하고 일흔 시간 다녀올래").canApply)
    }
    @Test fun explicitNegativeGrammarNeverBecomesAPositiveRequirement() {
        val result = parse("바다가 아닌 숲으로 가고 싶어")
        assertEquals(setOf(PlaceCategory.NATURE), result.patch.categories)
        assertEquals(setOf(PlaceCategory.WATER), result.patch.excludedCategories)
        assertNull(parse("신나지 않고 싶어").patch.mood)
        assertNull(parse("안 신나는 숲").patch.mood)
    }
    @Test fun negatedCompanionScopeAndReturnNeverBecomePositiveFacts() {
        val companion = parse("혼자는 싫어. 숲으로 가자")
        assertNull(companion.patch.companion)
        assertTrue(InterpretationIssue.UNSUPPORTED_CONDITION in companion.issues)
        assertFalse(companion.canApply)
        val scope = parse("숙박은 안 해. 숲으로 가자")
        assertNull(scope.patch.scope)
        assertTrue(InterpretationIssue.UNSUPPORTED_CONDITION in scope.issues)
        assertFalse(scope.canApply)
        val roundTrip = parse("왕복은 안 해")
        assertNotEquals(true, roundTrip.patch.returnToStart)
        assertTrue(roundTrip.patch.returnToStart == false || !roundTrip.canApply)
    }
    @Test fun signedAndDecimalNumbersAreRejectedWithoutPartialPositiveExtraction() {
        listOf("-30분 숲", "-1시간 숲", "1.5시간 숲", "30.5분 숲").forEach {
            val result = parse(it)
            assertFalse(it, result.canApply)
            assertNull(it, result.patch.availableMinutes)
        }
        listOf("랜덤 40.5", "랜덤 -0.5").forEach {
            val result = parse(it)
            assertFalse(it, result.canApply)
            assertNull(it, result.patch.randomness)
        }
    }
    @Test fun groupedNegationNeverTurnsTheFirstSceneryIntoAPositiveRequirement() {
        val result = parse("바다와 야경은 빼줘")
        assertFalse(result.patch.categories.orEmpty().contains(PlaceCategory.WATER))
        assertTrue(!result.canApply || result.patch.excludedCategories.orEmpty().containsAll(setOf(PlaceCategory.WATER, PlaceCategory.NIGHT)))
    }
}
