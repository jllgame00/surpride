package com.surpride.navhostpoc

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.util.Log
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.map.RoadMapRenderer
import com.surpride.navhostpoc.ownership.*
import com.surpride.navhostpoc.tracking.*
import com.surpride.navhostpoc.routing.*
import com.surpride.navhostpoc.voice.*
import com.surpride.navhostpoc.returntrip.*
import java.util.concurrent.Executor
import kotlin.math.ceil
import androidx.car.app.navigation.model.Maneuver as CarManeuver

/** Parked AVD only. Launch/import never activates a route. Explicit one-shot operator action required. */
internal class ActiveMysteryScreen(context:CarContext,private var packet:ActiveRoutePacket?=ActiveRoutePacket.load(context)):Screen(context) {
    private val handler=Handler(Looper.getMainLooper())
    private val ux=PocParkedMonitor(carContext.applicationContext)
    private var requiresDo:Boolean?=null
    private var foreground=false
    private val emulator=Build.MODEL=="Connect on x86_64 emulator" && Build.PRODUCT=="crp_avd"
    private val allowed get()=foreground && emulator && requiresDo==false
    private var route=packet?.route
    private var matcher=route?.let(::RouteMatcher)
    private var waypointProgress=packet?.let {WaypointProgress(it.route,it.requestOptions.waypoints.size)}
    private val arrival=ArrivalGate()
    private var fix:PositionFix?=null
    private var revealed=false
    private var received=0
    private var sent=0
    private var subscribed=false
    private var pendingStart=false
    private var pendingStartAt=0L
    private var result="PREPARED / NO ACTIVE GUIDANCE"
    private var surface:SurfaceContainer?=null
    private var visible:Rect?=null
    private var stable:Rect?=null
    private var renderer:RoadMapRenderer?=null
    private var lastRefresh=0L
    private var scheduled=false
    private var controlsOpen=false
    private val menuBack=object:OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {closeControls()}
    }
    private val diagnosticsEnabled get()=carContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0
    private var focus=AndroidCarFocusPort(carContext.applicationContext)
    private var oem=PleosOemRouteMonitor(carContext.applicationContext)
    private var hasStarted=false
    private var voice=AndroidNavigationVoice(carContext.applicationContext)
    private var voiceOn=false
    private var guidanceGeneration=1L
    private var reroute:RerouteCoordinator?=null
    private var loadingPublished=false
    private var gpsMissingSince:Long?=null
    private var startedAt=packet?.startedAt
    private var budget=startedAt?.let {packet?.budget(it)}
    private var returnEstimate=packet?.initialReturnEstimate()
    private var returning=packet?.returning?:false
    private var returnCompleted=false
    private var arrivedAt:Long?=null
    private var pendingPreparation:String?=null
    private var preparation:RouteRefreshCall?=null
    private var preparationGeneration=0L
    private var returnEstimateCall:RouteRefreshCall?=null
    private var lastEstimateAttempt=0L
    private var lastPreparationAt:Long?=null
    private val refreshClient:RouteRefreshClient get()=ProxyRouteRefreshClient(packet?.proxySettings,callbackExecutor=Executor {handler.post(it)})
    // Latest user instruction authorizes a controlled AVD experiment under the contest-permitted assumption.
    // Default gate elsewhere stays CLOSED. This is not an assertion of Pleos marketplace approval.
    private var adapter=newAdapter()
    private fun newAdapter()=AndroidXNavigationOwnershipAdapter({carContext.getCarService(NavigationManager::class.java)},ReadinessFocusPort(focus) {readyForGuidance()},
        {voice.setOwnership(false);reroute?.cancel();cancelRequests();stopPosition();matcher?.unavailable("GUIDANCE_STOPPED");loadingPublished=false;result="GUIDANCE_STOPPED";refresh()},HostIntegrationGate(true,true,emulator))
    private fun acquiringOrActive()=adapter.state in listOf(OwnershipState.REQUESTED,OwnershipState.ACTIVE)
    private fun readyForGuidance()=allowed && oem.status.routeActive==false && packet?.isFresh(System.currentTimeMillis())==true &&
        matcher?.state()?.status==MatchStatus.ON_ROUTE && fix?.let {SystemClock.elapsedRealtime()-it.elapsedMillis in 0..5000}==true
    private val positions=PositionSession(AndroidPositionProvider(carContext.applicationContext)) {event->
        if(!subscribed || !allowed) return@PositionSession
        when(event) {
            is PositionEvent.Fix->{
                if(fix?.let {event.value.elapsedMillis<=it.elapsedMillis}==true) return@PositionSession
                received++;val state=matcher!!.accept(event.value,SystemClock.elapsedRealtime())
                if(state.reason !in listOf("STALE_OR_FUTURE","LOW_OR_INVALID_ACCURACY","OUT_OF_ORDER")) fix=event.value
                waypointProgress?.observe(state.progress,state.status==MatchStatus.ON_ROUTE)
                if(adapter.state==OwnershipState.ACTIVE) {
                    if(waypointProgress?.canConfirmFinalArrival(state.progress)==true)
                        arrival.observe(event.value,state,route!!.distanceToRouteEnd(event.value.coordinate),requiresDo==false,SystemClock.elapsedRealtime())
                    else arrival.invalidate()
                }
                maybeStart()
                maybePrepare()
                if(adapter.state==OwnershipState.ACTIVE) {
                    ensureDeparture()
                    if(state.status==MatchStatus.OFF_ROUTE && waypointProgress?.canRefreshDirect()!=true) stopForWaypointRefresh()
                    else {reroute?.updateEligibility(true,allowed);reroute?.onMatch(event.value,state.status)}
                    when(state.status) {
                        MatchStatus.ON_ROUTE->{gpsMissingSince=null;loadingPublished=false;publish();refreshReturnEstimate()}
                        MatchStatus.OFF_ROUTE_CANDIDATE,MatchStatus.OFF_ROUTE,MatchStatus.REJOINING->{gpsMissingSince=null;beginLoading("경로 이탈 확인 · 재탐색 대기")}
                        else->positionUnavailable(state.reason)
                    }
                } else if(adapter.state==OwnershipState.REQUESTED && state.status!=MatchStatus.ON_ROUTE) stopForUnreliable(state.reason)
                Log.i("SurprideActive","FIX n=$received status=${state.status} progressM=${state.progress?.traveledMeters?.toInt()} nextM=${state.progress?.remainingToManeuverMeters?.toInt()} source=${event.value.source}")
            }
            is PositionEvent.Unavailable->{matcher?.unavailable(event.reason.name);arrival.invalidate();positionUnavailable(event.reason.name)}
        };refresh()
    }
    private val tick=object:Runnable {override fun run() {
        if(!foreground) return
        matcher?.expire(SystemClock.elapsedRealtime())
        if(acquiringOrActive() && subscribed && matcher?.state()?.status==MatchStatus.UNKNOWN && received>0) positionUnavailable("GPS_LOST")
        if(acquiringOrActive() && packet?.isFresh(System.currentTimeMillis())!=true) stopForUnreliable("ROUTE_EXPIRED")
        if(gpsMissingSince?.let {SystemClock.elapsedRealtime()-it>10_000}==true && acquiringOrActive()) stopForUnreliable("GPS_LOST_TIMEOUT")
        if(!subscribed && allowed) startPosition()
        maybePrepare()
        refresh();handler.postDelayed(this,1000)
    }}
    private val surfaces=object:SurfaceCallback {
        override fun onSurfaceAvailable(container:SurfaceContainer) {visible=null;stable=null;surface=container;paint()}
        override fun onVisibleAreaChanged(area:Rect) {visible=Rect(area);paint()}
        override fun onStableAreaChanged(area:Rect) {stable=Rect(area);paint()}
        override fun onSurfaceDestroyed(container:SurfaceContainer) {surface=null;visible=null;stable=null}
    }
    init {
        carContext.onBackPressedDispatcher.addCallback(this,menuBack)
        lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onStart(owner:LifecycleOwner) {
            if(hasStarted) {focus=AndroidCarFocusPort(carContext.applicationContext);oem=PleosOemRouteMonitor(carContext.applicationContext);adapter=newAdapter()}
            hasStarted=true
            foreground=true;renderer=RoadMapRenderer(carContext.applicationContext) {refresh()}
            voice.close();voice=AndroidNavigationVoice(carContext.applicationContext)
            voice.start {s->
                if(s.status in listOf(VoiceStatus.FOCUS_LOST,VoiceStatus.FOCUS_DENIED,VoiceStatus.ERROR)) voiceOn=false
                Log.i("SurprideVoice","status=${s.status} enabled=${s.enabled} focus=${s.focusGranted} requested=${s.requested} started=${s.started} completed=${s.completed} errors=${s.errors} network=${s.voiceNetworkRequired} backend=${s.backend} preRoll=${s.preRoll}")
                refresh()
            };voice.setEnabled(voiceOn)
            configureReroute()
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaces)
            focus.connect {status->
                Log.i("SurprideActive","FOCUS connected=${status.connected} otherActive=${status.otherNavigationActive} error=${status.error}")
                if(status.otherNavigationActive==true) {adapter.environmentChanged(allowed,true);pendingStart=false}
                if(!status.connected) adapter.onAppStopped()
                maybeStart();refresh()
            }
            oem.connect {status->Log.i("SurprideActive","OEM connected=${status.connected} active=${status.routeActive} error=${status.error}");adapter.environmentChanged(if(allowed) true else null,status.routeActive);maybeStart();refresh()}
            ux.start {val prior=requiresDo;requiresDo=it;if(it==true || (prior==false && it==null)) pendingStart=false
                adapter.environmentChanged(if(allowed) true else null,oem.status.routeActive);if(allowed) startPosition() else stopPosition();maybeStart();refresh()}
            handler.post(tick)
        }
        override fun onStop(owner:LifecycleOwner) {
            controlsOpen=false;menuBack.isEnabled=false
            pendingStart=false;pendingPreparation=null;foreground=false;requiresDo=null;adapter.onAppStopped();adapter.release();reroute?.cancel();cancelRequests();voice.close();stopPosition();handler.removeCallbacksAndMessages(null)
            scheduled=false;ux.stop();oem.close();focus.close();renderer?.close();renderer=null
            carContext.getCarService(AppManager::class.java).setSurfaceCallback(null);surface=null
        }
        override fun onDestroy(owner:LifecycleOwner) {pendingStart=false;adapter.release();stopPosition();Log.i("SurprideActive","DESTROYED")}
    })}
    private fun maybeStart() {
        if(pendingStart && SystemClock.elapsedRealtime()-pendingStartAt>15_000) {pendingStart=false;result="START_APPROVAL_EXPIRED"}
        if(!pendingStart || !allowed || !focus.status.connected || !oem.status.connected || oem.status.routeActive==null) return
        if(oem.status.routeActive!=false || focus.status.otherNavigationActive==true) {pendingStart=false;result="EXISTING_NAVIGATION_BLOCKED";return}
        if(ContextCompat.checkSelfPermission(carContext,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {pendingStart=false;result="PRECISE_LOCATION_REQUIRED";return}
        if(route?.source!=RouteDataSource.KAKAO_DIRECTIONS) {pendingStart=false;result="LIVE_ROUTE_REQUIRED";return}
        // Waypoint support has synthetic contract coverage only; LIVE activation stays closed.
        if(packet?.requestOptions?.waypoints?.isNotEmpty()==true) {pendingStart=false;result="WAYPOINT_LIVE_NOT_VERIFIED";return}
        if(packet?.isFresh(System.currentTimeMillis())!=true) {pendingStart=false;result="ROUTE_EXPIRED / PREPARE_AGAIN";return}
        if(matcher?.state()?.status!=MatchStatus.ON_ROUTE || fix?.let {SystemClock.elapsedRealtime()-it.elapsedMillis in 0..5000}!=true) {result="FRESH_ON_ROUTE_GPS_REQUIRED";return}
        pendingStart=false
        adapter.reconnect();arrival.reset();sent=0
        val ok=adapter.requestActivation(ActivationChecks(true,true,false,true,true),ControlledTestApproval.fromExplicitUserAction())
        result=if(ok) "FOCUS_REQUEST_SENT / HOST_ACCEPTANCE_PENDING" else adapter.reason
        Log.i("SurprideActive","START_REQUEST accepted=$ok state=${adapter.state} reason=${adapter.reason}")
        if(adapter.state==OwnershipState.ACTIVE) {startPosition();publish()}
    }
    private fun startPosition() {
        if(subscribed || !allowed || matcher==null || (arrival.arrived && pendingPreparation==null)) return
        subscribed=true;positions.start();Log.i("SurprideActive","POSITION_SUBSCRIBED owner=${adapter.state==OwnershipState.ACTIVE}")
    }
    private fun stopPosition() {subscribed=false;positions.stop();arrival.invalidate()}
    private fun stopForUnreliable(reason:String) {
        // No exact TBT from uncertain positions. Stop active host session; restart requires another explicit action.
        adapter.stop();result="POSITION_UNTRUSTED / $reason";arrival.invalidate()
    }
    private fun stopForWaypointRefresh() {
        adapter.stop();reroute?.cancel();pendingPreparation=null;cancelRequests();arrival.invalidate()
        result="WAYPOINT_REFRESH_NOT_SUPPORTED / PREPARE_AGAIN"
    }
    private fun ensureDeparture() {
        if(startedAt==null) {
            startedAt=System.currentTimeMillis();budget=packet?.budget(startedAt!!)
            try {packet?.rememberStart(carContext,startedAt!!)} catch(_:Exception) {result="SESSION_CACHE_UNAVAILABLE"}
        }
        voice.setOwnership(adapter.state==OwnershipState.ACTIVE)
    }
    private fun configureReroute() {
        reroute?.cancel()
        val p=packet?:return
        reroute=RerouteCoordinator(refreshClient,p.destination,if(returning) "출발지" else p.identity.name,
            SystemClock::elapsedRealtime,{value->
                if(allowed && adapter.state==OwnershipState.ACTIVE) {
                    if(installRoute(value,returning)) {result="REROUTE_READY";publish()}
                    else stopForUnreliable("NEW_ROUTE_NOT_MATCHED")
                }
            },{state->
                Log.i("SurprideReroute","state=${state.status} attempts=${state.attempts} error=${state.error} owner=${adapter.state}")
                if(state.status==RerouteStatus.LOADING) {
                    beginLoading("실제 경로 재탐색 중");voice.setOwnership(adapter.state==OwnershipState.ACTIVE)
                    voice.invalidateRoute();guidanceGeneration++;voice.announceRerouting()
                } else if(state.status==RerouteStatus.FAILED && adapter.state==OwnershipState.ACTIVE) {
                    adapter.stop();result="REROUTE_FAILED / ${state.error} · 직접 재시도 필요"
                }
                refresh()
            })
    }
    private fun beginLoading(reason:String) {
        if(adapter.state!=OwnershipState.ACTIVE) return
        if(!loadingPublished) {
            voice.setOwnership(false)
            loadingPublished=adapter.updateLoadingTrip()
            Log.i("SurprideReroute","LOADING_TRIP returned=$loadingPublished owner=${adapter.state}")
        }
        result=reason;arrival.invalidate()
    }
    private fun positionUnavailable(reason:String) {
        if(adapter.state==OwnershipState.REQUESTED) {stopForUnreliable(reason);return}
        if(adapter.state!=OwnershipState.ACTIVE) return
        reroute?.onGpsUnavailable();beginLoading("위치 신뢰도 낮음 · 정상 회전 안내 보류")
        if(gpsMissingSince==null) {
            gpsMissingSince=SystemClock.elapsedRealtime();voice.setOwnership(true);voice.invalidateRoute();guidanceGeneration++;voice.announceGpsLost()
        }
    }
    private fun installRoute(value:RouteRefreshResult.Success,isReturn:Boolean):Boolean {
        if(!isReturn && waypointProgress?.canRefreshDirect()!=true) {stopForWaypointRefresh();return false}
        val position=fix?.takeIf {SystemClock.elapsedRealtime()-it.elapsedMillis in 0..5000}?:return false
        val nextMatcher=RouteMatcher(value.route)
        if(nextMatcher.accept(position,SystemClock.elapsedRealtime()).status!=MatchStatus.ON_ROUTE) return false
        return try {
            val next=packet!!.replace(carContext,value,isReturn,startedAt,
                remainingWaypointCount=waypointProgress?.state()?.remaining?:0)
            packet=next;route=value.route;matcher=nextMatcher;returning=isReturn;arrival.reset();returnCompleted=false
            waypointProgress=WaypointProgress(next.route,next.requestOptions.waypoints.size)
            loadingPublished=false;gpsMissingSince=null;voice.invalidateRoute();guidanceGeneration++
            Log.i("SurprideReroute","ROUTE_REPLACED source=${value.route.source} return=$isReturn segments=${value.route.segments.size} maneuvers=${value.route.maneuvers.size} owner=${adapter.state} gpsSubscribed=$subscribed")
            refresh(true);true
        } catch(_:Exception) {false}
    }
    private fun cancelRequests() {
        preparationGeneration++;preparation?.cancel();preparation=null
        returnEstimateCall?.cancel();returnEstimateCall=null
    }
    private fun maybePrepare() {
        val purpose=pendingPreparation?:return
        if(!allowed || preparation!=null || acquiringOrActive()) return
        val p=packet?:return
        val position=fix?.takeIf {SystemClock.elapsedRealtime()-it.elapsedMillis in 0..5000 && it.accuracyMeters in 0.1..50.0}?:return
        val isReturn=purpose=="RETURN" || returning
        if(!isReturn && waypointProgress?.canRefreshDirect()!=true) {stopForWaypointRefresh();refresh(true);return}
        val target=if(purpose=="RETURN") p.tripOrigin else p.destination
        if(target==null) {pendingPreparation=null;result="RETURN_ORIGIN_UNKNOWN";return}
        if(lastPreparationAt?.let {SystemClock.elapsedRealtime()-it<30_000}==true) {pendingPreparation=null;result="REQUEST_COOLDOWN · 잠시 후 다시 선택";return}
        pendingPreparation=null;lastPreparationAt=SystemClock.elapsedRealtime()
        val token=++preparationGeneration
        result="${if(isReturn) "RETURN" else "RETRY"}_REQUEST_LOADING"
        preparation=refreshClient.request(position.coordinate,target,if(isReturn) "출발지" else p.identity.name) {reply->
            if(token!=preparationGeneration || !allowed) return@request
            preparation=null
            when(reply) {
                is RouteRefreshResult.Success-> {
                    if(installRoute(reply,isReturn)) {
                        configureReroute();returnEstimate=if(isReturn) ReturnEstimate(reply.route.durationSeconds,reply.measuredAtEpochMillis) else returnEstimate
                        result="${if(isReturn) "RETURN" else "RETRY"}_READY · 직접 안내 시작 필요"
                    } else result="NEW_ROUTE_NOT_MATCHED · 위치 확인 후 재시도"
                }
                is RouteRefreshResult.Failure->{result="${if(isReturn) "RETURN" else "RETRY"}_FAILED / ${reply.error}";if(arrival.arrived) stopPosition()}
            }
            Log.i("SurprideReturn","PREPARED success=${reply is RouteRefreshResult.Success} returning=$returning autoStart=false")
            refresh(true)
        };refresh(true)
    }
    private fun refreshReturnEstimate() {
        val p=packet?:return
        if(p.returnToStart!=true || returnEstimateCall!=null || returnCompleted) return
        val now=System.currentTimeMillis()
        if(returnEstimate?.let {now-it.measuredAtMillis in 0..240_000}==true || now-lastEstimateAttempt<60_000) return
        val target=p.tripOrigin?:return
        val origin=if(returning) fix?.coordinate?:return else p.outboundDestination
        lastEstimateAttempt=now
        val token=preparationGeneration;val wasReturning=returning
        returnEstimateCall=refreshClient.request(origin,target,"출발지") {reply->
            if(token!=preparationGeneration || !allowed || returning!=wasReturning) return@request
            returnEstimateCall=null
            if(reply is RouteRefreshResult.Success) returnEstimate=ReturnEstimate(reply.route.durationSeconds,reply.measuredAtEpochMillis)
            Log.i("SurprideReturn","ETA_REFRESH success=${reply is RouteRefreshResult.Success} returning=$returning")
            refresh()
        }
    }
    private fun budgetSnapshot():ReturnBudgetSnapshot? {
        val stay=if(returning) 0L else arrivedAt?.let {((packet?.budget(startedAt?:System.currentTimeMillis())?.staySeconds?:0)-(System.currentTimeMillis()-it)/1000).coerceAtLeast(0)}
        return budget?.evaluate(System.currentTimeMillis(),if(returning || arrival.arrived) 0 else matcher?.state()?.progress?.remainingSeconds,
            returnEstimate,stay?:budget!!.staySeconds)
    }
    private fun turn():NextTurnMetadata? {
        val currentRoute=route?:return null
        val state=matcher?.state()?:return null
        if(state.status!=MatchStatus.ON_ROUTE) return null
        val p=state.progress?:return null
        val type=state.nextManeuver?.type
        return NextTurnMetadata(if(type==101 || type==null) "경로 끝 접근 · 안전한 정차 후 확인" else maneuverText(type),
            currentRoute.segments[p.segmentIndex].roadName,carType(type),p.remainingToManeuverMeters,
            ceil(currentRoute.durationSeconds.toDouble()*p.remainingToManeuverMeters/currentRoute.distanceMeters).toLong(),System.currentTimeMillis())
    }
    private fun publish() {
        if(adapter.state!=OwnershipState.ACTIVE) return
        val t=turn()?:return
        val p=matcher!!.state().progress!!
        val identity=if(returning) DestinationIdentity("출발지",null) else packet!!.identity
        val ok=if(revealed && !returning) adapter.revealDestination(t,identity,true,p.remainingMeters,p.remainingSeconds)
            else adapter.updateHiddenTrip(t,identity)
        if(ok) {sent++;result="TRIP_CALL_RETURNED / DESTINATIONS=${if(revealed && !returning) 1 else 0}"}
        else result="TRIP_FAILED / ${adapter.reason}"
        voice.setOwnership(ok)
        if(ok) {
            val next=matcher!!.state().nextManeuver
            if(next?.type==101 && p.remainingMeters<=200) voice.announceNearDestination()
            else if(next!=null) voice.onProgress(guidanceGeneration,route!!.maneuvers.indexOf(next),next.type,p.remainingToManeuverMeters)
        }
        Log.i("SurprideActive","TRIP returned=$ok updates=$sent destinations=${if(revealed && !returning) 1 else 0} state=${adapter.state}")
    }
    private fun reveal() {if(!allowed) return;revealed=true;publish();refresh(true);Log.i("SurprideActive","DISCLOSURE explicit=true")}
    private fun confirmArrival() {
        if(waypointProgress?.canConfirmFinalArrival(matcher?.state()?.progress)!=true) {arrival.invalidate();result="ARRIVAL_NOT_CONFIRMED";refresh(true);return}
        if(!allowed || !arrival.confirm(true,true,SystemClock.elapsedRealtime())) {result="ARRIVAL_NOT_CONFIRMED";refresh(true);return}
        adapter.stop();revealed=true;arrivedAt=System.currentTimeMillis();returnCompleted=returning
        result=if(returning) "RETURN_COMPLETED / GUIDANCE_ENDED" else "ARRIVED / USER_CONFIRMED_PARKED / GUIDANCE_ENDED"
        Log.i("SurprideActive","ARRIVED confirmed=true ownerReleased=${adapter.state}");refresh(true)
    }
    private fun returnResult() {
        val currentPacket=packet?:return
        if(!allowed || !revealed || currentPacket.nonce==null || currentPacket.candidateId==null) return
        adapter.stop()
        try {carContext.startActivity(Intent().setComponent(ComponentName("com.surpride.app","com.surpride.app.realdata.LiveNavigationResultActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("session_nonce",currentPacket.nonce).putExtra("candidate_id",currentPacket.candidateId)
            .putExtra("source","DEVICE_API_EMULATOR").putExtra("arrived",arrival.arrived).putExtra("revealed",true).putExtra("stopped",true))}
        catch(_:Exception) {result="MAIN_APP_RESULT_UNAVAILABLE";refresh(true)}
    }
    private fun controls() {
        if(!allowed) return
        val diagnostics=if(diagnosticsEnabled) listOf(
            "호스트·경로 소유권" to "${adapter.state} · ${adapter.reason}\n$result",
            "위치·Trip 수신" to "GPS $received / Trip $sent · ${matcher?.state()?.status?:"UNKNOWN"}\n수신 위치 원본과 좌표는 표시하지 않습니다",
            "음성 연결" to "${voice.state.status} · ${if(voiceOn) "ON" else "OFF"}\n${voice.state.backend}",
            "재탐색·귀가" to "${reroute?.state()?.status?:"대기"} · 요청 ${reroute?.state()?.attempts?:0}\n귀가 ${budgetSnapshot()?.risk?:"UNKNOWN"}"
        ) else emptyList()
        screenManager.push(ActiveControlsScreen(carContext,route!=null,revealed,packet?.nonce!=null && !returning,arrival.arrived && !returning && packet?.tripOrigin!=null,voiceOn,diagnostics) {command->
            screenManager.pop()
            when(command) {
                "start"->{matcher?.reset();arrival.reset();gpsMissingSince=null;pendingStart=true;pendingStartAt=SystemClock.elapsedRealtime();result="EXPLICIT_OPERATOR_APPROVAL / CHECKING";maybeStart()}
                "reveal"->{revealed=true}
                "voice"->{voiceOn=!voiceOn;voice.setEnabled(voiceOn)}
                "record"->{handler.postDelayed({returnResult()},700)}
                "retry"->{pendingPreparation="RETRY";startPosition();maybePrepare()}
                "return"->{pendingPreparation="RETURN";startPosition();maybePrepare()}
                "back"->screenManager.popToRoot()
            }
        })
    }
    override fun onGetTemplate():Template {
        val active=adapter.state==OwnershipState.ACTIVE && allowed
        if(controlsOpen) return NavigationControlMenu.build(allowed,voiceOn,active && arrival.candidate,
            arrival.arrived && !returning && packet?.tripOrigin!=null) {command->
            when(command) {
                "stop"->{adapter.stop();result="USER_STOPPED";closeControls()}
                "map"->closeControls()
                "reveal"->{if(allowed) {reveal();closeControls()}}
                "voice"->{if(allowed) {voiceOn=!voiceOn;voice.setEnabled(voiceOn);closeControls()}}
                "arrival"->{if(allowed) {confirmArrival();closeControls()}}
                "return"->{if(allowed && arrival.arrived) {pendingPreparation="RETURN";startPosition();maybePrepare();closeControls()}}
            }
        }
        // Template-scoped controls belong in the app action strip, not the map pan/zoom strip.
        // The host chooses placement/visibility. No Surface click hit targets are used on API 4.
        val strip=ActionStrip.Builder().addAction(Action.Builder().setTitle(if(active) "종료·조작" else "안내 준비").setOnClickListener {
            if(active) {controlsOpen=true;menuBack.isEnabled=true;refresh(true)} else controls()
        }.build())
        fun action(label:String,drawable:Int,selected:()->Unit)=Action.Builder().setTitle(label)
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext,drawable)).build()).setOnClickListener {selected()}.build()
        strip.addAction(action(if(revealed) "공개한 목적지" else "목적지 공개",R.drawable.ic_reveal) {reveal()})
        strip.addAction(action(if(voiceOn) "음성 끄기" else "음성 켜기",if(voiceOn) R.drawable.ic_voice else R.drawable.ic_voice_off) {
            if(allowed) {voiceOn=!voiceOn;voice.setEnabled(voiceOn);refresh(true)}
        })
        if(active && arrival.candidate) strip.addAction(action("정차·도착 확인",R.drawable.ic_arrival_confirm) {confirmArrival()})
        else if(arrival.arrived && !returning && packet?.tripOrigin!=null) strip.addAction(action("복귀 준비",R.drawable.ic_return) {
            if(allowed) {pendingPreparation="RETURN";startPosition();maybePrepare();refresh(true)}
        })
        val b=NavigationTemplate.Builder().setActionStrip(strip.build())
        if(active && matcher?.state()?.status!=MatchStatus.ON_ROUTE) b.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
        else if(active) turn()?.let {t->
            val maneuver=CarManeuver.Builder(t.maneuverType)
            val drawable=when(t.maneuverType) {
                CarManeuver.TYPE_STRAIGHT->R.drawable.ic_turn_straight
                CarManeuver.TYPE_TURN_NORMAL_LEFT->R.drawable.ic_turn_left
                CarManeuver.TYPE_TURN_NORMAL_RIGHT->R.drawable.ic_turn_right
                else->null
            }
            drawable?.let {maneuver.setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext,it)).build())}
            val distance=if(t.distanceMeters>=1000) Distance.create(ceil(t.distanceMeters/100)/10,Distance.UNIT_KILOMETERS)
                else Distance.create(ceil(t.distanceMeters/10)*10,Distance.UNIT_METERS)
            b.setNavigationInfo(RoutingInfo.Builder().setCurrentStep(Step.Builder(t.cue).setRoad(t.road)
                .setManeuver(maneuver.build()).build(),distance).build())
        }
        // The earlier native ETA-card clipping is a host investigation, not an app layout fix.
        // Do not create a destination card merely to show totals; this app footer labels its estimate.
        return b.build()
    }
    private fun closeControls() {controlsOpen=false;menuBack.isEnabled=false;refresh(true)}
    private fun refresh(immediate:Boolean=false) {
        if(!foreground || scheduled) return
        val delay=if(immediate) 0L else (1000-(SystemClock.elapsedRealtime()-lastRefresh)).coerceAtLeast(0)
        scheduled=true;handler.postDelayed({scheduled=false;if(foreground) {lastRefresh=SystemClock.elapsedRealtime();paint();invalidate()}},delay)
    }
    private fun paint() {
        val s=surface?.surface?.takeIf {it.isValid}?:return
        val c=try {s.lockCanvas(null)} catch(_:Exception) {return}
        try {
            val dark=carContext.isDarkMode
            val background=if(dark) Color.rgb(10,24,24) else Color.rgb(246,247,242)
            val ink=if(dark) Color.rgb(235,241,225) else Color.rgb(25,35,30)
            val accent=if(dark) Color.rgb(215,242,139) else Color.rgb(63,95,39)
            c.drawColor(background)
            val area=Rect(0,0,c.width,c.height)
            visible?.takeIf {!it.isEmpty}?.let {if(!area.intersect(it)) return};stable?.takeIf {!it.isEmpty}?.let {if(!area.intersect(it)) return}
            val density=surface?.dpi?.takeIf {it>0}?.div(160f)?:carContext.resources.displayMetrics.density
            val scale=density*carContext.resources.configuration.fontScale
            area.inset((16*density).toInt(),(16*density).toInt())
            if(area.isEmpty) return
            c.clipRect(area)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=28f*scale;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
            val metrics=p.fontMetrics
            val lineHeight=metrics.descent-metrics.ascent+8*density
            val layout=SurfaceFooterLayout.measure(area.height(),lineHeight,density)
            val map=Rect(area.left,area.top,area.right,area.top+layout.mapHeight)
            if(map.width()<=0) return
            val locationStatus=when {
                matcher?.state()?.status!=MatchStatus.ON_ROUTE->"정차 에뮬레이터 · 마지막 위치 · 신뢰도 낮음"
                adapter.state==OwnershipState.ACTIVE->"정차 에뮬레이터 · 안내 중"
                else->"정차 에뮬레이터 · 안내 종료/준비"
            }
            // Tiny map regions cannot fit readable attribution. Preserve the critical status instead;
            // those dimensions are unsupported for a complete map+guidance experience.
            if(map.height()>=128*density) renderer?.render(c,map,route,fix,matcher?.state(),locationStatus,dark,surface?.dpi?:0)
            val lines=NavigationFooter.present(NavigationFooterInput(packet?.identity?.name,revealed,
                adapter.state==OwnershipState.ACTIVE,returning,returnCompleted,arrival.arrived,arrival.candidate,
                preparation!=null || pendingPreparation!=null,
                reroute?.state()?.status in listOf(RerouteStatus.LOADING,RerouteStatus.WAITING_RETRY),
                matcher?.state()?.status,matcher?.state()?.progress,turn()?.road,voiceOn,budgetSnapshot()?.risk,result,adapter.state))
            val footer=Rect(area.left,map.bottom,area.right,area.bottom)
            val saved=c.save()
            try {
                c.clipRect(footer)
                p.color=background;c.drawRect(footer,p)
                var y=footer.top+layout.padding-metrics.ascent
                lines.take(layout.rows).forEachIndexed {index,value->
                    p.color=if(index==0) accent else ink
                    p.textSize=if(index==2) 24f*scale else 28f*scale
                    val width=(footer.width()-layout.padding*2).coerceAtLeast(0f)
                    val text=android.text.TextUtils.ellipsize(value,android.text.TextPaint(p),width,android.text.TextUtils.TruncateAt.END)
                    c.drawText(text.toString(),footer.left+layout.padding,y,p);y+=lineHeight
                }
            } finally {c.restoreToCount(saved)}
            Log.i("SurprideActive","SURFACE state=${adapter.state} fixes=$received tripUpdates=$sent revealed=$revealed candidate=${arrival.candidate}")
        } finally {try {s.unlockCanvasAndPost(c)} catch(_:Exception) {}}
    }
    private fun carType(type:Int?)=when(type) {0->CarManeuver.TYPE_STRAIGHT;1->CarManeuver.TYPE_TURN_NORMAL_LEFT;2->CarManeuver.TYPE_TURN_NORMAL_RIGHT;3->CarManeuver.TYPE_U_TURN_LEFT;5,82->CarManeuver.TYPE_KEEP_LEFT;6,83->CarManeuver.TYPE_KEEP_RIGHT;else->CarManeuver.TYPE_UNKNOWN}
}

private class ActiveControlsScreen(context:CarContext,private val routeReady:Boolean,private val disclosed:Boolean,
    private val canRecord:Boolean,private val canReturn:Boolean,private val voiceOn:Boolean,
    private val diagnostics:List<Pair<String,String>>,private val selected:(String)->Unit):Screen(context) {
    private val ux=PocParkedMonitor(carContext.applicationContext);private var allowed=false
    init {lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onStart(owner:LifecycleOwner) {ux.start {allowed=it==false;invalidate()}}
        override fun onStop(owner:LifecycleOwner) {allowed=false;ux.stop()}
        override fun onDestroy(owner:LifecycleOwner) {ux.stop()}
    })}
    override fun onGetTemplate():Template {
        val list=ItemList.Builder()
        if(!allowed) list.addItem(Row.Builder().setTitle("정차 상태 확인 중 / 조작 제한").build()) else {
            list.addItem(Row.Builder().setTitle("정확한 위치 권한 요청").setOnClickListener {
                carContext.requestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)) {_,_->invalidate()}
            }.build())
            list.addItem(Row.Builder().setTitle(if(routeReady) "다른 안내·Phone Projection 종료 확인 후 시작" else "실제 경로 없음 / 만료 · 먼저 준비 필요")
                .addText("정차 AVD 한정 · 정책 허용 가정 · 일회성 활성 실험 동의").setOnClickListener {if(routeReady && allowed) selected("start")}.build())
            list.addItem(Row.Builder().setTitle("목적지 즉시 공개").setOnClickListener {if(allowed) selected("reveal")}.build())
            list.addItem(Row.Builder().setTitle(if(voiceOn) "음성 안내 끄기" else "음성 안내 켜기").setOnClickListener {if(allowed) selected("voice")}.build())
            if(disclosed && canRecord) list.addItem(Row.Builder().setTitle("안내 종료 후 SURPRIDE 평가·기록").setOnClickListener {if(allowed) selected("record")}.build())
            if(canReturn) list.addItem(Row.Builder().setTitle("출발지 복귀 경로 준비 · 자동 시작 없음").setOnClickListener {if(allowed) selected("return")}.build())
            if(routeReady) list.addItem(Row.Builder().setTitle("현재 위치에서 경로 재요청 · 준비만").setOnClickListener {if(allowed) selected("retry")}.build())
            if(diagnostics.isNotEmpty()) list.addItem(Row.Builder().setTitle("개발 진단").addText("정차 상태에서만 확인").setOnClickListener {
                if(allowed) screenManager.push(NavigationDiagnosticsScreen(carContext,diagnostics))
            }.build())
            list.addItem(Row.Builder().setTitle("기존 PoC로 돌아가기").setOnClickListener {if(allowed) selected("back")}.build())
        }
        return ListTemplate.Builder().setTitle("실제 지도 · 정차 AVD 개발 검증").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}

/** Debug-only safe snapshots: no coordinates, destination identity or provider credentials. */
private class NavigationDiagnosticsScreen(context:CarContext,private val rows:List<Pair<String,String>>):Screen(context) {
    private val ux=PocParkedMonitor(carContext.applicationContext);private var allowed=false
    init {lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onStart(owner:LifecycleOwner) {ux.start {allowed=it==false;invalidate()}}
        override fun onStop(owner:LifecycleOwner) {allowed=false;ux.stop()}
        override fun onDestroy(owner:LifecycleOwner) {ux.stop()}
    })}
    override fun onGetTemplate():Template {
        val debug=carContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0
        val list=ItemList.Builder()
        if(!allowed || !debug) list.addItem(Row.Builder().setTitle("정차 상태 확인 후 이용할 수 있어요").build())
        else rows.forEach {(title,detail)->list.addItem(Row.Builder().setTitle(title).addText(detail.take(240)).build())}
        return ListTemplate.Builder().setTitle("개발 진단 · 정차 에뮬레이터").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
