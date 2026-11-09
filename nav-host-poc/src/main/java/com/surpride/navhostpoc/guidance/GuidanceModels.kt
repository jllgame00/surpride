package com.surpride.navhostpoc.guidance

import kotlin.math.*

data class RouteCoordinate(val longitude:Double,val latitude:Double) {
    init {require(longitude.isFinite() && latitude.isFinite() && longitude in -180.0..180.0 && latitude in -90.0..90.0)}
    override fun toString()="RouteCoordinate(REDACTED)"
}
fun meters(a:RouteCoordinate,b:RouteCoordinate):Double {
    val p1=Math.toRadians(a.latitude);val p2=Math.toRadians(b.latitude)
    val h=sin((p2-p1)/2).pow(2)+cos(p1)*cos(p2)*sin(Math.toRadians(b.longitude-a.longitude)/2).pow(2)
    return 6_371_000*2*asin(sqrt(h.coerceIn(0.0,1.0)))
}
class RouteGeometry(input:List<RouteCoordinate>) {
    val points=input.fold(mutableListOf<RouteCoordinate>()) {result,p->if(result.lastOrNull()!=p) result.add(p);result}.toList()
    val cumulative:List<Double>
    val lengthMeters:Double
    init {
        require(points.size in 2..100_000)
        var total=0.0
        cumulative=points.mapIndexed {i,p->if(i>0) total+=meters(points[i-1],p);total}
        lengthMeters=total;require(total.isFinite() && total>0)
    }
    fun atFraction(value:Double):RouteCoordinate {
        val target=value.coerceIn(0.0,1.0)*lengthMeters
        val next=cumulative.indexOfFirst {it>=target}.coerceAtLeast(1)
        val fraction=(target-cumulative[next-1])/(cumulative[next]-cumulative[next-1])
        val a=points[next-1];val b=points[next]
        return RouteCoordinate(a.longitude+(b.longitude-a.longitude)*fraction,a.latitude+(b.latitude-a.latitude)*fraction)
    }
    override fun toString()="RouteGeometry(points=${points.size}, coordinates=REDACTED)"
}
data class RouteSegment(val roadName:String,val distanceMeters:Long,val durationSeconds:Long,val trafficState:Int,val geometry:List<RouteCoordinate>)
data class Maneuver(val type:Int,val offsetMeters:Long,val roadIndex:Int)
enum class RouteDataSource { KAKAO_DIRECTIONS, MOCK }
enum class GuidanceStatus { READY, RUNNING, PAUSED, ARRIVED, STOPPED }
class GuidanceRoute(val source:RouteDataSource,val geometry:RouteGeometry,val segments:List<RouteSegment>,val maneuvers:List<Maneuver>,
    val distanceMeters:Long,val durationSeconds:Long,private val destination:RouteCoordinate) {
    internal fun distanceToRouteEnd(point:RouteCoordinate)=meters(point,geometry.points.last())
    internal fun outsideDestinationArea(point:RouteCoordinate)=meters(point,destination)>=200 && meters(point,geometry.points.last())>=200
    override fun toString()="GuidanceRoute(source=$source, destination=REDACTED)"
}
data class RouteProgress(val traveledMeters:Double,val remainingMeters:Double,val remainingToManeuverMeters:Double,
    val segmentIndex:Int,val remainingSeconds:Long)
data class NavigationGuidanceState(val status:GuidanceStatus,val progress:RouteProgress,val nextManeuver:Maneuver?,val source:RouteDataSource)
data class LocalRoutePoint(val eastMeters:Double,val northMeters:Double)
data class GuidancePresentation(val status:GuidanceStatus,val source:RouteDataSource,val simulatedPosition:Boolean=true,
    val instruction:String,val road:String,val nextDistanceMeters:Double,val remainingMeters:Double,val remainingSeconds:Long,
    val warning:String,val localPath:List<LocalRoutePoint>)

fun maneuverText(type:Int):String = when(type) {
    0->"직진";1->"좌회전";2->"우회전";3->"유턴";5->"왼쪽 방향";6->"오른쪽 방향"
    7,42->"출구";8,43->"왼쪽 출구";9,44->"오른쪽 출구";10,45->"진입";11,46,48->"왼쪽 진입";12,47,49->"오른쪽 진입"
    14->"고가 도로 진입";15->"지하 차도 진입";16->"고가 옆길";17->"지하 차도 옆길"
    in 18..29->"${mapOf(18 to 1,19 to 2,20 to 3,21 to 4,22 to 5,23 to 6,24 to 7,25 to 8,26 to 9,27 to 10,28 to 11,29 to 12)[type]}시 방향"
    in 30..41->"로터리 ${type-29}시 방향";in 70..81->"회전 교차로 ${type-69}시 방향"
    61->"페리 진입";62->"페리 진출";82->"왼쪽 직진";83->"오른쪽 직진"
    84->"톨게이트 진입";85->"원톨링 진입";86->"분기 후 합류";100->"출발";101->"모의 도착";1000->"경유";300->"톨게이트";301->"휴게소"
    else->"안내 형식 미지원 ($type)"
}
