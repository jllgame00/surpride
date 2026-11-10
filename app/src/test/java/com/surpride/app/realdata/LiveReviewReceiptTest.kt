package com.surpride.app.realdata

import org.junit.Assert.*
import org.junit.Test

class LiveReviewReceiptTest {
    private val now=1_800_000_000_000L
    private val nonce="00000000-0000-4000-8000-000000000001"
    private val source="DEVICE_API_EMULATOR"
    private fun journal()=LiveNavigationJournal(PendingLiveNavigation(nonce,"public-candidate",now))
    @Test fun committedReceiptSurvivesReconstructionWithoutAddingAnotherVisit() {
        val original=journal()
        assertTrue(original.complete(nonce,"public-candidate",source,true,true,4,now+10))
        val restored=LiveNavigationJournal(original.pending,original.visits,original.completed)
        assertTrue(restored.hasCompleted(nonce,"public-candidate",source,true,true,now+20))
        assertFalse(restored.complete(nonce,"public-candidate",source,true,true,5,now+20))
        assertEquals(1,restored.visits.size)
        assertEquals(4,restored.visits.single().rating)
    }
    @Test fun wrongNonceCandidateSourceAndExpiredReceiptCannotAuthorizeSavedResult() {
        val value=journal();assertTrue(value.complete(nonce,"public-candidate",source,true,true,4,now+10))
        assertFalse(value.hasCompleted("other","public-candidate",source,true,true,now+20))
        assertFalse(value.hasCompleted(nonce,"other",source,true,true,now+20))
        assertFalse(value.hasCompleted(nonce,"public-candidate","MOCK",true,true,now+20))
        assertFalse(value.hasCompleted(nonce,"public-candidate",source,false,true,now+20))
        assertFalse(value.hasCompleted(nonce,"public-candidate",source,true,false,now+20))
        assertFalse(value.hasCompleted(nonce,"public-candidate",source,true,true,now+3_600_001))
    }
    @Test fun aClaimedReceiptWithoutMatchingVisitCannotAuthorizeAResult() {
        val forged=LiveNavigationJournal(completed=PendingLiveNavigation(nonce,"public-candidate",now))
        assertFalse(forged.hasCompleted(nonce,"public-candidate",source,true,true,now+20))
    }
    @Test fun legacyVisitsDoNotInventSessionAuthorization() {
        val value=LiveNavigationJournal(visits=listOf(LiveNavigationVisit("public-candidate",4,now)))
        assertFalse(value.hasCompleted(nonce,"public-candidate",source,true,true,now+20))
        assertFalse(value.complete(nonce,"public-candidate",source,true,true,5,now+20))
        assertEquals(1,value.visits.size)
    }
}
