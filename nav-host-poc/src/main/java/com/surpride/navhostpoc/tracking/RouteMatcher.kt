package com.surpride.navhostpoc.tracking

import com.surpride.navhostpoc.guidance.*
import kotlin.math.*

/** Experimental uncertainty/continuity policy, not a certified driving or lane matching threshold. */
data class MatcherConfig(val maxAccuracyMeters:Double=50.0,val maxAgeMillis:Long=5000,val minCorridorMeters:Double=15.0,
    val maxCorridorMeters:Double=60.0,val offRouteConfirmations:Int=3,val rejoinConfirmations:Int=2,
    val maxProgressSpeedMps:Double=55.0,val jumpSlackMeters:Double=25.0,val reverseToleranceMeters:Double=15.0) {
    fun corridorMeters(accuracy:Double)=(accuracy*2).coerceIn(minCorridorMeters,maxCorridorMeters)
}
enum class MatchStatus { UNKNOWN, ON_ROUTE, OFF_ROUTE_CANDIDATE, OFF_ROUTE, REJOINING, REVERSED, JUMP_REJECTED, AMBIGUOUS }
data class MatchState(val status:MatchStatus,val reason:String,val source:PositionSource?,val progress:RouteProgress?=null,
    val nextManeuver:Maneuver?=null,val deviationMeters:Double?=null,val geometryAlongMeters:Double?=null,val accuracyMeters:Double?=null)

class RouteMatcher(private val route:GuidanceRoute,private val config:MatcherConfig=MatcherConfig()) {
    private data class Candidate(val along:Double,val deviation:Double,val heading:Double,val index:Int)
    private var current=MatchState(MatchStatus.UNKNOWN,"NO_DATA",null)
    private var lastFix:PositionFix?=null
    private var lastAlong:Double?=null
    private var lastObservedTime:Long?=null
    private var offCount=0
    private var rejoinCount=0
    private var offRoute=false
    private var needsAcquisition=true
    fun state()=current
    fun reset() {current=MatchState(MatchStatus.UNKNOWN,"NO_DATA",null);lastFix=null;lastAlong=null;lastObservedTime=null;offCount=0;rejoinCount=0;offRoute=false;needsAcquisition=true}
    fun unavailable(reason:String):MatchState {needsAcquisition=true;current=MatchState(MatchStatus.UNKNOWN,reason,current.source);return current}
    fun expire(now:Long):MatchState {
        if(lastObservedTime==null || now-lastObservedTime!!>config.maxAgeMillis) return unavailable("NO_FRESH_FIX")
        return current
    }
    fun accept(fix:PositionFix,now:Long):MatchState {
        fun fail(status:MatchStatus,reason:String,deviation:Double?=null):MatchState {
            current=MatchState(status,reason,fix.source,deviationMeters=deviation,accuracyMeters=fix.accuracyMeters.takeIf {it.isFinite()})
            return current
        }
        if(!fix.accuracyMeters.isFinite() || fix.accuracyMeters<=0 || fix.accuracyMeters>config.maxAccuracyMeters)
            return fail(MatchStatus.UNKNOWN,"LOW_OR_INVALID_ACCURACY")
        if(fix.elapsedMillis<0 || now-fix.elapsedMillis !in -1000..config.maxAgeMillis) return fail(MatchStatus.UNKNOWN,"STALE_OR_FUTURE")
        if(lastObservedTime!=null && fix.elapsedMillis<=lastObservedTime!!) return fail(MatchStatus.UNKNOWN,"OUT_OF_ORDER")
        if(lastObservedTime!=null && fix.elapsedMillis-lastObservedTime!!>config.maxAgeMillis) needsAcquisition=true
        lastObservedTime=fix.elapsedMillis
        val candidates=route.geometry.points.zipWithNext().mapIndexedNotNull {index,(a,b)->
            project(fix.coordinate,a,b,route.geometry.cumulative[index],index)
        }
        val nearest=candidates.minOfOrNull {it.deviation}?:return fail(MatchStatus.UNKNOWN,"NO_GEOMETRY")
        if(nearest>config.corridorMeters(fix.accuracyMeters)) {
            rejoinCount=0;offCount++
            if(offCount>=config.offRouteConfirmations) offRoute=true
            return fail(if(offRoute) MatchStatus.OFF_ROUTE else MatchStatus.OFF_ROUTE_CANDIDATE,"OUTSIDE_UNCERTAINTY_CORRIDOR",nearest)
        }
        offCount=0
        if(offRoute && ++rejoinCount<config.rejoinConfirmations) return fail(MatchStatus.REJOINING,"NEED_CONFIRMATION",nearest)
        val acquiring=needsAcquisition || offRoute
        val bearing=fix.bearingDegrees?.takeIf {it.isFinite() && it in 0.0..360.0}
        val headingReliable=bearing!=null && fix.speedMetersPerSecond?.let {it.isFinite() && it>=2}==true &&
            fix.bearingAccuracyDegrees?.let {it.isFinite() && it in 0.0..45.0}==true
        val pool=candidates.filter {it.deviation<=nearest+min(15.0,fix.accuracyMeters) && it.deviation<=config.corridorMeters(fix.accuracyMeters)}
        fun score(c:Candidate):Double=c.deviation+
            (if(headingReliable) angle(c.heading,bearing!!)/180*40 else 0.0)+
            (if(lastAlong!=null && !acquiring) min(abs(c.along-lastAlong!!)*0.2,200.0) else 0.0)
        val ranked=pool.sortedBy(::score)
        val best=ranked.first()
        if(acquiring && ranked.drop(1).any {abs(it.along-best.along)>50 && abs(score(it)-score(best))<5})
            return fail(MatchStatus.AMBIGUOUS,"MULTIPLE_ROUTE_TRAVERSALS",nearest)
        if(headingReliable && angle(best.heading,bearing!!)>120) return fail(MatchStatus.REVERSED,"OPPOSITE_HEADING",nearest)
        if(lastAlong!=null) {
            val dt=(fix.elapsedMillis-lastFix!!.elapsedMillis)/1000.0
            if(abs(best.along-lastAlong!!)>config.maxProgressSpeedMps*dt+config.jumpSlackMeters+2*fix.accuracyMeters)
                return fail(MatchStatus.JUMP_REJECTED,"IMPLAUSIBLE_ARC_JUMP",nearest)
            if(best.along<lastAlong!!-max(config.reverseToleranceMeters,2*fix.accuracyMeters))
                return fail(MatchStatus.REVERSED,"BACKWARD_PROGRESS",nearest)
        }
        val accepted=if(lastAlong==null) best.along else if(best.along<lastAlong!!+max(3.0,fix.accuracyMeters)) lastAlong!! else best.along
        offRoute=false;rejoinCount=0;needsAcquisition=false
        lastAlong=accepted;lastFix=fix
        val traveled=(accepted/route.geometry.lengthMeters*route.distanceMeters).coerceIn(0.0,route.distanceMeters.toDouble())
        val next=route.maneuvers.firstOrNull {it.type!=100 && it.offsetMeters>traveled}
        var segmentOffset=0L
        val segment=route.segments.indexOfFirst {segmentOffset+=it.distanceMeters;traveled<segmentOffset}.let {if(it<0) route.segments.lastIndex else it}
        val remaining=route.distanceMeters-traveled
        current=MatchState(MatchStatus.ON_ROUTE,"MATCHED",fix.source,
            RouteProgress(traveled,remaining,((next?.offsetMeters?:route.distanceMeters)-traveled).coerceAtLeast(0.0),segment,ceil(route.durationSeconds*remaining/route.distanceMeters).toLong()),
            next,nearest,accepted,fix.accuracyMeters)
        return current
    }
    fun presentation():GuidancePresentation? {
        val p=current.progress?:return null
        if(current.status!=MatchStatus.ON_ROUTE) return null
        return GuidancePresentation(GuidanceStatus.RUNNING,route.source,current.source==PositionSource.FAKE,
            current.nextManeuver?.let {maneuverText(it.type)}?:"경로 끝 근처 · 도착 미판정",route.segments[p.segmentIndex].roadName,
            p.remainingToManeuverMeters,p.remainingMeters,p.remainingSeconds,
            "API 기록 비율 추정 ETA · 실시간 교통/실제 안내 아님",localRouteWindow(route,current.geometryAlongMeters!!))
    }
    private fun project(p:RouteCoordinate,a:RouteCoordinate,b:RouteCoordinate,start:Double,index:Int):Candidate? {
        val scale=cos(Math.toRadians(p.latitude))*6_371_000
        val ax=Math.toRadians(a.longitude-p.longitude)*scale;val ay=Math.toRadians(a.latitude-p.latitude)*6_371_000
        val bx=Math.toRadians(b.longitude-p.longitude)*scale;val by=Math.toRadians(b.latitude-p.latitude)*6_371_000
        val dx=bx-ax;val dy=by-ay;val size=dx*dx+dy*dy
        if(size<0.000001) return null
        val t=(-(ax*dx+ay*dy)/size).coerceIn(0.0,1.0)
        return Candidate(start+meters(a,b)*t,hypot(ax+t*dx,ay+t*dy),(Math.toDegrees(atan2(dx,dy))+360)%360,index)
    }
    private fun angle(a:Double,b:Double):Double {val d=abs(a-b)%360;return min(d,360-d)}
}
