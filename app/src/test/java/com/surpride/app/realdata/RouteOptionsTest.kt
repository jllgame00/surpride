package com.surpride.app.realdata

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RouteOptionsTest {
    private val a=GeoPoint(126.97,37.57);private val b=GeoPoint(126.98,37.58);private val via=GeoPoint(126.975,37.575)
    private val now=1_800_000_000_000L
    private val settings=ProxySettings("http://10.0.2.2:8765","synthetic-token-000000000000000000",now+1000)
    private fun response(options:RouteOptions,source:String="MOCK"):String = JSONObject()
        .put("schema_version",1).put("source",source)
        .put("origin",JSONObject().put("longitude",a.longitude).put("latitude",a.latitude))
        .put("destination",JSONObject().put("longitude",b.longitude).put("latitude",b.latitude))
        .put("duration_seconds",600).put("distance_meters",1200).put("measured_at_epoch_ms",now)
        .put("geometry",org.json.JSONArray().put(org.json.JSONArray().put(a.longitude).put(a.latitude)).put(org.json.JSONArray().put(b.longitude).put(b.latitude)))
        .put("request_options",JSONObject().put("priority",options.priority.name).put("waypoints",org.json.JSONArray().apply {
            options.waypoints.forEach {put(JSONObject().put("longitude",it.longitude).put("latitude",it.latitude))}
        })).put("guidance",JSONObject().put("segments",org.json.JSONArray()).put("maneuvers",org.json.JSONArray())).toString()

    @Test fun legacyProviderCannotSilentlyIgnoreRequestedOptions() {
        val provider=RouteProvider {_,_->RouteReply.Success(RouteLeg(600,1000,listOf(a,b),RouteOrigin.MOCK,now))}
        assertTrue(provider.route(a,b) is RouteReply.Success)
        assertEquals(RouteError.UNSUPPORTED_OPTIONS,(provider.route(a,b,RouteOptions(RoutePriority.TIME)) as RouteReply.Failure).error)
    }
    @Test fun optionRequestHasNoNamesOrCredentialsAndUsesLongitudeFirst() {
        val options=RouteOptions(RoutePriority.DISTANCE,listOf(via))
        val body=String(ProxyHttpTransport.requestBody(a,b,true,options),Charsets.UTF_8)
        val json=JSONObject(body)
        assertEquals("DISTANCE",json.getString("priority"));assertTrue(json.getBoolean("include_guidance"))
        assertEquals(via.longitude,json.getJSONArray("waypoints").getJSONObject(0).getDouble("longitude"),0.0)
        assertFalse(body.contains("synthetic"));assertFalse(body.contains("name"))
        assertFalse(options.toString().contains("126."))
    }
    @Test fun verifiedEchoRequiredForOptionsAndGuidancePayloadIsKeptPrivate() {
        val options=RouteOptions(RoutePriority.TIME,listOf(via))
        val parsed=ProxyRouteParser.parse(200,response(options),a,b,now,options) as RouteReply.Success
        assertEquals(options,parsed.leg.options);assertEquals(RouteOrigin.MOCK,parsed.leg.origin)
        assertNotNull(parsed.leg.guidancePayload)
        assertFalse(parsed.leg.toString().contains("126."))
        assertTrue(ProxyRouteParser.parse(200,response(options),a,b,now,RouteOptions()) is RouteReply.Failure)
        val missing=JSONObject(response(options)).apply {remove("request_options")}.toString()
        assertTrue(ProxyRouteParser.parse(200,missing,a,b,now,options) is RouteReply.Failure)
    }
    @Test fun liveProviderPreservesOptionsAndMockProvenance() {
        val options=RouteOptions(RoutePriority.DISTANCE,listOf(via))
        val provider=LiveRouteProvider(settings,clock={now},optionTransport={_,from,to,requested->
            assertEquals(a,from);assertEquals(b,to);assertEquals(options,requested);ProxyHttpResponse(200,response(requested))
        })
        val leg=(provider.route(a,b,options) as RouteReply.Success).leg
        assertEquals(options,leg.options);assertEquals(RouteOrigin.MOCK,leg.origin)
    }
    @Test fun excessiveDuplicateOrEndpointWaypointsFailBeforeNetwork() {
        assertThrows(IllegalArgumentException::class.java){RouteOptions(waypoints=listOf(via,via))}
        assertThrows(IllegalArgumentException::class.java){RouteOptions(waypoints=listOf(a,b,via))}
        var calls=0
        val provider=LiveRouteProvider(settings,clock={now},optionTransport={_,_,_,_->calls++;error("must not call")})
        assertTrue(provider.route(a,b,RouteOptions(waypoints=listOf(a))) is RouteReply.Failure)
        assertEquals(0,calls)
    }
    @Test fun preparedPacketCarriesTheSelectedOptionsAndRejectsAnotherRouteOption() {
        val options=RouteOptions(RoutePriority.TIME)
        val json=JSONObject(response(options,"KAKAO_DIRECTIONS"))
        json.put("guidance",JSONObject("""{"segments":[{"road_name":"road","distance_meters":1200,"duration_seconds":600,"traffic_state":0,"geometry":[[126.97,37.57],[126.98,37.58]]}],"maneuvers":[{"type":100,"offset_meters":0,"road_index":0},{"type":101,"offset_meters":1200,"road_index":0}]}"""))
        fun prepare(o:RouteOptions)=PreparedRouteContract.prepare(ProxyHttpResponse(200,json.toString()),a,b,
            "public-test","private label","reason","123e4567-e89b-42d3-a456-426614174000",now,expectedOptions=o)
        val result=prepare(options) as PreparedRouteReply.Success
        assertEquals("TIME",JSONObject(result.packet).getJSONObject("request_options").getString("priority"))
        assertEquals(json.getJSONArray("geometry").toString(),JSONObject(result.packet).getJSONArray("geometry").toString())
        assertTrue(prepare(RouteOptions(RoutePriority.DISTANCE)) is PreparedRouteReply.Failure)
    }
}
