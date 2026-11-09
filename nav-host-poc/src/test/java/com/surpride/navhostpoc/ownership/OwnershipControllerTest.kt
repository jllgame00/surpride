package com.surpride.navhostpoc.ownership

import org.junit.Assert.*
import org.junit.Test

class OwnershipControllerTest {
    private class Focus:FocusPort {
        var requests=0;var abandons=0;var denied=false
        var grant:(()->Unit)?=null;var lost:(()->Unit)?=null
        override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {requests++;grant=granted;this.lost=lost;return !denied}
        override fun abandon(id:Long) {abandons++}
    }
    private class Nav:NavigationLifecyclePort {
        var stops:(()->Unit)?=null;var starts=0;var ends=0;var clears=0;var failStart=false;var failEnd=false
        override fun setStopHandler(handler:()->Unit) {stops=handler}
        override fun clearStopHandler() {clears++;stops=null}
        override fun started() {starts++;if(failStart) error("test only")}
        override fun ended() {ends++;if(failEnd) error("test only")}
    }
    private val valid=ActivationChecks(userRequested=true,parked=true,oemRouteActive=false,policyReviewed=true,privacyReviewed=true)
    @Test fun nothingActivatesBeforeExplicitUserRequestAndAllGates() {
        listOf(valid.copy(userRequested=false),valid.copy(parked=null),valid.copy(parked=false),valid.copy(oemRouteActive=null),
            valid.copy(policyReviewed=false),valid.copy(privacyReviewed=false)).forEach {checks->
            val f=Focus();val n=Nav();val c=OwnershipController(f,n)
            assertEquals(OwnershipState.INACTIVE,c.state)
            assertFalse(c.request(checks));assertEquals(0,f.requests);assertEquals(0,n.starts)
        }
    }
    @Test fun oemActiveCannotBeTakenOverEvenWithSwitchConsent() {
        for(consent in listOf(false,true)) {
            val f=Focus();val c=OwnershipController(f,Nav())
            assertFalse(c.request(valid.copy(oemRouteActive=true,switchConsent=consent)))
            assertEquals(if(consent) "EXISTING_ROUTE_MUST_END" else "SWITCH_CONSENT_REQUIRED",c.reason)
            assertEquals(0,f.requests)
        }
    }
    @Test fun requestSuccessIsNotGrantAndDuplicateStartDoesNotAcquireTwice() {
        val f=Focus();val n=Nav();val c=OwnershipController(f,n)
        assertTrue(c.request(valid));assertEquals(OwnershipState.REQUESTED,c.state);assertEquals(0,n.starts)
        assertFalse(c.request(valid));f.grant!!();f.grant!!()
        assertEquals(OwnershipState.ACTIVE,c.state);assertEquals(1,n.starts);assertEquals(1,f.requests)
    }
    @Test fun hostStopEndsBeforeAbandonAndNoMoreTripPublication() {
        val f=Focus();val n=Nav();val c=OwnershipController(f,n)
        c.request(valid);f.grant!!();var updates=0
        assertTrue(c.publish {updates++});n.stops!!()
        assertEquals(OwnershipState.STOPPED,c.state);assertFalse(c.publish {updates++})
        assertEquals(1,updates);assertEquals(1,n.ends);assertEquals(1,f.abandons);assertEquals(1,n.clears)
    }
    @Test fun stopBeforeGrantRejectsLateGrantAndReleaseRequiresReconnect() {
        val f=Focus();val n=Nav();val c=OwnershipController(f,n)
        c.request(valid);val late=f.grant!!;val oldStop=n.stops!!;c.stop();late()
        assertEquals(0,n.starts);assertEquals(OwnershipState.STOPPED,c.state)
        c.release();assertFalse(c.request(valid));c.reconnect();assertEquals(OwnershipState.INACTIVE,c.state)
        c.request(valid);oldStop();late();assertEquals(OwnershipState.REQUESTED,c.state)
        f.grant!!();assertEquals(OwnershipState.ACTIVE,c.state)
    }
    @Test fun denialStartFailureFocusLossAndPublicationErrorCleanUp() {
        val denied=Focus().apply {denied=true};val c=OwnershipController(denied,Nav())
        assertFalse(c.request(valid));assertEquals(OwnershipState.ERROR,c.state);assertEquals(1,denied.abandons)
        val f=Focus();val n=Nav().apply {failStart=true};val failed=OwnershipController(f,n)
        failed.request(valid);f.grant!!();assertEquals(OwnershipState.ERROR,failed.state)
        assertEquals(1,n.ends);assertEquals(1,n.clears);assertEquals(1,f.abandons)
        val f2=Focus();val n2=Nav();val lost=OwnershipController(f2,n2)
        lost.request(valid);f2.grant!!();f2.lost!!();assertEquals(OwnershipState.STOPPED,lost.state);assertEquals(1,n2.ends)
        lost.request(valid);f2.grant!!();assertFalse(lost.publish {error("test only")});assertEquals(OwnershipState.ERROR,lost.state)
    }
    @Test fun cleanupFailureStillAttemptsOtherResourcesAndCannotRestartWithoutReconnect() {
        val f=Focus();val n=Nav().apply {failEnd=true};val c=OwnershipController(f,n)
        c.request(valid);f.grant!!();c.release()
        assertEquals(OwnershipState.ERROR,c.state);assertEquals(1,f.abandons);assertEquals(1,n.clears)
        assertFalse(c.request(valid));c.stop();assertEquals(1,f.abandons)
    }
    @Test fun environmentChangeWhilePendingOrActiveStopsAndLateGrantCannotStart() {
        for(granted in listOf(false,true)) {
            val f=Focus();val n=Nav();val c=OwnershipController(f,n)
            c.request(valid);val late=f.grant!!;if(granted) late()
            c.environmentChanged(parked=true,oemRouteActive=true);late()
            assertEquals(OwnershipState.STOPPED,c.state)
            assertEquals(if(granted) 1 else 0,n.starts)
            assertFalse(c.publish {})
        }
    }
    @Test fun failedLateGrantCleanupBlocksReconnectUntilOldLeaseIsReleased() {
        var late:(()->Unit)?=null;var fail=false
        val f=object:FocusPort {
            override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {late=granted;return true}
            override fun abandon(id:Long) {if(fail) error("test only")}
        }
        val c=OwnershipController(f,Nav());c.request(valid);c.stop();fail=true;late!!()
        assertEquals(OwnershipState.ERROR,c.state);assertFalse(c.request(valid));assertFalse(c.reconnect())
        fail=false;assertTrue(c.reconnect());assertEquals(OwnershipState.INACTIVE,c.state)
    }
    @Test fun stopCannotClearRequiredRecoveryAfterDenialStartOrUpdateFailure() {
        for(mode in 0..2) {
            val f=Focus().apply {denied=mode==0};val n=Nav().apply {failStart=mode==1};val c=OwnershipController(f,n)
            c.request(valid);if(mode!=0) f.grant!!();if(mode==2) c.publish {error("test only")}
            assertEquals(OwnershipState.ERROR,c.state);c.stop()
            assertEquals(OwnershipState.ERROR,c.state);assertFalse(c.request(valid))
            n.failStart=false;f.denied=false;assertTrue(c.reconnect());assertTrue(c.request(valid))
        }
    }
    @Test fun oldStartFailureCannotTearDownReentrantlyRequestedSession() {
        val f=Focus();lateinit var c:OwnershipController;var first=true
        val n=object:NavigationLifecyclePort {
            override fun setStopHandler(handler:()->Unit) {}
            override fun clearStopHandler() {}
            override fun ended() {}
            override fun started() {if(first) {first=false;c.stop();c.request(valid);error("old operation")}}
        }
        c=OwnershipController(f,n);c.request(valid);f.grant!!()
        assertEquals(OwnershipState.REQUESTED,c.state);f.grant!!();assertEquals(OwnershipState.ACTIVE,c.state)
    }
    @Test fun oldPublicationFailureCannotTearDownReentrantlyRequestedSession() {
        val f=Focus();val n=Nav();val c=OwnershipController(f,n)
        c.request(valid);f.grant!!()
        assertFalse(c.publish {c.stop();c.request(valid);error("old operation")})
        assertEquals(OwnershipState.REQUESTED,c.state);f.grant!!();assertEquals(OwnershipState.ACTIVE,c.state)
    }
    @Test fun reconnectIsRejectedWhileCleanupIsExecuting() {
        val f=Focus();lateinit var c:OwnershipController;var ends=0
        val n=object:NavigationLifecyclePort {
            override fun setStopHandler(handler:()->Unit) {}
            override fun clearStopHandler() {}
            override fun started() {}
            override fun ended() {ends++;if(ends==1) assertFalse(c.reconnect())}
        }
        c=OwnershipController(f,n);c.request(valid);f.grant!!();c.stop()
        assertEquals(1,ends);assertEquals(OwnershipState.STOPPED,c.state)
    }
    @Test fun grantAddedDuringStaleLeaseCleanupRemainsRecoveryDebt() {
        val grants=mutableMapOf<Long,()->Unit>();var latest=0L;var first=0L;var second=0L;var inject=false
        val f=object:FocusPort {
            override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {latest=id;grants[id]=granted;return true}
            override fun abandon(id:Long) {
                if(inject && id==second) grants[first]!!()
                else if(inject && id==first) {inject=false;grants[second]!!()}
            }
        }
        val c=OwnershipController(f,Nav());c.request(valid);first=latest;c.stop()
        c.request(valid);second=latest;inject=true;c.stop()
        assertEquals(OwnershipState.ERROR,c.state);assertFalse(c.request(valid))
        assertTrue(c.reconnect());assertTrue(c.request(valid))
    }
}
