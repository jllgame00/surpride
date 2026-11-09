package com.surpride.navhostpoc.routing

import com.surpride.navhostpoc.guidance.RouteCoordinate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class ProxyRouteRefreshClientTest {
    private val now=1_800_000_000_000L
    private val origin=RouteCoordinate(127.0,37.0)
    private val destination=RouteCoordinate(127.002,37.0)
    private val token="x".repeat(32)
    private val direct=Executor {it.run()}
    private fun payload()="""{"schema_version":1,"source":"KAKAO_DIRECTIONS","measured_at_epoch_ms":$now,
      "origin":{"longitude":127.0,"latitude":37.0},"destination":{"longitude":127.002,"latitude":37.0},
      "distance_meters":200,"duration_seconds":100,"geometry":[[127.0,37.0],[127.002,37.0]],
      "guidance":{"segments":[{"road_name":"Road","distance_meters":200,"duration_seconds":100,"traffic_state":0,"geometry":[[127.0,37.0],[127.002,37.0]]}],
      "maneuvers":[{"type":100,"offset_meters":0,"road_index":0},{"type":101,"offset_meters":200,"road_index":0}]}}"""
    private fun client(body:String=payload(),status:Int=200)=ProxyRouteRefreshClient(
        ProxyRouteSettings("http://127.0.0.1:8765",token,now+3_600_000),{now},direct,direct,
        RouteHttpTransport {_,_,_,_->RouteHttpResponse(status,body)})
    private fun result(client:RouteRefreshClient):RouteRefreshResult {var result:RouteRefreshResult?=null;client.request(origin,destination,"SecretPlace"){result=it};return result!!}
    @Test fun validatesFullRouteRetainsExactEndpointsAndAddsPrivateLabelLocally() {
        val result=result(client()) as RouteRefreshResult.Success
        val json=JSONObject(result.payload)
        assertEquals("SecretPlace",json.getString("private_destination_label"))
        assertEquals(destination.longitude,json.getJSONObject("destination").getDouble("longitude"),0.0)
        assertEquals(now+3_600_000,result.expiresAtEpochMillis)
        assertFalse(result.toString().contains("SecretPlace"));assertFalse(result.toString().contains("127.002"))
    }
    @Test fun refusesMockWrongEndpointsStaleRouteAndSensitiveRoadInstructions() {
        listOf(payload().replace("KAKAO_DIRECTIONS","MOCK"),payload().replace("127.002","127.003"),
            payload().replace(now.toString(),(now-3_600_001).toString()),payload().replace("Road","SecretPlace Road"),"{}").forEach {
            assertEquals(RouteRefreshError.INVALID_RESPONSE,(result(client(it)) as RouteRefreshResult.Failure).error)
        }
    }
    @Test fun safeErrorCodesAreBoundedAndNoRawServerMessageEscapes() {
        listOf(401 to RouteRefreshError.AUTH,429 to RouteRefreshError.RATE_LIMIT,504 to RouteRefreshError.TIMEOUT,
            422 to RouteRefreshError.NO_ROUTE).forEach {(status,error)->
            val result=result(client("""{"error":"NO_ROUTE","detail":"SecretPlace"}""",status)) as RouteRefreshResult.Failure
            assertEquals(error,result.error);assertFalse(result.toString().contains("SecretPlace"))
        }
    }
    @Test fun acceptsOnlyExplicitLocalProxyAndStrongUnexpiredTokenWithoutPrintingCredentials() {
        listOf("https://example.com","http://localhost:8765","http://127.0.0.1:8765/evil","http://10.0.2.2:9999").forEach {
            assertThrows(IllegalArgumentException::class.java){ProxyRouteSettings(it,token,now+1)}
        }
        assertThrows(IllegalArgumentException::class.java){ProxyRouteSettings("http://127.0.0.1:8765","weak",now+1)}
        val settings=ProxyRouteSettings("http://10.0.2.2:8765",token,now-1)
        val client=ProxyRouteRefreshClient(settings,{now},direct,direct,RouteHttpTransport {_,_,_,_->throw AssertionError("must not send expired credentials")})
        assertEquals(RouteRefreshError.NOT_CONFIGURED,(result(client) as RouteRefreshResult.Failure).error)
        assertFalse(settings.toString().contains(token))
    }
    @Test fun requestContainsCoordinatesAndGuidanceButNeverPrivateLabelOrKakaoCredential() {
        val client=ProxyRouteRefreshClient(ProxyRouteSettings("http://127.0.0.1:8765",token,now+1),{now},direct,direct,
            RouteHttpTransport {url,bearer,body,_->
                assertEquals("http://127.0.0.1:8765/v1/routes",url);assertEquals("Bearer $token",bearer)
                val json=JSONObject(body);assertTrue(json.getBoolean("include_guidance"));assertEquals(3,json.length())
                assertFalse(body.contains("SecretPlace"));assertFalse(bearer.contains("KakaoAK"));RouteHttpResponse(200,payload())})
        assertTrue(result(client) is RouteRefreshResult.Success)
    }
    @Test fun cancellationSuppressesQueuedWorkerAndQueuedDelivery() {
        val workers=mutableListOf<Runnable>();val deliveries=mutableListOf<Runnable>();var sends=0;var callbacks=0
        val client=ProxyRouteRefreshClient(ProxyRouteSettings("http://127.0.0.1:8765",token,now+1),{now},
            Executor {workers.add(it)},Executor {deliveries.add(it)},RouteHttpTransport {_,_,_,_->sends++;RouteHttpResponse(200,payload())})
        val cancelled=client.request(origin,destination,"SecretPlace"){callbacks++};cancelled.cancel();workers.removeAt(0).run()
        assertEquals(0,sends);assertEquals(0,callbacks)
        val queued=client.request(origin,destination,"SecretPlace"){callbacks++};workers.removeAt(0).run()
        queued.cancel();deliveries.forEach {it.run()};assertEquals(1,sends);assertEquals(0,callbacks)
    }
    @Test fun timeoutAndNetworkExceptionsNeverEscapeTheirMessages() {
        listOf(java.net.SocketTimeoutException("SecretPlace") to RouteRefreshError.TIMEOUT,
            java.io.IOException("token and endpoint") to RouteRefreshError.NETWORK).forEach {(exception,error)->
            val client=ProxyRouteRefreshClient(ProxyRouteSettings("http://127.0.0.1:8765",token,now+1),{now},direct,direct,
                RouteHttpTransport {_,_,_,_->throw exception})
            assertEquals(error,(result(client) as RouteRefreshResult.Failure).error)
        }
    }
    @Test fun expiredExplicitTtlAndOversizedResponseAreRejected() {
        val expired=JSONObject(payload()).put("expires_at_epoch_ms",now).toString()
        listOf(expired," ".repeat(2_000_001)).forEach {
            assertEquals(RouteRefreshError.INVALID_RESPONSE,(result(client(it)) as RouteRefreshResult.Failure).error)
        }
    }
    @Test fun responseCannotInjectProxySessionOrRequestAuthenticationIntoReplacementPacket() {
        val body=JSONObject(payload()).put("proxy_session",JSONObject().put("token",token)).put("authorization","Bearer $token").toString()
        val result=result(client(body)) as RouteRefreshResult.Success
        assertFalse(result.payload.contains(token));assertFalse(result.payload.contains("proxy_session"));assertFalse(result.payload.contains("authorization"))
    }
    @Test fun responseThatExpiresWhileCallbackIsQueuedIsNeverDeliveredAsSuccess() {
        var clock=now;val deliveries=mutableListOf<Runnable>();var delivered:RouteRefreshResult?=null
        val client=ProxyRouteRefreshClient(ProxyRouteSettings("http://127.0.0.1:8765",token,now+3_600_000),{clock},direct,
            Executor {deliveries.add(it)},RouteHttpTransport {_,_,_,_->RouteHttpResponse(200,payload())})
        client.request(origin,destination,"SecretPlace"){delivered=it};clock+=3_600_001;deliveries.single().run()
        assertEquals(RouteRefreshError.INVALID_RESPONSE,(delivered as RouteRefreshResult.Failure).error)
    }
}
