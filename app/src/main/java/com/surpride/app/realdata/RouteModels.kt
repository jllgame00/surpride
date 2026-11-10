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

/** Exact seconds for measured routes; DEMO's minute-based estimates keep their own contract. */
internal fun routeBudgetSeconds(p:DrivePreferences,now:Long):Long {
    if(p.availableMinutes==null && p.returnByEpochMillis==null || p.availableMinutes?.let {it<=0}==true) return 0
    val available=p.availableMinutes?.toLong()?.times(60)?:Long.MAX_VALUE
    val deadline=p.returnByEpochMillis?.let {(it-now)/1000}?:Long.MAX_VALUE
    return minOf(available,deadline).coerceAtLeast(0)
}

internal fun requestRoute(request:()->RouteReply):RouteReply {
    if(Thread.currentThread().isInterrupted) return RouteReply.Failure(RouteError.NETWORK)
    val reply=try {request()} catch(_:InterruptedException) {
        Thread.currentThread().interrupt();RouteReply.Failure(RouteError.NETWORK)
    }
    return if(Thread.currentThread().isInterrupted) RouteReply.Failure(RouteError.NETWORK) else reply
}
internal fun RouteReply.stopsRecommendation()=Thread.currentThread().isInterrupted ||
    this is RouteReply.Failure && error in setOf(RouteError.AUTH,RouteError.RATE_LIMIT,RouteError.NOT_CONFIGURED)
enum class RouteError { NOT_CONFIGURED, AUTH, RATE_LIMIT, NETWORK, INVALID_COORDINATE, NO_ROUTE, API, INVALID_RESPONSE, TIME_EXCEEDED, NO_CANDIDATE, STALE_DATA, UNSUPPORTED_OPTIONS }

enum class RoutePriority { RECOMMEND, TIME, DISTANCE }
data class RouteOptions(val priority:RoutePriority=RoutePriority.RECOMMEND,val waypoints:List<GeoPoint> = emptyList()) {
    init {require(waypoints.size<=2 && waypoints.distinct().size==waypoints.size)}
    fun validFor(origin:GeoPoint,destination:GeoPoint)=origin!=destination && waypoints.none {it==origin || it==destination}
    override fun toString()="RouteOptions(priority=$priority, waypointCount=${waypoints.size})"
}
data class RouteLeg(val seconds:Long,val meters:Long,val geometry:List<GeoPoint>,val origin:RouteOrigin,val receivedAtMillis:Long,
    val options:RouteOptions=RouteOptions(),val guidancePayload:String?=null) {
    init {require(seconds in 1..604800 && meters in 0..100_000_000)}
    override fun toString()="RouteLeg(seconds=$seconds, meters=$meters, origin=$origin, PRIVATE_GEOMETRY)"
}
sealed interface RouteReply {
    data class Success(val leg:RouteLeg):RouteReply
    data class Failure(val error:RouteError):RouteReply
}
/** Synchronous contract: call blocking implementations on a worker, never the UI thread. */
fun interface RouteProvider {
    fun route(origin:GeoPoint,destination:GeoPoint):RouteReply
    fun route(origin:GeoPoint,destination:GeoPoint,options:RouteOptions):RouteReply =
        if(options==RouteOptions()) route(origin,destination) else RouteReply.Failure(RouteError.UNSUPPORTED_OPTIONS)
}
data class PublicRoutePresentation(val mode:RouteOrigin,val totalMinutes:Int,val distanceMeters:Long,val destination:RealPlace?)
sealed interface RoutePlan {
    class Success internal constructor(val place:RealPlace,val outbound:RouteLeg,val inbound:RouteLeg?,
        val totalSeconds:Long,val scoring:RecommendationResult.Success,
        val experience:com.surpride.app.realdata.experience.ExperienceSelection?=null):RoutePlan {
        val routeOrigin get()=outbound.origin
        fun presentation(reveal:Boolean)=PublicRoutePresentation(routeOrigin,((totalSeconds+59)/60).toInt(),
            outbound.meters+(inbound?.meters?:0),if(reveal) place else null)
        override fun toString()="RoutePlan.Success(destination=REDACTED, origin=$routeOrigin)"
    }
    data class Failure(val errors:Set<RouteError>):RoutePlan
}

/** Revalidate a cached plan at explicit handoff; the selection deadline keeps elapsing. */
fun RoutePlan.Success.feasibleForHandoff(preferences:DrivePreferences,now:Long):Boolean {
    if(routeOrigin!=RouteOrigin.LIVE_API || preferences.returnToStart && inbound==null) return false
    if(listOfNotNull(outbound,inbound).any {it.origin!=RouteOrigin.LIVE_API || now-it.receivedAtMillis !in -60_000..300_000}) return false
    val available=preferences.availableMinutes?.toLong()?.times(60)?:Long.MAX_VALUE
    val deadline=preferences.returnByEpochMillis?.let {(it-now)/1000}?:Long.MAX_VALUE
    return totalSeconds>0 && totalSeconds<=minOf(available,deadline)
}
