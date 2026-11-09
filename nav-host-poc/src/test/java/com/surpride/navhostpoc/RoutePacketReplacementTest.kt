package com.surpride.navhostpoc

import androidx.car.app.testing.TestCarContext
import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.routing.RouteRefreshResult
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class RoutePacketReplacementTest {
    @Test fun legacyPacketKeepsOriginalDepartureAcrossRerouteAndReturn() {
        val now=System.currentTimeMillis()
        fun payload(origin:Double,destination:Double,label:String)="""{
          "schema_version":1,"source":"KAKAO_DIRECTIONS","measured_at_epoch_ms":$now,
          "private_destination_label":"$label","origin":{"longitude":$origin,"latitude":37.0},
          "destination":{"longitude":$destination,"latitude":37.0},"distance_meters":200,"duration_seconds":100,
          "guidance":{"segments":[{"road_name":"Road","distance_meters":200,"duration_seconds":100,"traffic_state":0,
          "geometry":[[$origin,37.0],[$destination,37.0]]}],"maneuvers":[{"type":100,"offset_meters":0,"road_index":0},
          {"type":101,"offset_meters":200,"road_index":0}]}}
        """
        fun reply(body:String)=RouteRefreshResult.Success(body,GuidanceParser.parse(body,now),now,now+3_600_000)
        val context=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        File(context.cacheDir,"route-guidance.json").writeText(payload(127.0,127.002,"Secret"))
        val original=ActiveRoutePacket.load(context)!!
        val rerouted=original.replace(context,reply(payload(127.001,127.002,"Secret")),false,now)
        assertEquals(RouteCoordinate(127.0,37.0),rerouted.tripOrigin)
        val returning=rerouted.replace(context,reply(payload(127.002,127.0,"출발지")),true,now)
        assertEquals(RouteCoordinate(127.0,37.0),returning.tripOrigin)
        assertEquals(RouteCoordinate(127.002,37.0),returning.outboundDestination)
        assertEquals("Secret",returning.identity.name)
        assertTrue(returning.returning)
        assertEquals(now,returning.startedAt)
        val cached=JSONObject(File(context.cacheDir,"route-guidance.json").readText())
        assertEquals(127.0,cached.getJSONObject("trip_origin").getDouble("longitude"),0.0)
    }
}
