package com.surpride.app.realdata

import com.surpride.app.experience.core.*
import com.surpride.app.realdata.experience.DriveExperienceEngine
import org.junit.Assert.*
import org.junit.Test

class LiveRecommendationHistoryTest {
    private val now=1_800_000_000_000L
    private val day=86_400_000L
    private val id=SeoulPilotCatalog.places.first().id
    private val other=SeoulPilotCatalog.places.last().id
    private val nonce="00000000-0000-4000-8000-000000000001"
    private fun context(journal:LiveNavigationJournal,mock:Boolean=false,demo:Set<String> = emptySet(),at:Long=now)=
        journal.recommendationContext(at,mock,demo)
    private fun completed(arrived:Boolean?,at:Long=now):LiveNavigationJournal {
        val journal=LiveNavigationJournal(PendingLiveNavigation(nonce,id,at-1000))
        assertTrue(journal.complete(nonce,id,LiveNavigationResultPolicy.SOURCE,true,true,4,at,arrived))
        return journal
    }
    @Test fun explicitArrivalAndCompletedRatingAreExcludedFromNextLiveContext() {
        val journal=completed(true)
        assertEquals(setOf(id),context(journal).recentIds)
        assertEquals(4,journal.visits.single().rating)
        assertEquals(true,journal.visits.single().arrivalConfirmed)
    }
    @Test fun immediateDisclosureWithoutArrivalPreservesRatingButIsNotAVisitExclusion() {
        val journal=completed(false)
        assertTrue(context(journal).recentIds.isEmpty());assertEquals(1,journal.visits.size)
        assertEquals(4,journal.visits.single().rating)
    }
    @Test fun legacyUnknownArrivalIsPreservedWithoutInventingVisitEvidence() {
        val journal=LiveNavigationJournal(visits=listOf(LiveNavigationVisit(id,5,now)))
        assertTrue(context(journal).recentIds.isEmpty());assertEquals(5,journal.visits.single().rating)
        assertNull(journal.visits.single().arrivalConfirmed)
    }
    @Test fun pendingCancelledAndInvalidResultsCannotBecomeCompletedVisits() {
        val journal=LiveNavigationJournal(PendingLiveNavigation(nonce,id,now-1000))
        assertTrue(context(journal).recentIds.isEmpty())
        for(revealed in listOf(false,true)) for(stopped in listOf(false,true)) if(!revealed || !stopped) {
            assertFalse(journal.complete(nonce,id,LiveNavigationResultPolicy.SOURCE,revealed,stopped,5,now,true))
        }
        assertFalse(journal.complete("other",id,LiveNavigationResultPolicy.SOURCE,true,true,5,now,true))
        assertFalse(journal.complete(nonce,id,"MOCK",true,true,5,now,true))
        assertTrue(journal.visits.isEmpty())
        val cancelled=LiveNavigationJournal(visits=journal.visits)
        assertTrue(context(cancelled).recentIds.isEmpty())
    }
    @Test fun duplicateVisitsProduceOneRecentIdWithoutDeletingRatings() {
        val visit=LiveNavigationVisit(id,4,now,true)
        val journal=LiveNavigationJournal(visits=listOf(visit,visit.copy(rating=5)))
        assertEquals(setOf(id),context(journal).recentIds);assertEquals(2,journal.visits.size)
    }
    @Test fun sevenDayBoundaryExpiresWithoutDeletingOriginalHistory() {
        val journal=completed(true,now-7*day)
        assertEquals(setOf(id),context(journal).recentIds)
        assertTrue(context(journal,at=now+1).recentIds.isEmpty())
        assertEquals(1,journal.visits.size)
    }
    @Test fun malformedFutureUnknownAndInvalidRatingRecordsAreNotExclusions() {
        val journal=LiveNavigationJournal(visits=listOf(LiveNavigationVisit(id,5,now+1,true),
            LiveNavigationVisit(id,0,now,true),LiveNavigationVisit("unknown",5,now,true),LiveNavigationVisit(id,5,-1,true)))
        assertTrue(context(journal).recentIds.isEmpty())
    }
    @Test fun liveAndDemoIdsStaySeparateEvenOnLiteralCollision() {
        val journal=completed(true)
        assertEquals(setOf(id),context(journal,demo=setOf(other)).recentIds)
        assertEquals(setOf(other),context(journal,true,setOf(other)).recentIds)
        assertTrue(context(LiveNavigationJournal(),demo=setOf(id)).recentIds.isEmpty())
        assertEquals(setOf(id),context(LiveNavigationJournal(),true,setOf(id)).recentIds)
    }
    @Test fun emptyHistoryHasNoExclusionsInEitherLiveEngine() {
        assertTrue(context(LiveNavigationJournal()).recentIds.isEmpty())
    }
    @Test fun allFiveRecentPlacesReturnNoCandidateWithoutAnyRouteRequests() {
        val journal=LiveNavigationJournal(visits=SeoulPilotCatalog.places.map {LiveNavigationVisit(it.id,4,now,true)})
        var calls=0
        val provider=RouteProvider {_,_->calls++;RouteReply.Failure(RouteError.NO_ROUTE)}
        val preferences=DrivePreferences(availableMinutes=60,returnToStart=false)
        val engines=listOf(
            RouteRecommendationEngine(SeoulPilotCatalog.places,provider,{now}).recommend(SeoulPilotCatalog.developmentOrigin,preferences,context(journal),0,0,1),
            DriveExperienceEngine(SeoulPilotCatalog.places,provider,clock={now}).recommend(SeoulPilotCatalog.developmentOrigin,preferences,context(journal),0,0,1))
        engines.forEach {assertEquals(setOf(RouteError.NO_CANDIDATE),(it as RoutePlan.Failure).errors)}
        assertEquals(0,calls)
    }
    @Test fun rerecommendationAfterConditionChangeReadsFreshJournalAndKeepsExclusions() {
        val journal=LiveNavigationJournal(PendingLiveNavigation(nonce,id,now-1000))
        assertTrue(context(journal).recentIds.isEmpty())
        assertTrue(journal.complete(nonce,id,LiveNavigationResultPolicy.SOURCE,true,true,5,now,true))
        val updated=context(journal,at=now+1000)
        assertEquals(setOf(id),updated.recentIds);assertEquals(now+1000,updated.nowMillis)
        assertFalse(journal.complete(nonce,id,LiveNavigationResultPolicy.SOURCE,true,true,1,now+1000,true))
        assertEquals(1,journal.visits.size);assertEquals(5,journal.visits.single().rating)
    }
}
