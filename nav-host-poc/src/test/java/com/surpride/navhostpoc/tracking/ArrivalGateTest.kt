package com.surpride.navhostpoc.tracking

import com.surpride.navhostpoc.guidance.*
import org.junit.Assert.*
import org.junit.Test

class ArrivalGateTest {
    private fun fix(at:Long,speed:Double?=0.0)=PositionFix(RouteCoordinate(127.0,37.0),5.0,at,100_000+at,speed,null,null,PositionSource.FAKE,false)
    private fun match(remaining:Double=12.0)=MatchState(MatchStatus.ON_ROUTE,"MATCHED",PositionSource.FAKE,
        RouteProgress(988.0,remaining,remaining,0,10),accuracyMeters=5.0)
    @Test fun singleEndpointFixDoesNotArriveAndConfirmationIsRequired() {
        val gate=ArrivalGate()
        gate.observe(fix(1000),match(),8.0,true,1000)
        assertFalse(gate.candidate);assertFalse(gate.confirm(true,true,1000))
        gate.observe(fix(2000),match(),8.0,true,2000)
        gate.observe(fix(3000),match(),8.0,true,3000)
        assertTrue(gate.candidate);assertFalse(gate.confirm(false,true,3000))
        assertTrue(gate.confirm(true,true,3000));assertTrue(gate.arrived)
    }
    @Test fun movingUnknownParkedAndDistantEndpointNeverQualify() {
        listOf(Triple(2.0,true,8.0),Triple(0.0,null,8.0),Triple(0.0,true,100.0)).forEach {(speed,parked,end)->
            val gate=ArrivalGate();repeat(5) {gate.observe(fix(it*1000L,speed),match(),end,parked,it*1000L)}
            assertFalse(gate.candidate)
        }
    }
    @Test fun lossStaleOrOffRouteInvalidatesCandidate() {
        val gate=ArrivalGate();repeat(3) {gate.observe(fix(it*1000L),match(),8.0,true,it*1000L)}
        assertTrue(gate.candidate);assertFalse(gate.confirm(true,true,9000))
        gate.observe(fix(10000),match().copy(status=MatchStatus.OFF_ROUTE,progress=null),8.0,true,10000)
        assertFalse(gate.candidate)
    }
    @Test fun unknownSpeedRequiresExplicitParkedConfirmationAndResetClearsArrival() {
        val gate=ArrivalGate();repeat(3) {gate.observe(fix(it*1000L,null),match(),8.0,true,it*1000L)}
        assertFalse(gate.confirm(true,null,2000));assertTrue(gate.confirm(true,true,2000))
        gate.reset();assertFalse(gate.arrived);assertFalse(gate.candidate)
    }
    @Test fun duplicateOrInaccuratePositionCannotAccumulateArrivalEvidence() {
        val gate=ArrivalGate();repeat(5) {gate.observe(fix(1000),match(),8.0,true,1000)}
        assertFalse(gate.candidate)
        repeat(5) {gate.observe(fix(it*1000L+2000).copy(accuracyMeters=100.0),match(),8.0,true,it*1000L+2000)}
        assertFalse(gate.candidate)
    }
}
