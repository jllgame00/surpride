package com.surpride.app.vehicle

import org.junit.Assert.*
import org.junit.Test

class VehicleSessionTest {
    private class Client : VehicleReadClient {
        var released = 0
        var cancelled = 0
        var reads = 0
        var capability = CapabilityResult(true, "READ")
        var result = ReadResult(42.0)
        var subscription: ((ReadResult) -> Unit)? = null
        var initializeError: Exception? = null
        var cancelError = false
        var deferredChecks = false
        var deferredReads = false
        var immediateSubscriptionFailure = false
        val pendingChecks = mutableMapOf<VehicleMetric, (CapabilityResult) -> Unit>()
        val updates = mutableMapOf<VehicleMetric, (ReadResult) -> Unit>()
        override fun initialize() { initializeError?.let { throw it } }
        override fun check(metric: VehicleMetric, callback: (CapabilityResult) -> Unit) {
            if (deferredChecks) pendingChecks[metric] = callback else callback(capability)
        }
        override fun read(metric: VehicleMetric, callback: (ReadResult) -> Unit) { reads++; if (!deferredReads) callback(result) }
        override fun subscribe(metric: VehicleMetric, callback: (ReadResult) -> Unit): () -> Unit {
            subscription = callback
            updates[metric] = callback
            if (immediateSubscriptionFailure) callback(ReadResult(null, DataFailure(ReadingStatus.PERMISSION_DENIED, "permission")))
            return { cancelled++; if (cancelError) throw IllegalStateException("unregister") }
        }
        override fun release() { released++ }
    }

    private fun source(client: Client) = VehicleSession({ client }, { 1234L }, { it() })

    @Test fun readableCapabilityReadsAndKeepsRealOrigin() {
        val client = Client()
        val source = source(client)
        source.start { }
        assertEquals(DataOrigin.PLEOS_SERVICE, source.state.origin)
        assertEquals(ConnectionStatus.RESPONDING, source.state.connection)
        assertEquals(42.0, source.state.readings.getValue(VehicleMetric.BATTERY).value!!, 0.0)
        assertEquals(3, source.state.subscriptions)
        source.refresh()
        assertEquals(3, source.state.subscriptions)
        assertEquals(6, client.reads)
    }

    @Test fun unsupportedPermissionDeniedAndNullAreDifferent() {
        val client = Client()
        val source = source(client)
        client.capability = CapabilityResult(false, "NONE")
        source.start { }
        assertEquals(ReadingStatus.UNSUPPORTED, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        assertEquals(0, client.reads)
        client.capability = CapabilityResult(false, failure = DataFailure(ReadingStatus.PERMISSION_DENIED, "permission"))
        source.refresh()
        assertEquals(ReadingStatus.PERMISSION_DENIED, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        client.capability = CapabilityResult(true, "READ")
        client.result = ReadResult(null)
        source.refresh()
        assertEquals(ReadingStatus.NO_DATA, source.state.readings.getValue(VehicleMetric.BATTERY).status)
    }

    @Test fun stopCancelsEverythingAndRejectsLateCallbacks() {
        val client = Client()
        val source = source(client)
        source.start { }
        val lateCallback = client.subscription!!
        source.stop()
        val stopped = source.state
        lateCallback(ReadResult(99.0))
        source.stop()
        assertEquals(stopped, source.state)
        assertEquals(3, client.cancelled)
        assertEquals(1, client.released)
        assertEquals(0, source.state.subscriptions)
        assertEquals(ConnectionStatus.STOPPED, source.state.connection)
    }

    @Test fun initializationFailureDoesNotClaimConnectionOrRead() {
        val client = Client().apply { initializeError = IllegalStateException("no service") }
        val source = source(client)
        source.start { }
        assertEquals(ConnectionStatus.FAILED, source.state.connection)
        assertEquals(0, client.reads)
        assertEquals(ReadingStatus.ERROR, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        source.stop()
        assertEquals(1, client.released)
    }

    @Test fun noCallbackTimesOutAndDoesNotInventData() {
        val silent = object : VehicleReadClient {
            override fun initialize() = Unit
            override fun check(metric: VehicleMetric, callback: (CapabilityResult) -> Unit) = Unit
            override fun read(metric: VehicleMetric, callback: (ReadResult) -> Unit) = Unit
            override fun subscribe(metric: VehicleMetric, callback: (ReadResult) -> Unit): () -> Unit = { }
            override fun release() = Unit
        }
        val source = VehicleSession({ silent }, { 1234L }, { it() })
        source.start { }
        assertEquals(ConnectionStatus.INITIALIZED, source.state.connection)
        source.expirePending()
        assertEquals(ReadingStatus.TIMEOUT, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        assertNull(source.state.readings.getValue(VehicleMetric.BATTERY).value)
    }

    @Test fun fakeDataIsExplicitAndNeverDeliveredAfterStop() {
        val fake = FakeVehicleDataSource()
        var deliveries = 0
        fake.start { deliveries++ }
        fake.emit(VehicleState(origin = DataOrigin.FAKE))
        assertEquals(DataOrigin.FAKE, fake.state.origin)
        fake.stop()
        val before = deliveries
        fake.emit(VehicleState(origin = DataOrigin.FAKE))
        assertEquals(before, deliveries)
    }

    @Test fun documentedErrorsAreClassifiedAndUnknownMessagesAreNotExposed() {
        assertEquals(ReadingStatus.UNSUPPORTED, vehicleFailure(Exception("ERROR_NOT_SUPPORTED_PROPERTY")).status)
        assertEquals(ReadingStatus.PERMISSION_DENIED, vehicleFailure(Exception("ERROR_SECURITY_PERMISSION")).status)
        assertEquals(ReadingStatus.PERMISSION_DENIED, vehicleFailure(SecurityException()).status)
        assertEquals(ReadingStatus.NO_DATA, vehicleFailure(Exception("ERROR_EMPTY_RESULT")).status)
        val unknown = vehicleFailure(Exception("private path and unsupported-like text"))
        assertEquals(ReadingStatus.ERROR, unknown.status)
        assertFalse(unknown.detail.contains("private path"))
    }

    @Test fun failedUnregisterDoesNotPreventOtherCleanupOrRelease() {
        val client = Client().apply { cancelError = true }
        val source = source(client)
        source.start { }
        source.stop()
        assertEquals(3, client.cancelled)
        assertEquals(1, client.released)
        assertEquals(3, source.state.cleanupErrors)
    }

    @Test fun oldSessionUpdateCannotChangeRestartedSession() {
        val client = Client()
        val source = source(client)
        source.start { }
        val old = client.subscription!!
        source.stop()
        source.start { }
        val current = source.state
        old(ReadResult(91.0))
        assertEquals(current, source.state)
        source.stop()
    }

    @Test fun unqueriedGearDoesNotPretendThatVehicleIsParked() {
        val source = source(Client())
        source.start { }
        assertEquals(ReadingStatus.NOT_QUERIED, source.state.readings.getValue(VehicleMetric.GEAR).status)
        assertNull(source.state.readings.getValue(VehicleMetric.MOTION).value)
    }

    @Test fun liveUpdateDuringRefreshCannotHidePermissionDenial() {
        val client = Client()
        val source = source(client)
        source.start { }
        client.deferredChecks = true
        source.refresh()
        client.updates.getValue(VehicleMetric.BATTERY)(ReadResult(75.0))
        client.pendingChecks.getValue(VehicleMetric.BATTERY)(CapabilityResult(false,
            failure = DataFailure(ReadingStatus.PERMISSION_DENIED, "permission")))
        assertEquals(ReadingStatus.PERMISSION_DENIED, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        assertNull(source.state.readings.getValue(VehicleMetric.BATTERY).value)
        assertEquals(2, source.state.subscriptions)
    }

    @Test fun callbackFromRemovedSubscriptionCannotOverwriteDenial() {
        val client = Client()
        val source = source(client)
        source.start { }
        val queued = client.updates.getValue(VehicleMetric.BATTERY)
        client.capability = CapabilityResult(false, failure = DataFailure(ReadingStatus.PERMISSION_DENIED, "permission"))
        source.refresh()
        queued(ReadResult(91.0))
        assertEquals(ReadingStatus.PERMISSION_DENIED, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        assertEquals(0, source.state.subscriptions)
    }

    @Test fun lateCapabilityAfterTimeoutCannotCreateAnUnboundedPendingRead() {
        val client = Client().apply { deferredChecks = true; deferredReads = true }
        val source = source(client)
        source.start { }
        source.expirePending()
        client.pendingChecks.getValue(VehicleMetric.BATTERY)(CapabilityResult(true, "READ"))
        assertEquals(ReadingStatus.TIMEOUT, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        assertEquals(0, client.reads)
    }

    @Test fun synchronousSubscriptionFailureCancelsReturnedRegistration() {
        val client = Client().apply { immediateSubscriptionFailure = true }
        val source = source(client)
        source.start { }
        assertEquals(0, source.state.subscriptions)
        assertEquals(3, client.cancelled)
        assertEquals(ReadingStatus.PERMISSION_DENIED, source.state.readings.getValue(VehicleMetric.BATTERY).status)
        source.stop()
    }
}
