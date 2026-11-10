package com.surpride.navhostpoc

import android.os.SystemClock
import android.util.Log

/** Finite events and monotonic time only; no route, nonce, location, text or credentials. */
internal enum class HostEvent { FOCUS_REQUEST, FOCUS_CALLBACK, OWNERSHIP_DECISION, SCREEN_ACTIVE,
    CONTROL_REQUEST, REFRESH_SCHEDULED, TEMPLATE_UPDATE, TEMPLATE_READ, SURFACE_READY, FOCUS_RELEASE, TERMINATED }
internal object HostDiagnostics {
    fun record(event:HostEvent) {Log.i("SurprideHost", "event=${event.name} tNs=${SystemClock.elapsedRealtimeNanos()}")}
}
