package com.surpride.navhostpoc.returntrip

import org.junit.Assert.*
import org.junit.Test

class ReturnBudgetTest {
    private val start=1_800_000_000_000L
    private fun budget(minutes:Int?=60,deadline:Long?=null,returns:Boolean?=true)=
        ReturnBudget(start,minutes,deadline,returns,1200,600)

    @Test fun earlierDeadlineUsesActualStartAndNeverRestartsAtEstimateRefresh() {
        assertEquals(start+2_400_000,budget(deadline=start+2_400_000).deadlineMillis)
        assertEquals(start+3_600_000,budget(deadline=start+7_200_000).deadlineMillis)
        assertEquals(start+1_800_000,budget(minutes=null,deadline=start+1_800_000).deadlineMillis)
        val result=budget().evaluate(start+600_000,600,ReturnEstimate(600,start+600_000))
        assertEquals(start+3_600_000,result.deadlineMillis)
        assertEquals(3000L,result.remainingSeconds)
    }
    @Test fun roundTripIncludesOutboundStayReturnAndBuffer() {
        val result=budget().evaluate(start,900,ReturnEstimate(1000,start))
        assertEquals(ReturnRisk.AT_RISK,result.risk)
        assertEquals(3700L,result.requiredSeconds)
        assertEquals(-100L,result.slackSeconds)
    }
    @Test fun withinBudgetIsOnlyFreshEstimateWithNonnegativeSlack() {
        val result=budget().evaluate(start,600,ReturnEstimate(600,start))
        assertEquals(ReturnRisk.WITHIN_BUDGET,result.risk)
        assertEquals(3000L,result.requiredSeconds)
        assertEquals(600L,result.slackSeconds)
    }
    @Test fun missingReturnEstimateOrDeadlineOrTripFlagIsUnknown() {
        assertEquals(ReturnRisk.UNKNOWN,budget().evaluate(start,600,null).risk)
        assertEquals(ReturnRisk.UNKNOWN,budget(minutes=null).evaluate(start,600,ReturnEstimate(600,start)).risk)
        assertEquals(ReturnRisk.UNKNOWN,budget(returns=null).evaluate(start,600,ReturnEstimate(600,start)).risk)
        assertEquals(ReturnRisk.UNKNOWN,budget().evaluate(start,null,ReturnEstimate(600,start)).risk)
    }
    @Test fun staleOrImplausiblyFutureEstimateDoesNotClaimSafety() {
        assertEquals(ReturnRisk.UNKNOWN,budget().evaluate(start+300_001,0,ReturnEstimate(600,start)).risk)
        assertEquals(ReturnRisk.UNKNOWN,budget().evaluate(start,0,ReturnEstimate(600,start+60_001)).risk)
        assertEquals(ReturnRisk.WITHIN_BUDGET,budget().evaluate(start+300_000,0,ReturnEstimate(600,start)).risk)
    }
    @Test fun elapsedTimeCanExhaustBudgetAndNegativeSlackIsPreserved() {
        val result=budget().evaluate(start+3_700_000,0,ReturnEstimate(600,start+3_700_000),remainingStaySeconds=0)
        assertEquals(ReturnRisk.AT_RISK,result.risk)
        assertEquals(-100L,result.remainingSeconds)
        assertEquals(-1300L,result.slackSeconds)
    }
    @Test fun returningLegCanUseCurrentReturnEstimateWithoutRepeatingStay() {
        val result=budget().evaluate(start+1_800_000,0,ReturnEstimate(600,start+1_800_000),remainingStaySeconds=0)
        assertEquals(ReturnRisk.WITHIN_BUDGET,result.risk)
        assertEquals(1200L,result.requiredSeconds)
        assertEquals(600L,result.slackSeconds)
    }
    @Test fun oneWayTripHasNoReturnClaimAndClockRollbackIsUnknown() {
        assertEquals(ReturnRisk.NOT_REQUESTED,budget(returns=false).evaluate(start,600,null).risk)
        assertEquals(ReturnRisk.UNKNOWN,budget().evaluate(start-1,600,ReturnEstimate(600,start)).risk)
    }
}
