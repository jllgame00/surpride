package com.surpride.navhostpoc.guidance

import com.surpride.navhostpoc.map.FollowCamera
import com.surpride.navhostpoc.tracking.*
import com.surpride.navhostpoc.voice.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GuidanceAccuracyTest {
    private val now=1_800_000_000_000L
    private fun point(x:Double,y:Double=0.0)=GuidanceAccuracyFixture.point(x,y)
    private fun fix(x:Double,y:Double=0.0)=PositionFix(point(x,y),1.0,100_000,now,0.0,null,null,PositionSource.FAKE,false)
    private fun payload()=GuidanceAccuracyFixture.payload(now)
    private fun route(body:JSONObject=payload())=GuidanceParser.parse(body.toString(),now)

    @Test fun eachRoadUsesItsOwnApiDistanceInsteadOfWholeRouteRatio() {
        val state=RouteMatcher(route()).accept(fix(50.0),100_000)
        assertEquals(MatchStatus.ON_ROUTE,state.status)
        assertEquals(250.0,state.progress!!.traveledMeters,0.1)
        assertEquals(250.0,state.progress!!.remainingToManeuverMeters,0.1)
        assertEquals(0,state.progress!!.segmentIndex)
    }
    @Test fun roadBoundaryKeepsTheTurnAtZeroDistanceAndNamesTheOutgoingRoad() {
        val matcher=RouteMatcher(route())
        val state=matcher.accept(fix(100.0),100_000)
        assertEquals(500.0,state.progress!!.traveledMeters,0.1)
        assertEquals(1,state.progress!!.segmentIndex)
        assertEquals(2,state.nextManeuver!!.type)
        assertEquals(0.0,state.progress!!.remainingToManeuverMeters,0.1)
        assertEquals("B",matcher.presentation()!!.road)
    }
    @Test fun cameraAndVoiceUseTheSamePhysicalTurnAsTheMatcher() {
        val route=route();val sample=fix(50.0);val match=RouteMatcher(route).accept(sample,100_000)
        val spoken=mutableListOf<String>()
        val speech=object:VoiceSpeechPort {
            override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents)=onReady(VoiceReadiness.READY)
            override fun speak(id:String,text:String):Boolean {spoken+=text;return true}
            override fun stop()=Unit
            override fun close()=Unit
        }
        val voice=NavigationVoiceController(speech,object:VoiceFocusPort {
            override fun request(onLoss:()->Unit)=true
            override fun abandon()=Unit
        })
        voice.start {};voice.setEnabled(true);voice.setOwnership(true)
        voice.onProgress(1,1,match.nextManeuver!!.type,match.progress!!.remainingToManeuverMeters)
        assertEquals(listOf("약 250미터 앞에서 우회전하세요."),spoken)
        assertEquals(240.0,FollowCamera().update(route,sample,match,1200,700)!!.forwardMeters,0.1)
        voice.close()
    }
    @Test fun disconnectedRoadsAreRejectedBeforeTheGapCanBecomeOnRoute() {
        val body=payload();val second=body.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1)
        second.getJSONArray("geometry").getJSONArray(0).put(0,point(500.0).longitude)
        assertThrows(IllegalArgumentException::class.java) {route(body)}
    }
    @Test fun guideOffsetCannotReferToADifferentRoadInterval() {
        val body=payload()
        body.getJSONObject("guidance").getJSONArray("maneuvers").getJSONObject(1).put("road_index",0).put("offset_meters",700)
        assertThrows(IllegalArgumentException::class.java) {route(body)}
    }
    @Test fun nonzeroGeometryWithZeroApiDistanceIsRejectedRatherThanSkippingATurn() {
        val body=payload();val data=body.getJSONObject("guidance")
        data.getJSONArray("segments").getJSONObject(0).put("distance_meters",0)
        data.getJSONArray("segments").getJSONObject(1).put("distance_meters",1000)
        data.getJSONArray("maneuvers").getJSONObject(1).put("offset_meters",0)
        assertThrows(IllegalArgumentException::class.java) {route(body)}
    }
    @Test fun verifiedGuideAnchorSplitsDistanceConversionInsideItsRoad() {
        val body=payload();val data=body.getJSONObject("guidance")
        val roads=data.getJSONArray("segments")
        roads.getJSONObject(0).put("distance_meters",1000)
            .put("geometry",JSONArray().put(JSONArray(listOf(0.0,0.0)))
                .put(JSONArray(listOf(point(100.0).longitude,0.0)))
                .put(JSONArray(listOf(point(1000.0).longitude,0.0))))
        roads.remove(1)
        val guides=data.getJSONArray("maneuvers")
        guides.getJSONObject(1).put("road_index",0).put("anchor",JSONArray(listOf(point(100.0).longitude,0.0)))
        guides.getJSONObject(2).put("road_index",0)
        val state=RouteMatcher(route(body)).accept(fix(50.0),100_000)
        assertEquals(250.0,state.progress!!.traveledMeters,0.1)
        assertEquals(250.0,state.progress!!.remainingToManeuverMeters,0.1)
    }
    @Test fun anchorOutsideTheIndexedRoadCannotStartGuidance() {
        val body=payload()
        body.getJSONObject("guidance").getJSONArray("maneuvers").getJSONObject(1)
            .put("anchor",JSONArray(listOf(point(600.0).longitude,0.0)))
        assertThrows(IllegalArgumentException::class.java) {route(body)}
    }
}
