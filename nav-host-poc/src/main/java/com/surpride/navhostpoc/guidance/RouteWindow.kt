package com.surpride.navhostpoc.guidance

import kotlin.math.*

/** Public relative schematic only; no endpoint, absolute coordinate or final destination region. */
fun localRouteWindow(route:GuidanceRoute,along:Double):List<LocalRoutePoint> {
    val end=min(along+300,route.geometry.lengthMeters-200.0)
    if(along>=end) return emptyList()
    val center=route.geometry.atFraction(along/route.geometry.lengthMeters)
    val samples=mutableListOf<RouteCoordinate>();var m=along
    while(m<end) {samples.add(route.geometry.atFraction(m/route.geometry.lengthMeters));m+=10}
    samples.add(route.geometry.atFraction(end/route.geometry.lengthMeters))
    return samples.takeWhile {route.outsideDestinationArea(it)}.map {p->LocalRoutePoint(
        Math.toRadians(p.longitude-center.longitude)*6_371_000*cos(Math.toRadians(center.latitude)),Math.toRadians(p.latitude-center.latitude)*6_371_000)}
}
