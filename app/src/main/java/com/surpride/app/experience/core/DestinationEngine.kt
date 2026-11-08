package com.surpride.app.experience.core

import com.surpride.app.vehicle.*
import kotlin.math.min
import kotlin.random.Random

/** Pure deterministic rules; filtering always precedes scoring/randomness. No SDK or UI calls. */
class DestinationEngine(private val candidates: List<DestinationCandidate>) {
    fun recommend(p: DrivePreferences, c: RecommendationContext, seed: Long): RecommendationResult {
        val budget = budgetMinutes(p, c.nowMillis) ?: return RecommendationResult.Failure("시간 조건을 다시 선택해 주세요.")
        if (budget <= 0 || p.randomness !in 0..100) return RecommendationResult.Failure("유효한 시간과 랜덤 정도를 선택해 주세요.")
        val ranked = candidates.mapNotNull { candidate ->
            if (candidate.id in c.recentIds || !candidate.categories.containsAll(p.categories) || p.companion !in candidate.companions) return@mapNotNull null
            if (c.weather != null && c.weather !in candidate.weather) return@mapNotNull null
            val estimate = candidate.estimates[p.scope] ?: return@mapNotNull null
            if (!estimate.valid() || estimate.total(p.returnToStart) > budget) return@mapNotNull null
            val range = c.vehicle.verifiedRangeKm?.takeIf {it.isFinite() && it > 0}
            val distance = estimate.verifiedDistanceKm?.takeIf {estimate.origin == EstimateOrigin.VERIFIED_ROUTE && it.isFinite() && it >= 0}
            if (range != null && distance != null && distance > range) return@mapNotNull null
            val score = RecommendationScore(
                (candidate.moods[p.mood] ?: 0).coerceIn(0,5) * 10,
                if (p.categories.isEmpty()) 10 else 25, 15,
                if (c.season != null && c.season in candidate.seasons) 5 else 0,
                if (c.weather != null && c.weather in candidate.weather) 5 else 0,
                (Random(seed xor candidate.id.hashCode().toLong()).nextDouble() * 20 * p.randomness / 100).toInt()
            )
            val warnings = buildSet {
                if (estimate.origin == EstimateOrigin.DEMO) add(PlanWarning.DEMO_ESTIMATES)
                if (range == null) add(PlanWarning.RANGE_UNVERIFIED)
                if (distance == null) add(PlanWarning.DISTANCE_UNVERIFIED)
                if (c.weather == null) add(PlanWarning.WEATHER_UNKNOWN)
                if (c.season == null) add(PlanWarning.SEASON_UNKNOWN)
            }
            RecommendationResult.Success(candidate,estimate,estimate.total(p.returnToStart),score,warnings)
        }
        return ranked.sortedWith(compareByDescending<RecommendationResult.Success> {it.score.total}.thenBy {it.candidate.id}).firstOrNull()
            ?: RecommendationResult.Failure("조건에 맞는 샘플이 없어요. 시간·장소 조건을 바꿔 주세요. 최근 DEMO 코스는 제외됩니다.")
    }
}

fun budgetMinutes(p: DrivePreferences, now: Long): Int? {
    if (p.availableMinutes == null && p.returnByEpochMillis == null) return null
    if (p.availableMinutes != null && p.availableMinutes <= 0) return null
    val deadline = p.returnByEpochMillis?.let { ((it - now) / 60_000).coerceIn(-1, Int.MAX_VALUE.toLong()).toInt() }
    return min(p.availableMinutes ?: Int.MAX_VALUE, deadline ?: Int.MAX_VALUE)
}

/** Trust only a fresh real RANGE reading. Never derive distance from SOC/fuel/odometer. */
fun vehicleContext(state: VehicleState, now: Long, rangeUnitConfirmedKm: Boolean = false): VehicleContext {
    if (!rangeUnitConfirmedKm || state.origin != DataOrigin.PLEOS_SERVICE) return VehicleContext()
    val range = state.readings[VehicleMetric.RANGE] ?: return VehicleContext()
    val at = range.receivedAtMillis ?: return VehicleContext()
    return VehicleContext(range.value?.takeIf {range.status == ReadingStatus.AVAILABLE && it.isFinite() && it > 0 && now - at in 0..60_000})
}
