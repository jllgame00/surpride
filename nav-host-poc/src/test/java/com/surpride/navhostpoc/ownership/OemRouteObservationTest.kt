package com.surpride.navhostpoc.ownership

import org.junit.Assert.*
import org.junit.Test

class OemRouteObservationTest {
    @Test fun connectionAloneDoesNotProveNoRoute() {
        val observation = OemRouteObservation()
        observation.connected()
        assertTrue(observation.status.connected)
        assertNull(observation.status.routeActive)
    }

    @Test fun routeSearchAndGuidanceBothBlockTakingOwnership() {
        val observation = OemRouteObservation()
        observation.connected()
        observation.snapshot(guiding = false, routing = true, termsAgreed = true)
        assertEquals(true, observation.status.routeActive)
        observation.snapshot(guiding = true, routing = false, termsAgreed = true)
        assertEquals(true, observation.status.routeActive)
    }

    @Test fun onlyFreshAgreedIdleSnapshotProvesInactive() {
        val observation = OemRouteObservation()
        observation.connected()
        observation.snapshot(guiding = false, routing = false, termsAgreed = false)
        assertNull(observation.status.routeActive)
        observation.snapshot(guiding = false, routing = false, termsAgreed = true)
        assertEquals(false, observation.status.routeActive)
        observation.invalidate()
        assertNull(observation.status.routeActive)
    }

    @Test fun errorDiscardsCachedInactiveState() {
        val observation = OemRouteObservation()
        observation.connected()
        observation.snapshot(false, false, true)
        observation.failed("PERMISSION_DENIED")
        assertNull(observation.status.routeActive)
        assertNotNull(observation.status.error)
    }
}
