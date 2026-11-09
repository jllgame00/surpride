package com.surpride.navhostpoc.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.surpride.navhostpoc.guidance.RouteCoordinate

/** Foreground GPS subscription only. No mocks, writes, background services, persistence or logging. */
class AndroidPositionProvider(private val context:Context):PositionProvider {
    override fun subscribe(callback:(PositionEvent)->Unit):PositionSubscription {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
            callback(PositionEvent.Unavailable(if(coarse) PositionFailure.PRECISE_REQUIRED else PositionFailure.PERMISSION_DENIED))
            return PositionSubscription {}
        }
        val manager=context.getSystemService(LocationManager::class.java)
        if(manager==null || !manager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
            callback(PositionEvent.Unavailable(PositionFailure.NO_PROVIDER));return PositionSubscription {}
        }
        var active=true
        fun emit(location:Location) {
            if(!active) return
            try {
                val fix=PositionFix(RouteCoordinate(location.longitude,location.latitude),if(location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
                    location.elapsedRealtimeNanos/1_000_000,location.time,
                    location.speed.toDouble().takeIf {location.hasSpeed()},location.bearing.toDouble().takeIf {location.hasBearing()},
                    location.bearingAccuracyDegrees.toDouble().takeIf {location.hasBearingAccuracy()},PositionSource.DEVICE_API,location.isFromMockProvider)
                callback(PositionEvent.Fix(fix))
            } catch(_:Exception) {callback(PositionEvent.Unavailable(PositionFailure.INVALID_FIX))}
        }
        val listener=object:LocationListener {
            override fun onLocationChanged(location:Location)=emit(location)
            override fun onProviderDisabled(provider:String) {if(active) callback(PositionEvent.Unavailable(PositionFailure.DISABLED))}
            override fun onProviderEnabled(provider:String) {if(active) callback(PositionEvent.Unavailable(PositionFailure.NO_DATA))}
            @Deprecated("Legacy callback") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?) {}
        }
        try {
            if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) callback(PositionEvent.Unavailable(PositionFailure.DISABLED))
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,0f,listener,Looper.getMainLooper())
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(::emit)
        } catch(_:SecurityException) {active=false;try {manager.removeUpdates(listener)} catch(_:Exception) {};callback(PositionEvent.Unavailable(PositionFailure.PERMISSION_DENIED))}
          catch(_:Exception) {active=false;try {manager.removeUpdates(listener)} catch(_:Exception) {};callback(PositionEvent.Unavailable(PositionFailure.PROVIDER_ERROR))}
        return PositionSubscription {active=false;manager.removeUpdates(listener)}
    }
}
