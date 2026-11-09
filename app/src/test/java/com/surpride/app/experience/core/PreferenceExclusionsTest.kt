package com.surpride.app.experience.core

import com.surpride.app.realdata.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class PreferenceExclusionsTest {
    private val now = 1_800_000_000_000L
    private val context = RecommendationContext(now)
    private fun candidate(id: String, categories: Set<PlaceCategory>, mood: Int = 5) = DestinationCandidate(
        id, id, "fixture", mapOf(Mood.HEALING to mood), categories, setOf(Companion.SOLO),
        mapOf(TravelScope.LOCAL to JourneyEstimate(10, 10, 10, 5)))

    @Test fun demoRejectsAnyExcludedCategoryBeforeChoosingHigherScore() {
        val excluded = candidate("excluded", setOf(PlaceCategory.WATER, PlaceCategory.NIGHT))
        val allowed = candidate("allowed", setOf(PlaceCategory.NATURE), mood = 1)
        val p = DrivePreferences(excludedCategories = setOf(PlaceCategory.NIGHT), randomness = 0)
        val result = DestinationEngine(listOf(excluded, allowed)).recommend(p, context, 1)
        assertEquals("allowed", (result as RecommendationResult.Success).candidate.id)
    }

    @Test fun overlappingRequiredAndExcludedCategoriesFailRatherThanRelaxingEither() {
        val p = DrivePreferences(categories = setOf(PlaceCategory.WATER),
            excludedCategories = setOf(PlaceCategory.WATER))
        val result = DestinationEngine(listOf(candidate("water", setOf(PlaceCategory.WATER)),
            candidate("other", setOf(PlaceCategory.NATURE)))).recommend(p, context, 1)
        assertTrue(result is RecommendationResult.Failure)
    }

    @Test fun requiredCategoriesStillRequireAllWhenExclusionsExist() {
        val p = DrivePreferences(categories = setOf(PlaceCategory.WATER, PlaceCategory.NIGHT),
            excludedCategories = setOf(PlaceCategory.PET), randomness = 0)
        val result = DestinationEngine(listOf(candidate("partial", setOf(PlaceCategory.WATER)),
            candidate("all", setOf(PlaceCategory.WATER, PlaceCategory.NIGHT), mood = 1)))
            .recommend(p, context, 1)
        assertEquals("all", (result as RecommendationResult.Success).candidate.id)
    }

    @Test fun excludedLiveCandidateNeverTriggersRouteRequests() {
        val place = SeoulPilotCatalog.places.first().copy(categories = setOf(PlaceCategory.WATER, PlaceCategory.NIGHT))
        var routeCalls = 0
        val provider = RouteProvider { _, _ -> routeCalls++; RouteReply.Failure(RouteError.NETWORK) }
        val p = DrivePreferences(excludedCategories = setOf(PlaceCategory.NIGHT))
        val result = RouteRecommendationEngine(listOf(place), provider, { now })
            .recommend(GeoPoint(126.97, 37.57), p, context, 20, 10, 1)
        assertTrue(result is RoutePlan.Failure)
        assertEquals(0, routeCalls)
    }

    @Test fun overlappingLiveConditionsFailWithoutRouteRequests() {
        val place = SeoulPilotCatalog.places.first().copy(categories = setOf(PlaceCategory.WATER))
        var routeCalls = 0
        val provider = RouteProvider { _, _ -> routeCalls++; RouteReply.Failure(RouteError.NETWORK) }
        val p = DrivePreferences(categories = setOf(PlaceCategory.WATER), excludedCategories = setOf(PlaceCategory.WATER))
        val result = RouteRecommendationEngine(listOf(place), provider, { now })
            .recommend(GeoPoint(126.97, 37.57), p, context, 20, 10, 1)
        assertTrue(result is RoutePlan.Failure)
        assertEquals(0, routeCalls)
    }

    @Test fun snapshotRoundTripKeepsExclusionsAndExistingHistory() {
        val legacy = TripSnapshotCodec.decode(legacySnapshot(), DemoCatalog.candidates)
        val state = legacy.copy(preferences = legacy.preferences.copy(
            excludedCategories = setOf(PlaceCategory.NIGHT, PlaceCategory.PET)))
        val restored = TripSnapshotCodec.decode(TripSnapshotCodec.encode(state), DemoCatalog.candidates)
        assertEquals(state, restored)
        assertEquals("legacy-trip", restored.history.single().tripId)
    }

    @Test fun legacySnapshotDecodesWithEmptyExclusionsAndReencodesByteForByte() {
        val bytes = legacySnapshot()
        val restored = TripSnapshotCodec.decode(bytes, DemoCatalog.candidates)
        assertTrue(restored.preferences.excludedCategories.isEmpty())
        assertEquals(73L, restored.seed)
        assertEquals("legacy-trip", restored.history.single().tripId)
        assertArrayEquals(bytes, TripSnapshotCodec.encode(restored))
    }

    @Test fun extensionAppendsExclusionsWithoutReplacingLegacyRecords() {
        val restored = TripSnapshotCodec.decode(withExtension {
            writeInt(0x45584331); writeInt(1); writeUTF("PET")
        }, DemoCatalog.candidates)
        assertEquals(setOf(PlaceCategory.PET), restored.preferences.excludedCategories)
        assertEquals("legacy-trip", restored.history.single().tripId)
    }

    @Test fun snapshotRejectsUnknownExtensionAndTruncatedExtension() {
        listOf(withExtension { writeInt(123) }, withExtension { writeInt(0x45584331) },
            withExtension { writeInt(0x45584331); writeInt(1) }).forEach { bytes ->
            assertThrows(Exception::class.java) { TripSnapshotCodec.decode(bytes, DemoCatalog.candidates) }
        }
    }

    @Test fun snapshotRejectsInvalidExtensionCountsNamesDuplicatesAndTrailingBytes() {
        listOf(withExtension { writeInt(0x45584331); writeInt(-1) },
            withExtension { writeInt(0x45584331); writeInt(7) },
            withExtension { writeInt(0x45584331); writeInt(1); writeUTF("UNKNOWN") },
            withExtension { writeInt(0x45584331); writeInt(2); writeUTF("PET"); writeUTF("PET") },
            withExtension { writeInt(0x45584331); writeInt(1); writeUTF("PET"); writeByte(0) }).forEach { bytes ->
            assertThrows(Exception::class.java) { TripSnapshotCodec.decode(bytes, DemoCatalog.candidates) }
        }
    }

    private fun withExtension(write: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            bytes.write(legacySnapshot())
            DataOutputStream(bytes).use { it.write() }
            bytes.toByteArray()
        }

    /** Independent fixture of the shipped v1 format, including a saved visit. */
    private fun legacySnapshot(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x53555232); out.writeInt(1)
            out.writeUTF("SETUP"); out.writeUTF("HOME"); out.writeInt(1)
            out.writeUTF("HEALING"); out.writeUTF("LOCAL"); out.writeInt(1); out.writeUTF("WATER")
            out.writeUTF("SOLO"); out.writeInt(60); out.writeLong(-1); out.writeBoolean(true); out.writeInt(40)
            out.writeLong(73); out.writeUTF("draft"); out.writeInt(0); out.writeBoolean(false)
            out.writeBoolean(false); out.writeInt(-1); out.writeBoolean(false); out.writeBoolean(false)
            out.writeInt(1); out.writeUTF("legacy-trip"); out.writeUTF(DemoCatalog.candidates.first().id)
            out.writeUTF("HEALING"); out.writeInt(5); out.writeLong(now); out.writeBoolean(true)
        }
        bytes.toByteArray()
    }
}
