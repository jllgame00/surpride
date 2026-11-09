package com.surpride.app.realdata

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.math.abs

/** A short-lived local development credential, supplied after installation; never a provider key. */
data class ProxySettings(val baseUrl:String,val token:String,val expiresAtMillis:Long) {
    init {
        val uri=URI(baseUrl)
        require(uri.scheme=="http" && uri.host in setOf("10.0.2.2","127.0.0.1") && uri.port==8765 &&
            uri.userInfo==null && uri.rawQuery==null && uri.rawFragment==null && uri.path.isNullOrEmpty())
        require(token.matches(Regex("[A-Za-z0-9_-]{32,128}")))
    }
    override fun toString()="ProxySettings(REDACTED)"
}
data class ProxyHttpResponse(val status:Int,val body:String) {
    override fun toString()="ProxyHttpResponse(status=$status, body=REDACTED)"
}
object ProxyRouteParser {
    fun parse(status:Int,body:String,origin:GeoPoint,destination:GeoPoint,now:Long):RouteReply {
        if(status !in 200..299) {
            val error=try {RouteError.valueOf(JSONObject(body).getString("error"))} catch(_:Exception) {
                when(status) {401,403->RouteError.AUTH;429->RouteError.RATE_LIMIT;else->RouteError.API}
            }
            return RouteReply.Failure(error)
        }
        return try {
            require(body.length<=2_000_000)
            val json=JSONObject(body)
            require(json.get("schema_version").toString()=="1")
            val source=when(json.getString("source")) {
                "KAKAO_DIRECTIONS"->RouteOrigin.LIVE_API
                "MOCK"->RouteOrigin.MOCK
                else->throw IllegalArgumentException()
            }
            fun matches(name:String,p:GeoPoint):Boolean {
                val point=json.getJSONObject(name)
                return abs(point.getDouble("longitude")-p.longitude)<0.0000001 && abs(point.getDouble("latitude")-p.latitude)<0.0000001
            }
            require(matches("origin",origin) && matches("destination",destination))
            val timestamp=json.get("measured_at_epoch_ms")
            require(timestamp is Number && timestamp.toString().matches(Regex("[0-9]+")))
            val at=timestamp.toString().toLong()
            if(at<=0 || at>now+60_000 || at<now-300_000) return RouteReply.Failure(RouteError.STALE_DATA)
            val points=json.getJSONArray("geometry")
            require(points.length()<=100_000)
            val vertices=JSONArray()
            for(i in 0 until points.length()) {
                val pair=points.getJSONArray(i);require(pair.length()==2)
                val point=GeoPoint(pair.getDouble(0),pair.getDouble(1))
                vertices.put(point.longitude);vertices.put(point.latitude)
            }
            // Reuse the original official-schema validation for seconds/meters/geometry.
            val route=JSONObject().put("result_code",0)
                .put("summary",JSONObject().put("duration",json.get("duration_seconds")).put("distance",json.get("distance_meters")))
                .put("sections",JSONArray().put(JSONObject().put("roads",JSONArray().put(JSONObject().put("vertexes",vertices)))))
            KakaoRouteParser.parse(200,JSONObject().put("routes",JSONArray().put(route)).toString(),source,at)
        } catch(_:Exception) {RouteReply.Failure(RouteError.INVALID_RESPONSE)}
    }
}
/** Blocking contract; UI calls on a cancellable worker. No provider key exists in this process. */
class LiveRouteProvider(private val settings:ProxySettings?,
    private val transport:(ProxySettings,GeoPoint,GeoPoint)->ProxyHttpResponse=ProxyHttpTransport::post,
    private val clock:()->Long=System::currentTimeMillis):RouteProvider {
    override fun route(origin:GeoPoint,destination:GeoPoint):RouteReply {
        val config=settings?.takeIf {it.expiresAtMillis>clock()}?:return RouteReply.Failure(RouteError.NOT_CONFIGURED)
        if(Thread.currentThread().isInterrupted) return RouteReply.Failure(RouteError.NETWORK)
        return try {
            val response=transport(config,origin,destination)
            if(Thread.currentThread().isInterrupted) RouteReply.Failure(RouteError.NETWORK)
            else ProxyRouteParser.parse(response.status,response.body,origin,destination,clock())
        } catch(_:IOException) {RouteReply.Failure(RouteError.NETWORK)}
          catch(_:Exception) {RouteReply.Failure(RouteError.API)}
    }
}
object ProxyHttpTransport {
    fun post(settings:ProxySettings,origin:GeoPoint,destination:GeoPoint):ProxyHttpResponse {
        val connection=URL(settings.baseUrl+"/v1/routes").openConnection() as HttpURLConnection
        try {
            connection.requestMethod="POST";connection.connectTimeout=3000;connection.readTimeout=12000
            connection.instanceFollowRedirects=false;connection.useCaches=false;connection.doOutput=true
            connection.setRequestProperty("Authorization","Bearer "+settings.token)
            connection.setRequestProperty("Content-Type","application/json")
            fun point(p:GeoPoint)=JSONObject().put("longitude",p.longitude).put("latitude",p.latitude)
            val bytes=JSONObject().put("origin",point(origin)).put("destination",point(destination)).toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use {it.write(bytes)}
            val status=connection.responseCode
            val stream=if(status in 200..299) connection.inputStream else connection.errorStream
            val body=stream?.use {
                val output=java.io.ByteArrayOutputStream()
                val buffer=ByteArray(8192)
                while(true) {
                    if(Thread.currentThread().isInterrupted) throw IOException("Cancelled")
                    val count=it.read(buffer);if(count<0) break
                    if(output.size()+count>2_000_000) return ProxyHttpResponse(502,"{\"error\":\"INVALID_RESPONSE\"}")
                    output.write(buffer,0,count)
                }
                output.toString("UTF-8")
            }?:""
            return ProxyHttpResponse(status,body)
        } finally {connection.disconnect()}
    }
}
