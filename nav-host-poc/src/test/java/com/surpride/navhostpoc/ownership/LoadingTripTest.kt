package com.surpride.navhostpoc.ownership

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
class LoadingTripTest {
    @Test fun reroutingLoadingClearsOldTurnsAndKeepsExistingHostOwnership() {
        val manager=TestCarContext.createCarContext(RuntimeEnvironment.getApplication()).getCarService(TestNavigationManager::class.java)
        var grant:(()->Unit)?=null;var abandoned=0;var stopped=0
        val focus=object:FocusPort {
            override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {grant=granted;return true}
            override fun abandon(id:Long) {abandoned++}
        }
        val adapter=AndroidXNavigationOwnershipAdapter({manager},focus,{stopped++},HostIntegrationGate(true,true,true))
        assertFalse(adapter.updateLoadingTrip())
        adapter.requestActivation(ActivationChecks(true,true,false,true,true),ControlledTestApproval.fromExplicitUserAction());grant!!()
        assertTrue(adapter.updateLoadingTrip())
        val loading=manager.tripsSent.last()
        assertTrue(loading.isLoading);assertTrue(loading.destinations.isEmpty());assertTrue(loading.steps.isEmpty())
        assertEquals(OwnershipState.ACTIVE,adapter.state);assertEquals(1,manager.navigationStartedCount)
        assertEquals(0,manager.navigationEndedCount);assertEquals(0,abandoned);assertEquals(0,stopped)
        manager.navigationManagerCallback!!.onStopNavigation()
        assertFalse(adapter.updateLoadingTrip());assertEquals(1,abandoned);assertEquals(1,stopped)
    }
}
