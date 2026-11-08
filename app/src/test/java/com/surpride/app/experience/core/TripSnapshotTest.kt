package com.surpride.app.experience.core

import org.junit.Assert.*
import org.junit.Test

class TripSnapshotTest {
    @Test fun snapshotRoundTripPreservesHiddenAndCompletedState() {
        val c=TripController(DemoCatalog.candidates,{1_800_000_000_000},{7})
        c.openSetup(); c.beginPlanning(); c.finishPlanning(); c.startDemo(); c.advanceDemo()
        assertEquals(c.state,TripSnapshotCodec.decode(TripSnapshotCodec.encode(c.state),DemoCatalog.candidates))
        c.arrive(); c.revealArrival(); c.rate(5); c.complete()
        assertEquals(c.state,TripSnapshotCodec.decode(TripSnapshotCodec.encode(c.state),DemoCatalog.candidates))
    }
    @Test fun corruptedOrUnknownCatalogSnapshotIsRejected() {
        try {TripSnapshotCodec.decode(byteArrayOf(0,1,2),DemoCatalog.candidates); fail()} catch (_: Exception) {}
        val c=TripController(DemoCatalog.candidates,{1_800_000_000_000},{7})
        c.beginPlanning(); c.finishPlanning()
        try {TripSnapshotCodec.decode(TripSnapshotCodec.encode(c.state),emptyList()); fail()} catch (_: Exception) {}
    }
    @Test fun unknownOrRestrictedVehicleUxNeverAllowsParkedControls() {
        assertFalse(ParkedInteractionPolicy.allows(null))
        assertFalse(ParkedInteractionPolicy.allows(true))
        assertTrue(ParkedInteractionPolicy.allows(false))
    }
    @Test fun delayedRecoveryActionRechecksLatestVehiclePolicy() {
        var requiresDo: Boolean? = false
        var resets=0
        val confirm={ParkedInteractionPolicy.runIfAllowed({requiresDo}){resets++}}
        requiresDo=true;confirm();assertEquals(0,resets)
        requiresDo=null;confirm();assertEquals(0,resets)
        requiresDo=false;confirm();assertEquals(1,resets)
    }
}
