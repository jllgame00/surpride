package com.surpride.navhostpoc.tracking

import com.surpride.navhostpoc.guidance.*
import org.junit.Assert.*
import org.junit.Test

class RouteMatcherTest {
    private val now=100_000L
    private fun p(x:Double,y:Double=0.0)=RouteCoordinate(127.0+x/88_804.0,37.0+y/111_195.0)
    private fun route(points:List<RouteCoordinate> = listOf(p(0.0),p(500.0),p(1000.0))):GuidanceRoute {
        return GuidanceRoute(RouteDataSource.KAKAO_DIRECTIONS,
            listOf(RouteSegment("검증도로",1000,100,4,points)),listOf(Maneuver(100,0,0),Maneuver(2,500,0),Maneuver(101,1000,0)),1000,100,points.last())
    }
    private fun fix(x:Double,y:Double=0.0,t:Long=now,accuracy:Double=5.0,bearing:Double?=90.0,speed:Double?=10.0)=
        PositionFix(p(x,y),accuracy,t,1_800_000_000_000L,speed,bearing,10.0,PositionSource.FAKE,false)
    @Test fun projectsNearestSegmentAndComputesNextTurnDistance() {
        val matcher=RouteMatcher(route())
        val state=matcher.accept(fix(100.0,3.0),now)
        assertEquals(MatchStatus.ON_ROUTE,state.status)
        assertEquals(100.0,state.progress!!.traveledMeters,1.0)
        assertEquals(400.0,state.progress!!.remainingToManeuverMeters,1.0)
        assertTrue(state.deviationMeters!! in 2.5..3.5)
    }
    @Test fun advanceChangesNextManeuverWhileJitterDoesNotMoveBackward() {
        val matcher=RouteMatcher(route())
        matcher.accept(fix(490.0),now)
        val moved=matcher.accept(fix(520.0,t=now+1000),now+1000)
        assertEquals(101,moved.nextManeuver?.type)
        val stable=matcher.accept(fix(517.0,t=now+2000),now+2000)
        assertTrue(stable.progress!!.traveledMeters>=moved.progress!!.traveledMeters)
    }
    @Test fun staleInaccurateFutureAndOutOfOrderSamplesHaveNoGuidance() {
        listOf(fix(0.0,t=now-6000),fix(0.0,accuracy=200.0),fix(0.0,t=now+2000),fix(0.0,accuracy=Double.NaN)).forEach {
            val m=RouteMatcher(route());assertEquals(MatchStatus.UNKNOWN,m.accept(it,now).status);assertNull(m.presentation())
        }
        val m=RouteMatcher(route());m.accept(fix(100.0),now)
        assertEquals(MatchStatus.UNKNOWN,m.accept(fix(100.0),now+1).status)
    }
    @Test fun offRouteRequiresRepeatedFixesAndRejoinRequiresConfirmation() {
        val m=RouteMatcher(route());m.accept(fix(100.0),now)
        repeat(3) {i->m.accept(fix(110.0,150.0,now+(i+1)*1000),now+(i+1)*1000)}
        assertEquals(MatchStatus.OFF_ROUTE,m.state().status);assertNull(m.presentation())
        assertEquals(MatchStatus.REJOINING,m.accept(fix(130.0,t=now+4000),now+4000).status)
        assertEquals(MatchStatus.ON_ROUTE,m.accept(fix(140.0,t=now+5000),now+5000).status)
    }
    @Test fun jumpsReverseAndLossDoNotBecomeForwardGuidance() {
        val m=RouteMatcher(route());m.accept(fix(100.0),now)
        assertEquals(MatchStatus.JUMP_REJECTED,m.accept(fix(900.0,t=now+1000),now+1000).status)
        assertEquals(MatchStatus.REVERSED,m.accept(fix(80.0,t=now+2000,bearing=270.0),now+2000).status)
        assertEquals(MatchStatus.UNKNOWN,m.expire(now+10_000).status);assertNull(m.presentation())
    }
    @Test fun crossingWithoutHistoryOrReliableHeadingIsAmbiguous() {
        val points=listOf(p(-100.0,-100.0),p(100.0,100.0),p(-100.0,100.0),p(100.0,-100.0))
        val m=RouteMatcher(route(points))
        assertEquals(MatchStatus.AMBIGUOUS,m.accept(fix(0.0,bearing=null,speed=null),now).status)
        assertNull(m.presentation())
    }
    @Test fun crossingContinuityAvoidsJumpingToLaterTraversal() {
        val points=listOf(p(-100.0,-100.0),p(100.0,100.0),p(-100.0,100.0),p(100.0,-100.0))
        val m=RouteMatcher(route(points))
        m.accept(fix(-20.0,-20.0,bearing=45.0),now)
        val result=m.accept(fix(0.0,t=now+3000,bearing=45.0),now+3000)
        assertEquals(MatchStatus.ON_ROUTE,result.status)
        assertTrue(result.geometryAlongMeters!!<200)
    }
    @Test fun repeatedCoordinatesAndNoBearingWorkWithoutInventedSpeed() {
        val m=RouteMatcher(route(listOf(p(0.0),p(0.0),p(500.0),p(1000.0))))
        assertEquals(MatchStatus.ON_ROUTE,m.accept(fix(100.0,bearing=null,speed=null),now).status)
    }
    @Test fun publicProjectionHidesIdentityAndEndpointAndMarksLocationSource() {
        val m=RouteMatcher(route());m.accept(fix(900.0),now)
        val view=m.presentation()!!
        assertTrue(view.localPath.isEmpty());assertFalse(view.toString().contains("127."));assertTrue(view.simulatedPosition)
        val device=fix(900.0).copy(source=PositionSource.DEVICE_API)
        val live=RouteMatcher(route());live.accept(device,now)
        assertFalse(live.presentation()!!.simulatedPosition)
        assertFalse(device.toString().contains("127."))
    }
    @Test fun geometryAndRoadDistanceHaveExplicitSeparateScale() {
        val m=RouteMatcher(route(listOf(p(0.0),p(2000.0))))
        assertEquals(250.0,m.accept(fix(500.0),now).progress!!.traveledMeters,1.0)
    }
    @Test fun thresholdsTrackAccuracyWithinExplicitExperimentalBounds() {
        val config=MatcherConfig()
        assertTrue(config.corridorMeters(5.0)<config.corridorMeters(25.0))
        assertTrue(config.corridorMeters(1000.0)<=config.maxCorridorMeters)
    }
    @Test fun rejoinAtCrossingRequiresUnambiguousAcquisition() {
        val points=listOf(p(-100.0,-100.0),p(100.0,100.0),p(-100.0,100.0),p(100.0,-100.0))
        val m=RouteMatcher(route(points));m.accept(fix(-20.0,-20.0,bearing=45.0),now)
        repeat(3) {i->m.accept(fix(0.0,400.0,now+(i+1)*1000,bearing=null),now+(i+1)*1000)}
        m.accept(fix(0.0,t=now+4000,bearing=null,speed=null),now+4000)
        assertEquals(MatchStatus.AMBIGUOUS,m.accept(fix(0.0,t=now+5000,bearing=null,speed=null),now+5000).status)
        assertNull(m.presentation())
    }
    @Test fun confirmedRejoinStillRejectsImplausibleJump() {
        val m=RouteMatcher(route());m.accept(fix(100.0),now)
        repeat(3) {i->m.accept(fix(110.0,150.0,now+(i+1)*1000),now+(i+1)*1000)}
        m.accept(fix(900.0,t=now+4000),now+4000)
        assertEquals(MatchStatus.JUMP_REJECTED,m.accept(fix(900.0,t=now+5000),now+5000).status)
        assertNull(m.presentation())
    }
    @Test fun reacquisitionAfterLongLossDoesNotAssumeOldCrossingBranch() {
        val points=listOf(p(-100.0,-100.0),p(100.0,100.0),p(-100.0,100.0),p(100.0,-100.0))
        val m=RouteMatcher(route(points));m.accept(fix(-20.0,-20.0,bearing=45.0),now)
        m.expire(now+60_000)
        assertEquals(MatchStatus.AMBIGUOUS,m.accept(fix(0.0,t=now+61_000,bearing=null,speed=null),now+61_000).status)
        assertNull(m.presentation())
    }
}
