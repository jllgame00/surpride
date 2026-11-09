package com.surpride.app.moodinput.core

import com.surpride.app.experience.core.*

/** Nullable fields are UNKNOWN, never defaults inferred from an existing preference. */
data class PreferencePatch(
    val mood: Mood? = null,
    val scope: TravelScope? = null,
    val categories: Set<PlaceCategory>? = null,
    val excludedCategories: Set<PlaceCategory>? = null,
    val companion: Companion? = null,
    val availableMinutes: Int? = null,
    val returnByEpochMillis: Long? = null,
    val returnToStart: Boolean? = null,
    val randomness: Int? = null,
    /** A comparison removes a prior requirement without inventing a hard exclusion. */
    val removedCategories: Set<PlaceCategory>? = null
) {
    fun isEmpty(): Boolean = mood == null && scope == null && categories == null && excludedCategories == null &&
        companion == null && availableMinutes == null && returnByEpochMillis == null && returnToStart == null &&
        randomness == null && removedCategories == null

    fun isValid(): Boolean = (availableMinutes == null || availableMinutes in 1..10080) &&
        (randomness == null || randomness in 0..100) &&
        (returnByEpochMillis == null || returnByEpochMillis in 1..4102444800000L) &&
        (categories.orEmpty() intersect excludedCategories.orEmpty()).isEmpty() &&
        (categories.orEmpty() intersect removedCategories.orEmpty()).isEmpty()

    /** Call only after the user reviews the extraction. Unmentioned requirements survive. */
    fun applyTo(existing: DrivePreferences): DrivePreferences {
        require(isValid()) { "Invalid preference patch" }
        return existing.copy(
            mood = mood ?: existing.mood,
            scope = scope ?: existing.scope,
            categories = (existing.categories + categories.orEmpty()) - excludedCategories.orEmpty() - removedCategories.orEmpty(),
            excludedCategories = (existing.excludedCategories + excludedCategories.orEmpty()) - categories.orEmpty(),
            companion = companion ?: existing.companion,
            availableMinutes = availableMinutes ?: existing.availableMinutes,
            returnByEpochMillis = returnByEpochMillis ?: existing.returnByEpochMillis,
            returnToStart = returnToStart ?: existing.returnToStart,
            randomness = randomness ?: existing.randomness
        )
    }

    /** A review label based on time, never a claim about road distance or an applied scope. */
    fun suggestedScope(): TravelScope? = availableMinutes?.let {
        when { it <= 30 -> TravelScope.LOCAL; it < 120 -> TravelScope.NEARBY; it < 1440 -> TravelScope.DAY; else -> TravelScope.OVERNIGHT }
    }
}

enum class InterpretationSource { RULE_BASED }
enum class InterpretationIssue {
    EMPTY_INPUT, INPUT_TOO_LONG, UNKNOWN_INPUT, CONTRADICTORY_CONDITIONS,
    AMBIGUOUS_TIME, INVALID_TIME, INVALID_RANDOMNESS, UNSUPPORTED_CONDITION, UNSUPPORTED_LOCATION,
    ALTERNATIVE_CONDITIONS
}
/** Contains only finite structured values. The original utterance is deliberately never retained. */
data class MoodInterpretation(
    val patch: PreferencePatch,
    val issues: Set<InterpretationIssue> = emptySet(),
    val source: InterpretationSource = InterpretationSource.RULE_BASED
) {
    val canApply: Boolean get() = issues.isEmpty() && patch.isValid() && !patch.isEmpty()
}

enum class PreparationHint(val label: String) {
    WATER_AND_SHOES("물과 편한 신발을 준비해 주세요."),
    CHECK_FORECAST("출발 전 우천 예보를 직접 확인해 주세요."),
    OUTER_LAYER("야외 활동을 위해 겉옷을 준비해 주세요."),
    PLAN_REST("긴 일정에는 중간 휴식 시간을 계획해 주세요."),
    CONFIRM_LODGING("숙박 일정이면 숙소 예약과 입실 조건을 직접 확인해 주세요.")
}
object SafePreparationPolicy {
    fun hints(preferences: DrivePreferences): List<PreparationHint> = buildList {
        add(PreparationHint.WATER_AND_SHOES)
        add(PreparationHint.CHECK_FORECAST)
        if (preferences.categories.any { it in setOf(PlaceCategory.NATURE, PlaceCategory.WATER, PlaceCategory.CAMPING, PlaceCategory.NIGHT) }) add(PreparationHint.OUTER_LAYER)
        if (preferences.scope == TravelScope.OVERNIGHT) add(PreparationHint.CONFIRM_LODGING)
        if ((preferences.availableMinutes ?: 0) >= 180) add(PreparationHint.PLAN_REST)
    }
}
