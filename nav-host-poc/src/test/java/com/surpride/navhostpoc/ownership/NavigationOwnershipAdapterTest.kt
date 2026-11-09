package com.surpride.navhostpoc.ownership

import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.navigation.TestNavigationManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class NavigationOwnershipAdapterTest {
    private class Focus:FocusPort {
        var requests=0;var abandons=0;var denied=false
        var grant:(()->Unit)?=null;var lost:(()->Unit)?=null
        override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {requests++;grant=granted;this.lost=lost;return !denied}
        override fun abandon(id:Long) {abandons++}
    }
    private class Harness(val gate:HostIntegrationGate=HostIntegrationGate(true,true,true)) {
        val managers=mutableListOf<TestNavigationManager>();val focus=Focus();var lookups=0;var stops=0;var failStop=false;var failLookup=false
        val adapter:NavigationOwnershipAdapter=AndroidXNavigationOwnershipAdapter(
            managerProvider={lookups++;if(failLookup) error("test lookup failure");TestCarContext.createCarContext(RuntimeEnvironment.getApplication()).getCarService(TestNavigationManager::class.java).also(managers::add)},
            focus=focus,stopGuidance={stops++;if(failStop) error("test shutdown failure")},gate=gate)
        val manager get()=managers.last()
        fun request(approval:ControlledTestApproval?=ControlledTestApproval.fromExplicitUserAction())=adapter.requestActivation(checks,approval)
        fun activate() {assertTrue(request());focus.grant!!();assertEquals(OwnershipState.ACTIVE,adapter.state)}
    }
    companion object {
        private val checks=ActivationChecks(true,true,false,true,true)
        private val identity=DestinationIdentity("테스트 목적지","테스트 주소")
        private val turn=NextTurnMetadata("우회전","검증도로",Maneuver.TYPE_TURN_NORMAL_RIGHT,300.0,60,1_800_000_000_000)
    }
    @Test fun defaultAndUnconfirmedGatesCannotLookupManagerOrRequestFocusEvenWithConsent() {
        for(gate in listOf(HostIntegrationGate.UNVERIFIED,HostIntegrationGate(false,true,true),HostIntegrationGate(true,false,true),HostIntegrationGate(true,true,false))) {
            val h=Harness(gate);assertFalse(h.request());assertEquals(0,h.lookups);assertEquals(0,h.focus.requests)
            h.adapter.stop();h.adapter.release();h.adapter.reconnect();assertEquals(0,h.lookups)
        }
    }
    @Test fun explicitOneShotApprovalAndValidEnvironmentAreRequired() {
        val h=Harness();assertFalse(h.request(null));assertEquals(0,h.lookups)
        for(bad in listOf(checks.copy(userRequested=false),checks.copy(parked=null),checks.copy(oemRouteActive=true),checks.copy(oemRouteActive=null)))
            assertFalse(h.adapter.requestActivation(bad,ControlledTestApproval.fromExplicitUserAction()))
        assertEquals(0,h.lookups)
        val approval=ControlledTestApproval.fromExplicitUserAction();assertTrue(h.request(approval));h.adapter.stop()
        assertFalse(h.request(approval));assertEquals(1,h.focus.requests)
    }
    @Test fun noActivationUntilGrantAndDuplicateRequestsDoNotRegisterAgain() {
        val h=Harness();assertTrue(h.request());assertEquals(1,h.lookups);assertEquals(0,h.manager.navigationStartedCount)
        assertFalse(h.request());h.focus.grant!!();h.focus.grant!!()
        assertEquals(1,h.manager.navigationStartedCount);assertEquals(1,h.lookups)
    }
    @Test fun hiddenTripAndExplicitRevealUseOfficialManagerAndHostStopStopsAllGuidance() {
        val h=Harness();h.activate()
        assertTrue(h.adapter.updateHiddenTrip(turn,identity));assertTrue(h.manager.tripsSent.last().destinations.isEmpty())
        assertFalse(h.adapter.revealDestination(turn,identity,false,900.0,180));assertEquals(OwnershipState.ACTIVE,h.adapter.state)
        assertEquals(1,h.manager.tripsSent.size)
        assertTrue(h.adapter.revealDestination(turn,identity,true,900.0,180));assertEquals(identity.name,h.manager.tripsSent.last().destinations.single().name!!.toString())
        h.manager.navigationManagerCallback!!.onStopNavigation()
        assertEquals(OwnershipState.STOPPED,h.adapter.state);assertEquals(1,h.stops);assertEquals(1,h.manager.navigationEndedCount)
        assertNull(h.manager.navigationManagerCallback);assertFalse(h.adapter.updateHiddenTrip(turn,identity))
    }
    @Test fun backgroundFocusLossOemChangeAndReleaseStopGuidanceAndClearCallback() {
        for(event in 0..3) {
            val h=Harness();h.activate()
            when(event) {0->h.adapter.onAppStopped();1->h.focus.lost!!();2->h.adapter.environmentChanged(true,true);else->h.adapter.release()}
            assertEquals(OwnershipState.STOPPED,h.adapter.state);assertEquals(1,h.stops)
            assertEquals(1,h.manager.navigationEndedCount);assertNull(h.manager.navigationManagerCallback)
        }
    }
    @Test fun pendingStopAndDenialAlsoStopGuidanceWithoutCallingNavigationStarted() {
        val h=Harness();h.request();h.adapter.stop();assertEquals(1,h.stops);assertEquals(0,h.manager.navigationStartedCount)
        val denied=Harness();denied.focus.denied=true;assertFalse(denied.request())
        assertEquals(OwnershipState.ERROR,denied.adapter.state);assertEquals(1,denied.stops);assertEquals(0,denied.manager.navigationStartedCount)
    }
    @Test fun reconnectGetsFreshManagerAndOldStopCallbackCannotStopNewSession() {
        val h=Harness();h.activate();val old=h.manager.navigationManagerCallback!!;h.adapter.release()
        assertTrue(h.adapter.reconnect());assertEquals(1,h.lookups);assertFalse(h.request(null))
        h.activate();assertEquals(2,h.lookups);old.onStopNavigation()
        assertEquals(OwnershipState.ACTIVE,h.adapter.state);assertEquals(1,h.stops)
    }
    @Test fun stopHookFailureStillEndsNavigationAndReleasesFocusAndBlocksRestart() {
        val h=Harness();h.activate();h.failStop=true;h.adapter.stop()
        assertEquals(OwnershipState.ERROR,h.adapter.state);assertTrue(h.manager.navigationEndedCount>=1)
        assertEquals(1,h.focus.abandons);assertNull(h.manager.navigationManagerCallback)
        assertFalse(h.request());assertFalse(h.adapter.reconnect())
        h.failStop=false;assertTrue(h.adapter.reconnect());h.activate()
    }
    @Test fun identityContaminationFailsClosedAndCleansOwnedSession() {
        val h=Harness();h.activate()
        assertFalse(h.adapter.updateHiddenTrip(turn.copy(road=identity.name),identity))
        assertEquals(OwnershipState.ERROR,h.adapter.state);assertEquals(1,h.stops);assertEquals(0,h.manager.tripsSent.size)
    }
    @Test fun failedLookupCleansGuidanceAndRequiresExplicitReconnect() {
        val h=Harness();h.failLookup=true;assertFalse(h.request());assertEquals(OwnershipState.ERROR,h.adapter.state)
        assertEquals(1,h.stops);assertEquals(0,h.focus.requests);assertFalse(h.request())
        h.failLookup=false;assertTrue(h.adapter.reconnect());h.activate();assertEquals(2,h.lookups)
    }
    @Test fun offMainThreadRequestIsRejectedBeforeAnySdkAccess() {
        val h=Harness();val caught=java.util.concurrent.atomic.AtomicReference<Throwable>()
        val worker=Thread {try {h.request()} catch(t:Throwable) {caught.set(t)}}
        worker.start();worker.join(3000)
        assertFalse(worker.isAlive);assertTrue(caught.get() is IllegalStateException);assertEquals(0,h.lookups)
    }
    @Test fun stopDuringLookupDoesNotInstallAnOrphanedCallback() {
        val m=TestCarContext.createCarContext(RuntimeEnvironment.getApplication()).getCarService(TestNavigationManager::class.java)
        val f=Focus();lateinit var adapter:NavigationOwnershipAdapter
        adapter=AndroidXNavigationOwnershipAdapter(managerProvider={adapter.stop();m},focus=f,stopGuidance={},gate=HostIntegrationGate(true,true,true))
        assertFalse(adapter.requestActivation(checks,ControlledTestApproval.fromExplicitUserAction()))
        assertEquals(OwnershipState.STOPPED,adapter.state);assertNull(m.navigationManagerCallback);assertEquals(0,f.requests)
    }
}
