package com.surpride.app.experience.core

import org.junit.Assert.*
import org.junit.Test

class TripControllerTest {
    private var now = 1_800_000_000_000L
    private fun controller(catalog: List<DestinationCandidate> = DemoCatalog.candidates, restored: MysteryTripState? = null) = TripController(catalog, {now}, {42}, restored)
    private fun ready(c: TripController) {c.openSetup(); c.beginPlanning(); c.finishPlanning()}

    @Test fun completeFlowAndRatingAreRecordedOnlyOnce() {
        val c = controller(); ready(c)
        assertEquals(TripPhase.READY, c.state.phase)
        c.startDemo(); assertEquals(TripPhase.DEMO_DRIVING, c.state.phase)
        c.arrive(); assertEquals(TripPhase.ARRIVED, c.state.phase)
        assertNull(c.presentation().destination)
        c.revealArrival(); assertEquals(TripPhase.REVEALED, c.state.phase)
        c.rate(5); c.complete(); c.complete()
        assertEquals(TripPhase.COMPLETED, c.state.phase)
        assertEquals(1, c.state.history.size)
        assertTrue(c.state.history.single().simulatedArrival)
    }
    @Test fun earlyRevealDoesNotClaimArrival() {
        val c=controller(); ready(c); c.startDemo(); c.revealNow()
        assertNotNull(c.presentation().destination)
        assertFalse(c.state.arrived)
        c.rate(3); c.complete()
        assertFalse(c.state.history.single().simulatedArrival)
    }
    @Test fun privateDestinationNeverAppearsInPreRevealProjection() {
        val secret=DemoCatalog.candidates.first().copy(name="SECRET_NAME_998", description="SECRET_ADDRESS_997")
        val c=controller(listOf(secret)); ready(c)
        listOf(TripPhase.READY, TripPhase.DEMO_DRIVING, TripPhase.ARRIVED).forEach { phase ->
            val view = c.presentation().toString()
            assertFalse(view.contains("SECRET_")); assertNull(c.presentation().destination)
            if (phase==TripPhase.READY) c.startDemo() else if (phase==TripPhase.DEMO_DRIVING) c.arrive()
        }
        c.revealArrival(); assertEquals("SECRET_NAME_998", c.presentation().destination?.name)
    }
    @Test fun cancelAndConditionChangesInvalidateSelection() {
        val c=controller(); ready(c); c.cancel()
        assertEquals(TripPhase.SETUP,c.state.phase); assertNull(c.state.plan)
        ready(c); c.updatePreferences(c.state.preferences.copy(mood=Mood.ADVENTURE))
        assertEquals(TripPhase.SETUP,c.state.phase); assertNull(c.state.plan)
    }
    @Test fun noDataReturnsEditableSetupWithError() {
        val c=controller(emptyList()); ready(c)
        assertEquals(TripPhase.SETUP,c.state.phase); assertNotNull(c.state.error)
    }
    @Test fun staleDeadlineBlocksDemoStart() {
        val c=controller(); c.updatePreferences(DrivePreferences(returnByEpochMillis=now+60*60_000)); ready(c)
        now+=61*60_000; c.startDemo()
        assertEquals(TripPhase.SETUP,c.state.phase)
    }
    @Test fun invalidTransitionsAndRatingsCannotCompleteTrip() {
        val c=controller(); c.arrive(); c.revealArrival(); c.complete()
        assertNull(c.presentation().destination); assertTrue(c.state.history.isEmpty())
        ready(c); c.revealNow(); c.rate(0); c.complete()
        assertEquals(TripPhase.REVEALED,c.state.phase); assertTrue(c.state.history.isEmpty())
    }
    @Test fun progressAndHintsAreDemoOnlyAndBounded() {
        val c=controller(); ready(c); c.startDemo(); repeat(10){c.advanceDemo()}
        assertEquals(3,c.state.progress)
        c.toggleHint(); assertTrue(c.state.hintVisible)
        assertTrue(c.presentation().simulation)
    }
    @Test fun restorePreservesSelectedCourseWithoutRecommending() {
        val c=controller(); ready(c); c.startDemo(); c.advanceDemo()
        val restored=controller(restored=c.state)
        assertEquals(c.state,restored.state); assertNull(restored.presentation().destination)
    }
    @Test fun completionRequiresRatingAndRecentHistorySurvivesNewSetup() {
        val c=controller(); ready(c); c.revealNow(); c.complete()
        assertEquals(TripPhase.REVEALED,c.state.phase)
        c.rate(4); c.complete(); val id=c.state.plan!!.candidate.id
        c.openSetup(); c.beginPlanning(); c.finishPlanning()
        assertEquals(1,c.state.history.size); assertNotEquals(id,c.state.plan!!.candidate.id)
    }
}
