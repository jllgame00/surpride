package com.surpride.navhostpoc.guidance

import com.surpride.navhostpoc.tracking.*
import org.junit.Assert.*
import org.junit.Test

class WaypointProgressTest {
    private fun route(guides:List<Maneuver> = listOf(Maneuver(100,0,0),Maneuver(1000,100,0),
        Maneuver(2,150,0),Maneuver(1000,200,0),Maneuver(101,300,0))) = GuidanceRoute(
        RouteDataSource.MOCK,RouteGeometry(listOf(RouteCoordinate(127.0,37.0),RouteCoordinate(127.003,37.0))),
        emptyList(),guides,300,60,RouteCoordinate(127.003,37.0))
    private fun progress(at:Double)=RouteProgress(at,300-at,0.0,0,0)

    @Test fun onlyOfficialWaypointOffsetsAdvancePassedAndRemaining() {
        val tracker=WaypointProgress(route(),2)
        assertEquals(WaypointState(0,2),tracker.state())
        tracker.observe(progress(99.0),true)
        assertEquals(WaypointState(0,2),tracker.state())
        tracker.observe(progress(100.0),true)
        assertEquals(WaypointState(1,1),tracker.state())
        tracker.observe(progress(150.0),true)
        assertEquals(WaypointState(1,1),tracker.state())
        tracker.observe(progress(200.0),true)
        assertEquals(WaypointState(2,0),tracker.state())
        tracker.observe(progress(100.0),true)
        assertEquals(WaypointState(2,0),tracker.state())
        assertFalse(tracker.toString().contains("127.0"))
    }

    @Test fun uncertainOrInvalidProgressCannotDiscardWaypoints() {
        val tracker=WaypointProgress(route(),2)
        tracker.observe(progress(250.0),false)
        tracker.observe(progress(Double.NaN),true)
        tracker.observe(progress(Double.POSITIVE_INFINITY),true)
        tracker.observe(progress(-1.0),true)
        tracker.observe(progress(301.0),true)
        tracker.observe(null,true)
        assertEquals(WaypointState(0,2),tracker.state())
        assertFalse(tracker.canRefreshDirect())
    }

    @Test fun waypointPassCannotQualifyForFinalArrivalAndExistingArrivalGateStillApplies() {
        val tracker=WaypointProgress(route(),2)
        tracker.observe(progress(100.0),true)
        assertFalse(tracker.canConfirmFinalArrival(progress(100.0)))
        tracker.observe(progress(200.0),true)
        assertTrue(tracker.canRefreshDirect())
        assertFalse(tracker.canConfirmFinalArrival(progress(200.0)))
        tracker.observe(progress(280.0),true)
        assertTrue(tracker.canConfirmFinalArrival(progress(280.0)))
        assertFalse(tracker.canConfirmFinalArrival(null))
        val gate=ArrivalGate()
        repeat(3) {i->
            val at=i*1000L
            val fix=PositionFix(RouteCoordinate(127.003,37.0),5.0,at,0,0.0,null,null,PositionSource.FAKE,false)
            gate.observe(fix,MatchState(MatchStatus.ON_ROUTE,"MATCHED",PositionSource.FAKE,progress(280.0)),5.0,true,at)
        }
        assertTrue(gate.candidate)
        assertTrue(gate.confirm(true,true,2000))
    }

    @Test fun mismatchedWaypointCountsAndInternalDestinationCodesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {WaypointProgress(route(),1)}
        assertThrows(IllegalArgumentException::class.java) {WaypointProgress(route(),3)}
        assertThrows(IllegalArgumentException::class.java) {WaypointProgress(route(listOf(
            Maneuver(100,0,0),Maneuver(101,100,0),Maneuver(101,300,0))),0)}
        assertThrows(IllegalArgumentException::class.java) {WaypointProgress(route(listOf(
            Maneuver(100,0,0),Maneuver(1000,300,0),Maneuver(101,300,0))),1)}
    }

    @Test fun legacyTwoPointRouteKeepsDirectRefreshAndFinalArrivalEligibility() {
        val tracker=WaypointProgress(route(listOf(Maneuver(100,0,0),Maneuver(101,300,0))),0)
        assertTrue(tracker.canRefreshDirect())
        assertFalse(tracker.canConfirmFinalArrival(progress(100.0)))
        assertTrue(tracker.canConfirmFinalArrival(progress(280.0)))
    }
}
