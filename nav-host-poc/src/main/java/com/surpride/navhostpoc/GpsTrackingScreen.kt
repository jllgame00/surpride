package com.surpride.navhostpoc

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.util.Log
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.tracking.*
import java.io.File
import kotlin.math.*
import androidx.car.app.navigation.model.Maneuver as CarManeuver

/** Read-only emulator position experiment. No active route/focus/Trip/voice/GPS injection. */
internal class GpsTrackingScreen(context:CarContext):Screen(context) {
    private val handler=Handler(Looper.getMainLooper())
    private val ux=PocParkedMonitor(carContext.applicationContext)
    private var requiresDo:Boolean?=null
    private var desired=false
    private var subscribed=false
    private var pending:String?=null
    private var received=0
    private var providerStatus="NOT_STARTED"
    private var renderTask:Runnable?=null
    private var lastRender=0L
    private var surface:SurfaceContainer?=null
    private var visible:Rect?=null
    private var stable:Rect?=null
    private var ownsSurface=false
    private val emulator=Build.MODEL=="Connect on x86_64 emulator" && Build.PRODUCT=="crp_avd"
    private val allowed get()=emulator && requiresDo==false
    private val route=if(emulator) try {
        val file=File(carContext.cacheDir,"route-guidance.json");require(file.length() in 1..2_000_000)
        GuidanceParser.parse(file.readText(),System.currentTimeMillis())
    } catch(_:Exception) {null} else null
    private val matcher=route?.let {RouteMatcher(it)}
    private val session=PositionSession(AndroidPositionProvider(carContext.applicationContext)) {event->
        if(!subscribed || !allowed) return@PositionSession
        when(event) {
            is PositionEvent.Fix->{received++;providerStatus="DEVICE_API";matcher?.accept(event.value,SystemClock.elapsedRealtime())}
            is PositionEvent.Unavailable->{providerStatus=event.reason.name;matcher?.unavailable(providerStatus)}
        }
        recordState();refresh()
    }
    private val timeoutCheck=object:Runnable {
        override fun run() {if(subscribed && allowed) {matcher?.expire(SystemClock.elapsedRealtime());refresh();handler.postDelayed(this,1000)}}
    }
    private val callback=object:SurfaceCallback {
        override fun onSurfaceAvailable(container:SurfaceContainer) {surface=container;paint()}
        override fun onVisibleAreaChanged(area:Rect) {visible=Rect(area);paint()}
        override fun onStableAreaChanged(area:Rect) {stable=Rect(area);paint()}
        override fun onSurfaceDestroyed(container:SurfaceContainer) {surface=null}
    }
    init {lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onStart(owner:LifecycleOwner) {
            if(emulator) {carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback);ownsSurface=true}
            ux.start {requiresDo=it
                if(!allowed) suspendUpdates()
                else {
                    when(pending) {
                        "start"->{desired=true;matcher?.reset()}
                        "pause"->{desired=false;matcher?.unavailable("PAUSED")}
                    }
                    pending=null
                    if(desired) beginUpdates()
                }
                refresh(true)
            }
        }
        override fun onStop(owner:LifecycleOwner) {requiresDo=null;suspendUpdates();ux.stop();detach()}
        override fun onDestroy(owner:LifecycleOwner) {desired=false;suspendUpdates();ux.stop();detach();Log.i("SurprideGps","SCREEN_DESTROYED")}
    })}
    private fun beginUpdates() {
        if(!allowed || subscribed || matcher==null) return
        matcher.reset();subscribed=true;providerStatus="WAITING_FOR_DEVICE_API"
        session.start();handler.post(timeoutCheck)
        Log.i("SurprideGps","SUBSCRIBED foreground=true")
    }
    private fun suspendUpdates() {
        val wasActive=subscribed;subscribed=false;session.stop();handler.removeCallbacks(timeoutCheck)
        renderTask?.let(handler::removeCallbacks);renderTask=null
        matcher?.unavailable("PAUSED_OR_UX_UNKNOWN")
        if(wasActive) Log.i("SurprideGps","UNSUBSCRIBED")
    }
    private fun recordState() {
        val s=matcher?.state()
        // Deliberately no coordinates, timestamps, road/POI identity or raw Location toString.
        Log.i("SurprideGps","FIX n=$received source=${s?.source} status=${s?.status} reason=${s?.reason} progressM=${s?.progress?.traveledMeters?.toInt()} nextM=${s?.progress?.remainingToManeuverMeters?.toInt()}")
    }
    private fun refresh(immediate:Boolean=false) {
        if(immediate || SystemClock.elapsedRealtime()-lastRender>=2000) {
            renderTask?.let(handler::removeCallbacks);renderTask=null;lastRender=SystemClock.elapsedRealtime();paint();invalidate()
        } else if(renderTask==null) {
            renderTask=Runnable {renderTask=null;refresh(true)};handler.postDelayed(renderTask!!,2000-(SystemClock.elapsedRealtime()-lastRender))
        }
    }
    private fun controls() {
        if(!allowed) return
        screenManager.push(GpsControlsScreen(carContext) {command->
            if(command=="back") {desired=false;screenManager.popToRoot()}
            else if(command=="exit") {desired=false;carContext.finishCarApp()}
            else {pending=command;screenManager.pop()}
        })
    }
    override fun onGetTemplate():Template {
        val builder=NavigationTemplate.Builder().setActionStrip(ActionStrip.Builder().addAction(Action.Builder().setTitle("GPS 조작").setOnClickListener {controls()}.build()).build())
        val view=if(allowed && subscribed) matcher?.presentation() else null
        if(view!=null) {
            val code=matcher?.state()?.nextManeuver?.type
            val type=when(code) {0->CarManeuver.TYPE_STRAIGHT;1->CarManeuver.TYPE_TURN_NORMAL_LEFT;2->CarManeuver.TYPE_TURN_NORMAL_RIGHT;3->CarManeuver.TYPE_U_TURN_LEFT;5,82->CarManeuver.TYPE_KEEP_LEFT;6,83->CarManeuver.TYPE_KEEP_RIGHT;else->CarManeuver.TYPE_UNKNOWN}
            val step=Step.Builder("정차 위치추적 · ${view.instruction}").setRoad(view.road).setManeuver(CarManeuver.Builder(type).build()).build()
            builder.setNavigationInfo(RoutingInfo.Builder().setCurrentStep(step,Distance.create(view.nextDistanceMeters,Distance.UNIT_METERS)).build())
        }
        // No native ETA: stale provider duration is only an approximate recorded-route ratio.
        // The OEM-owned bottom card is clipped in this host; diagnostic ETA stays inside Surface.
        val s=matcher?.state()
        Log.i("SurprideGps","TEMPLATE status=${s?.status} subscribed=$subscribed progressM=${s?.progress?.traveledMeters?.toInt()} nextM=${s?.progress?.remainingToManeuverMeters?.toInt()} nativeEta=false")
        return builder.build()
    }
    private fun detach() {
        if(!ownsSurface) return
        surface?.surface?.takeIf {it.isValid}?.let {s->try {val c=s.lockCanvas(null);try {c.drawColor(Color.rgb(17,18,20))} finally {s.unlockCanvasAndPost(c)}} catch(_:Exception) {}}
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null);surface=null;ownsSurface=false
    }
    private fun paint() {
        val s=surface?.surface?.takeIf {it.isValid}?:return
        val canvas=try {s.lockCanvas(null)} catch(_:Exception) {return}
        try {
            canvas.drawColor(Color.rgb(17,18,20))
            val area=Rect(0,0,canvas.width,canvas.height)
            visible?.takeIf {!it.isEmpty}?.let {area.intersect(it)};stable?.takeIf {!it.isEmpty}?.let {area.intersect(it)};area.inset(32,32)
            val save=canvas.save();canvas.clipRect(area)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(242,242,242);textSize=(area.width()/34f).coerceIn(20f,32f)}
            var y=area.top+p.textSize
            fun line(text:String) {val value=android.text.TextUtils.ellipsize(text,android.text.TextPaint(p),area.width().toFloat(),android.text.TextUtils.TruncateAt.END);canvas.drawText(value.toString(),area.left.toFloat(),y,p);y+=p.textSize*1.4f}
            line("GPS API / EMULATOR · PARKED EXPERIMENT")
            val state=matcher?.state()
            line(if(!allowed) "정차 UX 확인 / 제한 중" else if(route==null) "실경로 데이터 없음/만료" else "${state?.status} · ${state?.reason} · 수신 $received")
            line("${state?.source?:"NO_FIX"} / $providerStatus · 실차 주행·실제 안내 아님")
            val view=if(allowed && subscribed) matcher?.presentation() else null
            if(view!=null && state!=null) {
                line("진행 ${state.progress!!.traveledMeters.toInt()}m · 다음 ${view.nextDistanceMeters.toInt()}m · ${view.instruction}")
                line("${view.road} · 편차 ${"%.1f".format(state.deviationMeters)}m / 정확도 ${state.accuracyMeters}m")
                line("잔여 ${ceil(view.remainingMeters/100)/10}km / 약 ${ceil(view.remainingSeconds/60.0).toInt()}분 · API 기록 비율 추정 ETA")
                line("실시간 교통 갱신 아님 · 자동 재탐색/음성/활성 경로 없음")
                val map=Rect(area.left,y.toInt()+30,area.right,area.bottom-32)
                if(map.height()>80 && view.localPath.size>1) {
                    val points=view.localPath;val minX=points.minOf {it.eastMeters};val maxX=points.maxOf {it.eastMeters};val minY=points.minOf {it.northMeters};val maxY=points.maxOf {it.northMeters}
                    val scale=min(map.width()/max(maxX-minX,80.0),map.height()/max(maxY-minY,80.0))*.8
                    val path=Path();points.forEachIndexed {i,point->val x=(map.centerX()+(point.eastMeters-(minX+maxX)/2)*scale).toFloat();val py=(map.centerY()-(point.northMeters-(minY+maxY)/2)*scale).toFloat();if(i==0) path.moveTo(x,py) else path.lineTo(x,py)}
                    p.style=Paint.Style.STROKE;p.strokeWidth=10f;canvas.drawPath(path,p)
                }
            } else {line("진행률/회전/ETA UNKNOWN · 조작에서 권한 확인 및 시작");line("좌표 비표시·미저장 · 부정확/오래된 위치로 안내하지 않음")}
            canvas.restoreToCount(save)
        } finally {try {s.unlockCanvasAndPost(canvas)} catch(_:Exception) {}}
    }
}

private class GpsControlsScreen(context:CarContext,private val selected:(String)->Unit):Screen(context) {
    private val ux=PocParkedMonitor(carContext.applicationContext)
    private var allowed=false
    init {lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onStart(owner:LifecycleOwner) {ux.start {allowed=it==false;invalidate()}}
        override fun onStop(owner:LifecycleOwner) {allowed=false;ux.stop()}
        override fun onDestroy(owner:LifecycleOwner) {ux.stop()}
    })}
    override fun onGetTemplate():Template {
        val list=ItemList.Builder()
        if(!allowed) list.addItem(Row.Builder().setTitle("정차 확인 중 / 조작 제한").build()) else {
            val precise=ContextCompat.checkSelfPermission(carContext,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            list.addItem(Row.Builder().setTitle(if(precise) "위치 권한 허용됨 · 재확인" else "전경 위치 권한 요청").setOnClickListener {
                if(allowed) carContext.requestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) {granted,_->
                    Log.i("SurprideGps","PERMISSION_RESULT precise=${Manifest.permission.ACCESS_FINE_LOCATION in granted}");invalidate()
                }
            }.build())
            listOf("GPS 추적 시작 / 다시 시작" to "start","일시정지" to "pause","정적 PoC로 돌아가기" to "back","PoC 종료" to "exit").forEach {(title,command)->
                list.addItem(Row.Builder().setTitle(title).setOnClickListener {if(allowed) selected(command)}.build())
            }
        }
        return ListTemplate.Builder().setTitle("GPS · 정차 전용 · 메뉴에서는 구독 중지").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
