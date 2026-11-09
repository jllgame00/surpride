package com.surpride.navhostpoc.routing

import com.surpride.navhostpoc.guidance.RouteCoordinate
import com.surpride.navhostpoc.tracking.MatchStatus
import com.surpride.navhostpoc.tracking.PositionFix

enum class RerouteStatus { IDLE, LOADING, WAITING_FOR_FIX, WAITING_RETRY, FAILED, READY }
data class RerouteState(val status:RerouteStatus,val attempts:Int=0,val error:RouteRefreshError?=null)

/** Keeps the old route/owner untouched. Only a validated success asks the owner to replace its route atomically. */
class RerouteCoordinator(private val client:RouteRefreshClient,private val destination:RouteCoordinate,
    private val privateDestinationLabel:String,private val elapsedNow:()->Long,
    private val onRouteReady:(RouteRefreshResult.Success)->Unit,private val onStateChanged:(RerouteState)->Unit={}) {
    private var activeOwned=false
    private var refreshAllowed=false
    private var latestFix:PositionFix?=null
    private var match=MatchStatus.UNKNOWN
    private var current=RerouteState(RerouteStatus.IDLE)
    private var pending:RouteRefreshCall?=null
    private var generation=0L
    private var attempts=0
    private var lastStarted:Long?=null

    @Synchronized fun state()=current
    @Synchronized fun updateEligibility(activeOwned:Boolean,refreshAllowed:Boolean=true) {
        this.activeOwned=activeOwned;this.refreshAllowed=refreshAllowed
        if(!activeOwned || !refreshAllowed) {invalidate();publish(RerouteStatus.IDLE)}
    }
    @Synchronized fun onMatch(fix:PositionFix,status:MatchStatus) {
        if(!reliable(fix)) {onGpsUnavailable();return}
        latestFix=fix;match=status
        if(status==MatchStatus.ON_ROUTE) {
            if(current.status!=RerouteStatus.READY) {invalidate();attempts=0;publish(RerouteStatus.IDLE)}
            return
        }
        if(status!=MatchStatus.OFF_ROUTE || !eligible() || current.status in listOf(RerouteStatus.LOADING,RerouteStatus.FAILED)) return
        if(attempts>=MAX_ATTEMPTS) {publish(RerouteStatus.FAILED,current.error);return}
        if(cooldownPassed()) start(fix)
    }
    @Synchronized fun onGpsUnavailable() {
        latestFix=null;match=MatchStatus.UNKNOWN;invalidate()
        if(current.status!=RerouteStatus.FAILED) publish(RerouteStatus.WAITING_FOR_FIX)
    }
    /** Caller applies its parked/retry UI policy through eligibility before invoking this method. */
    @Synchronized fun retry():Boolean {
        val fix=latestFix?:return false
        if(!eligible() || !reliable(fix) || match!=MatchStatus.OFF_ROUTE ||
            current.status !in listOf(RerouteStatus.FAILED,RerouteStatus.WAITING_RETRY) || !cooldownPassed()) return false
        attempts=0;start(fix);return true
    }
    @Synchronized fun cancel() {
        activeOwned=false;latestFix=null;match=MatchStatus.UNKNOWN;invalidate();attempts=0;publish(RerouteStatus.IDLE)
    }
    private fun start(fix:PositionFix) {
        val token=++generation;attempts++;lastStarted=elapsedNow();publish(RerouteStatus.LOADING)
        val call=client.request(fix.coordinate,destination,privateDestinationLabel) {result->complete(token,result)}
        if(token==generation && current.status==RerouteStatus.LOADING) pending=call else call.cancel()
    }
    @Synchronized private fun complete(token:Long,result:RouteRefreshResult) {
        if(token!=generation || current.status!=RerouteStatus.LOADING || !eligible()) return
        generation++;pending=null
        when(result) {
            is RouteRefreshResult.Success->{attempts=0;publish(RerouteStatus.READY);onRouteReady(result)}
            is RouteRefreshResult.Failure->{
                val transient=result.error in listOf(RouteRefreshError.NETWORK,RouteRefreshError.TIMEOUT)
                publish(if(transient && attempts<MAX_ATTEMPTS) RerouteStatus.WAITING_RETRY else RerouteStatus.FAILED,result.error)
            }
        }
    }
    private fun reliable(fix:PositionFix)=fix.accuracyMeters.isFinite() && fix.accuracyMeters>0 && fix.accuracyMeters<=50 &&
        fix.elapsedMillis>=0 && elapsedNow()-fix.elapsedMillis in 0..5000
    private fun eligible()=activeOwned && refreshAllowed
    private fun cooldownPassed()=lastStarted?.let {elapsedNow()-it>=COOLDOWN_MILLIS}?:true
    private fun invalidate() {generation++;val old=pending;pending=null;old?.cancel()}
    private fun publish(status:RerouteStatus,error:RouteRefreshError?=null) {
        val next=RerouteState(status,attempts,error)
        if(next!=current) {current=next;onStateChanged(next)}
    }
    companion object {private const val MAX_ATTEMPTS=2;private const val COOLDOWN_MILLIS=30_000L}
}
