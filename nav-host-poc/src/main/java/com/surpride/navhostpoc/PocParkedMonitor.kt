package com.surpride.navhostpoc

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Public read-only UX restrictions. No speed inference, focus request or vehicle controls. */
internal class PocParkedMonitor(private val context:Context) {
    private var car:Car?=null
    private var manager:CarUxRestrictionsManager?=null
    private var generation=0
    fun start(changed:(Boolean?)->Unit) {
        Log.i("SurprideDynamic","UX_MONITOR_START")
        stop();val token=++generation;changed(null)
        try {
            car=Car.createCar(context,Handler(Looper.getMainLooper()),Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) {connected,ready->
                Log.i("SurprideDynamic","UX_CONNECTION ready=$ready")
                if(token!=generation) return@createCar
                if(!ready) {changed(null);return@createCar}
                try {
                    manager=connected.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
                    manager!!.registerListener {if(token==generation) changed(it.isRequiresDistractionOptimization)}
                    changed(manager!!.currentCarUxRestrictions.isRequiresDistractionOptimization)
                } catch(e:Exception) {Log.i("SurprideDynamic","UX_UNAVAILABLE ${e.javaClass.simpleName}");changed(null)}
            }
        } catch(e:Exception) {Log.i("SurprideDynamic","UX_UNAVAILABLE ${e.javaClass.simpleName}");changed(null)} catch(e:LinkageError) {Log.i("SurprideDynamic","UX_UNAVAILABLE ${e.javaClass.simpleName}");changed(null)}
    }
    fun stop() {
        generation++
        try {manager?.unregisterListener()} catch(_:Exception) {}
        manager=null
        try {car?.disconnect()} catch(_:Exception) {}
        car=null
    }
}
