package com.surpride.navhostpoc

import android.content.Intent
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.navigation.TestNavigationManager
import com.surpride.navhostpoc.guidance.GuidanceAccuracyFixture
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],shadows=[FaultAtomicFileShadow::class])
class RetentionContractTest {
    private val context=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
    private val file=File(context.cacheDir,"route-guidance.json")
    private val now=System.currentTimeMillis()
    private val nonce="00000000-0000-4000-8000-000000000001"
    private fun seed(returning:Boolean=false):ActiveRoutePacket {
        file.writeText(GuidanceAccuracyFixture.payload(now).put("session_nonce",nonce).put("candidate_id","synthetic")
            .put("prepared_at_epoch_ms",now).put("expires_at_epoch_ms",now+3_600_000).put("returning",returning)
            .put("proxy_session",JSONObject().put("base_url","https://example.com").put("token","synthetic-token")
                .put("expires_at_ms",now-1)).toString())
        return ActiveRoutePacket.load(context)!!
    }
    @After fun clearFault() {FaultAtomicFileShadow.fault="NONE"}
    @Test fun expiredPacketAndAtomicSidecarsArePhysicallyRemoved() {
        seed();val expired=JSONObject(file.readText()).put("expires_at_epoch_ms",now-1).toString()
        file.writeText(expired);File(file.path+".new").writeText(expired)
        assertNull(ActiveRoutePacket.load(context));assertFalse(file.exists())
        assertFalse(File(file.path+".new").exists());assertFalse(File(file.path+".bak").exists())
    }
    @Test fun expiredCredentialsAreRemovedWithoutChangingTheHiddenSession() {
        val packet=seed()
        assertFalse(JSONObject(file.readText()).has("proxy_session"))
        assertEquals(nonce,packet.nonce);assertFalse(packet.outcome.revealed)
    }
    @Test fun completedReturnKeepsAReceiptButNoRouteOrCredentialAndCannotCreateTbt() {
        val packet=seed(true);packet.rememberArrival(context,now)
        val json=JSONObject(file.readText())
        for(key in listOf("origin","destination","trip_origin","trip_destination","guidance","proxy_session","request_options"))
            assertFalse("retained $key",json.has(key))
        val restored=ActiveRoutePacket.load(context)!!
        assertEquals(ReturnPhase.COMPLETED,restored.outcome.returnPhase)
        val screen=ActiveMysteryScreen(context,restored)
        assertNull(ReflectionHelpers.getField<Any?>(screen,"route"))
        assertFalse(ReflectionHelpers.getField<Boolean>(screen,"subscribed"))
        assertEquals(0,context.getCarService(TestNavigationManager::class.java).navigationStartedCount)
    }
    @Test fun outboundArrivalAndReturnPreparationRetainRequiredOriginAndExplicitOutcome() {
        val packet=seed();packet.rememberArrival(context,now);packet.rememberReturnRequest(context)
        val restored=ActiveRoutePacket.load(context)!!
        assertNotNull(restored.tripOrigin);assertEquals(ReturnPhase.REQUESTED,restored.outcome.returnPhase)
        assertTrue(restored.arrivalConfirmed);assertTrue(restored.outcome.revealed)
    }
    @Test fun wrongNonceCannotDeleteOrChangeAValidPacket() {
        seed();val bytes=file.readBytes()
        assertNull(ActiveRoutePacket.load(context,Intent(ActiveRoutePacket.RESUME_ACTION)
            .putExtra("session_nonce","00000000-0000-4000-8000-000000000002").putExtra("candidate_id","synthetic")))
        assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun retirementWriteFailurePreservesThePreviousPacketAndOutcome() {
        val packet=seed(true);val bytes=file.readBytes();FaultAtomicFileShadow.fault="PARTIAL"
        try {packet.rememberArrival(context,now);fail("expected write failure")} catch(_:java.io.IOException) {}
        FaultAtomicFileShadow.fault="NONE"
        assertArrayEquals(bytes,file.readBytes());assertFalse(packet.arrivalConfirmed)
        packet.rememberArrival(context,now)
        assertEquals(ReturnPhase.COMPLETED,ActiveRoutePacket.load(context)!!.outcome.returnPhase)
    }
    @Test fun explicitScreenCancelRemovesTheCurrentPacketWithoutAutoRestart() {
        seed();val screen=ActiveMysteryScreen(context,ActiveRoutePacket.load(context))
        ReflectionHelpers.callInstanceMethod<Any?>(screen,"cancelSession")
        assertFalse(file.exists());assertNull(ActiveRoutePacket.load(context))
        assertNull(ReflectionHelpers.getField<Any?>(screen,"route"))
        assertFalse(ReflectionHelpers.getField<Boolean>(screen,"subscribed"))
        assertEquals(0,context.getCarService(TestNavigationManager::class.java).navigationStartedCount)
    }
    @Test fun staleCancellationCannotDeleteTheReplacementSession() {
        val stale=seed();file.writeText(JSONObject(file.readText()).put("session_nonce","00000000-0000-4000-8000-000000000002").toString())
        val bytes=file.readBytes();assertFalse(stale.cancel(context));assertArrayEquals(bytes,file.readBytes())
    }
    @Test fun credentialCleanupWriteFailureDoesNotPublishOrDestroyAValidPacket() {
        seed();val json=JSONObject(file.readText()).put("proxy_session",JSONObject().put("expires_at_ms",now-1))
        val bytes=json.toString().toByteArray();file.writeBytes(bytes);FaultAtomicFileShadow.fault="PARTIAL"
        assertNull(ActiveRoutePacket.load(context));assertArrayEquals(bytes,file.readBytes())
        FaultAtomicFileShadow.fault="NONE";assertNotNull(ActiveRoutePacket.load(context))
        assertFalse(JSONObject(file.readText()).has("proxy_session"))
    }
    @Test fun abandonedNewFilesAndExpiredMalformedPacketsArePhysicallyRemoved() {
        seed();assertTrue(file.delete())
        val unfinished=File(file.path+".new").apply {writeText("broken private fixture")}
        assertNull(ActiveRoutePacket.load(context));assertFalse(unfinished.exists())
        file.writeText("broken private fixture");assertTrue(file.setLastModified(1))
        assertNull(ActiveRoutePacket.load(context));assertFalse(file.exists())
    }
    @Test fun deletionFailureIsNotSuccessAndTheAbandonedFileCannotBeReopened() {
        val packet=seed();val blocked=File(file.path+".new").apply {mkdirs()}
        val child=File(blocked,"fixture").apply {writeText("synthetic")}
        try {
            assertThrows(java.io.IOException::class.java) {packet.cancel(context)}
            assertTrue(blocked.exists());assertNull(ActiveRoutePacket.load(context))
        } finally {assertTrue(child.delete());assertTrue(blocked.delete())}
    }
}
