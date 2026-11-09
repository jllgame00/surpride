package com.surpride.navhostpoc.ownership

enum class OwnershipState { INACTIVE, REQUESTED, ACTIVE, STOP_REQUESTED, STOPPED, ERROR }
data class ActivationChecks(val userRequested:Boolean,val parked:Boolean?,val oemRouteActive:Boolean?,
    val policyReviewed:Boolean,val privacyReviewed:Boolean,val switchConsent:Boolean=false)

/** A future real adapter must bind each id to a distinct platform ownership callback/lease. */
interface FocusPort {
    fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean
    fun abandon(id:Long)
}
interface NavigationLifecyclePort {
    fun setStopHandler(handler:()->Unit)
    fun clearStopHandler()
    fun started()
    fun ended()
}

/** Main-thread state model only. No platform manager lookup, no automatic route takeover. */
class OwnershipController(private val focus:FocusPort,private val navigation:NavigationLifecyclePort) {
    var state=OwnershipState.INACTIVE;private set
    var reason="MODEL_ONLY";private set
    private var generation=0L
    private var connected=true
    private var callbackAttempted=false
    private var startAttempted=false
    private var lease:Long?=null
    private val staleLeases=linkedSetOf<Long>()
    private var cleaning=false
    private var errorLatched=false

    fun request(checks:ActivationChecks):Boolean {
        if(state==OwnershipState.REQUESTED || state==OwnershipState.ACTIVE || state==OwnershipState.STOP_REQUESTED) return false
        if(!connected || state==OwnershipState.ERROR || cleaning || callbackAttempted || startAttempted || lease!=null || staleLeases.isNotEmpty()) {
            reason="RECONNECT_REQUIRED";return false
        }
        val blocked=when {
            !checks.userRequested->"USER_REQUEST_REQUIRED"
            checks.parked!=true->"PARKED_REQUIRED"
            !checks.policyReviewed->"POLICY_UNVERIFIED"
            !checks.privacyReviewed->"PRIVACY_UNVERIFIED"
            checks.oemRouteActive==null->"OEM_ROUTE_UNKNOWN"
            checks.oemRouteActive && !checks.switchConsent->"SWITCH_CONSENT_REQUIRED"
            checks.oemRouteActive->"EXISTING_ROUTE_MUST_END"
            else->null
        }
        if(blocked!=null) {reason=blocked;return false}
        val token=++generation
        state=OwnershipState.REQUESTED;reason="AWAITING_GRANT"
        try {
            callbackAttempted=true
            navigation.setStopHandler {if(token==generation) stop("HOST_STOP")}
            if(token!=generation || state!=OwnershipState.REQUESTED) return false
            lease=token
            val accepted=focus.request(token,{
                if(token!=generation) {
                    // Release only the old callback's lease, never the newer request's focus.
                    staleLeases.add(token)
                    if(!cleaning) try {focus.abandon(token);staleLeases.remove(token)}
                        catch(_:Exception) {finish("STALE_GRANT_CLEANUP_FAILED",true)}
                } else if(state==OwnershipState.REQUESTED) {
                    try {startAttempted=true;navigation.started()
                        if(token==generation && state==OwnershipState.REQUESTED) {state=OwnershipState.ACTIVE;reason="MODEL_ACTIVE"}
                    } catch(_:Exception) {if(token==generation) finish("START_FAILED",true)}
                }
            },{if(token==generation) stop("FOCUS_LOST")})
            if(!accepted && token==generation) finish("FOCUS_DENIED",true)
            return accepted && token==generation && state in listOf(OwnershipState.REQUESTED,OwnershipState.ACTIVE)
        } catch(_:Exception) {if(token==generation) finish("REQUEST_FAILED",true);return false}
    }
    fun publish(update:()->Unit):Boolean {
        if(state!=OwnershipState.ACTIVE || !connected) return false
        val token=generation
        return try {update();token==generation && state==OwnershipState.ACTIVE}
            catch(_:Exception) {if(token==generation) finish("UPDATE_FAILED",true);false}
    }
    /** A future platform adapter must continuously feed UX/focus/OEM-route changes, not a saved snapshot. */
    fun environmentChanged(parked:Boolean?,oemRouteActive:Boolean?) {
        if(state in listOf(OwnershipState.REQUESTED,OwnershipState.ACTIVE) && (parked!=true || oemRouteActive!=false))
            stop("ENVIRONMENT_INVALIDATED")
    }
    fun stop(cause:String="USER_STOP") {
        if(state==OwnershipState.INACTIVE || state==OwnershipState.STOPPED) {state=OwnershipState.STOPPED;return}
        finish(cause,false)
    }
    fun release() {connected=false;finish("RELEASED",false)}
    fun reconnect():Boolean {
        if(cleaning || state in listOf(OwnershipState.ACTIVE,OwnershipState.REQUESTED,OwnershipState.STOP_REQUESTED)) return false
        if(callbackAttempted || startAttempted || lease!=null || staleLeases.isNotEmpty()) finish("CLEANUP_RETRY",false)
        if(callbackAttempted || startAttempted || lease!=null || staleLeases.isNotEmpty()) return false
        generation++;errorLatched=false;connected=true;state=OwnershipState.INACTIVE;reason="RECONNECTED_EXPLICIT_START_REQUIRED";return true
    }
    private fun finish(cause:String,error:Boolean) {
        if(cleaning) {if(error) {errorLatched=true;connected=false};return}
        cleaning=true
        generation++;state=OwnershipState.STOP_REQUESTED;reason=cause
        var failed=false
        if(startAttempted) try {navigation.ended();startAttempted=false} catch(_:Exception) {failed=true}
        lease?.let {id->try {focus.abandon(id);lease=null;staleLeases.remove(id)} catch(_:Exception) {failed=true}}
        if(callbackAttempted) try {navigation.clearStopHandler();callbackAttempted=false} catch(_:Exception) {failed=true}
        for(id in staleLeases.toList()) try {focus.abandon(id);staleLeases.remove(id)} catch(_:Exception) {failed=true}
        // A synchronous grant can add debt while the snapshot above is being processed.
        if(startAttempted || callbackAttempted || lease!=null || staleLeases.isNotEmpty()) failed=true
        errorLatched=errorLatched || error || failed
        state=if(errorLatched) OwnershipState.ERROR else OwnershipState.STOPPED
        if(errorLatched) connected=false
        if(failed) reason="CLEANUP_FAILED_RECONNECT_REQUIRED"
        cleaning=false
    }
}
