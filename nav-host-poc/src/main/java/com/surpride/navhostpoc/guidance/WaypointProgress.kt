package com.surpride.navhostpoc.guidance

import org.json.JSONObject

/** Private route options. Coordinates must never appear in diagnostics or public extras. */
class RouteRequestOptions private constructor(val priority:String,val waypoints:List<RouteCoordinate>) {
    override fun toString()="RouteRequestOptions(priority=$priority, waypointCount=${waypoints.size})"
    companion object {
        fun parse(json:JSONObject?):RouteRequestOptions {
            if(json==null) return RouteRequestOptions("RECOMMEND",emptyList())
            require(json.keys().asSequence().toSet()==setOf("priority","waypoints"))
            val priority=json.get("priority")
            require(priority is String && priority in setOf("RECOMMEND","TIME","DISTANCE"))
            val points=json.getJSONArray("waypoints")
            require(points.length() in 0..2)
            val waypoints=(0 until points.length()).map {i->
                val point=points.getJSONObject(i)
                require(point.keys().asSequence().toSet()==setOf("longitude","latitude"))
                val x=point.get("longitude");val y=point.get("latitude")
                require(x is Number && y is Number)
                RouteCoordinate(x.toDouble(),y.toDouble())
            }
            require(waypoints.distinct().size==waypoints.size)
            return RouteRequestOptions(priority,waypoints)
        }
    }
}

data class WaypointState(val passed:Int,val remaining:Int)

/** Official guide type 1000 is a waypoint; only the final 101 is the destination.
 * Tracks accepted route progress, never geographic proximity or untrusted fixes.
 * This synthetic contract does not authorize LIVE waypoint activation.
 */
class WaypointProgress(private val route:GuidanceRoute,waypointCount:Int) {
    private val offsets=route.maneuvers.filter {it.type==1000}.map {it.offsetMeters}
    private var passed=0
    init {
        require(waypointCount in 0..2 && offsets.size==waypointCount)
        require(offsets.all {it>0 && it<route.distanceMeters})
        require(offsets.zipWithNext().all {(a,b)->a<b})
        require(route.maneuvers.lastOrNull()?.let {it.type==101 && it.offsetMeters==route.distanceMeters}==true)
        require(route.maneuvers.dropLast(1).none {it.type==101})
    }
    fun state()=WaypointState(passed,offsets.size-passed)
    fun observe(progress:RouteProgress?,onRoute:Boolean) {
        val traveled=progress?.traveledMeters?:return
        if(!onRoute || !traveled.isFinite() || traveled !in 0.0..route.distanceMeters.toDouble()) return
        passed=maxOf(passed,offsets.count {traveled>=it})
    }
    fun canRefreshDirect()=state().remaining==0
    fun canConfirmFinalArrival(progress:RouteProgress?):Boolean = canRefreshDirect() &&
        progress?.remainingMeters?.let {it.isFinite() && it in 0.0..30.0}==true
    override fun toString()="WaypointProgress(passed=$passed, remaining=${offsets.size-passed})"
}
