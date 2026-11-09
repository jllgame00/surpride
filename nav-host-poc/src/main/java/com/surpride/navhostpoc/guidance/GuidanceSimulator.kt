package com.surpride.navhostpoc.guidance

import kotlin.math.*

/** Deterministic internal replay. Never reads/writes vehicle position, GPS or OEM navigation. */
class GuidanceSimulator(private val route:GuidanceRoute,private val speedMetersPerSecond:Double=12.0) {
    private var position=0.0
    private var status=GuidanceStatus.READY
    init {require(speedMetersPerSecond.isFinite() && speedMetersPerSecond>0 && speedMetersPerSecond<=100)}
    fun start() {if(status in setOf(GuidanceStatus.READY,GuidanceStatus.PAUSED)) status=GuidanceStatus.RUNNING}
    fun pause() {if(status==GuidanceStatus.RUNNING) status=GuidanceStatus.PAUSED}
    fun restart() {position=0.0;status=GuidanceStatus.READY}
    fun stop() {status=GuidanceStatus.STOPPED}
    fun advance(seconds:Double) {
        require(seconds.isFinite() && seconds in 0.0..3600.0)
        if(status!=GuidanceStatus.RUNNING) return
        position=(position+seconds*speedMetersPerSecond).coerceAtMost(route.distanceMeters.toDouble())
        if(position>=route.distanceMeters) status=GuidanceStatus.ARRIVED
    }
    fun nextEvent() {
        if(status!=GuidanceStatus.RUNNING) return
        val next=if(status==GuidanceStatus.STOPPED) null else route.maneuvers.firstOrNull {it.offsetMeters>position && it.type!=100}
        position=((next?.offsetMeters?.toDouble()?:route.distanceMeters.toDouble())+0.5).coerceAtMost(route.distanceMeters.toDouble())
        if(position>=route.distanceMeters) status=GuidanceStatus.ARRIVED
    }
    fun state():NavigationGuidanceState {
        val next=if(status==GuidanceStatus.STOPPED) null else route.maneuvers.firstOrNull {it.offsetMeters>position && it.type!=100}
        var offset=0L
        val segment=route.segments.indexOfFirst {offset+=it.distanceMeters;position<offset}.let {if(it<0) route.segments.lastIndex else it}
        val remaining=route.distanceMeters-position
        return NavigationGuidanceState(status,RouteProgress(position,remaining,((next?.offsetMeters?:route.distanceMeters)-position).coerceAtLeast(0.0),
            segment,ceil(route.durationSeconds*remaining/route.distanceMeters).toLong()),next,route.source)
    }
    fun presentation():GuidancePresentation {
        val state=state();val p=state.progress
        val stopped=status in setOf(GuidanceStatus.ARRIVED,GuidanceStatus.STOPPED)
        val warning=when(route.segments[p.segmentIndex].trafficState) {
            6->"통행 불가 정보 · 개발용 재생만 허용"
            1->"교통 정체";2->"교통 지체";3->"교통 서행";4->"교통 원활";0->"교통 정보 없음"
            else->"교통 정보 형식 미지원"
        }
        return GuidancePresentation(status,route.source,instruction=if(status==GuidanceStatus.ARRIVED) "모의 도착" else if(status==GuidanceStatus.STOPPED) "모의 안내 중단" else maneuverText(state.nextManeuver?.type?:101),
            road=if(stopped) "" else route.segments[p.segmentIndex].roadName,
            nextDistanceMeters=if(stopped) 0.0 else p.remainingToManeuverMeters,remainingMeters=if(status==GuidanceStatus.STOPPED) 0.0 else p.remainingMeters,remainingSeconds=if(status==GuidanceStatus.STOPPED) 0 else p.remainingSeconds,
            warning=warning,localPath=if(stopped) emptyList() else localPath())
    }
    private fun localPath():List<LocalRoutePoint> {
        val along=position/route.distanceMeters*route.geometry.lengthMeters
        return localRouteWindow(route,along)
    }
}
