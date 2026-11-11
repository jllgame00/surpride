package com.surpride.navhostpoc

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.surpride.navhostpoc.guidance.*
import java.io.File
import java.util.TimeZone
import kotlin.math.*
import androidx.car.app.navigation.model.Maneuver as CarManeuver

/** Explicit parked internal replay. No NavigationManager, focus, TTS, notifications or GPS writes. */
internal class DynamicNavigationScreen(context:CarContext):Screen(context) {
    private val handler=Handler(Looper.getMainLooper())
    // CarContext is a virtual rendering display; use the application's physical host display context.
    private val ux=PocParkedMonitor(carContext.applicationContext)
    private var requiresDo:Boolean?=null
    private var surface:SurfaceContainer?=null
    private var visible:Rect?=null
    private var stable:Rect?=null
    private var ticker:Runnable?=null
    private var ownsSurface=false
    private var pendingControl:String?=null
    private val emulator=Build.MODEL=="Connect on x86_64 emulator" && Build.PRODUCT=="crp_avd"
    private val simulator:GuidanceSimulator?=if(emulator) try {
        val file=File(carContext.cacheDir,"route-guidance.json")
        require(file.length() in 1..2_000_000)
        val route=GuidanceParser.parse(file.readText(),System.currentTimeMillis())
        Log.i("SurprideDynamic","ROUTE_LOADED source=${route.source} segments=${route.segments.size} guides=${route.maneuvers.size} points=${route.geometry.points.size}")
        GuidanceSimulator(route)
    } catch(_:Exception) {Log.i("SurprideDynamic","ROUTE_UNAVAILABLE");null} else null
    private val allowed get()=requiresDo==false && emulator

    private val callback=object:SurfaceCallback {
        override fun onSurfaceAvailable(container:SurfaceContainer) {surface=container;drawSurface()}
        override fun onVisibleAreaChanged(area:Rect) {visible=Rect(area);drawSurface()}
        override fun onStableAreaChanged(area:Rect) {stable=Rect(area);drawSurface()}
        override fun onSurfaceDestroyed(container:SurfaceContainer) {surface=null}
    }
    init {
        lifecycle.addObserver(object:DefaultLifecycleObserver {
            override fun onStart(owner:LifecycleOwner) {
                if(emulator) {carContext.getCarService(AppManager::class.java).setSurfaceCallback(callback);ownsSurface=true}
                ux.start {requiresDo=it;if(!allowed) pause() else applyPendingControl();refresh()}
            }
            override fun onStop(owner:LifecycleOwner) {requiresDo=null;pause();ux.stop();detachSurface()}
            override fun onDestroy(owner:LifecycleOwner) {
                pause();ux.stop();simulator?.stop()
                detachSurface()
                surface=null;Log.i("SurprideDynamic","SCREEN_DESTROYED_NO_ACTIVE_ROUTE")
            }
        })
    }
    private fun detachSurface() {
        if(!ownsSurface) return
        val target=surface?.surface
        if(target?.isValid==true) try {val canvas=target.lockCanvas(null);try {canvas.drawColor(Color.rgb(17,18,20))} finally {target.unlockCanvasAndPost(canvas)}} catch(_:Exception) {}
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(null);ownsSurface=false;surface=null
    }
    private fun pause() {ticker?.let(handler::removeCallbacks);ticker=null;simulator?.pause()}
    private fun applyPendingControl() {
        val command=pendingControl?:return
        pendingControl=null
        when(command) {
            "start"->start()
            "next"->{simulator?.start();simulator?.nextEvent();simulator?.pause()}
            "restart"->{pause();simulator?.restart()}
            "stop"->{pause();simulator?.stop()}
            "exit"->{pause();simulator?.stop();carContext.finishCarApp()}
        }
    }
    private fun controls() {
        if(!allowed) return
        screenManager.push(ReplayControlsScreen(carContext) {command->
            if(command=="back") {pendingControl=null;screenManager.popToRoot()}
            else {pendingControl=command;screenManager.pop()}
        })
    }
    private fun refresh() {drawSurface();invalidate()}
    private fun start() {
        if(!allowed || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        simulator?.start();schedule();refresh()
    }
    private fun schedule() {
        if(ticker!=null || simulator?.state()?.status!=GuidanceStatus.RUNNING) return
        ticker=Runnable {
            ticker=null
            if(allowed && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                simulator?.advance(10.0);refresh();schedule()
            } else pause()
        }
        handler.postDelayed(ticker!!,2000) // one refresh per 2 seconds; host may coalesce invalidations.
    }
    override fun onGetTemplate():Template {
        Log.i("SurprideDynamic","SCREEN_STATE ${lifecycle.currentState} ux=$requiresDo")
        val sim=simulator
        // Keep one template type for this Screen ID, including unknown/restricted/stopped states.
        // Switching types on a host Back refresh violates the host's template stack contract.
        if(!allowed || sim==null) return NavigationTemplate.Builder().setActionStrip(ActionStrip.Builder()
            .addAction(Action.Builder().setTitle("종료").setOnClickListener {carContext.finishCarApp()}.build()).build()).build()
        val view=sim.presentation();val state=sim.state()
        if(view.status==GuidanceStatus.STOPPED) {
            Log.i("SurprideDynamic","TEMPLATE status=STOPPED noRoutingInfo=true")
            return NavigationTemplate.Builder().setActionStrip(ActionStrip.Builder()
                .addAction(Action.Builder().setTitle("조작").setOnClickListener {controls()}.build()).build()).build()
        }
        val type=when(state.nextManeuver?.type) {
            0->CarManeuver.TYPE_STRAIGHT;1->CarManeuver.TYPE_TURN_NORMAL_LEFT;2->CarManeuver.TYPE_TURN_NORMAL_RIGHT
            3->CarManeuver.TYPE_U_TURN_LEFT;5,82->CarManeuver.TYPE_KEEP_LEFT;6,83->CarManeuver.TYPE_KEEP_RIGHT
            101->CarManeuver.TYPE_DESTINATION;else->CarManeuver.TYPE_UNKNOWN
        }
        val step=Step.Builder("정차 DEMO · ${view.instruction}").setRoad(view.road).setManeuver(CarManeuver.Builder(type).build()).build()
        val estimate=TravelEstimate.Builder(Distance.create(view.remainingMeters,Distance.UNIT_METERS),
            DateTimeWithZone.create(System.currentTimeMillis()+view.remainingSeconds*1000,TimeZone.getDefault()))
            .setRemainingTimeSeconds(view.remainingSeconds).build()
        val strip=ActionStrip.Builder().addAction(Action.Builder().setTitle("조작").setOnClickListener {controls()}.build()).build()
        Log.i("SurprideDynamic","TEMPLATE status=${view.status} source=${view.source} segment=${state.progress.segmentIndex} maneuver=${state.nextManeuver?.type} nextM=${view.nextDistanceMeters.toInt()} remainingM=${view.remainingMeters.toInt()} etaS=${view.remainingSeconds}")
        return NavigationTemplate.Builder().setNavigationInfo(RoutingInfo.Builder().setCurrentStep(step,Distance.create(view.nextDistanceMeters,Distance.UNIT_METERS)).build())
            .setDestinationTravelEstimate(estimate).setActionStrip(strip).build()
    }
    private fun drawSurface() {
        val target=surface?.surface?:return
        if(!target.isValid) return
        val canvas=try {target.lockCanvas(null)} catch(_:Exception) {return}
        try {
            canvas.drawColor(Color.rgb(17,18,20))
            val bounds=Rect(0,0,canvas.width,canvas.height)
            visible?.takeIf {!it.isEmpty}?.let {bounds.intersect(it)}
            stable?.takeIf {!it.isEmpty}?.let {bounds.intersect(it)}
            bounds.inset(28,28)
            if(bounds.width()<150 || bounds.height()<150) return
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(242,242,242);textSize=(bounds.width()/32f).coerceIn(20f,34f)}
            val view=simulator?.presentation()?.takeIf {allowed}
            val saved=canvas.save();canvas.clipRect(bounds)
            val x=bounds.left.toFloat();var y=bounds.top+paint.textSize
            canvas.drawText("PARKED DEMO / SIMULATED POSITION",x,y,paint);y+=paint.textSize*1.5f
            if(view==null) canvas.drawText(if(simulator==null) "실경로 없음/만료/미지원 · 데이터 재공급 필요" else "정차 확인 중 또는 UX 조작 제한",x,y,paint)
            if(view!=null) {
                canvas.drawText("${view.source} · ${view.status}",x,y,paint);y+=paint.textSize*1.5f
                val direction="${view.instruction} · ${view.nextDistanceMeters.toInt()}m · ${view.road}"
                canvas.drawText(android.text.TextUtils.ellipsize(direction,android.text.TextPaint(paint),bounds.width().toFloat(),android.text.TextUtils.TruncateAt.END).toString(),x,y,paint);y+=paint.textSize*1.5f
                canvas.drawText("남은 ${ceil(view.remainingMeters/100)/10} km / ${ceil(view.remainingSeconds/60.0).toInt()}분 (모의 ETA)",x,y,paint);y+=paint.textSize*1.5f
                paint.color=Color.LTGRAY
                canvas.drawText(view.warning,x,y,paint);y+=paint.textSize*1.5f
                canvas.drawText("지도 아님 · 가까운 경로만 · 실제 안전 안내 미검증",x,y,paint)
                if(allowed && view.localPath.size>1) {
                    val area=Rect(bounds.left,bounds.top+(paint.textSize*10).toInt(),bounds.right,bounds.bottom-40)
                    if(area.height()>60) {
                        val points=view.localPath
                        val minX=points.minOf {it.eastMeters};val maxX=points.maxOf {it.eastMeters}
                        val minY=points.minOf {it.northMeters};val maxY=points.maxOf {it.northMeters}
                        val scale=min(area.width()/max(maxX-minX,80.0),area.height()/max(maxY-minY,80.0))*.8
                        val path=Path()
                        points.forEachIndexed {i,p->
                            val px=(area.centerX()+(p.eastMeters-(minX+maxX)/2)*scale).toFloat()
                            val py=(area.centerY()-(p.northMeters-(minY+maxY)/2)*scale).toFloat()
                            if(i==0) path.moveTo(px,py) else path.lineTo(px,py)
                        }
                        paint.color=Color.rgb(242,242,242);paint.style=Paint.Style.STROKE;paint.strokeWidth=10f
                        canvas.drawPath(path,paint)
                    }
                }
            }
            canvas.restoreToCount(saved)
        } catch(_:Exception) {Log.i("SurprideDynamic","SURFACE_RENDER_FAILED")}
        finally {try {target.unlockCanvasAndPost(canvas)} catch(_:Exception) {}}
    }
}

/** The Pleos host exposes one custom strip action; use standard parked list rows for replay controls. */
private class ReplayControlsScreen(context:CarContext,private val selected:(String)->Unit):Screen(context) {
    private val ux=PocParkedMonitor(carContext.applicationContext)
    private var allowed=false
    init {lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onStart(owner:LifecycleOwner) {ux.start {allowed=it==false;invalidate()}}
        override fun onStop(owner:LifecycleOwner) {allowed=false;ux.stop()}
        override fun onDestroy(owner:LifecycleOwner) {ux.stop()}
    })}
    override fun onGetTemplate():Template {
        val list=ItemList.Builder()
        if(!allowed) list.addItem(Row.Builder().setTitle("정차 확인 중 / 조작 제한").build())
        else listOf("시작 / 계속" to "start","정적 PoC로 돌아가기" to "back","다음 안내로 이동 (모의)" to "next",
            "처음부터 준비" to "restart","시뮬레이션 중단" to "stop","PoC 종료" to "exit").forEach {(title,command)->
            list.addItem(Row.Builder().setTitle(title).setOnClickListener {if(allowed) selected(command)}.build())
        }
        return ListTemplate.Builder().setTitle("정차 DEMO · 메뉴에서 일시정지").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
