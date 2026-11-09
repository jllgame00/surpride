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
}
