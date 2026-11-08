package com.surpride.app.vehicle

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.FuelPercentListener

/** Live Pleos service only. It never substitutes Fake readings. */
class PleosVehicleDataSource(context: Context) : VehicleDataSource {
    private val handler = Handler(Looper.getMainLooper())
    private val session = VehicleSession({ PleosReadClient(context.applicationContext) }, System::currentTimeMillis) { action ->
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }
    private val timeout = Runnable { session.expirePending() }
    override val state get() = session.state
    override fun start(observer: (VehicleState) -> Unit) { session.start(observer); armTimeout() }
    override fun refresh() { session.refresh(); armTimeout() }
    override fun stop() { handler.removeCallbacks(timeout); session.stop() }
    private fun armTimeout() { handler.removeCallbacks(timeout); handler.postDelayed(timeout, 10_000) }
}

private class PleosReadClient(private val context: Context) : VehicleReadClient {
    private val vehicle = Vehicle(context)
    override fun initialize() = vehicle.initialize()
    override fun release() = vehicle.release()

    override fun check(metric: VehicleMetric, callback: (CapabilityResult) -> Unit) {
        val permission = when (metric) {
            VehicleMetric.BATTERY, VehicleMetric.FUEL -> "pleos.car.permission.CAR_ENERGY"
            VehicleMetric.ODOMETER -> "pleos.car.permission.CAR_DRIVE"
            else -> { callback(CapabilityResult(false, "공개 API 미제공")); return }
        }
        if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            callback(CapabilityResult(false, failure = DataFailure(ReadingStatus.PERMISSION_DENIED, permission)))
            return
        }
        val complete: (CapabilityConfig) -> Unit = { config ->
            callback(CapabilityResult(config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE, config.accessType.name))
        }
        val failed: (Exception) -> Unit = { callback(CapabilityResult(false, failure = vehicleFailure(it))) }
        when (metric) {
            VehicleMetric.BATTERY -> vehicle.getEvBattery().checkEvBatteryStateOfChargeCapabilities(complete, failed)
            VehicleMetric.FUEL -> vehicle.getFuel().checkFuelPercentCapabilities(complete, failed)
            VehicleMetric.ODOMETER -> vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(complete, failed)
            else -> Unit
        }
    }

    override fun read(metric: VehicleMetric, callback: (ReadResult) -> Unit) {
        val failed: (Exception) -> Unit = { callback(ReadResult(null, vehicleFailure(it))) }
        when (metric) {
            VehicleMetric.BATTERY -> vehicle.getEvBattery().getEvBatteryStateOfCharge({ callback(ReadResult(it?.toDouble())) }, failed)
            VehicleMetric.FUEL -> vehicle.getFuel().getFuelPercent({ callback(ReadResult(it?.toDouble())) }, failed)
            VehicleMetric.ODOMETER -> vehicle.getOdometer().getCurrentDistanceDriven({ callback(ReadResult(it?.toDouble())) }, failed)
            else -> callback(ReadResult(null, DataFailure(ReadingStatus.UNSUPPORTED, "공개 API 미제공")))
        }
    }

    override fun subscribe(metric: VehicleMetric, callback: (ReadResult) -> Unit): () -> Unit = when (metric) {
        VehicleMetric.BATTERY -> {
            val listener = object : EvBatteryStateOfChargeListener {
                override fun onEvBatteryStateOfChargeUpdated(value: Int?) = callback(ReadResult(value?.toDouble()))
                override fun onFailed(e: Exception) = callback(ReadResult(null, vehicleFailure(e)))
            }
            vehicle.getEvBattery().registerEvBatteryStateOfCharge(listener)
            val cancel: () -> Unit = { vehicle.getEvBattery().unregisterEvBatteryStateOfCharge(listener) }
            cancel
        }
        VehicleMetric.FUEL -> {
            val listener = object : FuelPercentListener {
                override fun onFuelPercentUpdated(value: Int?) = callback(ReadResult(value?.toDouble()))
                override fun onFailed(e: Exception) = callback(ReadResult(null, vehicleFailure(e)))
            }
            vehicle.getFuel().registerFuelPercent(listener)
            val cancel: () -> Unit = { vehicle.getFuel().unregisterFuelPercent(listener) }
            cancel
        }
        VehicleMetric.ODOMETER -> {
            val listener = object : DistanceDrivenListener {
                override fun onDistanceDrivenUpdated(value: Float?) = callback(ReadResult(value?.toDouble()))
                override fun onFailed(e: Exception) = callback(ReadResult(null, vehicleFailure(e)))
            }
            vehicle.getOdometer().registerDistanceDriven(listener)
            val cancel: () -> Unit = { vehicle.getOdometer().unregisterDistanceDriven(listener) }
            cancel
        }
        else -> ({ })
    }

}
