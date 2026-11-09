package com.surpride.navhostpoc.ownership

data class CarFocusStatus(
    val connected: Boolean = false,
    val otherNavigationActive: Boolean? = null,
    val error: String? = null
)

/** Native calls remain at this boundary; every callback identifies one ownership lease. */
internal interface FocusDriver {
    fun listen(changed: (Boolean) -> Unit)
    fun request(callback: FocusLeaseCallback): Boolean
    fun owns(callback: FocusLeaseCallback): Boolean
    fun abandon(callback: FocusLeaseCallback)
    fun close()
}

internal interface FocusLeaseCallback {
    fun granted()
    fun lost()
}

/** Main-thread lease bookkeeping, independent from OEM route snapshots. */
internal class FocusLeaseSession : FocusPort {
    var status = CarFocusStatus(); private set
    private var observer: ((CarFocusStatus) -> Unit)? = null
    private var driver: FocusDriver? = null
    private val leases = linkedMapOf<Long, Lease>()

    private inner class Lease(
        val id: Long,
        val owner: FocusDriver,
        val onGranted: () -> Unit,
        val onLost: () -> Unit
    ) : FocusLeaseCallback {
        var valid = true
        var notifiedGrant = false
        var notifiedLoss = false
        override fun granted() {
            // A late native grant must only release the callback that produced it.
            if (!valid || owner !== driver || leases[id] !== this) {
                try { owner.abandon(this) } catch (_: Exception) { fail("STALE_FOCUS_CLEANUP_FAILED") }
                return
            }
            try {
                if (!owner.owns(this)) { fail("FOCUS_GRANT_NOT_OWNED"); return }
                publish(CarFocusStatus(connected = true, otherNavigationActive = false))
                if (valid && !notifiedGrant) { notifiedGrant = true; onGranted() }
            } catch (_: Exception) { fail("FOCUS_OWNERSHIP_CHECK_FAILED") }
        }
        override fun lost() {
            if (!valid || owner !== driver || leases[id] !== this) return
            valid = false
            // Loss does not establish who acquired navigation focus.
            publish(status.copy(otherNavigationActive = null))
            notifyLost()
        }
        fun notifyLost() {
            if (!notifiedLoss) { notifiedLoss = true; onLost() }
        }
    }

    fun observe(changed: (CarFocusStatus) -> Unit) { observer = changed; changed(status) }

    fun connected(target: FocusDriver) {
        if (driver != null) disconnected("CAR_RECONNECTED")
        driver = target
        // Public CarAppFocusManager has no initial active-focus snapshot API.
        publish(CarFocusStatus(connected = true))
        if (driver !== target) return
        try {
            target.listen { active ->
                if (driver !== target || !status.connected) return@listen
                try {
                    val live = leases.values.filter { it.valid && it.owner === target }
                    val own = live.any { target.owns(it) }
                    if (!own) live.forEach { it.lost() }
                    // Keep the actually observed global state after loss callbacks have run.
                    if (driver === target && status.connected) publish(CarFocusStatus(true, active && !own))
                } catch (_: Exception) { fail("FOCUS_OBSERVATION_FAILED") }
            }
            if (driver !== target) target.close()
        } catch (_: Exception) { fail("FOCUS_LISTENER_FAILED") }
    }

    override fun request(id: Long, granted: () -> Unit, lost: () -> Unit): Boolean {
        val target = driver ?: return false
        if (!status.connected || status.error != null || status.otherNavigationActive == true ||
            leases.containsKey(id) || leases.values.any { it.valid }) return false
        val lease = Lease(id, target, granted, lost)
        leases[id] = lease
        return try {
            val accepted = target.request(lease)
            if (!accepted) { abandon(id); false } else lease.valid && driver === target
        } catch (_: Exception) { fail("FOCUS_REQUEST_FAILED"); false }
    }

    override fun abandon(id: Long) {
        val lease = leases[id] ?: return
        lease.valid = false
        // Keep its callback identity until service disposal, so a late grant can be abandoned again.
        lease.owner.abandon(lease)
        if (driver === lease.owner && status.error == null) publish(status.copy(otherNavigationActive = null))
    }

    fun disconnected(error: String? = null) {
        val target = driver
        driver = null
        val live = leases.values.filter { it.owner === target && it.valid }
        live.forEach { it.valid = false }
        var cleanupFailed = false
        for (lease in leases.values.filter { it.owner === target }) {
            try { lease.owner.abandon(lease) } catch (_: Exception) { cleanupFailed = true }
        }
        try { target?.close() } catch (_: Exception) { cleanupFailed = true }
        publish(CarFocusStatus(error = if (cleanupFailed) "FOCUS_CLEANUP_FAILED" else error))
        live.forEach { it.notifyLost() }
    }

    private fun fail(error: String) = disconnected(error)
    private fun publish(value: CarFocusStatus) { status = value; observer?.invoke(value) }
}
