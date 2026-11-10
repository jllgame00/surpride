package com.surpride.navhostpoc.ownership

import com.surpride.navhostpoc.HostDiagnostics
import com.surpride.navhostpoc.HostEvent
import android.car.Car
import android.car.CarAppFocusManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import java.util.IdentityHashMap

/**
 * Official AAOS app focus only. Binding/observing never requests focus.
 * The caller must apply explicit-action, parked, OEM-route and experiment gates before request().
 * No hidden getActiveAppTypes/getAppTypeOwner APIs or reflected Binder calls.
 */
@MainThread
class AndroidCarFocusPort(context: Context) : FocusPort {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val session = FocusLeaseSession()
    private var car: Car? = null
    private var closed = false
    private var connecting = false
    val status: CarFocusStatus get() = session.status

    fun connect(changed: (CarFocusStatus) -> Unit) {
        checkMain()
        session.observe(changed)
        if (closed || connecting || car != null) return
        connecting = true
        try {
            val created = Car.createCar(appContext, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
                if (closed) return@createCar
                if (!ready) {
                    session.disconnected("CAR_DISCONNECTED")
                } else {
                    try {
                        val manager = service.getCarManager(Car.APP_FOCUS_SERVICE) as? CarAppFocusManager
                            ?: error("App focus manager unavailable")
                        session.connected(AndroidFocusDriver(manager))
                    } catch (_: Exception) { session.disconnected("CAR_FOCUS_SERVICE_UNAVAILABLE") }
                }
            }
            if (closed) created?.disconnect() else car = created
            if (created == null) session.disconnected("CAR_SERVICE_UNAVAILABLE")
        } catch (_: Exception) { session.disconnected("CAR_CONNECTION_FAILED") }
        finally { connecting = false }
    }

    override fun request(id: Long, granted: () -> Unit, lost: () -> Unit): Boolean {
        checkMain()
        if (closed) return false
        HostDiagnostics.record(HostEvent.FOCUS_REQUEST)
        return session.request(id, {HostDiagnostics.record(HostEvent.FOCUS_CALLBACK);granted()},
            {HostDiagnostics.record(HostEvent.FOCUS_CALLBACK);lost()})
    }

    override fun abandon(id: Long) { checkMain(); HostDiagnostics.record(HostEvent.FOCUS_RELEASE);session.abandon(id) }

    fun close() {
        checkMain()
        if (closed) return
        closed = true
        session.disconnected()
        try { car?.disconnect() } catch (_: Exception) { session.disconnected("CAR_DISCONNECT_FAILED") }
        finally { car = null }
    }

    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) { "Main thread required" } }
}

private class AndroidFocusDriver(private val manager: CarAppFocusManager) : FocusDriver {
    private val type = CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION
    private val callbacks = IdentityHashMap<FocusLeaseCallback, CarAppFocusManager.OnAppFocusOwnershipCallback>()
    private var listener: CarAppFocusManager.OnAppFocusChangedListener? = null

    override fun listen(changed: (Boolean) -> Unit) {
        val native = CarAppFocusManager.OnAppFocusChangedListener { appType, active ->
            if (appType == type) changed(active)
        }
        listener = native
        manager.addFocusListener(native, type)
    }

    override fun request(callback: FocusLeaseCallback): Boolean {
        val native = object : CarAppFocusManager.OnAppFocusOwnershipCallback {
            override fun onAppFocusOwnershipGranted(appType: Int) { if (appType == type) callback.granted() }
            override fun onAppFocusOwnershipLost(appType: Int) { if (appType == type) callback.lost() }
        }
        callbacks[callback] = native
        return manager.requestAppFocus(type, native) == CarAppFocusManager.APP_FOCUS_REQUEST_SUCCEEDED
    }

    override fun owns(callback: FocusLeaseCallback): Boolean =
        callbacks[callback]?.let { manager.isOwningFocus(it, type) } == true

    override fun abandon(callback: FocusLeaseCallback) {
        callbacks[callback]?.let { manager.abandonAppFocus(it, type) }
    }

    override fun close() {
        listener?.let { manager.removeFocusListener(it, type) }
        listener = null
        // Retain native identities for queued stale callbacks. No callback can refer to a new lease.
    }
}
