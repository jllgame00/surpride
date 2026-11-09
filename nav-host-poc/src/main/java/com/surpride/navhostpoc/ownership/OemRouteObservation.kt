package com.surpride.navhostpoc.ownership

/** OEM route activity is independent of AAOS app focus and says nothing about phone projection. */
data class OemRouteStatus(
    val connected: Boolean = false,
    val routeActive: Boolean? = null,
    val error: String? = null
)

internal class OemRouteObservation {
    var status = OemRouteStatus(); private set
    private var observer: ((OemRouteStatus) -> Unit)? = null
    fun observe(changed: (OemRouteStatus) -> Unit) { observer = changed; changed(status) }
    fun connected() = publish(OemRouteStatus(connected = true))
    fun snapshot(guiding: Boolean, routing: Boolean, termsAgreed: Boolean) {
        if (!status.connected) return
        publish(OemRouteStatus(true, if (guiding || routing) true else if (termsAgreed) false else null))
    }
    fun active() { if (status.connected) publish(OemRouteStatus(true, true)) }
    fun invalidate() = publish(status.copy(routeActive = null))
    fun failed(error: String) = publish(OemRouteStatus(error = error))
    fun closed() = publish(OemRouteStatus())
    private fun publish(value: OemRouteStatus) { status = value; observer?.invoke(value) }
}
