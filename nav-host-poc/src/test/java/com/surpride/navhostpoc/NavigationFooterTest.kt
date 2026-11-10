package com.surpride.navhostpoc

import com.surpride.navhostpoc.guidance.RouteProgress
import com.surpride.navhostpoc.returntrip.ReturnRisk
import com.surpride.navhostpoc.ownership.OwnershipState
import com.surpride.navhostpoc.tracking.MatchStatus
import org.junit.Assert.*
import org.junit.Test

class NavigationFooterTest {
    @Test fun undisclosedDestinationNeverEntersFooter() {
        val value=NavigationFooter.present(NavigationFooterInput(destinationName="시험 장소",active=true,
            match=MatchStatus.ON_ROUTE,progress=progress(),road="시험 도로"))
        assertFalse(value.joinToString().contains("시험 장소"))
        assertTrue(value.first().contains("비밀"))
        assertTrue(value.joinToString().contains("시험 도로"))
        assertTrue(value.joinToString().contains("추정"))
    }

    @Test fun explicitDisclosureChangesOnlyIdentityLine() {
        val input=NavigationFooterInput(destinationName="시험 장소",active=true,match=MatchStatus.ON_ROUTE,
            progress=progress(),road="시험 도로")
        val hidden=NavigationFooter.present(input)
        val disclosed=NavigationFooter.present(input.copy(revealed=true))
        assertTrue(disclosed.first().contains("시험 장소"))
        assertEquals(hidden.drop(1),disclosed.drop(1))
    }

    @Test fun uncertainPositionDoesNotShowOldExactEtaOrRoad() {
        val value=NavigationFooter.present(NavigationFooterInput(active=true,match=MatchStatus.JUMP_REJECTED,
            progress=progress(),road="지나간 도로"))
        assertTrue(value.first().contains("위치"))
        assertFalse(value.joinToString().contains("지나간 도로"))
        assertFalse(value.joinToString().contains("12분"))
    }

    @Test fun reroutingAndFailureHavePriorityOverOrdinaryPrivacyLine() {
        val loading=NavigationFooter.present(NavigationFooterInput(active=true,rerouting=true))
        assertTrue(loading.first().contains("다시 찾"))
        val failure=NavigationFooter.present(NavigationFooterInput(result="REROUTE_FAILED / NETWORK"))
        assertTrue(failure.first().contains("찾지 못"))
        assertFalse(failure.joinToString().contains("NETWORK"))
    }

    @Test fun returnRiskIsAdvisoryAndReturnCompletionIsDistinct() {
        val risk=NavigationFooter.present(NavigationFooterInput(active=true,match=MatchStatus.ON_ROUTE,
            progress=progress(),returnRisk=ReturnRisk.AT_RISK))
        assertTrue(risk.first().contains("복귀 시간"))
        val completed=NavigationFooter.present(NavigationFooterInput(returning=true,returnCompleted=true,arrived=true))
        assertTrue(completed.first().contains("출발지"))
        assertTrue(completed.joinToString().contains("종료"))
        val prepared=NavigationFooter.present(NavigationFooterInput(returning=true,active=false))
        assertTrue(prepared.first().contains("시작 전"))
    }

    @Test fun internalCountersAndEnumsAreNotDriverMessages() {
        val value=NavigationFooter.present(NavigationFooterInput(result="TRIP_FAILED / HOST_EXCEPTION"))
        assertTrue(value.first().contains("안내"))
        assertFalse(value.joinToString().contains("TRIP"))
        assertFalse(value.joinToString().contains("HOST_EXCEPTION"))
    }

    @Test fun ownershipErrorCannotBePresentedAsOrdinaryHiddenGuidance() {
        val value=NavigationFooter.present(NavigationFooterInput(ownerState=OwnershipState.ERROR,
            match=MatchStatus.ON_ROUTE,progress=progress(),road="지난 도로"))
        assertTrue(value.first().contains("연결 오류"))
        assertFalse(value.joinToString().contains("지난 도로"))
        assertFalse(value.joinToString().contains("12분"))
    }

    @Test fun pendingOwnershipIsNotPresentedAsAlreadyActive() {
        val value=NavigationFooter.present(NavigationFooterInput(ownerState=OwnershipState.REQUESTED))
        assertTrue(value.first().contains("확인하고"))
        assertFalse(value.first().contains("안내 중"))
    }

    @Test fun revealedNameCannotHideExplicitStopStatus() {
        val value=NavigationFooter.present(NavigationFooterInput(revealed=true,destinationName="공개한 시험 장소",
            active=false,result="USER_STOPPED",match=MatchStatus.ON_ROUTE,progress=progress(),road="지난 도로"))
        assertTrue(value.first().contains("종료"))
        assertFalse(value.first().contains("공개한 시험 장소"))
        assertFalse(value.joinToString().contains("12분"))
    }

    @Test fun returningCannotHideLostOrStoppedGuidance() {
        val value=NavigationFooter.present(NavigationFooterInput(returning=true,active=false,result="GUIDANCE_STOPPED"))
        assertTrue(value.first().contains("종료"))
        assertFalse(value.first().contains("돌아가는 중"))
        val complete=NavigationFooter.present(NavigationFooterInput(returning=true,returnCompleted=true,arrived=true,
            active=false,result="GUIDANCE_STOPPED"))
        assertTrue(complete.first().contains("출발지"))
    }

    @Test fun completedReturnDoesNotOfferUnavailableReviewOrAnotherReturn() {
        val lines=NavigationFooter.present(NavigationFooterInput(returning=true,returnCompleted=true,arrived=true))
        assertTrue(lines.first().contains("출발지"))
        assertFalse(lines.last().contains("평가"))
        assertFalse(lines.last().contains("복귀"))
    }

    private fun progress()=RouteProgress(200.0,3200.0,400.0,0,720)
}
