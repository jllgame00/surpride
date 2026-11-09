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
class WaypointPacketContractTest {
    private fun payload(now:Long,via:Boolean=true)="""{
      "schema_version":1,"source":"KAKAO_DIRECTIONS","measured_at_epoch_ms":$now,
      "candidate_id":"synthetic","session_nonce":"00000000-0000-0000-0000-000000000000",
      "private_destination_label":"Secret","origin":{"longitude":127.0,"latitude":37.0},
      "destination":{"longitude":127.003,"latitude":37.0},"distance_meters":300,"duration_seconds":60,
      "request_options":{"priority":"TIME","waypoints":${if(via) "[{\"longitude\":127.001,\"latitude\":37.0}]" else "[]"}},
      "guidance":{"segments":[{"road_name":"Road","distance_meters":300,"duration_seconds":60,"traffic_state":4,
      "geometry":[[127.0,37.0],[127.001,37.0],[127.003,37.0]]}],"maneuvers":[{"type":100,"offset_meters":0,"road_index":0},
      ${if(via) "{\"type\":1000,\"offset_meters\":100,\"road_index\":0}," else ""}
      {"type":101,"offset_meters":300,"road_index":0}]}}
    """
    private fun context()=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
    private fun reply(body:String,now:Long)=RouteRefreshResult.Success(body,GuidanceParser.parse(body,now),now,now+3_600_000)

    @Test fun privateHandoffRetainsStrictOptionsAndOfficialWaypointType() {
        val context=context();val now=System.currentTimeMillis()
        val file=File(context.cacheDir,"route-guidance.json");file.writeText(payload(now))
        val packet=ActiveRoutePacket.load(context)!!
        assertEquals("TIME",packet.requestOptions.priority)
        assertEquals(listOf(RouteCoordinate(127.001,37.0)),packet.requestOptions.waypoints)
        assertFalse(packet.requestOptions.toString().contains("127.001"))
        assertEquals(1000,packet.route.maneuvers[1].type)
    }

    @Test fun invalidOptionsOrMissingWaypointEventsCannotBecomeActivePacket() {
        val context=context();val now=System.currentTimeMillis()
        val base=payload(now)
        val invalid=listOf(base.replace("\"TIME\"","\"time\""),base.replace("\"longitude\":127.001","\"longitude\":\"127.001\""),
            base.replace("\"type\":1000","\"type\":2"),base.replace("\"waypoints\":[{","\"waypoints\":null,\"extra\":[{"))
        invalid.forEach {File(context.cacheDir,"route-guidance.json").writeText(it);assertNull(ActiveRoutePacket.load(context))}
    }

    @Test fun replacementCannotSilentlyDropRemainingWaypointsOrChangePrivateSession() {
        val context=context();val now=System.currentTimeMillis()
        val file=File(context.cacheDir,"route-guidance.json");val original=payload(now);file.writeText(original)
        val packet=ActiveRoutePacket.load(context)!!
        val direct=reply(payload(now,false),now)
        assertThrows(IllegalArgumentException::class.java) {packet.replace(context,direct,false,now)}
        assertEquals(original,file.readText())
        val replaced=packet.replace(context,direct,false,now,remainingWaypointCount=0)
        assertEquals(packet.nonce,replaced.nonce)
        assertEquals(packet.identity.name,replaced.identity.name)
        assertEquals(packet.destination,replaced.destination)
        assertTrue(replaced.requestOptions.waypoints.isEmpty())
        assertEquals("TIME",replaced.requestOptions.priority)
        assertEquals("TIME",JSONObject(file.readText()).getJSONObject("request_options").getString("priority"))
    }
}
