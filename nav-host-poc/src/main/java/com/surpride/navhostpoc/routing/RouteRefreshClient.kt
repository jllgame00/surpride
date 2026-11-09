package com.surpride.navhostpoc.routing

import com.surpride.navhostpoc.guidance.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

fun interface RouteRefreshCall { fun cancel() }
interface RouteRefreshClient {
    fun request(origin:RouteCoordinate,destination:RouteCoordinate,privateDestinationLabel:String,
        callback:(RouteRefreshResult)->Unit):RouteRefreshCall
}
enum class RouteRefreshError { AUTH, RATE_LIMIT, NETWORK, TIMEOUT, NO_ROUTE, INVALID_COORDINATE, INVALID_RESPONSE, NOT_CONFIGURED, API }
sealed interface RouteRefreshResult {
    /** Private packet body. Never log or put in a public Intent. Contains no proxy credentials. */
    class Success(val payload:String,val route:GuidanceRoute,val measuredAtEpochMillis:Long,val expiresAtEpochMillis:Long):RouteRefreshResult {
        override fun toString()="RouteRefreshResult.Success(PRIVATE_ROUTE)"
    }
    data class Failure(val error:RouteRefreshError):RouteRefreshResult
}

/** Local development proxy credentials, never the upstream Kakao key. */
class ProxyRouteSettings(val baseUrl:String,private val token:String,val expiresAtEpochMillis:Long) {
    init {
        require(baseUrl in setOf("http://127.0.0.1:8765","http://10.0.2.2:8765")) {"Invalid local proxy configuration"}
        require(token.matches(Regex("[A-Za-z0-9_-]{32,128}")) && expiresAtEpochMillis>0) {"Invalid local proxy configuration"}
    }
    internal fun authorization()="Bearer $token"
    override fun toString()="ProxyRouteSettings(REDACTED)"
    companion object {
        fun fromJson(json:JSONObject?):ProxyRouteSettings?=try {
            if(json==null) null else ProxyRouteSettings(json.getString("base_url"),json.getString("token"),
                strictPositiveLong(json,"expires_at_ms"))
        } catch(_:Exception) {null}
    }
}

internal class RouteHttpResponse(val status:Int,val body:String) {
    override fun toString()="RouteHttpResponse(status=$status, body=REDACTED)"
}
internal fun interface RouteHttpTransport {
    fun execute(url:String,authorization:String,body:String,cancellation:RouteHttpCancellation):RouteHttpResponse
}
internal class RouteHttpCancellation {
    private val cancelled=AtomicBoolean(false)
    private val disconnect=AtomicReference<(() -> Unit)?>(null)
    val isCancelled get()=cancelled.get()
    fun register(action:()->Unit) {disconnect.set(action);if(isCancelled) action()}
    fun cancel() {cancelled.set(true);try {disconnect.getAndSet(null)?.invoke()} catch(_:Exception) {}}
    fun clear() {disconnect.set(null)}
}

/** Callbacks use callbackExecutor; the Android screen supplies its main-thread executor. */
class ProxyRouteRefreshClient internal constructor(private val settings:ProxyRouteSettings?,
    private val wallNow:()->Long,private val worker:Executor,private val callbackExecutor:Executor,
    private val transport:RouteHttpTransport):RouteRefreshClient {
    constructor(settings:ProxyRouteSettings?,wallNow:()->Long={System.currentTimeMillis()},
        worker:Executor=workers,callbackExecutor:Executor=Executor {it.run()}):this(settings,wallNow,worker,callbackExecutor,UrlConnectionRouteTransport)

    override fun request(origin:RouteCoordinate,destination:RouteCoordinate,privateDestinationLabel:String,
        callback:(RouteRefreshResult)->Unit):RouteRefreshCall {
        val cancellation=RouteHttpCancellation()
        fun deliver(result:RouteRefreshResult) {
            if(!cancellation.isCancelled) callbackExecutor.execute {
                if(!cancellation.isCancelled) callback(
                    if(result is RouteRefreshResult.Success && wallNow()>=result.expiresAtEpochMillis)
                        RouteRefreshResult.Failure(RouteRefreshError.INVALID_RESPONSE) else result)
            }
        }
        try {
            worker.execute {
                if(cancellation.isCancelled) return@execute
                val result=try {
                    val config=settings
                    if(config==null || wallNow()>=config.expiresAtEpochMillis) RouteRefreshResult.Failure(RouteRefreshError.NOT_CONFIGURED)
                    else {
                        require(privateDestinationLabel.isNotBlank())
                        val body=JSONObject().put("origin",point(origin)).put("destination",point(destination)).put("include_guidance",true).toString()
                        val response=transport.execute(config.baseUrl+"/v1/routes",config.authorization(),body,cancellation)
                        if(response.status==200) validated(response.body,origin,destination,privateDestinationLabel,wallNow())
                        else RouteRefreshResult.Failure(error(response))
                    }
                } catch(_:SocketTimeoutException) {RouteRefreshResult.Failure(RouteRefreshError.TIMEOUT)}
                catch(_:IOException) {RouteRefreshResult.Failure(RouteRefreshError.NETWORK)}
                catch(_:Exception) {RouteRefreshResult.Failure(RouteRefreshError.INVALID_RESPONSE)}
                finally {cancellation.clear()}
                deliver(result)
            }
        } catch(_:java.util.concurrent.RejectedExecutionException) {deliver(RouteRefreshResult.Failure(RouteRefreshError.NETWORK))}
        return RouteRefreshCall {cancellation.cancel()}
    }

    private fun validated(body:String,origin:RouteCoordinate,destination:RouteCoordinate,label:String,now:Long):RouteRefreshResult.Success {
        require(body.toByteArray(Charsets.UTF_8).size<=MAX_RESPONSE)
        val json=JSONObject(body)
        require(json.getString("source")==RouteDataSource.KAKAO_DIRECTIONS.name)
        require(coordinate(json.getJSONObject("origin"))==origin && coordinate(json.getJSONObject("destination"))==destination)
        val measured=strictPositiveLong(json,"measured_at_epoch_ms")
        require(measured<=Long.MAX_VALUE-ROUTE_TTL)
        val expires=minOf(measured+ROUTE_TTL,if(json.has("expires_at_epoch_ms")) strictPositiveLong(json,"expires_at_epoch_ms") else Long.MAX_VALUE)
        require(now<expires)
        // Rebuild an allowlisted route packet so a server response can never supply session or credential metadata.
        val packet=JSONObject()
        listOf("schema_version","source","origin","destination","distance_meters","duration_seconds","geometry","guidance","measured_at_epoch_ms").forEach {
            if(json.has(it)) packet.put(it,json.get(it))
        }
        packet.put("private_destination_label",label).put("expires_at_epoch_ms",expires)
        val payload=packet.toString()
        return RouteRefreshResult.Success(payload,GuidanceParser.parse(payload,now),measured,expires)
    }
    private fun error(response:RouteHttpResponse):RouteRefreshError {
        if(response.status in listOf(401,403)) return RouteRefreshError.AUTH
        if(response.status==429) return RouteRefreshError.RATE_LIMIT
        if(response.status==504) return RouteRefreshError.TIMEOUT
        if(response.status in 300..399) return RouteRefreshError.INVALID_RESPONSE
        val code=try {JSONObject(response.body.take(MAX_RESPONSE)).optString("error")} catch(_:Exception) {""}
        return RouteRefreshError.entries.firstOrNull {it.name==code}?:RouteRefreshError.API
    }
    companion object {
        internal const val MAX_RESPONSE=2_000_000
        private const val ROUTE_TTL=3_600_000L
        private val workers=Executors.newCachedThreadPool {r->Thread(r,"private-route-refresh").apply {isDaemon=true}}
        private fun point(p:RouteCoordinate)=JSONObject().put("longitude",p.longitude).put("latitude",p.latitude)
        private fun coordinate(j:JSONObject):RouteCoordinate {
            val x=j.get("longitude");val y=j.get("latitude");require(x is Number && y is Number)
            return RouteCoordinate(x.toDouble(),y.toDouble())
        }
    }
}

private fun strictPositiveLong(json:JSONObject,key:String):Long {
    val value=json.get(key);require(value is Number && value.toString().matches(Regex("[0-9]+")))
    return value.toString().toLong().also {require(it>0)}
}

private object UrlConnectionRouteTransport:RouteHttpTransport {
    private val deadlines=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"private-route-timeout").apply {isDaemon=true}}
    override fun execute(url:String,authorization:String,body:String,cancellation:RouteHttpCancellation):RouteHttpResponse {
        val connection=URL(url).openConnection(Proxy.NO_PROXY) as HttpURLConnection
        cancellation.register {connection.disconnect()}
        val timedOut=AtomicBoolean(false)
        val deadline=deadlines.schedule({timedOut.set(true);connection.disconnect()},15,TimeUnit.SECONDS)
        try {
            if(cancellation.isCancelled) throw IOException()
            connection.apply {
                instanceFollowRedirects=false;connectTimeout=3000;readTimeout=12000;requestMethod="POST";doOutput=true;useCaches=false
                setRequestProperty("Authorization",authorization);setRequestProperty("Content-Type","application/json; charset=utf-8")
                setRequestProperty("Accept","application/json")
            }
            val bytes=body.toByteArray(Charsets.UTF_8);connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use {it.write(bytes)}
            val status=connection.responseCode
            val input=if(status in 200..299) connection.inputStream else connection.errorStream
            val output=ByteArrayOutputStream()
            input?.use {stream->
                val buffer=ByteArray(8192)
                while(true) {
                    if(timedOut.get()) throw SocketTimeoutException()
                    if(cancellation.isCancelled) throw IOException()
                    val count=stream.read(buffer);if(count<0) break
                    require(output.size()+count<=ProxyRouteRefreshClient.MAX_RESPONSE)
                    output.write(buffer,0,count)
                }
            }
            if(timedOut.get()) throw SocketTimeoutException()
            return RouteHttpResponse(status,String(output.toByteArray(),Charsets.UTF_8))
        } catch(exception:IOException) {
            if(timedOut.get()) throw SocketTimeoutException() else throw exception
        } finally {deadline.cancel(false);connection.disconnect();cancellation.clear()}
    }
}
