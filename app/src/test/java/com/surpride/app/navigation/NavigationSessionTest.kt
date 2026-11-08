package com.surpride.app.navigation

import org.junit.Assert.*
import org.junit.Test

class NavigationSessionTest {
    private class Client : NavigationClient {
        var events: ((NavigationEvent) -> Unit)? = null
        var requests = 0
        var cancels = 0
        var releases = 0
        var connections = 0
        var destinationQueries = 0
        var turnQueries = 0
        var failure: Throwable? = null
        var requestFailure: Exception? = null
        override fun connect(connected: () -> Unit, failed: (Throwable) -> Unit, event: (NavigationEvent) -> Unit) {
            connections++; events = event
            failure?.let(failed) ?: connected()
        }
        override fun queryRoute() = Unit
        override fun queryDestination() { destinationQueries++ }
        override fun queryTurns() { turnQueries++ }
        override fun queryHistory() = Unit
        override fun showNavigation() = Unit
        override fun requestPublicTestRoute() { requests++; requestFailure?.let { throw it } }
        override fun cancelRoute() { cancels++ }
        override fun close() { releases++ }
        fun emit(event: NavigationEvent) { events!!(event) }
    }

    private fun session(client: Client, emulator: Boolean = true) = NavigationSession({ client }, emulator, { it() })

    @Test fun connectingNeverStartsRouteAndRepeatedStartDoesNotDuplicateListener() {
        val client = Client(); val source = session(client)
        source.start { }; source.start { }
        assertEquals(1, client.connections)
        assertEquals(0, client.requests)
        assertEquals(NavigationConnection.CONNECTED, source.state.connection)
    }

    @Test fun requestWaitsForFreshIdleSnapshotAndForExplicitCommand() {
        val client = Client(); val source = session(client)
        source.start { }
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        assertEquals(0, client.requests)
        source.requestTestRoute()
        assertEquals(0, client.requests)
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        assertEquals(1, client.requests)
        assertEquals(NavigationStage.REQUEST_ACCEPTED, source.state.stage)
        client.emit(NavigationEvent.SearchStarted)
        assertEquals(NavigationStage.SEARCHING, source.state.stage)
        client.emit(NavigationEvent.SearchCompleted)
        assertEquals(NavigationStage.SEARCH_COMPLETED, source.state.stage)
    }

    @Test fun activeUnknownOrRealVehicleCannotBeOverridden() {
        val client = Client(); val source = session(client)
        source.start { }; source.requestTestRoute()
        client.emit(NavigationEvent.Snapshot(true, false, true, "ROUTE_GUIDING"))
        assertEquals(0, client.requests)
        source.cancelTestRoute()
        assertEquals(0, client.cancels)
        val physical = session(client, false)
        physical.start { }; physical.requestTestRoute()
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        assertEquals(0, client.requests)
    }

    @Test fun cancelRequiresSameSessionAndFreshDestinationMatch() {
        val client = Client(); val source = session(client)
        source.start { }; client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING")); source.requestTestRoute()
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        source.cancelTestRoute()
        client.emit(NavigationEvent.Destination(true, 500, 50))
        assertEquals(0, client.cancels)
        client.emit(NavigationEvent.Snapshot(true, false, true, "ROUTE_GUIDING"))
        assertEquals(1, client.cancels)
        assertEquals(NavigationStage.CANCEL_REQUESTED, source.state.stage)
        client.emit(NavigationEvent.Cancelled)
        assertEquals(NavigationStage.CANCELLED, source.state.stage)
        assertFalse(source.state.ownsTestRequest)
    }

    @Test fun foreignDestinationPreventsCancellation() {
        val client = Client(); val source = session(client)
        source.start { }; client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING")); source.requestTestRoute()
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        source.cancelTestRoute(); client.emit(NavigationEvent.Destination(false, null, null))
        client.emit(NavigationEvent.Snapshot(true, false, true, "ROUTE_GUIDING"))
        assertEquals(0, client.cancels)
        assertFalse(source.state.ownsTestRequest)
    }

    @Test fun initFailurePermissionDeniedAndTimeoutAreVisible() {
        val client = Client().apply { failure = SecurityException("private data") }
        val source = session(client); source.start { }
        assertEquals(NavigationConnection.FAILED, source.state.connection)
        assertEquals("PERMISSION_DENIED", source.state.error?.code)
        assertFalse(source.state.toString().contains("private data"))
        val good = Client(); val pending = session(good)
        pending.start { }; good.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING")); pending.requestTestRoute(); pending.expireQuery()
        good.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        assertEquals(0, good.requests)
        assertEquals("QUERY_TIMEOUT", pending.state.error?.code)
    }

    @Test fun disconnectAndRestartDoNotCancelAndRejectOldCallbacks() {
        val client = Client(); val source = session(client)
        source.start { }; client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING")); source.requestTestRoute()
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        val old = client.events!!
        source.stop(); source.stop(); source.start { }
        old(NavigationEvent.SearchCompleted)
        source.cancelTestRoute()
        assertEquals(0, client.cancels)
        assertEquals(1, client.releases)
        assertFalse(source.state.ownsTestRequest)
    }

    @Test fun fakeIsExplicitAndNoLocationOrDestinationTextIsInModel() {
        val fake = FakeNavigationDataSource()
        fake.start { }
        assertEquals(NavigationOrigin.FAKE, fake.state.origin)
        assertEquals(PrivacyVerdict.UNVERIFIED, fake.state.privacy)
    }

    @Test fun publicSdkPayloadUsesLatitudeThenLongitudeWithoutFakePoiName() {
        val route = publicTestRouteInfo()
        assertEquals(37.48544722, route.latitude, 0.00000001)
        assertEquals(127.03636666, route.longitude, 0.00000001)
        assertEquals("42dot", route.poiName)
    }

    @Test fun failedConnectionCanBeRetriedWithoutDuplicateListener() {
        val client = Client().apply { failure = IllegalStateException() }
        val source = session(client); source.start { }
        client.failure = null
        source.start { }
        assertEquals(NavigationConnection.CONNECTED, source.state.connection)
        assertEquals(1, client.releases)
        assertEquals(2, client.connections)
    }

    @Test fun rejectedRequestDoesNotClaimAcceptedOrOwnARoute() {
        val client = Client().apply { requestFailure = SecurityException() }
        val source = session(client); source.start { }
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        source.requestTestRoute()
        client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        assertEquals("PERMISSION_DENIED", source.state.error?.code)
        assertEquals(NavigationStage.FAILED, source.state.stage)
        assertFalse(source.state.ownsTestRequest)
    }

    @Test fun destinationChangeAbortsCancellationBeforeLateRouteSnapshot() {
        val client = Client(); val source = session(client)
        source.start { }; client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        source.requestTestRoute(); client.emit(NavigationEvent.Snapshot(false, false, true, "SAFE_GUIDING"))
        source.cancelTestRoute(); client.emit(NavigationEvent.Destination(true, 500, 50))
        client.emit(NavigationEvent.DestinationChanged)
        client.emit(NavigationEvent.Snapshot(true, false, true, "ROUTE_GUIDING"))
        client.emit(NavigationEvent.Destination(false, null, null))
        assertEquals(0, client.cancels)
        assertFalse(source.state.ownsTestRequest)
    }

    @Test fun activeRefreshQueriesMetadataEvenWithoutRoutingCompletedCallback() {
        val client = Client(); val source = session(client)
        source.start { }
        client.emit(NavigationEvent.Snapshot(true, false, true, "ROUTE_GUIDING"))
        assertEquals(1, client.destinationQueries)
        assertEquals(1, client.turnQueries)
        client.emit(NavigationEvent.Destination(true, 50, 60))
        assertEquals(50, source.state.remainDistanceRaw)
        assertEquals(60, source.state.remainTimeRaw)
        assertFalse(source.state.ownsTestRequest)
    }
}
