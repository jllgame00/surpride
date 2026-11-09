package com.surpride.navhostpoc

import android.content.Intent
import androidx.car.app.CarContext
import com.surpride.navhostpoc.guidance.GuidanceParser
import com.surpride.navhostpoc.guidance.GuidanceRoute
import com.surpride.navhostpoc.ownership.DestinationIdentity
import org.json.JSONObject
import java.io.File

/** Private ephemeral handoff. Never serialize this object into logs or public Intent extras. */
internal class ActiveRoutePacket(val route:GuidanceRoute,val identity:DestinationIdentity,val reason:String,
    val candidateId:String?,val nonce:String?,private val measuredAt:Long,private val expiresAt:Long) {
    fun isFresh(now:Long)=validAt(measuredAt,expiresAt,now)
    companion object {
        internal fun validAt(measuredAt:Long,expiresAt:Long,now:Long)=measuredAt>0 &&
            now-measuredAt in -60_000..3_600_000 && now<expiresAt
        fun load(context:CarContext,intent:Intent?=null):ActiveRoutePacket?=try {
            val file=File(context.cacheDir,"route-guidance.json")
            val uri=intent?.data
            val raw=if(uri!=null) {
                require(uri.scheme=="content" && uri.authority=="com.surpride.app.navigationfiles")
                val bytes=context.contentResolver.openInputStream(uri)!!.use {it.readBytesBounded()}
                String(bytes,Charsets.UTF_8)
            } else {require(file.length() in 1..2_000_000);file.readText()}
            val json=JSONObject(raw)
            if(uri!=null) {
                val now=System.currentTimeMillis()
                require(json.getLong("prepared_at_epoch_ms") in (now-3_600_000)..(now+60_000))
                require(json.getLong("expires_at_epoch_ms")>now)
                require(json.getString("session_nonce").matches(Regex("[a-fA-F0-9-]{36}")))
            }
            val route=GuidanceParser.parse(raw,System.currentTimeMillis())
            val packet=ActiveRoutePacket(route,DestinationIdentity(json.getString("private_destination_label"),null),
                json.optString("private_reason","실제 자동차 경로 · 개발용 공개 장소 검증"),
                json.optString("candidate_id").takeIf {it.isNotBlank()},json.optString("session_nonce").takeIf {it.isNotBlank()},
                json.getLong("measured_at_epoch_ms"),minOf(json.optLong("expires_at_epoch_ms",Long.MAX_VALUE),json.getLong("measured_at_epoch_ms")+3_600_000))
            if(uri!=null) file.writeText(raw)
            packet
        } catch(_:Exception) {null}
        private fun java.io.InputStream.readBytesBounded():ByteArray {
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val n=read(buffer);if(n<0) break;require(out.size()+n<=2_000_000);out.write(buffer,0,n)}
            return out.toByteArray()
        }
    }
}
