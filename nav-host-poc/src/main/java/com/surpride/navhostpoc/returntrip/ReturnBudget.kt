package com.surpride.navhostpoc.returntrip

/** A provider estimate, never a promise of arrival by a deadline. No coordinates are retained here. */
data class ReturnEstimate(val durationSeconds:Long,val measuredAtMillis:Long)

enum class ReturnRisk { WITHIN_BUDGET, AT_RISK, UNKNOWN, NOT_REQUESTED }

data class ReturnBudgetSnapshot(val risk:ReturnRisk,val deadlineMillis:Long?,val remainingSeconds:Long?,
    val requiredSeconds:Long?,val slackSeconds:Long?)

/** Pure advisory policy. Create once at actual departure and keep it through an explicit return leg. */
class ReturnBudget(val startedAtMillis:Long,availableMinutes:Int?,returnByEpochMillis:Long?,
    val returnToStart:Boolean?,val staySeconds:Long,val safetyBufferSeconds:Long) {
    private val validPreferences=startedAtMillis>0 && (availableMinutes==null || availableMinutes>0) &&
        (returnByEpochMillis==null || returnByEpochMillis>0) &&
        staySeconds in 0..604800 && safetyBufferSeconds in 0..86400
    val deadlineMillis:Long?=if(!validPreferences) null else {
        val durationMillis=availableMinutes?.toLong()?.times(60_000)
        val availableDeadline=durationMillis?.takeIf {startedAtMillis<=Long.MAX_VALUE-it}?.let {startedAtMillis+it}
        listOfNotNull(availableDeadline,returnByEpochMillis).minOrNull()
    }

    /** During return, pass zero outbound/stay and an estimate from the current position to the start. */
    fun evaluate(now:Long,remainingOutboundSeconds:Long?,returnEstimate:ReturnEstimate?,
        remainingStaySeconds:Long=staySeconds):ReturnBudgetSnapshot {
        val remaining=deadlineMillis?.takeIf {now>0}?.let {Math.floorDiv(it-now,1000L)}
        fun unknown(risk:ReturnRisk=ReturnRisk.UNKNOWN)=ReturnBudgetSnapshot(risk,deadlineMillis,remaining,null,null)
        if(returnToStart==false) return unknown(ReturnRisk.NOT_REQUESTED)
        if(returnToStart==null || !validPreferences || now<startedAtMillis || remaining==null ||
            remainingOutboundSeconds==null || remainingOutboundSeconds !in 0..604800 ||
            remainingStaySeconds !in 0..604800 || returnEstimate==null ||
            returnEstimate.durationSeconds !in 1..604800 || returnEstimate.measuredAtMillis<=0 ||
            now-returnEstimate.measuredAtMillis !in -60_000..300_000) return unknown()
        val required=remainingOutboundSeconds+remainingStaySeconds+returnEstimate.durationSeconds+safetyBufferSeconds
        val slack=remaining-required
        return ReturnBudgetSnapshot(if(slack<0) ReturnRisk.AT_RISK else ReturnRisk.WITHIN_BUDGET,
            deadlineMillis,remaining,required,slack)
    }
}
