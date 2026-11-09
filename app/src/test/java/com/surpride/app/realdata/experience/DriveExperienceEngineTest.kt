package com.surpride.app.realdata.experience

import com.surpride.app.experience.core.*
import com.surpride.app.realdata.*
import org.junit.Assert.*
import org.junit.Test

class DriveExperienceEngineTest {
    private val now = 1_800_000_000_000L
    private val origin = GeoPoint(126.0, 37.0)
    private val target = GeoPoint(126.2, 37.2)
    private val entry = GeoPoint(126.1, 37.1)
    private val place = RealPlace("private-id", "PRIVATE DESTINATION", target, "test", setOf(PlaceCategory.NATURE),
        Mood.entries.associateWith { 5 }, "https://example.org/source")
    private fun preferences(minutes: Int = 100) = DrivePreferences(availableMinutes = minutes, randomness = 0)
    private fun waypoint() = ScenicWaypoint("private-waypoint", "PRIVATE VIEW", GeoPoint(126.15, 37.15), entry,
        setOf("water"), EvidenceStatus.VERIFIED, AccessStatus.ALLOWED, EvidenceStatus.VERIFIED,
        true, listOf("https://example.org/legal-entry"), "2026-11-09")
    private class FixtureProvider(private val reply: (GeoPoint, GeoPoint, RouteOptions) -> RouteReply) : RouteProvider {
        val calls = mutableListOf<Triple<GeoPoint, GeoPoint, RouteOptions>>()
        override fun route(origin: GeoPoint, destination: GeoPoint) = route(origin, destination, RouteOptions())
        override fun route(origin: GeoPoint, destination: GeoPoint, options: RouteOptions): RouteReply {
            calls.add(Triple(origin, destination, options))
            return reply(origin, destination, options)
        }
    }
    private fun leg(seconds: Long, options: RouteOptions, geometry: List<GeoPoint> = listOf(origin, entry, target),
        meters: Long = 10000, at: Long = now, mode: RouteOrigin = RouteOrigin.LIVE_API) =
        RouteReply.Success(RouteLeg(seconds, meters, geometry, mode, at, options))
    private fun provider() = FixtureProvider { a, _, options ->
        leg(if (a == target) 1800 else if (options.waypoints.isNotEmpty()) 3360 else 2400, options,
            geometry = if (options.waypoints.isNotEmpty()) listOf(origin, entry, target) else listOf(origin, target),
            meters = if (options.waypoints.isNotEmpty()) 12000 else 10000)
    }
    private fun evaluate(provider: RouteProvider = provider(), p: DrivePreferences = preferences(),
        candidates: List<RealPlace> = listOf(place), points: List<ScenicWaypoint> = listOf(waypoint()),
        context: RecommendationContext = RecommendationContext(now), clock: () -> Long = { now },
        stay: Int = 0, buffer: Int = 0) =
        DriveExperienceEngine(candidates, provider, points, clock).recommend(origin, p, context, stay, buffer, 17)

    @Test fun verifiedScenicEightySixMinuteRouteCanBeatNormalSeventyMinuteRoute() {
        val plan = evaluate() as RoutePlan.Success
        assertEquals(5160L, plan.totalSeconds)
        assertEquals(listOf(entry), plan.experience!!.selectedOptions.waypoints)
        assertEquals(1.0, plan.experience!!.assessment.sceneryScore!!, 0.0)
        assertTrue(plan.experience!!.comparisons.any { it.totalSeconds == 4200L })
    }
    @Test fun eightyMinuteBudgetExcludesScenicCandidate() {
        val plan = evaluate(p = preferences(80)) as RoutePlan.Success
        assertEquals(4200L, plan.totalSeconds)
        assertTrue(plan.experience!!.selectedOptions.waypoints.isEmpty())
        assertTrue(plan.experience!!.comparisons.any { it.status == ComparisonStatus.TIME_EXCEEDED && it.totalSeconds == 5160L })
    }
    @Test fun unknownSceneryIsMissingEvidenceAndWeightsRenormalize() {
        val plan = evaluate(points = emptyList()) as RoutePlan.Success
        val assessment = plan.experience!!.assessment
        assertNull(assessment.sceneryScore)
        assertTrue(assessment.routeScore > 0.5)
        assertEquals(plan.scoring.score.total, assessment.destinationScore)
        assertEquals(plan.experience!!.weights, (evaluate(points = emptyList()) as RoutePlan.Success).experience!!.weights)
    }
    @Test fun unsafeUnlicensedUnknownAndRepresentativeOnlyWaypointsAreNeverRequested() {
        val unsafe = listOf(waypoint().copy(access = AccessStatus.RESTRICTED), waypoint().copy(licenseVerified = false),
            waypoint().copy(entryEvidence = EvidenceStatus.UNKNOWN), waypoint().copy(sceneryEvidence = EvidenceStatus.INFERRED),
            waypoint().copy(entryPoint = null), waypoint().copy(sources = emptyList()), waypoint().copy(checkedDate = ""))
        val api = provider()
        val plan = evaluate(api, points = unsafe) as RoutePlan.Success
        assertTrue(api.calls.all { it.third.waypoints.isEmpty() })
        assertNull(plan.experience!!.assessment.sceneryScore)
    }
    @Test fun shortlistUsesMoodThenIdAndFiltersBeforeRequests() {
        val api = provider()
        val low = place.copy(id = "a-low", moods = emptyMap(), point = GeoPoint(126.3, 37.3))
        val high = place.copy(id = "z-high", point = GeoPoint(126.4, 37.4))
        val recent = place.copy(id = "recent", point = GeoPoint(126.5, 37.5))
        evaluate(api, candidates = listOf(low, high, recent, place), context = RecommendationContext(now, recentIds = setOf("recent")))
        assertFalse(api.calls.any { it.first == low.point || it.second == low.point || it.first == recent.point || it.second == recent.point })
        assertTrue(api.calls.size <= 10)
    }
    @Test fun unsupportedCompanionAndMissingCandidatesMakeNoRequests() {
        val api = provider()
        assertTrue(evaluate(api, p = preferences().copy(companion = Companion.FAMILY)) is RoutePlan.Failure)
        assertTrue(evaluate(api, p = preferences().copy(categories = setOf(PlaceCategory.PET))) is RoutePlan.Failure)
        assertEquals(0, api.calls.size)
    }
    @Test fun reverseLegRequiredAndStayBufferCountInExactSeconds() {
        val api = FixtureProvider { a, _, options -> leg(if (a == target) 601 else 599, options) }
        val plan = evaluate(api, preferences(50), points = emptyList(), stay = 20, buffer = 10) as RoutePlan.Success
        assertEquals(3000L, plan.totalSeconds)
        assertEquals(1, api.calls.count { it.first == target && it.second == origin })
        val missing = FixtureProvider { a, _, options -> if (a == target) RouteReply.Failure(RouteError.NO_ROUTE) else leg(600, options) }
        assertTrue(evaluate(missing, points = emptyList()) is RoutePlan.Failure)
    }
    @Test fun duplicateAlternativesAreComparedAndBounded() {
        val api = provider()
        val plan = evaluate(api) as RoutePlan.Success
        assertTrue(plan.experience!!.comparisons.any { it.status == ComparisonStatus.DUPLICATE })
        assertEquals(api.calls.size, plan.experience!!.requestCount)
        assertTrue(api.calls.size <= 5)
    }
    @Test fun repeatedSegmentsAndExcessiveDetoursCannotGainScenicBoost() {
        val api = FixtureProvider { a, _, options ->
            if (options.waypoints.isNotEmpty()) leg(6000, options, listOf(origin, entry, origin, entry, target), 60000)
            else leg(if (a == target) 1800 else 2400, options)
        }
        val plan = evaluate(api, preferences(180)) as RoutePlan.Success
        assertTrue(plan.experience!!.selectedOptions.waypoints.isEmpty())
        assertTrue(plan.experience!!.comparisons.any { it.status == ComparisonStatus.EXCESSIVE_DETOUR })
    }
    @Test fun repeatScoreUsesObservedSegmentsWithoutClaimingEnjoyment() {
        val api = FixtureProvider { a, _, options ->
            val geometry = if (options.priority == RoutePriority.TIME) listOf(origin, entry, target)
                else listOf(origin, entry, origin, entry, target)
            leg(if (a == target) 1800 else 2400, options, geometry)
        }
        val plan = evaluate(api, points = emptyList()) as RoutePlan.Success
        assertEquals(RoutePriority.TIME, plan.experience!!.selectedOptions.priority)
        assertEquals(1.0, plan.experience!!.assessment.repeatScore!!, 0.0)
        assertFalse(plan.experience!!.publicReason.contains("재미"))
    }
    @Test fun finalDeadlineAndFreshnessAreRecheckedAfterAllRequests() {
        var current = now
        val api = FixtureProvider { _, _, options -> current += 30_000; leg(30, options) }
        assertTrue(evaluate(api, preferences().copy(returnByEpochMillis = now + 120_000), points = emptyList(), clock = { current }) is RoutePlan.Failure)
        val stale = FixtureProvider { _, _, options -> leg(30, options, at = now - 300001) }
        assertTrue(evaluate(stale, points = emptyList()) is RoutePlan.Failure)
    }
    @Test fun liveVehicleRangeIsHardLimitButMockDoesNotInventVerifiedDistance() {
        val context = RecommendationContext(now, vehicle = VehicleContext(0.1))
        assertTrue(evaluate(context = context) is RoutePlan.Failure)
        val mock = FixtureProvider { _, _, options -> leg(600, options, mode = RouteOrigin.MOCK) }
        val plan = evaluate(mock, context = context, points = emptyList()) as RoutePlan.Success
        assertEquals(RouteOrigin.MOCK, plan.routeOrigin)
        assertTrue(PlanWarning.DISTANCE_UNVERIFIED in plan.scoring.warnings)
    }
    @Test fun publicComparisonAndReasonDoNotExposeDestinationOrWaypointIdentity() {
        val plan = evaluate() as RoutePlan.Success
        val publicText = plan.experience!!.comparisons.toString() + plan.experience!!.publicReason + plan.experience.toString()
        listOf(place.name, place.id, waypoint().privateName, waypoint().id, "126.1", "37.1").forEach { assertFalse(publicText.contains(it)) }
        assertNull(plan.presentation(false).destination)
    }
    @Test fun fatalProviderErrorsStopAllRemainingRequests() {
        listOf(RouteError.AUTH, RouteError.RATE_LIMIT, RouteError.NOT_CONFIGURED).forEach { error ->
            val api = FixtureProvider { _, _, _ -> RouteReply.Failure(error) }
            val result = evaluate(api, candidates = listOf(place, place.copy(id = "second", point = entry))) as RoutePlan.Failure
            assertTrue(error in result.errors)
            assertEquals(1, api.calls.size)
        }
    }
    @Test fun interruptedWorkerStopsRequestsAndPreservesInterruptFlag() {
        val api = FixtureProvider { _, _, options -> Thread.currentThread().interrupt(); leg(600, options) }
        try {
            assertTrue(evaluate(api) is RoutePlan.Failure)
            assertEquals(1, api.calls.size)
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
    @Test fun scenicTagsRespectPreferredAndExcludedLandscapes() {
        listOf(preferences().copy(categories = setOf(PlaceCategory.NATURE)),
            preferences().copy(excludedCategories = setOf(PlaceCategory.WATER))).forEach { p ->
            val api = provider()
            val result = evaluate(api, p) as RoutePlan.Success
            assertTrue(api.calls.all { it.third.waypoints.isEmpty() })
            assertNull(result.experience!!.assessment.sceneryScore)
        }
    }
    @Test fun explicitScenicTagsMapToEachLandscapeAndUnknownNeverBoosts() {
        mapOf("water" to PlaceCategory.WATER, "nature" to PlaceCategory.NATURE,
            "night" to PlaceCategory.NIGHT, "camping" to PlaceCategory.CAMPING).forEach { (tag, category) ->
            val result = evaluate(p = preferences().copy(categories = setOf(category)),
                candidates = listOf(place.copy(categories = setOf(category))),
                points = listOf(waypoint().copy(tags = setOf(tag)))) as RoutePlan.Success
            assertEquals(1.0, result.experience!!.assessment.sceneryScore!!, 0.0)
        }
        val unknown = evaluate(points = listOf(waypoint().copy(tags = setOf("unverified-tag")))) as RoutePlan.Success
        assertNull(unknown.experience!!.assessment.sceneryScore)
        assertTrue(unknown.experience!!.selectedOptions.waypoints.isEmpty())
    }
    @Test fun everyOutboundAndInboundResponseMustEchoRequestedOptions() {
        listOf(true, false).forEach { mismatchOutbound ->
            val api = FixtureProvider { a, _, options ->
                val mismatch = if (mismatchOutbound) a == origin else a == target
                val returned = if (mismatch) RouteOptions(if (options.priority == RoutePriority.TIME)
                    RoutePriority.DISTANCE else RoutePriority.TIME) else options
                leg(600, returned)
            }
            val result = evaluate(api, points = emptyList())
            assertTrue(result is RoutePlan.Failure)
            assertTrue(RouteError.INVALID_RESPONSE in (result as RoutePlan.Failure).errors)
        }
    }
    @Test fun liveRoutesRequireGeometryButLegacyMockMayOmitIt() {
        listOf(emptyList(), listOf(origin)).forEach { geometry ->
            val api = FixtureProvider { _, _, options -> leg(600, options, geometry) }
            val result = evaluate(api, points = emptyList())
            assertTrue(result is RoutePlan.Failure)
            assertTrue(RouteError.INVALID_RESPONSE in (result as RoutePlan.Failure).errors)
        }
        val mock = FixtureProvider { _, _, options -> leg(600, options, emptyList(), mode = RouteOrigin.MOCK) }
        assertTrue(evaluate(mock, points = emptyList()) is RoutePlan.Success)
    }
}
