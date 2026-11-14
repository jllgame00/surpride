package com.surpride.navhostpoc

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.map.RoadMapRenderer
import com.surpride.navhostpoc.tracking.*
import org.json.JSONObject
import java.io.File
import kotlin.math.ceil

/** Debug-only foreground map preview. No NavigationManager, focus, voice or route-state writes. */
class ReplayPreviewActivity:Activity() {
    private val ux by lazy {PocParkedMonitor(applicationContext)}
    private var allowed=false
    private var subscribed=false
    private var route:GuidanceRoute?=null
    private var measuredAt=0L
    private var matcher:RouteMatcher?=null
    private var fix:PositionFix?=null
    private var renderer:RoadMapRenderer?=null
    private lateinit var map:View
    private lateinit var detail:TextView
    private var received=0
    private val positions by lazy {PositionSession(AndroidPositionProvider(applicationContext)) {event->
        if(allowed && subscribed) {
            if(System.currentTimeMillis()-measuredAt !in 0..3_600_000) {
                stopPositions();detail.text="경로 만료 · 다시 준비해 주세요"
            } else when(event) {
                is PositionEvent.Fix->{
                    val state=matcher!!.accept(event.value,SystemClock.elapsedRealtime())
                    fix=event.value.takeIf {state.status==MatchStatus.ON_ROUTE}
                    val p=state.progress
                    detail.text=if(p==null) "GPS 재현 위치 확인 중 · ${state.status}" else
                        "${maneuverText(state.nextManeuver?.type?:100)} · ${ceil(p.remainingToManeuverMeters).toInt()} m\n"+
                        "진행 ${p.traveledMeters.toInt()} m · 남은 ${p.remainingMeters.toInt()} m · ${route!!.segments[p.segmentIndex].roadName}"
                    received++
                    Log.i("SurpridePreview","FIX n=$received source=${event.value.source} status=${state.status} progressM=${p?.traveledMeters?.toInt()} nextM=${p?.remainingToManeuverMeters?.toInt()} maneuver=${state.nextManeuver?.type} active=false")
                }
                is PositionEvent.Unavailable->{fix=null;matcher?.unavailable(event.reason.name);detail.text="위치 확인 필요 · ${event.reason}"}
            }
            map.invalidate()
        }
    }}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        if(Build.MODEL!="Connect on x86_64 emulator" || Build.PRODUCT!="crp_avd") {finish();return}
        route=try {
            val file=File(cacheDir,"route-guidance.json");require(file.length() in 1..2_000_000)
            val raw=file.readText();measuredAt=JSONObject(raw).getLong("measured_at_epoch_ms")
            PublicReplayRoute.parse(raw,System.currentTimeMillis())
        } catch(_:Exception) {null}
        matcher=route?.let(::RouteMatcher)
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(17,18,20))}
        fun label(value:String,size:Float)=TextView(this).apply {
            text=value;textSize=size;setTextColor(Color.rgb(242,242,242));setPadding(24,8,24,8)
        }
        val header=LinearLayout(this).apply {gravity=Gravity.CENTER_VERTICAL}
        header.addView(label("SURPRIDE / GPS REPLAY PREVIEW",28f),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(this).apply {text="Preview 종료";textSize=24f;minHeight=88;setOnClickListener {finish()}})
        root.addView(header)
        root.addView(label("실제 Kakao 경로 · 에뮬레이터 GPS 재현 · HOST ACTIVE 아님",22f))
        detail=label(if(route==null) "공개 시험 경로 없음 / 만료" else "정차 UX와 GPS 확인 중",28f)
        map=object:View(this) {
            override fun onDraw(canvas:Canvas) {
                super.onDraw(canvas)
                if(allowed) renderer?.render(canvas,Rect(0,0,width,height),route,fix,matcher?.state(),
                    "GPS REPLAY PREVIEW · 실제 주행 아님",true)
            }
        }
        root.addView(map,LinearLayout.LayoutParams(-1,0,1f));root.addView(detail)
        setContentView(root)
        root.setOnApplyWindowInsetsListener {v,i->v.setPadding(i.systemWindowInsetLeft,i.systemWindowInsetTop,i.systemWindowInsetRight,i.systemWindowInsetBottom);i}
    }
    override fun onStart() {
        super.onStart()
        if(!::map.isInitialized)return
        renderer=RoadMapRenderer(applicationContext) {map.invalidate()}
        ux.start {value->
            allowed=value==false
            if(allowed && route!=null && !subscribed) {subscribed=true;positions.start()}
            else if(!allowed) {stopPositions();detail.text="정차 UX 확인 중 / 조작 제한"}
            map.invalidate()
        }
    }
    private fun stopPositions() {subscribed=false;positions.stop();fix=null;matcher?.unavailable("PREVIEW_STOPPED")}
    override fun onStop() {
        allowed=false;stopPositions();ux.stop();renderer?.close();renderer=null
        Log.i("SurpridePreview","STOPPED subscribed=false active=false")
        super.onStop()
    }
}

/** Keep this capture entry point restricted to the same public route as backend.nav_poc. */
internal object PublicReplayRoute {
    fun parse(raw:String,now:Long):GuidanceRoute {
        require(raw.length<=2_000_000)
        val json=JSONObject(raw)
        require(json.optBoolean("development_public_replay") && json.getString("source")=="KAKAO_DIRECTIONS")
        fun point(key:String,lon:Double,lat:Double) {
            val p=json.getJSONObject(key);require(p.getDouble("longitude")==lon && p.getDouble("latitude")==lat)
        }
        point("origin",126.976787992565,37.5716228232824)
        point("destination",126.970850893942,37.517820588578)
        return GuidanceParser.parse(raw,now)
    }
}
