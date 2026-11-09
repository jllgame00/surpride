package com.surpride.navhostpoc.ownership

import androidx.car.app.navigation.NavigationManagerCallback
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.navigation.TestNavigationManager
import androidx.car.app.navigation.model.Maneuver
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Official AndroidX FakeHost, not a Pleos host or real CarAppFocusManager. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class TestNavigationManagerContractTest {
    private val identity=DestinationIdentity("테스트 전용 목적지","테스트 전용 주소")
    private val turn=NextTurnMetadata("우회전","검증도로",Maneuver.TYPE_TURN_NORMAL_RIGHT,300.0,60,1_800_000_000_000)
    private fun manager()=TestCarContext.createCarContext(RuntimeEnvironment.getApplication()).getCarService(TestNavigationManager::class.java)
    @Test fun officialManagerRequiresCallbackAndRejectsUpdateBeforeStart() {
        val m=manager()
        assertThrows(IllegalStateException::class.java) {m.navigationStarted()}
        assertThrows(IllegalStateException::class.java) {m.updateTrip(PrivacyTripFactory.hidden(turn,identity))}
        // TestNavigationManager records ATTEMPTS before delegating to super. This is not delivery proof.
        assertEquals(1,m.tripsSent.size)
    }
    @Test fun controllerPublishesHiddenThenExplicitRevealAndStopsThroughOfficialCallback() {
        val m=manager()
        var grant:(()->Unit)?=null
        val f=object:FocusPort {
            override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {grant=granted;return true}
            override fun abandon(id:Long) {}
        }
        val n=object:NavigationLifecyclePort {
            override fun setStopHandler(handler:()->Unit) {m.setNavigationManagerCallback({it.run()},object:NavigationManagerCallback {override fun onStopNavigation()=handler()})}
            override fun clearStopHandler()=m.clearNavigationManagerCallback()
            override fun started()=m.navigationStarted()
            override fun ended()=m.navigationEnded()
        }
        val c=OwnershipController(f,n)
        assertFalse(c.publish {m.updateTrip(PrivacyTripFactory.hidden(turn,identity))})
        c.request(ActivationChecks(true,true,false,true,true));assertEquals(0,m.navigationStartedCount)
        grant!!();assertEquals(OwnershipState.ACTIVE,c.state)
        assertTrue(c.publish {m.updateTrip(PrivacyTripFactory.hidden(turn,identity))})
        assertTrue(m.tripsSent.last().destinations.isEmpty())
        assertTrue(c.publish {m.updateTrip(PrivacyTripFactory.revealed(turn,identity,true,900.0,180))})
        assertEquals(identity.name,m.tripsSent.last().destinations.single().name!!.toString())
        // Public testing getter + callback injection. Internal manager Binder dispatch is not exercised.
        m.navigationManagerCallback!!.onStopNavigation()
        assertEquals(OwnershipState.STOPPED,c.state);assertEquals(1,m.navigationStartedCount);assertEquals(1,m.navigationEndedCount)
        assertNull(m.navigationManagerCallback)
        assertFalse(c.publish {m.updateTrip(PrivacyTripFactory.hidden(turn,identity))});assertEquals(2,m.tripsSent.size)
        assertThrows(IllegalStateException::class.java) {m.updateTrip(PrivacyTripFactory.hidden(turn,identity))}
    }
    @Test fun callbacksCannotBeClearedDuringActiveNavigationAndEndIsIdempotent() {
        val m=manager();m.setNavigationManagerCallback({it.run()},object:NavigationManagerCallback {})
        m.navigationStarted();m.navigationStarted()
        assertThrows(IllegalStateException::class.java) {m.clearNavigationManagerCallback()}
        m.navigationEnded();m.navigationEnded();m.clearNavigationManagerCallback()
        assertThrows(IllegalStateException::class.java) {m.updateTrip(PrivacyTripFactory.hidden(turn,identity))}
    }
}
