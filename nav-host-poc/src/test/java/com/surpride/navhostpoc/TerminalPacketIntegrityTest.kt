package com.surpride.navhostpoc

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.car.app.testing.TestCarContext
import com.surpride.navhostpoc.guidance.GuidanceAccuracyFixture
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class TerminalPacketIntegrityTest {
    private val context=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
    private val file=File(context.cacheDir,"route-guidance.json")
    private val now=System.currentTimeMillis()
    private val nonce="00000000-0000-4000-8000-000000000001"
    private fun payload()=GuidanceAccuracyFixture.payload(now).put("candidate_id","synthetic")
        .put("session_nonce",nonce).put("prepared_at_epoch_ms",now).put("expires_at_epoch_ms",now+3_600_000)
    private fun disclose():JSONObject {
        ReflectionHelpers.setStaticField(Build::class.java,"MODEL","Connect on x86_64 emulator")
        ReflectionHelpers.setStaticField(Build::class.java,"PRODUCT","crp_avd")
        file.writeText(payload().toString())
        val screen=ActiveMysteryScreen(context,ActiveRoutePacket.load(context))
        ReflectionHelpers.setField(screen,"foreground",true);ReflectionHelpers.setField(screen,"requiresDo",false)
        ReflectionHelpers.callInstanceMethod<Any?>(screen,"reveal")
        return JSONObject(file.readText()).also {assertTrue(it.has("terminal_state"))}
    }
    private fun import(body:JSONObject):ActiveRoutePacket? {
        val uri=Uri.parse("content://com.surpride.app.navigationfiles/prepared_routes/00000000-0000-4000-8000-000000000001.json")
        shadowOf(context.contentResolver).registerInputStream(uri,ByteArrayInputStream(body.toString().toByteArray()))
        return ActiveRoutePacket.load(context,Intent(Intent.ACTION_VIEW,uri))
    }
    @Test fun malformedPresentMetadataCannotFallBackToAHiddenValidRoute() {
        file.writeText(payload().put("terminal_state",JSONObject()).toString())
        assertNull(ActiveRoutePacket.load(context))
    }
    @Test fun expiredSessionCannotBeExtendedByFreshRouteMeasurement() {
        file.writeText(payload().put("prepared_at_epoch_ms",now-3_600_001).toString())
        assertNull(ActiveRoutePacket.load(context))
    }
    @Test fun metadataFromAnotherNonceIsRejected() {
        file.writeText(disclose().put("session_nonce","00000000-0000-4000-8000-000000000002").toString())
        assertNull(ActiveRoutePacket.load(context))
    }
    @Test fun metadataCannotMoveToAnotherValidRoute() {
        val body=disclose();body.getJSONObject("guidance").getJSONArray("segments").getJSONObject(0).put("road_name","Other")
        file.writeText(body.toString());assertNull(ActiveRoutePacket.load(context))
    }
    @Test fun unsupportedVersionOrChangedOutcomeIsRejected() {
        val original=disclose()
        for(key in listOf("version","revealed","expires_at_epoch_ms")) {
            val copy=JSONObject(original.toString());val state=copy.getJSONObject("terminal_state")
            when(key) {"version"->state.put(key,99);"revealed"->state.put(key,false);else->state.put(key,now-1)}
            file.writeText(copy.toString());assertNull(ActiveRoutePacket.load(context))
        }
    }
    @Test fun providerImportCannotAssertDisclosureOrArrival() {
        val packet=disclose();val committed=file.readBytes()
        assertNull(import(packet));assertArrayEquals(committed,file.readBytes())
    }
    @Test fun reimportingOriginalSameSessionDoesNotEraseExplicitOutcome() {
        disclose();val committed=file.readBytes()
        assertNotNull(import(payload()));assertArrayEquals(committed,file.readBytes())
    }
    @Test fun firstImportCommitsAndExpiredOrWrongNonceResumeDoesNotLoadCachedRoute() {
        assertNotNull(import(payload()));assertTrue(file.exists())
        assertNull(ActiveRoutePacket.load(context,Intent("com.surpride.navhostpoc.RESUME_SESSION").putExtra("session_nonce","wrong")))
    }
    @Test fun sameNonceReimportCannotEraseAnInvalidOrExpiredTerminalRecord() {
        val damaged=disclose();damaged.getJSONObject("terminal_state").put("checksum","broken")
        file.writeText(damaged.toString());val committed=file.readBytes()
        assertNull(import(payload()));assertArrayEquals(committed,file.readBytes())
    }
    @Test fun matchingNonceWithDifferentCandidateCannotResumeAnotherSession() {
        assertNotNull(import(payload()))
        val intent=Intent(ActiveRoutePacket.RESUME_ACTION).putExtra("session_nonce",nonce).putExtra("candidate_id","other")
        assertNull(ActiveRoutePacket.load(context,intent))
    }
}
