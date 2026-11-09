package com.surpride.app.moodinput

import com.surpride.app.experience.core.*
import org.junit.Assert.*
import org.junit.Test

class PreferenceInputContractTest {
    @Test fun structuredPreferencesRoundTripWithoutTripOrHistoryPayload() {
        val p=DrivePreferences(availableMinutes=90,categories=setOf(PlaceCategory.NATURE))
        assertEquals(p,PreferenceInputContract.decode(PreferenceInputContract.encode(p)))
        assertNull(PreferenceInputContract.decode(byteArrayOf(1,2)))
    }
    @Test fun arbitraryTripHistoryAndOutOfRangePreferencesAreNotAccepted() {
        val record=DemoVisit("visit","demo-water",Mood.HEALING,5,1,false)
        assertNull(PreferenceInputContract.decode(TripSnapshotCodec.encode(MysteryTripState(history=listOf(record)))))
        assertNull(PreferenceInputContract.decode(TripSnapshotCodec.encode(MysteryTripState(preferences=DrivePreferences(randomness=200)))))
    }
}
