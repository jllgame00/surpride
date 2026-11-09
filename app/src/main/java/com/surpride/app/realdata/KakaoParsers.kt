package com.surpride.app.realdata

import org.json.JSONObject
import java.io.IOException

/** Official Directions schema. No response body/error message is logged or sent to UI. */
object KakaoRouteParser {
    fun parse(status:Int,body:String,origin:RouteOrigin,now:Long):RouteReply {
        if(status==401 || status==403) return RouteReply.Failure(RouteError.AUTH)
        if(status==429) return RouteReply.Failure(RouteError.RATE_LIMIT)
        if(status !in 200..299) return RouteReply.Failure(RouteError.API)
        return try {
            require(body.length<=2_000_000)
            val route=JSONObject(body).getJSONArray("routes").getJSONObject(0)
            val code=integer(route,"result_code")
            if(code!=0L) return RouteReply.Failure(when(code) {
                1L,105L,106L,107L -> RouteError.NO_ROUTE
                101L,102L,103L,104L -> RouteError.INVALID_COORDINATE
                else -> RouteError.API
            })
            val summary=route.getJSONObject("summary")
            val points=mutableListOf<GeoPoint>()
            val sections=route.optJSONArray("sections")
            if(route.has("sections")) require(sections!=null)
            if(sections!=null) for(i in 0 until sections.length()) {
                val section=sections.getJSONObject(i)
                if(!section.has("roads")) continue // summary=true omits roads/guides but keeps sections.
                val roads=section.getJSONArray("roads")
                for(j in 0 until roads.length()) {
                    val vertices=roads.getJSONObject(j).getJSONArray("vertexes")
                    require(vertices.length()%2==0)
                    for(k in 0 until vertices.length() step 2) points.add(GeoPoint(vertices.getDouble(k),vertices.getDouble(k+1)))
                }
            }
            RouteReply.Success(RouteLeg(integer(summary,"duration"),integer(summary,"distance"),points.toList(),origin,now))
        } catch(_:Exception) {RouteReply.Failure(RouteError.INVALID_RESPONSE)}
    }
    private fun integer(json:JSONObject,key:String):Long {
        val value=json.get(key)
        require(value is Number && value.toString().matches(Regex("[0-9]+")))
        return value.toString().toLong()
    }
}
data class RouteHttpResponse(val status:Int,val body:String,val origin:RouteOrigin)
/** Transport belongs on a trusted backend. This app supplies no credentials or network transport.
 * Injected fixture transports must report MOCK, even when JSON is copied from API documentation. */
class KakaoRouteProvider(private val transport:(GeoPoint,GeoPoint)->RouteHttpResponse,private val clock:()->Long):RouteProvider {
    override fun route(origin:GeoPoint,destination:GeoPoint):RouteReply=try {
        val response=transport(origin,destination)
        KakaoRouteParser.parse(response.status,response.body,response.origin,clock())
    } catch(_:IOException) {RouteReply.Failure(RouteError.NETWORK)}
      catch(_:Exception) {RouteReply.Failure(RouteError.API)}
}
data class SearchedPlace(val providerId:String,val name:String,val point:GeoPoint,val sourceUrl:String)
object KakaoPlaceParser {
    /** Parsing a document does not validate its facilities, vehicle entry or suitability. */
    fun parse(body:String):List<SearchedPlace> = try {
        require(body.length<=2_000_000)
        val documents=JSONObject(body).getJSONArray("documents")
        (0 until documents.length()).mapNotNull {index->try {
            val item=documents.getJSONObject(index)
            SearchedPlace(item.getString("id").also {require(it.isNotBlank())},item.getString("place_name").also {require(it.isNotBlank())},
                GeoPoint(item.getString("x").toDouble(),item.getString("y").toDouble()),item.getString("place_url"))
        } catch(_:Exception) {null}}
    } catch(_:Exception) {emptyList()}
}
