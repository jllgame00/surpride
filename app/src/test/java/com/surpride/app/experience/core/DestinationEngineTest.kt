package com.surpride.app.experience.core

import com.surpride.app.vehicle.*
import org.junit.Assert.*
import org.junit.Test

class DestinationEngineTest {
    private val now = 1_800_000_000_000L
    private val engine = DestinationEngine(DemoCatalog.candidates)
    private fun recommend(p: DrivePreferences = DrivePreferences(), c: RecommendationContext = RecommendationContext(now), seed: Long = 1) = engine.recommend(p, c, seed)

    @Test fun eachMoodHasAnAppropriateBestCandidate() {
        val ids = Mood.entries.map { mood ->
            val result = recommend(DrivePreferences(mood = mood, randomness = 0)) as RecommendationResult.Success
            assertEquals(5, result.candidate.moods.getValue(mood)); result.candidate.id
        }
        assertEquals(6, ids.toSet().size)
    }
    @Test fun requiredPlaceCategoriesAreNeverRelaxed() {
        val result = recommend(DrivePreferences(categories = setOf(PlaceCategory.WATER, PlaceCategory.CHILD), randomness = 100)) as RecommendationResult.Success
        assertTrue(result.candidate.categories.containsAll(setOf(PlaceCategory.WATER, PlaceCategory.CHILD)))
    }
    @Test fun timeBudgetExcludesOverlongPlans() {
        assertTrue(recommend(DrivePreferences(availableMinutes = 5)) is RecommendationResult.Failure)
        val result = recommend(DrivePreferences(availableMinutes = 30)) as RecommendationResult.Success
        assertTrue(result.totalMinutes <= 30)
    }
    @Test fun returnDeadlineAndRoundTripAreIncluded() {
        assertTrue(recommend(DrivePreferences(availableMinutes = null, returnByEpochMillis = now + 10 * 60_000)) is RecommendationResult.Failure)
        val out = recommend(DrivePreferences(returnToStart = false, randomness = 0)) as RecommendationResult.Success
        val round = recommend(DrivePreferences(returnToStart = true, randomness = 0)) as RecommendationResult.Success
        assertTrue(out.totalMinutes < round.totalMinutes)
    }
    @Test fun noTimeDataAndExpiredDeadlineAreErrors() {
        assertTrue(recommend(DrivePreferences(availableMinutes = null)) is RecommendationResult.Failure)
        assertTrue(recommend(DrivePreferences(returnByEpochMillis = now - 1)) is RecommendationResult.Failure)
    }
    @Test fun missingEstimatesAreNotInvented() {
        val missing = DemoCatalog.candidates.map {it.copy(estimates = emptyMap())}
        assertTrue(DestinationEngine(missing).recommend(DrivePreferences(), RecommendationContext(now), 0) is RecommendationResult.Failure)
    }
    @Test fun recentPlacesAreExcludedBeforeRandomization() {
        val first = recommend(DrivePreferences(randomness = 0)) as RecommendationResult.Success
        val next = recommend(DrivePreferences(randomness = 100), RecommendationContext(now, recentIds = setOf(first.candidate.id))) as RecommendationResult.Success
        assertNotEquals(first.candidate.id, next.candidate.id)
    }
    @Test fun noCandidatesAndAllRecentlyUsedDoNotSilentlyFallback() {
        assertTrue(DestinationEngine(emptyList()).recommend(DrivePreferences(), RecommendationContext(now), 0) is RecommendationResult.Failure)
        assertTrue(recommend(c = RecommendationContext(now, recentIds = DemoCatalog.candidates.map {it.id}.toSet())) is RecommendationResult.Failure)
    }
    @Test fun randomnessIsSeededAndNeverBreaksConstraints() {
        val p = DrivePreferences(categories = setOf(PlaceCategory.NATURE), randomness = 100)
        assertEquals(recommend(p, seed = 123), recommend(p, seed = 123))
        (0L..30L).forEach { seed ->
            val r = recommend(p, seed = seed) as RecommendationResult.Success
            assertTrue(PlaceCategory.NATURE in r.candidate.categories)
        }
    }
    @Test fun knownWeatherCanExcludeUnsafeSampleAndUnknownWeatherIsExplicit() {
        val p = DrivePreferences(categories = setOf(PlaceCategory.CAMPING), scope = TravelScope.DAY, availableMinutes = 240)
        assertTrue(recommend(p, RecommendationContext(now, weather = Weather.STORM)) is RecommendationResult.Failure)
        assertTrue((recommend() as RecommendationResult.Success).warnings.contains(PlanWarning.WEATHER_UNKNOWN))
    }
    @Test fun companionIsAHardConstraint() {
        val p = DrivePreferences(companion = Companion.FAMILY)
        val restricted = DemoCatalog.candidates.map {it.copy(companions = setOf(Companion.SOLO))}
        assertTrue(DestinationEngine(restricted).recommend(p, RecommendationContext(now), 0) is RecommendationResult.Failure)
    }
    @Test fun allFourScopesHaveSamplePlansWithSufficientTime() {
        TravelScope.entries.forEach { scope ->
            assertTrue(recommend(DrivePreferences(scope = scope, availableMinutes = scope.defaultMinutes)) is RecommendationResult.Success)
        }
    }
    @Test fun batterySocDoesNotBecomeRangeAndFakeRangeIsNotTrusted() {
        val state = VehicleState(readings = mapOf(VehicleMetric.BATTERY to VehicleReading(ReadingStatus.AVAILABLE, 100.0)))
        assertNull(vehicleContext(state, now, true).verifiedRangeKm)
        val fake = VehicleState(origin = DataOrigin.FAKE, readings = mapOf(VehicleMetric.RANGE to VehicleReading(ReadingStatus.AVAILABLE, 500.0, receivedAtMillis = now)))
        assertNull(vehicleContext(fake, now, true).verifiedRangeKm)
        assertTrue((recommend() as RecommendationResult.Success).warnings.contains(PlanWarning.RANGE_UNVERIFIED))
    }
    @Test fun verifiedFreshRangeOnlyFiltersVerifiedRoadDistances() {
        val candidate = DemoCatalog.candidates.first().copy(estimates = mapOf(TravelScope.LOCAL to JourneyEstimate(10, 10, 10, 0, EstimateOrigin.VERIFIED_ROUTE, 80.0)))
        val context = RecommendationContext(now, vehicle = VehicleContext(20.0))
        assertTrue(DestinationEngine(listOf(candidate)).recommend(DrivePreferences(), context, 0) is RecommendationResult.Failure)
        val stale = VehicleState(readings = mapOf(VehicleMetric.RANGE to VehicleReading(ReadingStatus.AVAILABLE, 500.0, receivedAtMillis = now - 120_000)))
        assertNull(vehicleContext(stale, now, true).verifiedRangeKm)
    }
    @Test fun rangeUnitMustBeConfirmedBeforeSdkValueIsUsedAsKilometers() {
        val fresh=VehicleState(readings=mapOf(VehicleMetric.RANGE to VehicleReading(ReadingStatus.AVAILABLE,500.0,receivedAtMillis=now)))
        assertNull(vehicleContext(fresh,now).verifiedRangeKm)
        assertEquals(500.0,vehicleContext(fresh,now,true).verifiedRangeKm)
    }
    @Test fun overflowingExternalDurationsCannotBecomeAShortOrNegativeTrip() {
        val malformed=DemoCatalog.candidates.first().copy(estimates=mapOf(TravelScope.LOCAL to JourneyEstimate(Int.MAX_VALUE,Int.MAX_VALUE,Int.MAX_VALUE,Int.MAX_VALUE)))
        assertTrue(DestinationEngine(listOf(malformed)).recommend(DrivePreferences(),RecommendationContext(now),0) is RecommendationResult.Failure)
    }
}
