package com.surpride.navhostpoc.ownership

import org.junit.Assert.*
import org.junit.Test

class FocusLeaseSessionTest {
    @Test fun connectionDoesNotAcquireFocusOrInventAnInactiveSnapshot() {
        val session = FocusLeaseSession()
        val driver = Driver()
        session.connected(driver)
        assertTrue(session.status.connected)
        assertNull(session.status.otherNavigationActive)
        assertTrue(driver.requested.isEmpty())
    }

    @Test fun grantRequiresNativeOwnershipVerification() {
        val session = FocusLeaseSession()
        val driver = Driver()
        session.connected(driver)
        var grants = 0
        var losses = 0
        assertTrue(session.request(1, { grants++ }, { losses++ }))
        driver.requested.single().granted()
        assertEquals(0, grants)
        assertEquals(1, losses)
        assertNotNull(session.status.error)
    }

    @Test fun lateGrantReleasesOnlyItsOriginalLease() {
        val session = FocusLeaseSession()
        val driver = Driver()
        session.connected(driver)
        var currentGrants = 0
        session.request(1, {}, {})
        val old = driver.requested.single()
        session.abandon(1)
        session.request(2, { currentGrants++ }, {})
        val current = driver.requested.last()
        driver.owned = current
        current.granted()
        old.granted()
        assertEquals(1, currentGrants)
        assertSame(current, driver.owned)
        assertFalse(driver.abandoned.contains(current))
        assertSame(old, driver.abandoned.last())
    }

    @Test fun carConnectionLossInvalidatesLeaseAndUnknownsOtherNavigation() {
        val session = FocusLeaseSession()
        val driver = Driver()
        session.connected(driver)
        var losses = 0
        session.request(1, {}, { losses++ })
        val callback = driver.requested.single()
        driver.owned = callback
        callback.granted()
        session.disconnected("CAR_DISCONNECTED")
        assertEquals(1, losses)
        assertFalse(session.status.connected)
        assertNull(session.status.otherNavigationActive)
        assertFalse(session.request(2, {}, {}))
        assertNull(driver.owned)
    }

    @Test fun anotherNavigationOwnerStopsCurrentLease() {
        val session = FocusLeaseSession()
        val driver = Driver()
        session.connected(driver)
        var losses = 0
        session.request(1, {}, { losses++ })
        driver.owned = driver.requested.single()
        driver.owned!!.granted()
        driver.owned = null
        driver.listener!!(true)
        assertEquals(1, losses)
        assertEquals(true, session.status.otherNavigationActive)
        assertFalse(session.request(2, {}, {}))
    }

    @Test fun permissionFailureFailsClosed() {
        val session = FocusLeaseSession()
        val driver = Driver().apply { failRequest = true }
        session.connected(driver)
        assertFalse(session.request(1, {}, {}))
        assertNotNull(session.status.error)
        assertNull(session.status.otherNavigationActive)
        assertFalse(session.request(2, {}, {}))
    }

    private class Driver : FocusDriver {
        var listener: ((Boolean) -> Unit)? = null
        val requested = mutableListOf<FocusLeaseCallback>()
        val abandoned = mutableListOf<FocusLeaseCallback>()
        var owned: FocusLeaseCallback? = null
        var failRequest = false
        override fun listen(changed: (Boolean) -> Unit) { listener = changed }
        override fun request(callback: FocusLeaseCallback): Boolean {
            if (failRequest) throw SecurityException("denied")
            requested += callback
            return true
        }
        override fun owns(callback: FocusLeaseCallback) = owned === callback
        override fun abandon(callback: FocusLeaseCallback) {
            abandoned += callback
            if (owned === callback) owned = null
        }
        override fun close() { listener = null }
    }
}
