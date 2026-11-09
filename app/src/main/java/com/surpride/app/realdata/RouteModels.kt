package com.surpride.app.realdata

import com.surpride.app.experience.core.*

/** WGS84; deliberately redact diagnostic string output. Never pass these objects to UI before reveal. */
data class GeoPoint(val longitude:Double,val latitude:Double) {
    init {require(longitude.isFinite() && latitude.isFinite() && longitude in -180.0..180.0 && latitude in -90.0..90.0)}
    override fun toString()="GeoPoint(REDACTED)"
}
enum class Knowledge { UNKNOWN }
data class RealPlace(val id:String,val name:String,val point:GeoPoint,val type:String,
    val categories:Set<PlaceCategory>,val moods:Map<Mood,Int>,val sourceUrl:String,
    val checkedDate:String="2026-11-09",val license:String="서울특별시 · 공공누리 1유형",
    val parking:Knowledge=Knowledge.UNKNOWN,val hours:Knowledge=Knowledge.UNKNOWN,
    val accessibility:Knowledge=Knowledge.UNKNOWN) {
    override fun toString()="RealPlace(REDACTED)"
}
enum class RouteOrigin { LIVE_API, MOCK }
fun DrivePreferences.withAvailableBudget(minutes:Int)=copy(availableMinutes=minutes,returnByEpochMillis=null)
enum class RouteError { NOT_CONFIGURED, AUTH, RATE_LIMIT, NETWORK, INVALID_COORDINATE, NO_ROUTE, API, INVALID_RESPONSE, TIME_EXCEEDED, NO_CANDIDATE, STALE_DATA }
data class RouteLeg(val seconds:Long,val meters:Long,val geometry:List<GeoPoint>,val origin:RouteOrigin,val receivedAtMillis:Long) {
    init {require(seconds in 1..604800 && meters in 0..100_000_000)}
}
sealed interface RouteReply {
    data class Success(val leg:RouteLeg):RouteReply
    data class Failure(val error:RouteError):RouteReply
}
/** Synchronous contract: call blocking implementations on a worker, never the UI thread. */
fun interface RouteProvider {fun route(origin:GeoPoint,destination:GeoPoint):RouteReply}
class UnconfiguredRouteProvider:RouteProvider {
    override fun route(origin:GeoPoint,destination:GeoPoint)=RouteReply.Failure(RouteError.NOT_CONFIGURED)
}
data class PublicRoutePresentation(val mode:RouteOrigin,val totalMinutes:Int,val distanceMeters:Long,val destination:RealPlace?)
sealed interface RoutePlan {
    class Success internal constructor(val place:RealPlace,val outbound:RouteLeg,val inbound:RouteLeg?,
        val totalSeconds:Long,val scoring:RecommendationResult.Success):RoutePlan {
        val routeOrigin get()=outbound.origin
        fun presentation(reveal:Boolean)=PublicRoutePresentation(routeOrigin,((totalSeconds+59)/60).toInt(),
            outbound.meters+(inbound?.meters?:0),if(reveal) place else null)
        override fun toString()="RoutePlan.Success(destination=REDACTED, origin=$routeOrigin)"
    }
    data class Failure(val errors:Set<RouteError>):RoutePlan
}
