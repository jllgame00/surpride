package com.surpride.navhostpoc.guidance

import com.surpride.navhostpoc.tracking.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GuidanceIntegrityTest {
    private val now=1_800_000_000_000L
    private fun payload()=GuidanceAccuracyFixture.payload(now)
    private fun parse(body:JSONObject)=GuidanceParser.parse(body.toString(),now)
    private fun fix(x:Double,y:Double=0.0,t:Long=100_000)=PositionFix(GuidanceAccuracyFixture.point(x,y),1.0,t,now,0.0,null,null,PositionSource.FAKE,false)
    @Test fun unverifiedGapsDegenerateRoadsAndCoercedCoordinatesFailClosed() {
        val cases=listOf<(JSONObject)->Unit>(
            {it.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1).getJSONArray("geometry").getJSONArray(0).put(0,0.0008993210)},
            {it.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1).put("geometry",JSONArray().put(JSONArray(listOf(0,0))))},
            {it.getJSONObject("guidance").getJSONArray("segments").getJSONObject(0).put("geometry",JSONArray("[[0,0],[0,0]]"))},
            {it.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1).getJSONArray("geometry").getJSONArray(0).put(0,"0.001")},
            {it.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1).getJSONArray("geometry").getJSONArray(0).put(0,true)},
            {it.put("distance_meters",1001)},
            {val road=it.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1);val points=road.getJSONArray("geometry");road.put("geometry",JSONArray().put(points.get(1)).put(points.get(0)))})
        cases.forEachIndexed {index,change->
            val body=payload();change(body)
            assertThrows("invalid geometry case $index",IllegalArgumentException::class.java) {parse(body)}
        }
    }
    @Test fun repeatedBoundaryPointsKeepOnlyRealEdgesAndTheirRoadIndices() {
        val body=payload();val road=body.getJSONObject("guidance").getJSONArray("segments").getJSONObject(0)
        val points=road.getJSONArray("geometry")
        road.put("geometry",JSONArray().put(points.get(0)).put(points.get(0)).put(points.get(1)).put(points.get(1)))
        val route=parse(body)
        assertEquals(3,route.geometry.points.size)
        assertEquals(listOf(0,1),route.geometryRoadIndices)
        assertEquals(250.0,RouteMatcher(route).accept(fix(50.0),100_000).progress!!.traveledMeters,0.1)
    }
    @Test fun globalCoordinateLimitCountsAllRoadsBeforeDuplicateNormalization() {
        val body=payload();val roads=body.getJSONObject("guidance").getJSONArray("segments")
        for(index in 0..1) {
            val road=roads.getJSONObject(index);val original=road.getJSONArray("geometry")
            road.put("geometry",JSONArray().apply {repeat(50_000) {put(original.get(0))};put(original.get(1))})
        }
        assertThrows(IllegalArgumentException::class.java) {parse(body)}
    }
    @Test fun consecutiveTurnsAndSameOffsetDepartureMetadataUseOrderedPhysicalAnchors() {
        val body=payload();val data=body.getJSONObject("guidance");val original=data.getJSONArray("maneuvers")
        data.put("maneuvers",JSONArray().put(original.get(0))
            .put(JSONObject("{\"type\":100,\"offset_meters\":500,\"road_index\":1}"))
            .put(original.get(1)).put(JSONObject("{\"type\":1,\"offset_meters\":510,\"road_index\":1}"))
            .put(original.get(2)))
        val matcher=RouteMatcher(parse(body))
        assertEquals(2,matcher.accept(fix(100.0),100_000).nextManeuver!!.type)
        val next=matcher.accept(fix(100.0,-9.0,101_000),101_000)
        assertEquals(1,next.nextManeuver!!.type)
        assertEquals(5.0,next.progress!!.remainingToManeuverMeters,0.1)
    }
    @Test fun contradictoryTurnsAtTheSameOffsetAreRejected() {
        val body=payload();val data=body.getJSONObject("guidance");val original=data.getJSONArray("maneuvers")
        data.put("maneuvers",JSONArray().put(original.get(0)).put(original.get(1))
            .put(JSONObject("{\"type\":1,\"offset_meters\":500,\"road_index\":1}")).put(original.get(2)))
        assertThrows(IllegalArgumentException::class.java) {parse(body)}
    }
    @Test fun correctedEndProgressStillRequiresRepeatedFixesAndExplicitParkedConfirmation() {
        val route=parse(payload());val matcher=RouteMatcher(route);val arrival=ArrivalGate()
        repeat(3) {i->
            val sample=fix(100.0,-895.0,100_000+i*1000L);val match=matcher.accept(sample,sample.elapsedMillis)
            assertEquals(2.7778,match.progress!!.remainingMeters,0.1)
            arrival.observe(sample,match,route.distanceToRouteEnd(sample.coordinate),true,sample.elapsedMillis)
        }
        assertTrue(arrival.candidate);assertFalse(arrival.arrived)
        assertFalse(arrival.confirm(false,true,102_000));assertFalse(arrival.confirm(true,null,102_000))
        assertTrue(arrival.confirm(true,true,102_000))
    }
    @Test fun veryShortRoadUsesItsOwnDistanceWithoutSkippingItsBoundary() {
        val body=payload();val data=body.getJSONObject("guidance");val roads=data.getJSONArray("segments")
        val corner=GuidanceAccuracyFixture.point(1.0)
        roads.getJSONObject(0).put("distance_meters",1).getJSONArray("geometry").getJSONArray(1).put(0,corner.longitude)
        roads.getJSONObject(1).put("distance_meters",999).getJSONArray("geometry").getJSONArray(0).put(0,corner.longitude)
        data.getJSONArray("maneuvers").getJSONObject(1).put("offset_meters",1)
        val state=RouteMatcher(parse(body)).accept(fix(0.5),100_000)
        assertEquals(0.5,state.progress!!.traveledMeters,0.01)
        assertEquals(0.5,state.progress!!.remainingToManeuverMeters,0.01)
    }
    @Test fun overlappingReturnRoadsRemainAmbiguousWithoutDirectionEvidence() {
        val body=payload();val second=body.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1)
        second.put("geometry",JSONArray().put(JSONArray(listOf(GuidanceAccuracyFixture.point(100.0).longitude,0)))
            .put(JSONArray(listOf(0,0))))
        val matcher=RouteMatcher(parse(body))
        assertEquals(MatchStatus.AMBIGUOUS,matcher.accept(fix(50.0),100_000).status)
        assertNull(matcher.presentation())
    }
}
