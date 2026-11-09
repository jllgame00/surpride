package com.surpride.navhostpoc.ownership

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener

/** Read-only official NaviHelper subscription. No route or system-screen mutation methods. */
@MainThread
class PleosOemRouteMonitor(context: Context) {
    private val helper = NaviHelper(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val observation = OemRouteObservation()
    private var closed = false
    private var started = false
    private var registered = false
    val status: OemRouteStatus get() = observation.status

    private val timeout = Runnable { if (!closed) observation.failed("OEM_ROUTE_QUERY_TIMEOUT") }
    private val poll = object : Runnable {
        override fun run() {
            if (closed || !registered) return
            refresh()
            handler.postDelayed(this, 5_000)
        }
    }
    private val listener = object : NaviHelperEventListener {
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = onMain {
            // Errors are latched for this monitor; a queued snapshot cannot bypass a failure.
            if (!observation.status.connected) return@onMain
            handler.removeCallbacks(timeout)
            observation.snapshot(
                routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING,
                routeStateInfo.isRouting,
                routeStateInfo.termsAgreement
            )
            handler.postDelayed(timeout, 12_000)
        }
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = onMain { observation.active(); refresh() }
        override fun onRoutingCompleted() = changed()
        override fun onRouteEnded() = changed()
        override fun onReRouteEnded() = changed()
        override fun onRouteCancelled() = changed()
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = changed()
        override fun onDestinationChanged() = changed()
        override fun onNavigationStatus(status: NavigationStatus) = onMain {
            if (status == NavigationStatus.ROUTE_DRIVE) observation.active() else observation.invalidate()
            refresh()
        }
        override fun onError(errorCode: NaviErrorCode) = onMain { observation.failed("OEM_${errorCode.name}") }
        private fun changed() = onMain { observation.invalidate(); refresh() }
    }

    fun connect(changed: (OemRouteStatus) -> Unit) {
        checkMain()
        observation.observe(changed)
        if (closed || started) return
        started = true
        handler.postDelayed(timeout, 12_000)
        try {
            helper.initialize(onConnected = {
                onMain {
                    try {
                        // Set first so cleanup is attempted even if registration throws midway.
                        registered = true
                        helper.addListener(listener)
                        observation.connected()
                        refresh()
                        handler.postDelayed(poll, 5_000)
                    } catch (_: Exception) { observation.failed("OEM_LISTENER_FAILED") }
                }
            }, onFailed = { onMain { observation.failed("OEM_CONNECTION_FAILED") } })
        } catch (_: Exception) { observation.failed("OEM_INITIALIZE_FAILED") }
    }

    fun refresh() {
        checkMain()
        if (closed || !registered) return
        try { helper.getRouteStateInfo() } catch (_: Exception) { observation.failed("OEM_ROUTE_QUERY_FAILED") }
    }

    fun close() {
        checkMain()
        if (closed) return
        closed = true
        handler.removeCallbacks(timeout)
        handler.removeCallbacks(poll)
        var failed = false
        try { if (registered) helper.removeListener(listener) } catch (_: Exception) { failed = true }
        finally {
            registered = false
            try { helper.release() } catch (_: Exception) { failed = true }
        }
        if (failed) observation.failed("OEM_CLEANUP_FAILED") else observation.closed()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) { if (!closed) action() }
        else handler.post { if (!closed) action() }
    }
    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) { "Main thread required" } }
}
