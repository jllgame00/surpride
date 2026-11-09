package com.surpride.app.realdata

import com.surpride.app.realdata.experience.*
import org.junit.Assert.*
import org.junit.Test

class ScenicCatalogTest {
    @Test fun representativeCoordinatesAndClosedParkingNeverBecomeRoutableWaypoints() {
        assertEquals(2,PendingScenicCatalog.records.size)
        assertTrue(PendingScenicCatalog.routingCandidates.isEmpty())
        PendingScenicCatalog.records.forEach {
            assertNull(it.waypoint.entryPoint)
            assertFalse(it.waypoint.eligible)
            assertTrue(it.waypoint.sources.isNotEmpty())
            assertEquals("2026-11-09",it.waypoint.checkedDate)
            assertTrue(it.uncertainty.isNotBlank())
        }
        assertEquals(AccessStatus.RESTRICTED,PendingScenicCatalog.records.first().waypoint.access)
        assertEquals(EvidenceStatus.UNKNOWN,PendingScenicCatalog.records.last().drivingViewEvidence)
    }
}
