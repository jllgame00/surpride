package com.surpride.navhostpoc.guidance

import org.json.JSONArray
import org.json.JSONObject

object GuidanceParser {
    fun parse(body:String,now:Long):GuidanceRoute = try {
        require(body.length<=2_000_000)
        val json=JSONObject(body)
        require(number(json,"schema_version",1,1)==1L)
        val at=number(json,"measured_at_epoch_ms",1,Long.MAX_VALUE)
        require(at in now-3_600_000..now+60_000)
        val source=RouteDataSource.valueOf(json.getString("source"))
        val distance=number(json,"distance_meters",1,100_000_000)
        val duration=number(json,"duration_seconds",1,604800)
        val privateLabel=json.getString("private_destination_label").also {require(it.isNotBlank())}
        val data=json.getJSONObject("guidance")
        val roads=data.getJSONArray("segments");require(roads.length() in 1..10000)
        val segments=(0 until roads.length()).map {i->
            val r=roads.getJSONObject(i)
            val name=r.getString("road_name")
            // Fail the route rather than hiding a potentially necessary road/safety instruction.
            require(!name.contains(privateLabel) && name.length<=200 && name.none {it.code<32})
            RouteSegment(name,number(r,"distance_meters",0,100_000_000),number(r,"duration_seconds",0,604800),
                number(r,"traffic_state",0,999).toInt(),points(r.getJSONArray("geometry")))
        }
        require(kotlin.math.abs(segments.sumOf {it.distanceMeters}-distance)<=maxOf(10,distance/100))
        val guides=data.getJSONArray("maneuvers");require(guides.length() in 2..10000)
        var previousDistance=-1L;var previousRoad=-1
        val maneuvers=(0 until guides.length()).map {i->
            val g=guides.getJSONObject(i)
            val offset=number(g,"offset_meters",0,distance)
            val road=number(g,"road_index",0,segments.lastIndex.toLong()).toInt()
            require(offset>=previousDistance && road>=previousRoad)
            previousDistance=offset;previousRoad=road
            Maneuver(number(g,"type",0,10000).toInt(),offset,road)
        }
        require(maneuvers.first().type==100 && maneuvers.first().offsetMeters==0L)
        require(maneuvers.last().type==101 && maneuvers.last().offsetMeters==distance)
        require(maneuvers.dropLast(1).none {it.type==101}) // single-destination PoC only
        val destination=json.getJSONObject("destination")
        GuidanceRoute(source,RouteGeometry(segments.flatMap {it.geometry}),segments,maneuvers,distance,duration,
            RouteCoordinate(destination.getDouble("longitude"),destination.getDouble("latitude")))
    } catch(_:Exception) {throw IllegalArgumentException("Invalid/expired/unsupported private route data")}

    private fun number(j:JSONObject,key:String,min:Long,max:Long):Long {
        val n=j.get(key);require(n is Number && n.toString().matches(Regex("[0-9]+")))
        return n.toString().toLong().also {require(it in min..max)}
    }
    private fun points(array:JSONArray):List<RouteCoordinate> {
        require(array.length() in 2..100000)
        return (0 until array.length()).map {i->val pair=array.getJSONArray(i);require(pair.length()==2);RouteCoordinate(pair.getDouble(0),pair.getDouble(1))}
    }
}
