package com.surpride.navhostpoc.ownership

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import java.util.concurrent.Executor

/** Attestations required to attempt a controlled test, not a claim that Pleos accepts the Trip. */
data class HostIntegrationGate(val policyConfirmed:Boolean,val permissionsConfirmed:Boolean,val emulatorConfirmed:Boolean) {
    val blockReason:String? get()=when {
        !policyConfirmed->"POLICY_UNVERIFIED"
        !permissionsConfirmed->"PERMISSIONS_UNVERIFIED"
        !emulatorConfirmed->"EMULATOR_REQUIRED"
        else->null
    }
    companion object {val UNVERIFIED=HostIntegrationGate(false,false,false)}
}

/** One request attempt only. Must be created by a future explicit user experiment action, never on resume. */
class ControlledTestApproval private constructor() {
    private var consumed=false
    internal fun consume():Boolean {if(consumed) return false;consumed=true;return true}
    companion object {fun fromExplicitUserAction()=ControlledTestApproval()}
}

@MainThread
interface NavigationOwnershipAdapter {
    val state:OwnershipState
    val reason:String
    fun requestActivation(checks:ActivationChecks,approval:ControlledTestApproval?):Boolean
    fun updateHiddenTrip(turn:NextTurnMetadata,identity:DestinationIdentity):Boolean
    fun updateLoadingTrip():Boolean
    fun revealDestination(turn:NextTurnMetadata,identity:DestinationIdentity,userRequested:Boolean,distanceMeters:Double,seconds:Long):Boolean
    fun environmentChanged(parked:Boolean?,oemRouteActive:Boolean?)
    fun onAppStopped()
    fun stop()
    fun release()
    fun reconnect():Boolean
}

/** Prepared integration only: default-closed and not instantiated/bound by the audit UI. */
@MainThread
class AndroidXNavigationOwnershipAdapter(
    managerProvider:()->NavigationManager,
    focus:FocusPort,
    stopGuidance:()->Unit,
    private val gate:HostIntegrationGate=HostIntegrationGate.UNVERIFIED
):NavigationOwnershipAdapter {
    private val navigation=DeferredNavigationLifecycle(managerProvider,stopGuidance)
    private val controller=OwnershipController(focus,navigation)
    private var rejected:String?=gate.blockReason
    override val state get():OwnershipState {checkMain();return controller.state}
    override val reason get():String {checkMain();return rejected?:controller.reason}
    override fun requestActivation(checks:ActivationChecks,approval:ControlledTestApproval?):Boolean {
        checkMain()
        val approved=approval?.consume()==true
        val blocked=gate.blockReason?:if(!approved) "EXPLICIT_EXPERIMENT_APPROVAL_REQUIRED" else null
        if(blocked!=null) {rejected=blocked;return false}
        rejected=null
        return controller.request(checks)
    }
    override fun updateHiddenTrip(turn:NextTurnMetadata,identity:DestinationIdentity):Boolean {
        checkMain();return controller.publish {navigation.update(PrivacyTripFactory.hidden(turn,identity))}
    }
    override fun updateLoadingTrip():Boolean {
        checkMain();return controller.publish {navigation.update(androidx.car.app.navigation.model.Trip.Builder().setLoading(true).build())}
    }
    override fun revealDestination(turn:NextTurnMetadata,identity:DestinationIdentity,userRequested:Boolean,distanceMeters:Double,seconds:Long):Boolean {
        checkMain()
        if(!userRequested) return false
        return controller.publish {navigation.update(PrivacyTripFactory.revealed(turn,identity,true,distanceMeters,seconds))}
    }
    override fun environmentChanged(parked:Boolean?,oemRouteActive:Boolean?) {checkMain();controller.environmentChanged(parked,oemRouteActive)}
    override fun onAppStopped() {checkMain();controller.stop("APP_BACKGROUND")}
    override fun stop() {checkMain();controller.stop()}
    override fun release() {checkMain();controller.release()}
    override fun reconnect():Boolean {
        checkMain()
        if(!controller.reconnect()) return false
        navigation.forgetManager();rejected=gate.blockReason;return true
    }
    private fun checkMain() {check(Looper.myLooper()==Looper.getMainLooper()) {"Main thread required"}}
}

/** Only public NavigationManager methods. No RestrictTo/Binder calls, no focus or OEM takeover. */
private class DeferredNavigationLifecycle(private val provider:()->NavigationManager,private val stopGuidance:()->Unit):NavigationLifecyclePort {
    private var manager:NavigationManager?=null
    private var needsShutdown=false
    private var registration=0L
    private val callbackExecutor=Executor {task->
        if(Looper.myLooper()==Looper.getMainLooper()) task.run() else Handler(Looper.getMainLooper()).post(task)
    }
    override fun setStopHandler(handler:()->Unit) {
        val token=++registration
        needsShutdown=true
        val target=manager?:provider()
        // A stop/reconnect during service lookup must not install a stale callback or cache its manager.
        if(token!=registration) return
        manager=target
        target.setNavigationManagerCallback(callbackExecutor,object:NavigationManagerCallback {
            // Controller's session-generation guard decides whether this is the current session.
            override fun onStopNavigation()=handler()
        })
    }
    override fun started()=checkNotNull(manager).navigationStarted()
    override fun ended()=cleanup {manager?.navigationEnded()}
    override fun clearStopHandler()=cleanup {manager?.clearNavigationManagerCallback()}
    fun update(trip:androidx.car.app.navigation.model.Trip) {checkNotNull(manager).updateTrip(trip)}
    fun forgetManager() {check(!needsShutdown);registration++;manager=null}
    private fun cleanup(sdkAction:()->Unit) {
        registration++
        var failed=false
        if(needsShutdown) try {stopGuidance();needsShutdown=false} catch(_:Exception) {failed=true}
        try {sdkAction()} catch(_:Exception) {failed=true}
        if(failed) error("Guidance/manager cleanup failed")
    }
}
