package com.surpride.app.vehicle

interface VehicleDataSource {
    val state: VehicleState
    fun start(observer: (VehicleState) -> Unit)
    fun refresh()
    fun stop()
}

/** Only the read/capability/subscription boundary is exposed; no control operation. */
internal interface VehicleReadClient {
    fun initialize()
    fun check(metric: VehicleMetric, callback: (CapabilityResult) -> Unit)
    fun read(metric: VehicleMetric, callback: (ReadResult) -> Unit)
    fun subscribe(metric: VehicleMetric, callback: (ReadResult) -> Unit): () -> Unit
    fun release()
}

/** Serialized state machine; Android and SDK callbacks are dispatched by the adapter. */
internal class VehicleSession(
    private val createClient: () -> VehicleReadClient,
    private val clock: () -> Long,
    private val dispatch: (() -> Unit) -> Unit
) : VehicleDataSource {
    override var state = VehicleState()
        private set
    private var client: VehicleReadClient? = null
    private var observer: ((VehicleState) -> Unit)? = null
    private var active = false
    private var generation = 0
    private val cancellations = mutableMapOf<VehicleMetric, () -> Unit>()
    private val subscriptionTokens = mutableMapOf<VehicleMetric, Any>()
    private val queryRevisions = mutableMapOf<VehicleMetric, Int>()
    private val valueRevisions = mutableMapOf<VehicleMetric, Int>()

    override fun start(observer: (VehicleState) -> Unit) {
        if (active) stop()
        this.observer = observer
        active = true
        generation++
        state = VehicleState()
        try {
            client = createClient()
            client!!.initialize()
            publish(state.copy(connection = ConnectionStatus.INITIALIZED))
            refresh()
        } catch (e: Exception) {
            val status = if (e is SecurityException) ReadingStatus.PERMISSION_DENIED else ReadingStatus.ERROR
            publish(state.copy(connection = ConnectionStatus.FAILED, readings = state.readings.mapValues { (metric, reading) ->
                if (metric.isPublicSdkRead) reading.copy(status = status, detail = "초기화 실패: ${e.javaClass.simpleName}") else reading
            }))
        }
    }

    override fun refresh() {
        val currentClient = client ?: return
        if (!active) return
        val token = generation
        VehicleMetric.entries.filter { it.isPublicSdkRead }.forEach { metric ->
            val revision = (queryRevisions[metric] ?: 0) + 1
            queryRevisions[metric] = revision
            update(metric, VehicleReading())
            try {
                currentClient.check(metric) { capability -> guarded(token) {
                    if (queryRevisions[metric] != revision) return@guarded
                    when {
                        capability.failure != null -> {
                            removeSubscription(metric)
                            update(metric, VehicleReading(capability.failure.status, capability = "조회 실패", detail = capability.failure.detail))
                        }
                        !capability.readable -> {
                            removeSubscription(metric)
                            responded()
                            update(metric, VehicleReading(ReadingStatus.UNSUPPORTED, capability = capability.access, detail = "읽기 지원 없음"))
                        }
                        else -> {
                            responded()
                            update(metric, state.readings.getValue(metric).copy(capability = capability.access))
                            try {
                                val valueRevision = valueRevisions[metric] ?: 0
                                currentClient.read(metric) { result -> guarded(token) {
                                    if (queryRevisions[metric] == revision &&
                                        (result.failure != null || (valueRevisions[metric] ?: 0) == valueRevision)) {
                                        accept(metric, result, false)
                                    }
                                } }
                                if (metric !in cancellations && queryRevisions[metric] == revision) {
                                    val subscriptionToken = Any()
                                    subscriptionTokens[metric] = subscriptionToken
                                    try {
                                        val cancel = currentClient.subscribe(metric) { result -> guarded(token) {
                                            if (subscriptionTokens[metric] === subscriptionToken) {
                                                valueRevisions[metric] = (valueRevisions[metric] ?: 0) + 1
                                                accept(metric, result, true)
                                            }
                                        } }
                                        if (active && generation == token && subscriptionTokens[metric] === subscriptionToken) {
                                            cancellations[metric] = cancel
                                        } else {
                                            // Registration itself may synchronously report a denial or trigger stop.
                                            try { cancel() } catch (_: Exception) { state = state.copy(cleanupErrors = state.cleanupErrors + 1) }
                                        }
                                    } catch (e: Exception) {
                                        subscriptionTokens.remove(metric)
                                        throw e
                                    }
                                    publish(state.copy(subscriptions = cancellations.size))
                                }
                            } catch (e: Exception) { accept(metric, ReadResult(null, vehicleFailure(e)), false) }
                        }
                    }
                } }
            } catch (e: Exception) { accept(metric, ReadResult(null, vehicleFailure(e)), false) }
        }
    }

    fun expirePending() {
        if (!active) return
        publish(state.copy(readings = state.readings.mapValues { (metric, reading) ->
            if (reading.status == ReadingStatus.PENDING) {
                // Close this request deadline; late capability/read callbacks cannot reopen PENDING.
                queryRevisions[metric] = (queryRevisions[metric] ?: 0) + 1
                reading.copy(status = ReadingStatus.TIMEOUT, detail = "10초 동안 응답 없음")
            } else reading
        }))
    }

    override fun stop() {
        if (!active) return
        active = false
        generation++
        cancellations.keys.toList().forEach(::removeSubscription)
        try { client?.release() } catch (_: Exception) { state = state.copy(cleanupErrors = state.cleanupErrors + 1) }
        client = null
        publish(state.copy(connection = ConnectionStatus.STOPPED, subscriptions = 0))
        observer = null
    }

    private fun removeSubscription(metric: VehicleMetric) {
        subscriptionTokens.remove(metric)
        val cancel = cancellations.remove(metric) ?: return
        try { cancel() } catch (_: Exception) { state = state.copy(cleanupErrors = state.cleanupErrors + 1) }
        state = state.copy(subscriptions = cancellations.size)
    }

    private fun guarded(token: Int, action: () -> Unit) = dispatch {
        if (active && generation == token) action()
    }

    private fun responded() { state = state.copy(connection = ConnectionStatus.RESPONDING, lastResponseMillis = clock()) }
    private fun accept(metric: VehicleMetric, result: ReadResult, liveUpdate: Boolean) {
        val value = result.value
        val valid = value != null && value.isFinite() && value >= 0 &&
            (metric == VehicleMetric.ODOMETER || value <= 100)
        val failure = result.failure
        if (failure?.status == ReadingStatus.PERMISSION_DENIED || failure?.status == ReadingStatus.UNSUPPORTED) {
            removeSubscription(metric)
            queryRevisions[metric] = (queryRevisions[metric] ?: 0) + 1
        }
        val status = failure?.status ?: when { value == null -> ReadingStatus.NO_DATA; valid -> ReadingStatus.AVAILABLE; else -> ReadingStatus.ERROR }
        if (failure == null) responded()
        if (liveUpdate) state = state.copy(receivedUpdates = state.receivedUpdates + 1)
        update(metric, VehicleReading(status, if (valid && failure == null) value else null,
            state.readings.getValue(metric).capability,
            failure?.detail ?: if (value != null && !valid) "유효 범위 밖 값" else "", clock()))
    }
    private fun update(metric: VehicleMetric, reading: VehicleReading) = publish(state.copy(readings = state.readings + (metric to reading)))
    private fun publish(next: VehicleState) { state = next; observer?.invoke(next) }
}
