package com.surpride.navhostpoc.routing

import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.tracking.*
import org.junit.Assert.*
import org.junit.Test

class RerouteCoordinatorTest {
    private val destination=RouteCoordinate(127.02,37.0)
    private var now=10_000L
    private class Pending(val origin:RouteCoordinate,val destination:RouteCoordinate,val callback:(RouteRefreshResult)->Unit) {
        var cancelled=false
    }
    private class Client:RouteRefreshClient {
        val calls=mutableListOf<Pending>()
        override fun request(origin:RouteCoordinate,destination:RouteCoordinate,privateDestinationLabel:String,callback:(RouteRefreshResult)->Unit):RouteRefreshCall {
            val pending=Pending(origin,destination,callback);calls.add(pending)
            return RouteRefreshCall {pending.cancelled=true}
        }
    }
    private fun fix(age:Long=0,accuracy:Double=5.0)=PositionFix(RouteCoordinate(127.0,37.001),accuracy,now-age,1_800_000_000_000L,null,null,null,PositionSource.DEVICE_API,false)
    private fun coordinator(client:Client,accepted:MutableList<RouteRefreshResult.Success> = mutableListOf())=
        RerouteCoordinator(client,destination,"PrivatePlace",{now},{accepted.add(it)}).also {it.updateEligibility(true,true)}

    @Test fun onlyConfirmedReliableFreshOffRouteStartsAndKeepsExactEndpoint() {
        val client=Client();val coordinator=coordinator(client)
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE_CANDIDATE)
        coordinator.onMatch(fix(5001),MatchStatus.OFF_ROUTE)
        coordinator.onMatch(fix(accuracy=51.0),MatchStatus.OFF_ROUTE)
        assertTrue(client.calls.isEmpty())
        val current=fix();coordinator.onMatch(current,MatchStatus.OFF_ROUTE)
        assertEquals(1,client.calls.size);assertEquals(current.coordinate,client.calls[0].origin)
        assertEquals(destination,client.calls[0].destination)
        assertEquals(RerouteStatus.LOADING,coordinator.state().status)
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);assertEquals(1,client.calls.size)
    }
    @Test fun ownershipGateStopsRequestAndIgnoresLateSuccess() {
        val client=Client();val accepted=mutableListOf<RouteRefreshResult.Success>();val coordinator=coordinator(client,accepted)
        coordinator.updateEligibility(false,true);coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);assertTrue(client.calls.isEmpty())
        coordinator.updateEligibility(true,true);coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE)
        coordinator.cancel();assertTrue(client.calls.single().cancelled)
        client.calls.single().callback(success());assertTrue(accepted.isEmpty())
    }
    @Test fun transientFailureRetriesOnceAfterCooldownThenRequiresExplicitRetry() {
        val client=Client();val coordinator=coordinator(client)
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE)
        client.calls[0].callback(RouteRefreshResult.Failure(RouteRefreshError.TIMEOUT))
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);assertEquals(1,client.calls.size)
        now+=30_000;coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);assertEquals(2,client.calls.size)
        client.calls[1].callback(RouteRefreshResult.Failure(RouteRefreshError.NETWORK))
        now+=30_000;coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);assertEquals(2,client.calls.size)
        assertEquals(RerouteStatus.FAILED,coordinator.state().status)
        assertTrue(coordinator.retry());assertEquals(3,client.calls.size)
    }
    @Test fun gpsLossCancelsWithoutRetryUntilReliableFixReturnsAndLateFailureCannotClobberNewRequest() {
        val client=Client();val coordinator=coordinator(client)
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);coordinator.onGpsUnavailable()
        assertTrue(client.calls[0].cancelled);assertFalse(coordinator.retry())
        now+=30_000;coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);assertEquals(2,client.calls.size)
        client.calls[0].callback(RouteRefreshResult.Failure(RouteRefreshError.AUTH))
        assertEquals(RerouteStatus.LOADING,coordinator.state().status)
    }
    @Test fun successIsDeliveredOnceAndCooldownPreventsImmediateDuplicateRefresh() {
        val client=Client();val accepted=mutableListOf<RouteRefreshResult.Success>();val coordinator=coordinator(client,accepted)
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);val response=success()
        client.calls[0].callback(response);client.calls[0].callback(response)
        assertEquals(1,accepted.size);assertEquals(RerouteStatus.READY,coordinator.state().status)
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE);assertEquals(1,client.calls.size)
    }
    @Test fun authenticationFailureDoesNotBecomeAutomaticRetryAfterGpsReturns() {
        val client=Client();val coordinator=coordinator(client)
        coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE)
        client.calls[0].callback(RouteRefreshResult.Failure(RouteRefreshError.AUTH))
        coordinator.onGpsUnavailable();now+=30_000;coordinator.onMatch(fix(),MatchStatus.OFF_ROUTE)
        assertEquals(1,client.calls.size);assertEquals(RerouteStatus.FAILED,coordinator.state().status)
    }
    private fun success():RouteRefreshResult.Success {
        val geometry=listOf(RouteCoordinate(127.0,37.0),destination)
        val route=GuidanceRoute(RouteDataSource.KAKAO_DIRECTIONS,RouteGeometry(geometry),listOf(RouteSegment("Road",100,60,0,geometry)),listOf(Maneuver(100,0,0),Maneuver(101,100,0)),100,60,destination)
        return RouteRefreshResult.Success("PRIVATE_PAYLOAD",route,1_800_000_000_000L,1_800_003_600_000L)
    }
}
