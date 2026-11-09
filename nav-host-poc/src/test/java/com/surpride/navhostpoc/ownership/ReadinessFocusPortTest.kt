package com.surpride.navhostpoc.ownership

import org.junit.Assert.*
import org.junit.Test

class ReadinessFocusPortTest {
    private class Native:FocusPort {
        var grant:(()->Unit)?=null;val abandoned=mutableListOf<Long>();var requests=0
        override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {requests++;grant=granted;return true}
        override fun abandon(id:Long) {abandoned.add(id)}
    }
    private class Navigation:NavigationLifecyclePort {
        var starts=0
        override fun setStopHandler(handler:()->Unit) {}
        override fun clearStopHandler() {}
        override fun started() {starts++}
        override fun ended() {}
    }
    @Test fun delayedNativeGrantAfterGpsOrRouteExpiryIsAbandonedWithoutStartingHost() {
        var ready=true;val native=Native();val nav=Navigation()
        val owner=OwnershipController(ReadinessFocusPort(native) {ready},nav)
        assertTrue(owner.request(ActivationChecks(true,true,false,true,true)))
        ready=false;native.grant!!()
        assertEquals(0,nav.starts);assertEquals(OwnershipState.STOPPED,owner.state)
        assertEquals(listOf(1L),native.abandoned)
    }
    @Test fun unreadyRequestNeverReachesNativeAndFreshGrantStartsExactlyOnce() {
        var ready=false;val native=Native();val nav=Navigation()
        val owner=OwnershipController(ReadinessFocusPort(native) {ready},nav)
        assertFalse(owner.request(ActivationChecks(true,true,false,true,true)));assertEquals(0,native.requests)
        assertTrue(owner.reconnect());ready=true
        assertTrue(owner.request(ActivationChecks(true,true,false,true,true)));native.grant!!();native.grant!!()
        assertEquals(1,nav.starts)
    }
}
