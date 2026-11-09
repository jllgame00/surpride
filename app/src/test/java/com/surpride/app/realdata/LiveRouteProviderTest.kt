package com.surpride.app.realdata

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LiveRouteProviderTest {
    private val now=1_800_000_000_000L
    private val origin=GeoPoint(126.97,37.57)
    private val destination=GeoPoint(126.98,37.58)
    private fun json(source:String="KAKAO_DIRECTIONS", at:Long=now)=
        """{"schema_version":1,"source":"$source","origin":{"longitude":126.97,"latitude":37.57},"destination":{"longitude":126.98,"latitude":37.58},"duration_seconds":600,"distance_meters":4200,"geometry":[[126.97,37.57],[126.98,37.58]],"measured_at_epoch_ms":$at}"""
    private fun parse(body:String=json(),status:Int=200)=ProxyRouteParser.parse(status,body,origin,destination,now)

    @Test fun liveContractReusesValidatedKakaoUnitsAndGeometry() {
        val leg=(parse() as RouteReply.Success).leg
        assertEquals(RouteOrigin.LIVE_API,leg.origin);assertEquals(600L,leg.seconds);assertEquals(4200L,leg.meters)
        assertEquals(destination,leg.geometry.last());assertEquals(now,leg.receivedAtMillis)
    }
    @Test fun mockOverHttpNeverBecomesLive() {
        assertEquals(RouteOrigin.MOCK,(parse(json("MOCK")) as RouteReply.Success).leg.origin)
    }
    @Test fun staleFutureWrongDestinationAndUnknownSourceAreRejected() {
        assertEquals(RouteError.STALE_DATA,(parse(json(at=now-300001)) as RouteReply.Failure).error)
        assertEquals(RouteError.STALE_DATA,(parse(json(at=now+60001)) as RouteReply.Failure).error)
        assertTrue(parse(json().replace("126.98","127.98")) is RouteReply.Failure)
        assertTrue(parse(json("UNKNOWN")) is RouteReply.Failure)
    }
    @Test fun badSchemaNumbersAndGeometryAreRejected() {
        listOf("{}",json().replace("\"schema_version\":1","\"schema_version\":2"),json().replace("600","1.5"),
            json().replace("[126.97,37.57]","[126.97]"),json().replace("4200","-1")).forEach {
            assertEquals(RouteError.INVALID_RESPONSE,(parse(it) as RouteReply.Failure).error)
        }
    }
    @Test fun sanitizedServerErrorsRemainDistinct() {
        mapOf("AUTH" to 401,"NOT_CONFIGURED" to 503,"RATE_LIMIT" to 429,"NETWORK" to 504,"NO_ROUTE" to 422,"INVALID_COORDINATE" to 422,"API" to 502).forEach {(error,status)->
            assertEquals(RouteError.valueOf(error),(parse("""{"error":"$error"}""",status) as RouteReply.Failure).error)
        }
        assertEquals(RouteError.API,(parse("upstream HTML / private input",500) as RouteReply.Failure).error)
    }
    @Test fun networkFailureAndMissingConfigurationDoNotFallBack() {
        assertEquals(RouteError.NOT_CONFIGURED,(LiveRouteProvider(null,{_,_,_->error("no call")},{now}).route(origin,destination) as RouteReply.Failure).error)
        val provider=LiveRouteProvider(ProxySettings("http://10.0.2.2:8765","synthetic-token-000000000000000000",now+1000),{_,_,_->throw IOException("private")},{now})
        assertEquals(RouteError.NETWORK,(provider.route(origin,destination) as RouteReply.Failure).error)
    }
    @Test fun providerSendsCoordinatesWithoutIdentityAndKeepsTokensOutOfDiagnosticStrings() {
        val settings=ProxySettings("http://10.0.2.2:8765","synthetic-token-000000000000000000",now+1000)
        val provider=LiveRouteProvider(settings,{config,a,b->
            assertEquals(settings,config);assertEquals(origin,a);assertEquals(destination,b)
            ProxyHttpResponse(200,json("MOCK"))
        },{now})
        assertEquals(RouteOrigin.MOCK,(provider.route(origin,destination) as RouteReply.Success).leg.origin)
        assertFalse(settings.toString().contains("synthetic"))
        assertFalse(ProxyHttpResponse(200,"private body").toString().contains("private body"))
    }
    @Test fun expiredConfigAndUntrustedEndpointsAreRejectedBeforeNetwork() {
        val provider=LiveRouteProvider(ProxySettings("http://10.0.2.2:8765","synthetic-token-000000000000000000",now-1),{_,_,_->error("no call")},{now})
        assertEquals(RouteError.NOT_CONFIGURED,(provider.route(origin,destination) as RouteReply.Failure).error)
        listOf("http://example.com:8765","http://10.0.2.2:8765@evil.test","https://evil.test/redirect").forEach {
            assertThrows(IllegalArgumentException::class.java){ProxySettings(it,"synthetic-token-000000000000000000",now+1000)}
        }
    }
    @Test fun observedPublicApiSummaryRejects60MinutesButFits180WithIndependentReturn() {
        // Aggregate values observed in the public-coordinate smoke test, not a new HTTP request.
        val start=SeoulPilotCatalog.developmentOrigin
        val places=listOf(SeoulPilotCatalog.places[2])
        val provider=RouteProvider {from,_->
            RouteReply.Success(if(from==start) RouteLeg(1247,7437,emptyList(),RouteOrigin.LIVE_API,now)
                else RouteLeg(2553,11149,emptyList(),RouteOrigin.LIVE_API,now))
        }
        val engine=RouteRecommendationEngine(places,provider,{now})
        val p=com.surpride.app.experience.core.DrivePreferences(availableMinutes=60,randomness=0)
        val context=com.surpride.app.experience.core.RecommendationContext(now)
        assertEquals(setOf(RouteError.TIME_EXCEEDED),(engine.recommend(start,p,context,20,10,1) as RoutePlan.Failure).errors)
        val result=engine.recommend(start,p.withAvailableBudget(180),context,20,10,1) as RoutePlan.Success
        assertEquals(5600L,result.totalSeconds)
        assertEquals(RouteOrigin.LIVE_API,result.routeOrigin)
        assertNull(result.presentation(false).destination)
    }
    @Test fun freshResponsesAfterSixtySecondsOfSequentialLookupsRemainEligible() {
        var time=now
        val provider=RouteProvider {_,_->time+=8000;RouteReply.Success(RouteLeg(600,4200,emptyList(),RouteOrigin.LIVE_API,time))}
        val engine=RouteRecommendationEngine(SeoulPilotCatalog.places,provider,{time})
        val p=com.surpride.app.experience.core.DrivePreferences(mood=com.surpride.app.experience.core.Mood.ADVENTURE,availableMinutes=180,randomness=0)
        val result=engine.recommend(SeoulPilotCatalog.developmentOrigin,p,com.surpride.app.experience.core.RecommendationContext(now),20,10,1) as RoutePlan.Success
        assertEquals(SeoulPilotCatalog.places.last().id,result.place.id)
    }
    @Test fun returnDeadlineIsRecheckedAfterNetworkTime() {
        var time=now
        val provider=RouteProvider {_,_->time+=8000;RouteReply.Success(RouteLeg(600,4200,emptyList(),RouteOrigin.LIVE_API,time))}
        val engine=RouteRecommendationEngine(SeoulPilotCatalog.places.take(1),provider,{time})
        val p=com.surpride.app.experience.core.DrivePreferences(availableMinutes=60,returnByEpochMillis=now+3_010_000)
        val result=engine.recommend(SeoulPilotCatalog.developmentOrigin,p,com.surpride.app.experience.core.RecommendationContext(now),20,10,1) as RoutePlan.Failure
        assertEquals(setOf(RouteError.TIME_EXCEEDED),result.errors)
    }
}
