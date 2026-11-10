package com.surpride.navhostpoc.guidance

import org.json.JSONArray
import org.json.JSONObject

/** One hand-measured 100m/900m L-shaped geometry with 500m/500m API roads. */
internal object GuidanceAccuracyFixture {
    fun point(x:Double,y:Double=0.0)=RouteCoordinate(x/111195.0,y/111195.0)
    fun payload(now:Long):JSONObject {
        fun road(name:String,distance:Long,vararg points:RouteCoordinate)=JSONObject().put("road_name",name)
            .put("distance_meters",distance).put("duration_seconds",50).put("traffic_state",4)
            .put("geometry",JSONArray().apply {points.forEach {put(JSONArray(listOf(it.longitude,it.latitude)))}})
        fun guide(type:Int,offset:Long,index:Int)=JSONObject().put("type",type).put("offset_meters",offset).put("road_index",index)
        return JSONObject().put("schema_version",1).put("source","KAKAO_DIRECTIONS").put("measured_at_epoch_ms",now)
            .put("private_destination_label","Private").put("origin",JSONObject().put("longitude",0).put("latitude",0))
            .put("destination",JSONObject().put("longitude",point(100.0,-900.0).longitude).put("latitude",point(100.0,-900.0).latitude))
            .put("distance_meters",1000).put("duration_seconds",100).put("guidance",JSONObject()
                .put("segments",JSONArray().put(road("A",500,point(0.0),point(100.0)))
                    .put(road("B",500,point(100.0),point(100.0,-900.0))))
                .put("maneuvers",JSONArray().put(guide(100,0,0)).put(guide(2,500,1)).put(guide(101,1000,1))))
    }
}
