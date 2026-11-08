package com.surpride.app.navigation

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.*
import kotlin.math.abs

/** Official public sample payload, unchanged; never a user's location or a fake POI name. */
internal fun publicTestRouteInfo() = RouteInfo(
    latitude = 37.48544722, longitude = 127.03636666,
    poiName = "42dot", poiId = "36khp37hh8gqcm5nqhe5", poiSubId = "0",
    address = "서울시 강남구 남부순환로 2621"
)

private fun matchesPublicTest(latitude: Double, longitude: Double, poiId: String?): Boolean =
    poiId == "36khp37hh8gqcm5nqhe5" ||
        (abs(latitude - 37.48544722) < 0.0001 && abs(longitude - 127.03636666) < 0.0001)

class PleosNavigationDataSource(context: Context) : NavigationDataSource {
    private val handler = Handler(Looper.getMainLooper())
    private val session = NavigationSession({ PleosNavigationClient(context.applicationContext) },
        Build.MODEL == "Connect on x86_64 emulator" && Build.PRODUCT == "crp_avd" && Build.DEVICE == "crp_avd_x86_64") { action ->
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
    private val timeout = Runnable { session.expireQuery() }
    override val state get() = session.state
    override fun start(observer: (NavigationState) -> Unit) { session.start(observer); arm() }
    override fun refresh() { session.refresh(); arm() }
    override fun requestTestRoute() { session.requestTestRoute(); arm() }
    override fun cancelTestRoute() { session.cancelTestRoute(); arm() }
    override fun showNavigation() = session.showNavigation()
    override fun queryHistory() = session.queryHistory()
    override fun stop() { handler.removeCallbacks(timeout); session.stop() }
    private fun arm() { handler.removeCallbacks(timeout); handler.postDelayed(timeout, 15_000) }
}

private class PleosNavigationClient(context: Context) : NavigationClient {
    private val helper = NaviHelper(context)
    private var closed = false
    private var registered = false
    private var emit: ((NavigationEvent) -> Unit)? = null
    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) { emit?.invoke(NavigationEvent.SearchStarted) }
        override fun onRoutingCompleted() { emit?.invoke(NavigationEvent.SearchCompleted) }
        override fun onRouteCancelled() { emit?.invoke(NavigationEvent.Cancelled) }
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) { emit?.invoke(NavigationEvent.Arrived) }
        override fun onDestinationChanged() { emit?.invoke(NavigationEvent.DestinationChanged) }
        override fun onError(errorCode: NaviErrorCode) { emit?.invoke(NavigationEvent.Failure(NavigationError(errorCode.name))) }
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {
            emit?.invoke(NavigationEvent.Snapshot(
                routeStateInfo.naviMode == NaviMode.ROUTE_GUIDING || routeStateInfo.naviMode == NaviMode.SIMULATION_GUIDING,
                routeStateInfo.isRouting, routeStateInfo.termsAgreement, routeStateInfo.naviMode.name
            ))
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            emit?.invoke(NavigationEvent.Destination(matchesPublicTest(destinationInfo.latitude, destinationInfo.longitude, destinationInfo.poiId),
                destinationInfo.remainDistance, destinationInfo.remainTime))
        }
        override fun onNavigationStatus(status: NavigationStatus) {
            emit?.invoke(NavigationEvent.Status(when (status) {
                NavigationStatus.ROUTE_DRIVE -> true
                NavigationStatus.FREE_DRIVE, NavigationStatus.INACTIVE -> false
                else -> null
            }, status.name))
        }
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) { emit?.invoke(NavigationEvent.Turns(tbtInfoList.size)) }
        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) {
            emit?.invoke(NavigationEvent.History(recentDestinationInfoList.any { matchesPublicTest(it.latitude, it.longitude, it.poiId) }))
        }
    }

    override fun connect(connected: () -> Unit, failed: (Throwable) -> Unit, event: (NavigationEvent) -> Unit) {
        emit = event
        helper.initialize(onConnected = {
            if (!closed) {
                try { helper.addListener(listener); registered = true; connected() } catch (e: Exception) { failed(e) }
            }
        }, onFailed = { if (!closed) failed(it) })
    }
    override fun queryRoute() = helper.getRouteStateInfo()
    override fun queryDestination() = helper.getDestinationInfo()
    override fun queryTurns() = helper.getTBTInfo()
    override fun requestPublicTestRoute() = helper.requestRoute(publicTestRouteInfo())
    override fun cancelRoute() = helper.cancelRoute()
    override fun showNavigation() = helper.requestNavigationScreen(NavigationScreenRequest.Foreground)
    override fun queryHistory() {
        helper.getRecentDestinationInfo()
        helper.requestNavigationScreen(NavigationScreenRequest.RecentDestinations)
    }
    override fun close() {
        closed = true
        emit = null
        try { if (registered) helper.removeListener(listener) } finally { registered = false; helper.release() }
    }
}
