package com.surpride.app.experience

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/** Official UX restrictions only. Unknown/disconnected is fail-closed, never inferred from battery. */
class ParkedUxMonitor(private val context: Context) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var generation = 0
    fun start(changed: (Boolean?) -> Unit) {
        stop()
        val token = ++generation
        changed(null)
        try {
            car = Car.createCar(context, Handler(Looper.getMainLooper()), Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { connected, ready ->
                if (token != generation) return@createCar
                if (!ready) {manager=null;changed(null);return@createCar}
                try {
                    manager=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    manager!!.registerListener { if(token==generation) changed(it.isRequiresDistractionOptimization) }
                    changed(manager!!.currentCarUxRestrictions.isRequiresDistractionOptimization)
                } catch (_: Exception) {changed(null)}
            }
        } catch (_: Exception) {changed(null)} catch (_: LinkageError) {changed(null)}
    }
    fun stop() {
        generation++
        try {manager?.unregisterListener()} catch (_: Exception) {}
        manager=null
        try {car?.disconnect()} catch (_: Exception) {}
        car=null
    }
}
