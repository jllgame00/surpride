package com.surpride.app.vehicle

/** Test-only source. Never packaged into the app or used as a live-data fallback. */
class FakeVehicleDataSource : VehicleDataSource {
    override var state = VehicleState(origin = DataOrigin.FAKE)
        private set
    private var observer: ((VehicleState) -> Unit)? = null
    override fun start(observer: (VehicleState) -> Unit) { this.observer = observer; observer(state) }
    override fun refresh() { observer?.invoke(state) }
    override fun stop() { observer = null }
    fun emit(value: VehicleState) {
        state = value.copy(origin = DataOrigin.FAKE)
        observer?.invoke(state)
    }
}
