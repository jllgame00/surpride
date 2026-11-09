package com.surpride.app.realdata

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat

enum class LocationOrigin { DEVICE, DEVELOPMENT_FIXED }
enum class LocationError { PERMISSION_DENIED, UNSUPPORTED, DISABLED, NO_FRESH_FIX }
sealed interface LocationReply {
    data class Available(val point:GeoPoint,val origin:LocationOrigin):LocationReply
    data class Unavailable(val error:LocationError):LocationReply
}
fun interface LocationProvider {fun current():LocationReply}
class DevelopmentLocationProvider:LocationProvider {
    override fun current()=LocationReply.Available(SeoulPilotCatalog.developmentOrigin,LocationOrigin.DEVELOPMENT_FIXED)
}
/** Read-only foreground snapshot. No listeners, prompts, logging, storage or automatic fallback.
 * Permission is requested only by the explicit parked-screen button. A snapshot is not a GPS stream.
 * Returned position is never automatically substituted for the development origin. */
class AndroidLocationProvider(private val context:Context):LocationProvider {
    override fun current():LocationReply {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)
            return LocationReply.Unavailable(LocationError.PERMISSION_DENIED)
        return try {
            val manager=context.getSystemService(LocationManager::class.java)?:return LocationReply.Unavailable(LocationError.UNSUPPORTED)
            val providers=manager.getProviders(true)
            if(providers.isEmpty()) return LocationReply.Unavailable(LocationError.DISABLED)
            val location=providers.mapNotNull {provider->try {manager.getLastKnownLocation(provider)} catch(_:SecurityException) {null}}.filter {
                val age=SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos
                age in 0..60_000_000_000 && it.hasAccuracy() && it.accuracy in 0f..100f
            }.maxByOrNull {it.elapsedRealtimeNanos}?:return LocationReply.Unavailable(LocationError.NO_FRESH_FIX)
            LocationReply.Available(GeoPoint(location.longitude,location.latitude),LocationOrigin.DEVICE)
        } catch(_:SecurityException) {LocationReply.Unavailable(LocationError.PERMISSION_DENIED)}
          catch(_:Exception) {LocationReply.Unavailable(LocationError.UNSUPPORTED)}
    }
}
