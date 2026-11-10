package com.surpride.navhostpoc

import android.content.Context
import android.content.ContextWrapper
import androidx.car.app.CarContext
import androidx.car.app.HostDispatcher
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.navhostpoc.guidance.GuidanceParser
import com.surpride.navhostpoc.routing.RouteRefreshResult
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Native AtomicFile and parser. Isolated MOCK fixtures; no GPS or navigation authority is injected. */
@RunWith(AndroidJUnit4::class)
class PersistenceDeviceTest {
    @Test fun explicitOutcomesCommitAndReloadOnAndroidWithoutActivatingNavigation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val base=instrumentation.targetContext
            val directory=File(base.cacheDir,"b2-native-contract").apply {mkdirs()}
            val owner=object:LifecycleOwner {val registry=LifecycleRegistry(this);override fun getLifecycle()=registry}
            val isolated=object:ContextWrapper(base) {
                override fun getCacheDir()=directory
                override fun getApplicationContext():Context=this
            }
            val context=object:CarContext(owner.registry,HostDispatcher()) {init {attachBaseContext(isolated)}}
            val file=File(context.cacheDir,"route-guidance.json")
            val now=System.currentTimeMillis()
            fun payload(reverse:Boolean=false):String {
                val start=if(reverse) 0.002 else 0.0;val end=if(reverse) 0.0 else 0.002
                return """{"schema_version":1,"source":"MOCK","measured_at_epoch_ms":$now,
                  "prepared_at_epoch_ms":$now,"expires_at_epoch_ms":${now+3_600_000},
                  "session_nonce":"00000000-0000-4000-8000-000000000001","candidate_id":"b2-synthetic",
                  "private_destination_label":"${if(reverse) "출발지" else "B2 공개 합성 시험"}",
                  "origin":{"longitude":$start,"latitude":0},"destination":{"longitude":$end,"latitude":0},
                  "distance_meters":200,"duration_seconds":60,"guidance":{"segments":[{"road_name":"Fixture road",
                  "distance_meters":200,"duration_seconds":60,"traffic_state":0,"geometry":[[$start,0],[$end,0]]}],
                  "maneuvers":[{"type":100,"offset_meters":0,"road_index":0},{"type":101,"offset_meters":200,"road_index":0}]}}"""
            }
            fun export(name:String) {File(directory,"$name.json").writeBytes(file.readBytes())}
            file.writeText(payload())
            var packet=ActiveRoutePacket.load(context)!!;export("prepared")
            packet.rememberStart(context,now);packet.rememberDisclosure(context)
            assertTrue(ActiveRoutePacket.load(context)!!.outcome.revealed);export("disclosed")
            packet.rememberArrival(context,now)
            assertTrue(ActiveRoutePacket.load(context)!!.arrivalConfirmed);export("arrived")
            packet.rememberReturnRequest(context)
            assertEquals(ReturnPhase.REQUESTED,ActiveRoutePacket.load(context)!!.outcome.returnPhase);export("return-requested")
            val body=payload(true)
            packet=packet.replace(context,RouteRefreshResult.Success(body,GuidanceParser.parse(body,now),now,now+3_600_000),true,now)
            assertFalse(packet.arrivalConfirmed);assertTrue(packet.returning);export("return-ready")
            packet.rememberArrival(context,now)
            packet=ActiveRoutePacket.load(context)!!
            assertEquals(ReturnPhase.COMPLETED,packet.outcome.returnPhase);export("return-completed")
            val expired=JSONObject(File(directory,"disclosed.json").readText())
                .put("measured_at_epoch_ms",now-3_600_001).put("prepared_at_epoch_ms",now-3_600_001).put("expires_at_epoch_ms",now-1)
            SessionOutcomeCodec.attach(expired,SessionOutcome(revealed=true),now-3_600_001,now-1)
            File(directory,"expired.json").writeText(expired.toString())
            val screen=ActiveMysteryScreen(context,packet)
            assertNull((screen.onGetTemplate() as NavigationTemplate).navigationInfo)
            val damaged=JSONObject(file.readText()).getJSONObject("terminal_state").put("checksum","invalid")
            val whole=JSONObject(file.readText()).put("terminal_state",damaged)
            file.writeText(whole.toString());assertNull(ActiveRoutePacket.load(context))
            file.writeBytes(File(directory,"return-completed.json").readBytes())
        }
    }
}
