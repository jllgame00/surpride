package com.surpride.app.realdata

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreparedRouteContractTest {
    private val now=1_800_000_000_000L
    private val origin=GeoPoint(126.97,37.57)
    private val destination=GeoPoint(126.98,37.58)
    private val nonce="01234567-89ab-4def-8123-0123456789ab"
    private fun response(source:String="KAKAO_DIRECTIONS",at:Long=now)=ProxyHttpResponse(200,"""{
      "schema_version":1,"source":"$source","measured_at_epoch_ms":$at,
      "origin":{"longitude":126.97,"latitude":37.57},"destination":{"longitude":126.98,"latitude":37.58},
      "duration_seconds":600,"distance_meters":4200,"geometry":[[126.97,37.57],[126.98,37.58]],
      "guidance":{"segments":[{"road_name":"도로","distance_meters":4200,"duration_seconds":600,"traffic_state":0,"geometry":[[126.97,37.57],[126.98,37.58]]}],
      "maneuvers":[{"type":100,"offset_meters":0,"road_index":0},{"type":101,"offset_meters":4200,"road_index":0}]}}
    """)
    private fun prepare(r:ProxyHttpResponse=response())=PreparedRouteContract.prepare(r,origin,destination,
        "public-candidate","공개 전 비밀명소","선정 이유",nonce,now)

    @Test fun livePacketPreservesGuidanceAndCarriesPrivateOneHourSessionMetadata() {
        val reply=prepare() as PreparedRouteReply.Success
        val packet=JSONObject(reply.packet)
        assertEquals("KAKAO_DIRECTIONS",packet.getString("source"))
        assertEquals(2,packet.getJSONObject("guidance").getJSONArray("maneuvers").length())
        assertEquals(nonce,packet.getString("session_nonce"))
        assertEquals("public-candidate",packet.getString("candidate_id"))
        assertEquals("공개 전 비밀명소",packet.getString("private_destination_label"))
        assertEquals(now+3_600_000,packet.getLong("expires_at_epoch_ms"))
        assertFalse(reply.toString().contains("비밀명소"))
    }
    @Test fun staleMockWrongEndpointAndMissingGuidanceNeverProduceHandoff() {
        val invalid=listOf(response("MOCK"),response(at=now-300_001),
            response().copy(body=response().body.replace("126.98","127.98")),
            response().copy(body=JSONObject(response().body).apply {remove("guidance")}.toString()),
            ProxyHttpResponse(401,"{\"error\":\"AUTH\"}"),
            response().copy(body=response().body+" ".repeat(2_000_000)))
        invalid.forEach {assertTrue(prepare(it) is PreparedRouteReply.Failure)}
    }
    @Test fun emptyGuidanceAndMalformedSessionMetadataFailClosed() {
        val empty=JSONObject(response().body).apply {getJSONObject("guidance").put("maneuvers",org.json.JSONArray())}
        assertTrue(prepare(response().copy(body=empty.toString())) is PreparedRouteReply.Failure)
        assertTrue(PreparedRouteContract.prepare(response(),origin,destination,"candidate","name","reason","guessable",now) is PreparedRouteReply.Failure)
    }
    @Test fun guidanceRequestIsExplicitAndContainsOnlyEndpointCoordinates() {
        val standard=JSONObject(ProxyHttpTransport.requestBody(origin,destination,false).toString(Charsets.UTF_8))
        val full=JSONObject(ProxyHttpTransport.requestBody(origin,destination,true).toString(Charsets.UTF_8))
        assertFalse(standard.has("include_guidance"))
        assertEquals(setOf("origin","destination"),standard.keys().asSequence().toSet())
        assertTrue(full.getBoolean("include_guidance"))
        assertEquals(setOf("origin","destination","include_guidance"),full.keys().asSequence().toSet())
        assertEquals(126.98,full.getJSONObject("destination").getDouble("longitude"),0.0)
    }
    @Test fun pendingResultRequiresMatchingFreshNonceCandidateSourceAndExplicitReveal() {
        val pending=PendingLiveNavigation(nonce,"public-candidate",now)
        fun valid(n:String=nonce,c:String="public-candidate",source:String="DEVICE_API_EMULATOR",revealed:Boolean=true,stopped:Boolean=true,time:Long=now)=
            LiveNavigationResultPolicy.accepts(pending,n,c,source,revealed,stopped,time)
        assertTrue(valid())
        assertFalse(valid(n="different"));assertFalse(valid(c="different"))
        assertFalse(valid(source="MOCK"));assertFalse(valid(revealed=false));assertFalse(valid(stopped=false))
        assertFalse(valid(time=now+3_600_001));assertFalse(valid(time=now-1))
        assertFalse(LiveNavigationResultPolicy.accepts(null,nonce,"public-candidate","DEVICE_API_EMULATOR",true,true,now))
    }
    @Test fun manualCompletionConsumesTheNonceExactlyOnceAndStoresOnlyPublicVisitFields() {
        val journal=LiveNavigationJournal(PendingLiveNavigation(nonce,"public-candidate",now))
        assertFalse(journal.complete(nonce,"public-candidate","DEVICE_API_EMULATOR",true,true,0,now))
        assertNotNull(journal.pending)
        assertTrue(journal.complete(nonce,"public-candidate","DEVICE_API_EMULATOR",true,true,4,now+10))
        assertNull(journal.pending)
        assertEquals(listOf(LiveNavigationVisit("public-candidate",4,now+10)),journal.visits)
        assertFalse(journal.complete(nonce,"public-candidate","DEVICE_API_EMULATOR",true,true,5,now+20))
        assertEquals(1,journal.visits.size)
    }
    @Test fun privatePacketCarriesOriginalTripPreferencesAndOnlyShortLivedProxyCredential() {
        val trip=PreparedTripMetadata(origin,true,60,now+2_000_000,1200,600,720,now-1000)
        val proxy=ProxySettings("http://10.0.2.2:8765","a".repeat(32),now+600_000)
        val reply=PreparedRouteContract.prepare(response(),origin,destination,"public-candidate","name","reason",
            nonce,now,trip,proxy) as PreparedRouteReply.Success
        val packet=JSONObject(reply.packet)
        assertEquals(126.97,packet.getJSONObject("trip_origin").getDouble("longitude"),0.0)
        assertEquals(37.57,packet.getJSONObject("trip_origin").getDouble("latitude"),0.0)
        assertTrue(packet.getBoolean("return_to_start"))
        assertEquals(60,packet.getInt("available_minutes"))
        assertEquals(now+2_000_000,packet.getLong("return_by_epoch_ms"))
        assertEquals(1200,packet.getLong("stay_seconds"))
        assertEquals(600,packet.getLong("safety_buffer_seconds"))
        assertEquals(720,packet.getLong("initial_return_duration_seconds"))
        assertEquals(now-1000,packet.getLong("initial_return_measured_at_epoch_ms"))
        assertFalse(packet.has("started_at_epoch_ms"))
        assertEquals(setOf("base_url","token","expires_at_ms"),packet.getJSONObject("proxy_session").keys().asSequence().toSet())
        assertEquals(now+600_000,packet.getJSONObject("proxy_session").getLong("expires_at_ms"))
        assertFalse(trip.toString().contains("126.97"))
        assertFalse(reply.toString().contains(proxy.token))
    }
    @Test fun absentTripInformationIsNotInventedForOldOrOneWayPreparation() {
        val old=JSONObject((prepare() as PreparedRouteReply.Success).packet)
        assertFalse(old.has("trip_origin"));assertFalse(old.has("return_to_start"));assertFalse(old.has("proxy_session"))
        val trip=PreparedTripMetadata(origin,false,null,null,0,600,null,null)
        val packet=JSONObject((PreparedRouteContract.prepare(response(),origin,destination,"public-candidate","name","reason",
            nonce,now,trip) as PreparedRouteReply.Success).packet)
        assertFalse(packet.getBoolean("return_to_start"))
        listOf("available_minutes","return_by_epoch_ms","initial_return_duration_seconds","initial_return_measured_at_epoch_ms")
            .forEach {assertFalse(packet.has(it))}
    }
    @Test fun invalidTripBudgetOrExpiredProxyCannotCreatePrivatePacket() {
        val invalid=listOf(
            PreparedTripMetadata(origin,true,0,null,1200,600,720,now),
            PreparedTripMetadata(origin,true,60,null,-1,600,720,now),
            PreparedTripMetadata(origin,true,60,null,1200,600,720,null),
            PreparedTripMetadata(destination,true,60,null,1200,600,720,now))
        invalid.forEach {trip->assertTrue(PreparedRouteContract.prepare(response(),origin,destination,"public-candidate","name","reason",
            nonce,now,trip) is PreparedRouteReply.Failure)}
        val proxy=ProxySettings("http://10.0.2.2:8765","a".repeat(32),now)
        assertTrue(PreparedRouteContract.prepare(response(),origin,destination,"public-candidate","name","reason",
            nonce,now,proxySession=proxy) is PreparedRouteReply.Failure)
    }
}
