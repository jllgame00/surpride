package com.surpride.app.navigation

interface NavigationDataSource {
    val state: NavigationState
    fun start(observer: (NavigationState) -> Unit)
    fun refresh()
    fun requestTestRoute()
    fun cancelTestRoute()
    fun showNavigation()
    fun queryHistory()
    fun stop()
}

internal interface NavigationClient {
    fun connect(connected: () -> Unit, failed: (Throwable) -> Unit, event: (NavigationEvent) -> Unit)
    fun queryRoute()
    fun queryDestination()
    fun queryTurns()
    fun queryHistory()
    fun showNavigation()
    fun requestPublicTestRoute()
    fun cancelRoute()
    fun close()
}

internal class NavigationSession(
    private val factory: () -> NavigationClient,
    private val emulator: Boolean,
    private val dispatch: (() -> Unit) -> Unit
) : NavigationDataSource {
    override var state = NavigationState()
        private set
    private enum class Pending { NONE, OBSERVE, REQUEST, CANCEL_DESTINATION, CANCEL_ROUTE }
    private var pending = Pending.NONE
    private var client: NavigationClient? = null
    private var observer: ((NavigationState) -> Unit)? = null
    private var generation = 0
    private var active = false
    private var mutationsLocked = false

    override fun start(observer: (NavigationState) -> Unit) {
        if (active && state.connection != NavigationConnection.FAILED) { this.observer = observer; observer(state); return }
        if (active) stop()
        this.observer = observer
        active = true
        val token = ++generation
        pending = Pending.NONE
        mutationsLocked = false
        state = NavigationState(connection = NavigationConnection.CONNECTING)
        publish()
        try {
            client = factory()
            client!!.connect({ guarded(token) {
                state = state.copy(connection = NavigationConnection.CONNECTED, listenerCount = 1, error = null)
                record("CONNECTED")
                refresh()
            } }, { error -> guarded(token) {
                state = state.copy(connection = NavigationConnection.FAILED, error = failure(error))
                pending = Pending.NONE
                record("INITIALIZE_FAILED")
            } }, { event -> guarded(token) { receive(event) } })
        } catch (error: Exception) {
            state = state.copy(connection = NavigationConnection.FAILED, error = failure(error))
            record("INITIALIZE_FAILED")
        }
    }

    override fun refresh() {
        if (!connected()) return
        if (pending != Pending.NONE) { fail("QUERY_IN_PROGRESS"); return }
        pending = Pending.OBSERVE
        call { it.queryRoute() }
    }

    override fun requestTestRoute() {
        if (!connected()) return
        when {
            !emulator -> fail("EMULATOR_ONLY")
            mutationsLocked -> fail("RECONNECT_REQUIRED")
            state.ownsTestRequest || pending != Pending.NONE -> fail("REQUEST_IN_PROGRESS")
            state.routeActive != false || state.routeBusy -> fail("EXISTING_OR_UNKNOWN_ROUTE")
            else -> {
                pending = Pending.REQUEST
                state = state.copy(error = null)
                record("PRECHECK_REQUEST")
                call { it.queryRoute() }
            }
        }
    }

    override fun cancelTestRoute() {
        if (!connected()) return
        if (!emulator || mutationsLocked || !state.ownsTestRequest || pending != Pending.NONE) { fail("NOT_OWNED_OR_BUSY"); return }
        pending = Pending.CANCEL_DESTINATION
        call { it.queryDestination() }
    }

    override fun showNavigation() { if (connected()) call { it.showNavigation() } }
    override fun queryHistory() { if (connected()) call { it.queryHistory() } }

    fun expireQuery() {
        if (active && state.connection == NavigationConnection.CONNECTING) {
            active = false
            generation++
            try { client?.close() } catch (_: Exception) { state = state.copy(cleanupErrors = state.cleanupErrors + 1) }
            client = null
            state = state.copy(connection = NavigationConnection.FAILED, error = NavigationError("INITIALIZE_TIMEOUT"), listenerCount = 0)
            record("INITIALIZE_TIMEOUT")
            observer = null
            return
        }
        if (active && pending != Pending.NONE) {
            pending = Pending.NONE
            mutationsLocked = true // No SDK correlation IDs: do not trust delayed replies for another mutation.
            fail("QUERY_TIMEOUT")
        }
    }

    override fun stop() {
        if (!active) return
        active = false
        generation++
        pending = Pending.NONE
        try { client?.close() } catch (_: Exception) { state = state.copy(cleanupErrors = state.cleanupErrors + 1) }
        client = null
        state = state.copy(connection = NavigationConnection.DISCONNECTED, listenerCount = 0, ownsTestRequest = false)
        record("RELEASED_NO_ROUTE_CANCEL")
        observer = null
    }

    private fun receive(event: NavigationEvent) {
        when (event) {
            is NavigationEvent.Snapshot -> {
                state = state.copy(routeActive = event.active, routeBusy = event.busy, termsAgreed = event.terms, sdkMode = event.mode)
                val intent = pending
                pending = Pending.NONE
                when (intent) {
                    Pending.REQUEST -> {
                        if (event.active || event.busy || !event.terms) { fail("PRECHECK_BLOCKED"); return }
                        state = state.copy(ownsTestRequest = true, destinationMatchesTest = null, stage = NavigationStage.REQUEST_ACCEPTED)
                        record("REQUEST_API_CALL") // This is NOT a routing-completed acknowledgment.
                        if (!call { it.requestPublicTestRoute() }) {
                            state = state.copy(ownsTestRequest = false, stage = NavigationStage.FAILED)
                            record("REQUEST_REJECTED")
                            return
                        }
                        record("REQUEST_API_RETURNED")
                    }
                    Pending.CANCEL_ROUTE -> {
                        if (!state.ownsTestRequest || state.destinationMatchesTest != true) { fail("OWNERSHIP_LOST"); return }
                        if (!event.active && !event.busy) { state = state.copy(ownsTestRequest = false); record("NO_ROUTE_TO_CANCEL"); return }
                        state = state.copy(stage = NavigationStage.CANCEL_REQUESTED)
                        record("CANCEL_API_CALL")
                        call { it.cancelRoute() }
                    }
                    Pending.OBSERVE -> {
                        if (event.active) call { it.queryDestination(); it.queryTurns() }
                        else if (!event.busy && !state.ownsTestRequest) state = state.copy(stage = NavigationStage.IDLE)
                    }
                    else -> if (!event.active && !event.busy && !state.ownsTestRequest) state = state.copy(stage = NavigationStage.IDLE)
                }
                record("ROUTE_STATE")
            }
            is NavigationEvent.Destination -> {
                state = state.copy(destinationMatchesTest = event.matchesTest,
                    remainDistanceRaw = event.distance?.takeIf { it >= 0 }, remainTimeRaw = event.time?.takeIf { it >= 0 })
                if (!event.matchesTest) state = state.copy(ownsTestRequest = false)
                if (pending == Pending.CANCEL_DESTINATION) {
                    if (!event.matchesTest || !state.ownsTestRequest) { pending = Pending.NONE; fail("OWNERSHIP_LOST"); return }
                    pending = Pending.CANCEL_ROUTE
                    call { it.queryRoute() }
                }
                record("DESTINATION_INFO_REDACTED")
            }
            is NavigationEvent.Status -> { state = state.copy(routeActive = event.active, sdkMode = event.mode); record("NAVIGATION_STATUS_${event.mode}") }
            is NavigationEvent.Turns -> { state = state.copy(turnCount = event.count); record("TBT_INFO_REDACTED") }
            is NavigationEvent.History -> { state = state.copy(historyContainsTest = event.containsTest); record("RECENT_DESTINATIONS_REDACTED") }
            is NavigationEvent.Failure -> { pending = Pending.NONE; state = state.copy(error = event.error, stage = NavigationStage.FAILED); record("SDK_ERROR_${event.error.code}") }
            NavigationEvent.DestinationChanged -> {
                // Never let a route snapshot authorize cancellation using an older destination.
                if (pending == Pending.CANCEL_DESTINATION || pending == Pending.CANCEL_ROUTE || pending == Pending.REQUEST) {
                    pending = Pending.NONE
                    state = state.copy(error = NavigationError("DESTINATION_CHANGED_RETRY"))
                }
                state = state.copy(destinationMatchesTest = null)
                record("DESTINATION_CHANGED")
                call { it.queryDestination() }
            }
            NavigationEvent.SearchStarted -> { state = state.copy(stage = NavigationStage.SEARCHING); record("ROUTE_STARTED") }
            NavigationEvent.SearchCompleted -> {
                state = state.copy(stage = NavigationStage.SEARCH_COMPLETED)
                record("ROUTING_COMPLETED")
                call { it.queryDestination(); it.queryTurns() }
            }
            NavigationEvent.Cancelled -> {
                state = state.copy(stage = NavigationStage.CANCELLED, ownsTestRequest = false, routeActive = false, routeBusy = false)
                pending = Pending.NONE
                record("ROUTE_CANCELLED")
            }
            NavigationEvent.Arrived -> { state = state.copy(stage = NavigationStage.ARRIVED, ownsTestRequest = false); record("DESTINATION_ARRIVED") }
        }
    }

    private fun call(action: (NavigationClient) -> Unit): Boolean {
        val current = client ?: return false
        return try { action(current); true } catch (error: Exception) {
            pending = Pending.NONE
            state = state.copy(error = failure(error))
            record("API_FAILED")
            false
        }
    }
    private fun connected(): Boolean {
        if (active && state.connection == NavigationConnection.CONNECTED) return true
        fail("NOT_CONNECTED"); return false
    }
    private fun failure(error: Throwable) = NavigationError(if (error is SecurityException) "PERMISSION_DENIED" else "SDK_CALL_FAILED")
    private fun fail(code: String) { state = state.copy(error = NavigationError(code)); record(code) }
    private fun guarded(token: Int, action: () -> Unit) = dispatch { if (active && generation == token) action() }
    private fun record(name: String) { state = state.copy(lastEvent = name, recentEvents = (state.recentEvents + name).takeLast(10)); publish() }
    private fun publish() { observer?.invoke(state) }
}
