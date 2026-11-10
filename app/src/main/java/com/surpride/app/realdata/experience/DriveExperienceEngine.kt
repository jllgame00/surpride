package com.surpride.app.realdata.experience

import com.surpride.app.experience.core.*
import com.surpride.app.realdata.*

/** Bounded measured-route comparison. Duration, repetition and detour are evidence, not enjoyment. */
class DriveExperienceEngine(private val places: List<RealPlace>, private val routes: RouteProvider,
    private val waypoints: List<ScenicWaypoint> = emptyList(), private val clock: () -> Long = System::currentTimeMillis) {
    private data class Option(val options: RouteOptions, val label: String, val type: ComparisonType)
    private data class Measured(val option: Option, val leg: RouteLeg)
    private data class Evaluated(val place: RealPlace, val out: RouteLeg, val back: RouteLeg?, val seconds: Long,
        val scoring: RecommendationResult.Success, val assessment: ExperienceAssessment, val option: Option, val comparison: Int)

    fun recommend(origin: GeoPoint, p: DrivePreferences, context: RecommendationContext,
        stayMinutes: Int, bufferMinutes: Int, seed: Long): RoutePlan {
        if (stayMinutes !in 0..10080 || bufferMinutes !in 0..1440 || p.randomness !in 0..100)
            return RoutePlan.Failure(setOf(RouteError.INVALID_RESPONSE))
        if (routeBudgetSeconds(p, context.nowMillis) <= 0) return RoutePlan.Failure(setOf(RouteError.TIME_EXCEEDED))
        val shortlist = places.filter { p.companion == Companion.SOLO && it.point != origin &&
            it.id !in context.recentIds && it.categories.containsAll(p.categories) &&
            it.categories.none { category -> category in p.excludedCategories } &&
            p.categories.none { category -> category in p.excludedCategories } }
            .distinctBy { it.id }.sortedWith(compareByDescending<RealPlace> { (it.moods[p.mood] ?: 0).coerceIn(0, 5) }
                .thenBy { it.id }).take(2)
        if (shortlist.isEmpty()) return RoutePlan.Failure(setOf(RouteError.NO_CANDIDATE))
        val weights = weights(p.mood)
        val comparisons = mutableListOf<RouteComparison>()
        val errors = mutableSetOf<RouteError>()
        val evaluated = mutableListOf<Evaluated>()
        var requests = 0
        fun request(a: GeoPoint, b: GeoPoint, options: RouteOptions): RouteReply = requestRoute {
            if (requests >= 12) RouteReply.Failure(RouteError.RATE_LIMIT)
            else {requests++;routes.route(a, b, options)}
        }
        for (place in shortlist) {
            val options = mutableListOf(
                Option(RouteOptions(RoutePriority.RECOMMEND), "기본 경로", ComparisonType.PRIORITY),
                Option(RouteOptions(RoutePriority.TIME), "시간 우선", ComparisonType.PRIORITY),
                Option(RouteOptions(RoutePriority.DISTANCE), "거리 우선", ComparisonType.PRIORITY))
            waypoints.filter { it.eligible && matchesScenery(it, p) && it.entryPoint != origin && it.entryPoint != place.point }
                .sortedBy { it.id }.firstOrNull()?.let {
                    options.add(Option(RouteOptions(waypoints = listOf(it.entryPoint!!)), "검증 경유", ComparisonType.SCENIC))
                }
            val measured = options.mapNotNull { option ->
                when (val reply = request(origin, place.point, option.options)) {
                    is RouteReply.Success -> Measured(option, reply.leg)
                    is RouteReply.Failure -> {
                        errors.add(reply.error)
                        if (reply.stopsRecommendation()) return RoutePlan.Failure(errors)
                        comparisons.add(RouteComparison(option.label, option.type, null, null, ComparisonStatus.NO_ROUTE))
                        null
                    }
                }
            }
            if (measured.isEmpty()) continue
            val reverse = if (p.returnToStart) request(place.point, origin, RouteOptions()) else null
            val back = (reverse as? RouteReply.Success)?.leg
            if (reverse is RouteReply.Failure) {
                errors.add(reverse.error)
                if (reverse.stopsRecommendation()) return RoutePlan.Failure(errors)
                measured.forEach { comparisons.add(RouteComparison(it.option.label, it.option.type, null, null, ComparisonStatus.NO_ROUTE)) }
                continue
            }
            val baseline = measured.filter { it.option.type == ComparisonType.PRIORITY && fresh(it.leg, clock()) &&
                validResponse(it.leg, it.option.options) }
            val bestSeconds = baseline.minOfOrNull { it.leg.seconds }
            val bestMeters = baseline.minOfOrNull { it.leg.meters }?.takeIf { it > 0 }
            for ((option, out) in measured) {
                val legs = listOfNotNull(out, back)
                val seconds = legs.sumOf { it.seconds } + (stayMinutes.toLong() + bufferMinutes) * 60
                val distance = legs.sumOf { it.meters }
                val checkedAt = clock()
                var status = when {
                    legs.any { !fresh(it, checkedAt) || it.origin != out.origin } -> ComparisonStatus.STALE_DATA
                    !validResponse(out, option.options) || back != null && !validResponse(back, RouteOptions()) -> ComparisonStatus.INVALID_RESPONSE
                    seconds > routeBudgetSeconds(p, checkedAt) -> ComparisonStatus.TIME_EXCEEDED
                    option.type == ComparisonType.SCENIC && (bestSeconds == null ||
                        out.seconds > bestSeconds * 1.6 || bestMeters != null && out.meters > bestMeters * 1.6) -> ComparisonStatus.EXCESSIVE_DETOUR
                    else -> ComparisonStatus.ELIGIBLE
                }
                val score = if (status == ComparisonStatus.ELIGIBLE) destinationScore(place, out, back, p, context, stayMinutes, bufferMinutes, seed) else null
                if (status == ComparisonStatus.ELIGIBLE && score == null) status = ComparisonStatus.RANGE_EXCEEDED
                val comparison = comparisons.size
                comparisons.add(RouteComparison(option.label, option.type, seconds, distance, status))
                if (status != ComparisonStatus.ELIGIBLE) {
                    errors.add(error(status)); continue
                }
                val time = (1 - seconds.toDouble() / routeBudgetSeconds(p, checkedAt)).coerceIn(0.0, 1.0)
                val repeat = repeatScore(out.geometry)
                val detour = bestMeters?.let { (it.toDouble() / out.meters.coerceAtLeast(1)).coerceIn(0.0, 1.0) }
                val scenery = if (option.type == ComparisonType.SCENIC && out.origin == RouteOrigin.LIVE_API) 1.0 else null
                val observed = listOf(time to weights.time, repeat to weights.repeat, detour to weights.detour, scenery to weights.scenery)
                    .filter { it.first != null }
                val routeScore = observed.sumOf { it.first!! * it.second } / observed.sumOf { it.second }
                val assessment = ExperienceAssessment(score!!.score.total, routeScore,
                    weights.destination * score.score.total / 120.0 + weights.route * routeScore, time, repeat, detour, scenery)
                evaluated.add(Evaluated(place, out, back, seconds, score, assessment, option, comparison))
            }
        }
        val finishedAt = clock()
        val finalists = evaluated.filter {
            val status = when {
                listOfNotNull(it.out, it.back).any { leg -> !fresh(leg, finishedAt) } -> ComparisonStatus.STALE_DATA
                it.seconds > routeBudgetSeconds(p, finishedAt) -> ComparisonStatus.TIME_EXCEEDED
                else -> ComparisonStatus.ELIGIBLE
            }
            if (status != ComparisonStatus.ELIGIBLE) {
                errors.add(error(status))
                comparisons[it.comparison] = comparisons[it.comparison].copy(status = status)
            }
            status == ComparisonStatus.ELIGIBLE
        }
        if (Thread.currentThread().isInterrupted) return RoutePlan.Failure(setOf(RouteError.NETWORK))
        val ordered = finalists.sortedWith(compareByDescending<Evaluated> { it.assessment.combinedScore }
            .thenBy { it.place.id }.thenBy { it.option.options.priority.ordinal })
        val seen = mutableSetOf<Any>()
        val representatives = ordered.filter {
            val key = Triple(it.place.id, it.out.origin,
                if (it.out.geometry.size >= 2) it.out.geometry else it.out.seconds to it.out.meters)
            if (seen.add(key)) true else {
                comparisons[it.comparison] = comparisons[it.comparison].copy(status = ComparisonStatus.DUPLICATE)
                false
            }
        }
        val winner = representatives.firstOrNull()
            ?: return RoutePlan.Failure(errors.ifEmpty { setOf(RouteError.NO_CANDIDATE) })
        val reason = if (winner.assessment.sceneryScore != null)
            "검증된 경유지와 관측된 시간·거리·반복 구간을 비교했습니다. 기분 가중치는 초기 가설입니다."
        else "경관 근거는 미확인으로 제외하고 관측된 시간·거리·반복 구간을 비교했습니다. 기분 가중치는 초기 가설입니다."
        val selection = ExperienceSelection(winner.out.options, weights, winner.assessment, comparisons.toList(), requests, reason)
        return RoutePlan.Success(winner.place, winner.out, winner.back, winner.seconds, winner.scoring, selection)
    }

    private fun destinationScore(place: RealPlace, out: RouteLeg, back: RouteLeg?, p: DrivePreferences,
        c: RecommendationContext, stay: Int, buffer: Int, seed: Long): RecommendationResult.Success? {
        val estimate = JourneyEstimate(((out.seconds + 59) / 60).toInt(), stay,
            back?.let { ((it.seconds + 59) / 60).toInt() } ?: 0, buffer,
            if (out.origin == RouteOrigin.LIVE_API) EstimateOrigin.VERIFIED_ROUTE else EstimateOrigin.DEMO,
            if (out.origin == RouteOrigin.LIVE_API) (out.meters + (back?.meters ?: 0)) / 1000.0 else null)
        val candidate = DestinationCandidate(place.id, place.name, "출처 확인 장소 · 진입 정보 별도 검증", place.moods,
            place.categories, setOf(Companion.SOLO), mapOf(p.scope to estimate), sample = false)
        return DestinationEngine(listOf(candidate)).recommend(p.copy(availableMinutes = Int.MAX_VALUE, returnByEpochMillis = null), c, seed)
            as? RecommendationResult.Success
    }
    private fun fresh(leg: RouteLeg, now: Long) = now - leg.receivedAtMillis in -60_000..300_000
    private fun validResponse(leg: RouteLeg, requested: RouteOptions) = leg.options == requested &&
        (leg.origin != RouteOrigin.LIVE_API || leg.geometry.size >= 2)
    private fun matchesScenery(waypoint: ScenicWaypoint, p: DrivePreferences): Boolean {
        val mapping = mapOf("water" to PlaceCategory.WATER, "nature" to PlaceCategory.NATURE,
            "night" to PlaceCategory.NIGHT, "camping" to PlaceCategory.CAMPING)
        val categories = waypoint.tags.mapNotNull { mapping[it.lowercase()] }.toSet()
        val preferred = p.categories.intersect(mapping.values.toSet())
        return categories.isNotEmpty() && categories.none { it in p.excludedCategories } && categories.containsAll(preferred)
    }
    private fun repeatScore(points: List<GeoPoint>): Double? {
        val segments = points.zipWithNext().filter { it.first != it.second }
        if (segments.isEmpty()) return null
        return segments.map { setOf(it.first, it.second) }.distinct().size.toDouble() / segments.size
    }
    private fun error(status: ComparisonStatus) = when (status) {
        ComparisonStatus.TIME_EXCEEDED -> RouteError.TIME_EXCEEDED
        ComparisonStatus.STALE_DATA -> RouteError.STALE_DATA
        ComparisonStatus.INVALID_RESPONSE -> RouteError.INVALID_RESPONSE
        else -> RouteError.NO_CANDIDATE
    }
    private fun weights(mood: Mood): ExperienceWeights = when (mood) {
        Mood.HEALING -> ExperienceWeights(.35, .65, .20, .15, .10, .55)
        Mood.REFLECTION -> ExperienceWeights(.35, .65, .20, .20, .10, .50)
        Mood.ADVENTURE, Mood.EXCITEMENT -> ExperienceWeights(.40, .60, .25, .15, .15, .45)
        Mood.ENERGY -> ExperienceWeights(.40, .60, .30, .20, .20, .30)
        Mood.COMFORT -> ExperienceWeights(.50, .50, .60, .15, .20, .05)
    }
}
