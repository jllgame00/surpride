package com.surpride.navhostpoc.tracking

import com.surpride.navhostpoc.guidance.RouteCoordinate

enum class PositionSource { DEVICE_API, FAKE }
enum class PositionFailure { PERMISSION_DENIED, PRECISE_REQUIRED, NO_PROVIDER, DISABLED, NO_DATA, INVALID_FIX, PROVIDER_ERROR }
data class PositionFix(val coordinate:RouteCoordinate,val accuracyMeters:Double,val elapsedMillis:Long,val wallMillis:Long,
    val speedMetersPerSecond:Double?,val bearingDegrees:Double?,val bearingAccuracyDegrees:Double?,val source:PositionSource,val platformMock:Boolean) {
    override fun toString()="PositionFix(source=$source, coordinate=REDACTED)"
}
sealed interface PositionEvent {
    data class Fix(val value:PositionFix):PositionEvent
    data class Unavailable(val reason:PositionFailure):PositionEvent
}
fun interface PositionSubscription {fun close()}
fun interface PositionProvider {fun subscribe(callback:(PositionEvent)->Unit):PositionSubscription}

/** Session owns exactly one subscription and rejects stale lifecycle-generation callbacks. */
class PositionSession(private val provider:PositionProvider,private val received:(PositionEvent)->Unit) {
    private var subscription:PositionSubscription?=null
    private var generation=0
    fun start() {
        if(subscription!=null) return
        val token=++generation
        val created=try {provider.subscribe {event->if(token==generation) received(event)}} catch(_:Exception) {
            received(PositionEvent.Unavailable(PositionFailure.PROVIDER_ERROR));PositionSubscription {}
        }
        if(token==generation) subscription=created else created.close()
    }
    fun stop() {generation++;try {subscription?.close()} catch(_:Exception) {};subscription=null}
}
