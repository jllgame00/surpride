package com.surpride.app.navigation

enum class NavigationOrigin { PLEOS_SERVICE, FAKE }
enum class NavigationConnection { DISCONNECTED, CONNECTING, CONNECTED, FAILED }
enum class NavigationStage { UNKNOWN, IDLE, REQUEST_ACCEPTED, SEARCHING, SEARCH_COMPLETED, CANCEL_REQUESTED, CANCELLED, ARRIVED, FAILED }
enum class PrivacyVerdict { CONFIRMED, PARTIAL, UNSUPPORTED, UNVERIFIED }
data class NavigationError(val code: String)
data class NavigationState(
    val origin: NavigationOrigin = NavigationOrigin.PLEOS_SERVICE,
    val connection: NavigationConnection = NavigationConnection.DISCONNECTED,
    val stage: NavigationStage = NavigationStage.UNKNOWN,
    val routeActive: Boolean? = null,
    val routeBusy: Boolean = false,
    val termsAgreed: Boolean? = null,
    val sdkMode: String = "미확인",
    val ownsTestRequest: Boolean = false,
    val destinationMatchesTest: Boolean? = null,
    val remainTimeRaw: Int? = null,
    val remainDistanceRaw: Int? = null,
    val turnCount: Int? = null,
    val historyContainsTest: Boolean? = null,
    val lastEvent: String = "없음",
    val recentEvents: List<String> = emptyList(),
    val error: NavigationError? = null,
    val listenerCount: Int = 0,
    val cleanupErrors: Int = 0,
    val privacy: PrivacyVerdict = PrivacyVerdict.UNVERIFIED
)

/** No names, addresses, coordinates, current location or history payloads cross this boundary. */
sealed interface NavigationEvent {
    data class Snapshot(val active: Boolean, val busy: Boolean, val terms: Boolean, val mode: String) : NavigationEvent
    data class Destination(val matchesTest: Boolean, val distance: Int?, val time: Int?) : NavigationEvent
    data class Status(val active: Boolean?, val mode: String) : NavigationEvent
    data class Turns(val count: Int) : NavigationEvent
    data class History(val containsTest: Boolean) : NavigationEvent
    data class Failure(val error: NavigationError) : NavigationEvent
    data object DestinationChanged : NavigationEvent
    data object SearchStarted : NavigationEvent
    data object SearchCompleted : NavigationEvent
    data object Cancelled : NavigationEvent
    data object Arrived : NavigationEvent
}
