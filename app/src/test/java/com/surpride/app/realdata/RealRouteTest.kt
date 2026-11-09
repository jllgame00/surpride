package com.surpride.app.realdata

import com.surpride.app.experience.core.*
import org.junit.Assert.*
import org.junit.Test

class RealRouteTest {
    private val now=1_800_000_000_000L
    private val origin=GeoPoint(126.976787992565,37.5716228232824)
    private val places get()=SeoulPilotCatalog.places.take(1)
    private fun json(seconds: String="600", meters: String="3200", code:Int=0, vertices:String="126.97,37.57,126.98,37.58") =
        """{"routes":[{"result_code":$code,"summary":{"duration":$seconds,"distance":$meters},"sections":[{"roads":[{"vertexes":[$vertices]}]}]}]}"""
    private fun parsed(body:String=json(), status:Int=200)=KakaoRouteParser.parse(status,body,RouteOrigin.MOCK,now)
    private fun preferences(minutes:Int=60, returns:Boolean=true)=DrivePreferences(availableMinutes=minutes,returnToStart=returns,randomness=0)
    private fun evaluate(provider:RouteProvider,p:DrivePreferences=preferences(),context:RecommendationContext=RecommendationContext(now))=
        RouteRecommendationEngine(places,provider,{now}).recommend(origin,p,context,20,10,1)

    @Test fun catalogContainsFiveSourcedRealPlacesAndUnknownFacilities() {
        assertEquals(5,SeoulPilotCatalog.places.size)
        SeoulPilotCatalog.places.forEach { assertTrue(it.sourceUrl.startsWith("https://data.seoul.go.kr/"));assertEquals(Knowledge.UNKNOWN,it.parking);assertEquals(Knowledge.UNKNOWN,it.hours);assertEquals(Knowledge.UNKNOWN,it.accessibility) }
    }
    @Test fun invalidCoordinatesAreRejectedAndDebugOutputIsRedacted() {
        assertThrows(IllegalArgumentException::class.java){GeoPoint(37.0,126.0)}
        assertThrows(IllegalArgumentException::class.java){GeoPoint(Double.NaN,37.0)}
        assertFalse(origin.toString().contains("126."))
    }
    @Test fun parserUsesSecondsMetersAndLongitudeLatitudeOrder() {
        val leg=(parsed() as RouteReply.Success).leg
        assertEquals(600L,leg.seconds);assertEquals(3200L,leg.meters);assertEquals(RouteOrigin.MOCK,leg.origin)
        assertEquals(GeoPoint(126.97,37.57),leg.geometry.first())
    }
    @Test fun malformedMissingNegativeAndFractionalDurationsAreRejected() {
        listOf("{}","not json",json("-1"),json("1.5"),json("0"),json("999999999999999999999999")).forEach {assertEquals(RouteError.INVALID_RESPONSE,(parsed(it) as RouteReply.Failure).error)}
    }
    @Test fun malformedGeometryIsRejectedButSummaryOnlyIsAllowed() {
        assertEquals(RouteError.INVALID_RESPONSE,(parsed(json(vertices="126.97,37.57,127")) as RouteReply.Failure).error)
        assertTrue(parsed("""{"routes":[{"result_code":0,"summary":{"duration":9,"distance":5}}]}""") is RouteReply.Success)
    }
    @Test fun httpAuthQuotaNetworkAndNoRouteStayDistinct() {
        assertEquals(RouteError.AUTH,(parsed(status=403) as RouteReply.Failure).error)
        assertEquals(RouteError.RATE_LIMIT,(parsed(status=429) as RouteReply.Failure).error)
        assertEquals(RouteError.API,(parsed(status=500) as RouteReply.Failure).error)
        assertEquals(RouteError.NO_ROUTE,(parsed(json(code=1)) as RouteReply.Failure).error)
        assertEquals(RouteError.INVALID_COORDINATE,(parsed(json(code=104)) as RouteReply.Failure).error)
        assertEquals(RouteError.NOT_CONFIGURED,(UnconfiguredRouteProvider().route(origin,origin) as RouteReply.Failure).error)
        assertEquals(RouteError.NETWORK,(KakaoRouteProvider({_,_->throw java.io.IOException()}, {now}).route(origin,origin) as RouteReply.Failure).error)
    }
    @Test fun roundTripQueriesReverseLegAndIncludesStayAndBuffer() {
        val calls=mutableListOf<Pair<GeoPoint,GeoPoint>>()
        val result=evaluate(RouteProvider {a,b->calls.add(a to b);parsed(json(if(calls.size==1) "600" else "1200"))}) as RoutePlan.Success
        assertEquals(listOf(origin to places[0].point,places[0].point to origin),calls)
        assertEquals(3600L,result.totalSeconds);assertEquals(RouteOrigin.MOCK,result.routeOrigin)
    }
    @Test fun oneWayDoesNotQueryOrIncludeReturn() {
        var calls=0
        val result=evaluate(RouteProvider {_,_->calls++;parsed()},preferences(60,false)) as RoutePlan.Success
        assertEquals(1,calls);assertEquals(2400L,result.totalSeconds)
    }
    @Test fun missingReturnNeverProducesRecommendation() {
        var calls=0
        val result=evaluate(RouteProvider {_,_->if(++calls==2) RouteReply.Failure(RouteError.NO_ROUTE) else parsed()}) as RoutePlan.Failure
        assertTrue(RouteError.NO_ROUTE in result.errors)
    }
    @Test fun overBudgetIncludingOneSecondAndDeadlineIsExcluded() {
        assertTrue(evaluate(RouteProvider {_,_->parsed(json("901"))}) is RoutePlan.Failure)
        assertTrue(evaluate(RouteProvider {_,_->parsed()},preferences().copy(returnByEpochMillis=now+49*60_000)) is RoutePlan.Failure)
    }
    @Test fun exactBudgetFitsAndUnknownRangeIsNotInvented() {
        val result=evaluate(RouteProvider {_,_->parsed() },preferences(50)) as RoutePlan.Success
        assertEquals(3000L,result.totalSeconds);assertTrue(PlanWarning.RANGE_UNVERIFIED in result.scoring.warnings)
    }
    @Test fun recentAndUnsupportedCategoriesNeverTriggerRouteRequests() {
        var calls=0;val provider=RouteProvider {_,_->calls++;parsed()}
        assertTrue(evaluate(provider,preferences().copy(categories=setOf(PlaceCategory.PET))) is RoutePlan.Failure)
        assertTrue(evaluate(provider,context=RecommendationContext(now,recentIds=setOf(places[0].id))) is RoutePlan.Failure)
        assertEquals(0,calls)
    }
    @Test fun privateProjectionRevealsNeitherIdentityNorCoordinatesUntilExplicitReveal() {
        val result=evaluate(RouteProvider {_,_->parsed()}) as RoutePlan.Success
        val hidden=result.presentation(false)
        assertNull(hidden.destination);assertFalse(hidden.toString().contains(places[0].name));assertFalse(hidden.toString().contains(places[0].point.longitude.toString()))
        assertEquals(places[0].name,result.presentation(true).destination?.name)
    }
    @Test fun routeErrorsDoNotBecomeDemoFallback() {
        val result=evaluate(UnconfiguredRouteProvider()) as RoutePlan.Failure
        assertEquals(setOf(RouteError.NOT_CONFIGURED),result.errors)
    }
    @Test fun kakaoPlaceParsingRejectsInvalidCoordinatesAndMapsXToLongitude() {
        val body="""{"documents":[{"id":"one","place_name":"public","x":"126.97","y":"37.57","place_url":"https://place.map.kakao.com/one"}]}"""
        val place=KakaoPlaceParser.parse(body).single()
        assertEquals(GeoPoint(126.97,37.57),place.point)
        assertTrue(KakaoPlaceParser.parse(body.replace("37.57","137.57")).isEmpty())
    }
    @Test fun summarySectionsWithoutRoadsAreValid() {
        assertTrue(parsed("""{"routes":[{"result_code":0,"summary":{"duration":600,"distance":10},"sections":[{"duration":600,"distance":10}]}]}""") is RouteReply.Success)
    }
    @Test fun staleFutureAndMixedProvenanceResponsesAreRejected() {
        listOf(now-300001,now+60001).forEach {at->
            assertTrue(evaluate(RouteProvider {_,_->KakaoRouteParser.parse(200,json(),RouteOrigin.LIVE_API,at)}) is RoutePlan.Failure)
        }
        var calls=0
        assertTrue(evaluate(RouteProvider {_,_->KakaoRouteParser.parse(200,json(),if(++calls==1) RouteOrigin.LIVE_API else RouteOrigin.MOCK,now)}) is RoutePlan.Failure)
    }
    @Test fun onlyLiveRoutesCanBeComparedToVerifiedRange() {
        val context=RecommendationContext(now,vehicle=VehicleContext(0.1))
        assertTrue(evaluate(RouteProvider {_,_->KakaoRouteParser.parse(200,json(),RouteOrigin.LIVE_API,now)},context=context) is RoutePlan.Failure)
        assertTrue(evaluate(RouteProvider {_,_->parsed()},context=context) is RoutePlan.Success)
    }
    @Test fun asymmetricSecondRoundingDoesNotRejectAnExactlyFittingTrip() {
        var calls=0
        val result=evaluate(RouteProvider {_,_->parsed(json(if(++calls==1) "599" else "601"))},preferences(50))
        assertTrue(result is RoutePlan.Success)
        assertEquals(3000L,(result as RoutePlan.Success).totalSeconds)
    }
    @Test fun explicitAvailableTimeReplacesInheritedReturnDeadline() {
        val p=preferences().copy(returnByEpochMillis=now-1).withAvailableBudget(180)
        assertNull(p.returnByEpochMillis)
        assertTrue(evaluate(RouteProvider {_,_->parsed()},p) is RoutePlan.Success)
    }
}
