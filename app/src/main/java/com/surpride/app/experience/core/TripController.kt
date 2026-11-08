package com.surpride.app.experience.core

import java.util.UUID

class TripController(private val catalog: List<DestinationCandidate>, private val now: () -> Long,
    private val nextSeed: () -> Long, restored: MysteryTripState? = null) {
    var state: MysteryTripState = restored ?: MysteryTripState()
        private set
    fun home() { state = state.copy(panel = ExperiencePanel.HOME) }
    fun showTrip() { state = state.copy(panel = ExperiencePanel.TRIP) }
    fun openMap() { state = state.copy(panel = ExperiencePanel.MAP) }
    fun openSetup() {
        state = MysteryTripState(panel=ExperiencePanel.TRIP, preferences=state.preferences, history=state.history)
    }
    fun setupStep(step: Int) { if (state.phase == TripPhase.SETUP) state=state.copy(setupStep=step.coerceIn(0,2)) }
    fun updatePreferences(p: DrivePreferences) {
        state=state.copy(preferences=p,phase=TripPhase.SETUP,panel=ExperiencePanel.TRIP,plan=null,error=null,progress=0,arrived=false,rating=null,hintVisible=false)
    }
    fun beginPlanning() {
        if (state.phase != TripPhase.SETUP) return
        state=state.copy(phase=TripPhase.PLANNING,panel=ExperiencePanel.TRIP,seed=nextSeed(),tripId=UUID.randomUUID().toString(),error=null)
    }
    fun finishPlanning() {
        if (state.phase != TripPhase.PLANNING) return
        val context=RecommendationContext(now(),recentIds=state.history.takeLast(5).map {it.candidateId}.toSet())
        state=when(val r=DestinationEngine(catalog).recommend(state.preferences,context,state.seed)) {
            is RecommendationResult.Success -> state.copy(phase=TripPhase.READY,plan=r,error=null)
            is RecommendationResult.Failure -> state.copy(phase=TripPhase.SETUP,plan=null,error=r.reason)
        }
    }
    fun startDemo() {
        if (state.phase != TripPhase.READY) return
        val budget=budgetMinutes(state.preferences,now())
        if (budget == null || budget < (state.plan?.totalMinutes ?: Int.MAX_VALUE)) {
            state=state.copy(phase=TripPhase.SETUP,plan=null,error="귀가 시각이 가까워졌어요. 시간 조건을 다시 확인해 주세요."); return
        }
        state=state.copy(phase=TripPhase.DEMO_DRIVING,progress=0)
    }
    fun advanceDemo() { if (state.phase==TripPhase.DEMO_DRIVING) state=state.copy(progress=(state.progress+1).coerceAtMost(3)) }
    fun toggleHint() { if (state.phase in setOf(TripPhase.READY,TripPhase.DEMO_DRIVING)) state=state.copy(hintVisible=!state.hintVisible) }
    fun arrive() { if (state.phase==TripPhase.DEMO_DRIVING) state=state.copy(phase=TripPhase.ARRIVED,arrived=true,progress=3) }
    fun revealArrival() { if (state.phase==TripPhase.ARRIVED) state=state.copy(phase=TripPhase.REVEALED) }
    fun revealNow() { if (state.phase in setOf(TripPhase.READY,TripPhase.DEMO_DRIVING)) state=state.copy(phase=TripPhase.REVEALED) }
    fun rate(rating: Int) { if (state.phase==TripPhase.REVEALED && rating in 1..5) state=state.copy(rating=rating) }
    fun complete() {
        if (state.phase!=TripPhase.REVEALED || state.rating==null || state.plan==null) return
        val visit=DemoVisit(state.tripId,state.plan!!.candidate.id,state.preferences.mood,state.rating!!,now(),state.arrived)
        state=state.copy(phase=TripPhase.COMPLETED,history=(state.history.filterNot {it.tripId==visit.tripId}+visit).takeLast(30))
    }
    fun cancel() { state=MysteryTripState(panel=ExperiencePanel.TRIP,preferences=state.preferences,history=state.history,error="DEMO를 취소했어요. 실제 경로는 시작되지 않았습니다.") }
    fun presentation(): TripPresentation {
        val revealed=state.phase in setOf(TripPhase.REVEALED,TripPhase.COMPLETED)
        val p=state.preferences
        return TripPresentation(state.phase,true,state.plan?.totalMinutes,
            if (PlaceCategory.CAMPING in p.categories) "겉옷과 편안한 신발을 준비해 보세요." else "물 한 병과 편안한 신발이면 충분해요.",
            "${p.mood.label}의 기분과 ${p.companion.label} 떠나는 시간을 먼저 생각했어요.",
            state.plan?.candidate?.takeIf {revealed}?.let {RevealedDestination(it.name,it.description,it.sample)})
    }
}
