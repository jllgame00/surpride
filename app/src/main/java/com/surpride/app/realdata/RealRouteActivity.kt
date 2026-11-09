package com.surpride.app.realdata

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.surpride.app.experience.ParkedUxMonitor
import com.surpride.app.experience.core.*
import com.surpride.app.experience.core.Companion as DriveCompanion
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import com.surpride.app.realdata.experience.*
import com.surpride.app.moodinput.core.SafePreparationPolicy
import com.surpride.app.ui.SurprideUi

/** Parked-only validation and explicit private preparation; guidance starts only inside the PoC. */
class RealRouteActivity:AppCompatActivity() {
    class Session:ViewModel() {
        var initialized=false
        var preferences=DrivePreferences()
        var recentIds:Set<String> = emptySet()
        var plan:RoutePlan?=null
        var recommendationOrigin:GeoPoint?=null
        var revealed=false
        var mock=false
        var stay=20
        var gps="아직 확인하지 않음"
        var busy=false
        var requestStatus="요청 전"
        var optimize=true
        var diagnosticsExpanded=false
    }
    private lateinit var session:Session
    private lateinit var ux:ParkedUxMonitor
    private lateinit var root:LinearLayout
    private var requiresDo:Boolean?=null
    private val executor=Executors.newSingleThreadExecutor()
    private var pending:Future<*>?=null
    private var requestVersion=0
    private val allowed get()=ParkedInteractionPolicy.allows(requiresDo)
    private val ui by lazy {SurprideUi(this)}
    private val diagnosticsEnabled get()=applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0

    override fun onCreate(savedInstanceState:Bundle?) {
        SurprideUi.applyAppearance(this)
        super.onCreate(savedInstanceState)
        session=ViewModelProvider(this)[Session::class.java]
        if(!session.initialized) {
            session.preferences=DrivePreferences(
                mood=Mood.entries.getOrElse(intent.getIntExtra("mood",0)){Mood.HEALING},
                scope=TravelScope.entries.getOrElse(intent.getIntExtra("scope",0)){TravelScope.LOCAL},
                companion=DriveCompanion.entries.getOrElse(intent.getIntExtra("companion",0)){DriveCompanion.SOLO},
                categories=intent.getStringArrayExtra("categories")?.mapNotNull {s->PlaceCategory.entries.find {it.name==s}}?.toSet()?:emptySet(),
                availableMinutes=intent.getIntExtra("minutes",60).takeIf {it>0},
                returnByEpochMillis=intent.getLongExtra("deadline",0).takeIf {it>0},
                returnToStart=intent.getBooleanExtra("returns",true),randomness=0,
                excludedCategories=intent.getStringArrayExtra("excludedCategories")?.mapNotNull {s->PlaceCategory.entries.find {it.name==s}}?.toSet()?:emptySet())
            session.recentIds=intent.getStringArrayExtra("recent")?.toSet()?:emptySet()
            session.initialized=true
        }
        ux=ParkedUxMonitor(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(ui.background)}
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) {view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left+dp(32),bars.top+dp(16),bars.right+dp(32),bars.bottom+dp(12));insets
        }
        render()
    }
    override fun onStart() {super.onStart();ux.start {requiresDo=it;if(!allowed) cancelPending();render()}}
    override fun onStop() {requiresDo=null;cancelPending();ux.stop();super.onStop()}
    override fun onDestroy() {executor.shutdownNow();ux.stop();super.onDestroy()}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==700 && allowed) {readGpsStatus();render()}
    }
    private fun readGpsStatus() {
        session.gps=when(val gps=AndroidLocationProvider(this).current()) {
            is LocationReply.Available->"DEVICE 위치 사용 가능 (좌표 비표시·미사용)"
            is LocationReply.Unavailable->gps.error.name
        }
    }
    private fun act(action:()->Unit) {if(allowed) {action();render()}}
    private fun cancelPending() {
        requestVersion++;pending?.cancel(true);pending=null
        if(::session.isInitialized && session.busy) {session.busy=false;session.requestStatus="조회 중단 · 필요하면 다시 요청하세요"}
    }
    private fun reset() {cancelPending();session.plan=null;session.recommendationOrigin=null;session.revealed=false}
    private fun calculate(mock:Boolean) {
        reset();session.mock=mock
        val location=DevelopmentLocationProvider().current()
        val config=if(mock) null else ProxyConfiguration.load(this)
        if(!mock && config==null) {session.plan=RoutePlan.Failure(setOf(RouteError.NOT_CONFIGURED));session.requestStatus="디버그 서버 설정 없음/만료";return}
        val preferences=session.preferences;val recent=session.recentIds;val stay=session.stay
        session.busy=true;session.requestStatus="조회 중 · 자동 길 안내 없음"
        val version=++requestVersion
        pending=executor.submit {
            val provider=if(mock) MockRouteProvider(System::currentTimeMillis) else LiveRouteProvider(config)
            val result=try {
                val context=RecommendationContext(System.currentTimeMillis(),recentIds=recent)
                if(session.optimize) DriveExperienceEngine(SeoulPilotCatalog.places,provider,PendingScenicCatalog.routingCandidates).recommend(
                    location.point,preferences,context,stay,10,1)
                else RouteRecommendationEngine(SeoulPilotCatalog.places,provider).recommend(location.point,preferences,context,stay,10,1)
            } catch(_:Exception) {RoutePlan.Failure(setOf(RouteError.API))}
            runOnUiThread {
                if(version==requestVersion && allowed && !isFinishing && !isDestroyed) {
                    pending=null;session.busy=false;session.plan=result
                    session.recommendationOrigin=location.point.takeIf {result is RoutePlan.Success}
                    session.requestStatus="조회 완료 · 응답 출처 확인";render()
                }
            }
        }
    }
    private fun prepareNavigation(plan:RoutePlan.Success) {
        if(plan.routeOrigin!=RouteOrigin.LIVE_API || plan!==session.plan || !allowed) return
        if(!plan.feasibleForHandoff(session.preferences,System.currentTimeMillis())) {
            session.requestStatus="준비 중단: 귀가 시간 또는 데이터 신선도 변경 · 다시 평가해 주세요";return
        }
        val tripOrigin=session.recommendationOrigin?:return
        val config=ProxyConfiguration.load(this)
        if(config==null) {session.requestStatus="준비 불가: 프록시 설정 없음/만료";return}
        val nonce=UUID.randomUUID().toString()
        val preferences=session.preferences
        val inbound=plan.inbound?.takeIf {it.origin==RouteOrigin.LIVE_API}
        val trip=PreparedTripMetadata(tripOrigin,preferences.returnToStart,preferences.availableMinutes,
            preferences.returnByEpochMillis,session.stay.toLong()*60,600,inbound?.seconds,inbound?.receivedAtMillis)
        session.busy=true;session.requestStatus=if(plan.experience!=null) "선정 경로 확인 중 · 추가 기본 경로 조회 없음 · 자동 시작 없음" else "선정 코스의 최신 전체 안내 조회 중 · 자동 시작 없음"
        val version=++requestVersion
        pending=executor.submit {
            val result=try {
                // The exact measured alternative is handed off; do not replace it with a new default route.
                val response=if(plan.experience!=null) {
                    val raw=plan.outbound.guidancePayload?:throw IOException("Missing selected guidance")
                    ProxyHttpResponse(200,raw)
                } else ProxyHttpTransport.postGuidance(config,tripOrigin,plan.place.point)
                if(Thread.currentThread().isInterrupted) PreparedRouteReply.Failure(RouteError.NETWORK)
                else if(config.expiresAtMillis<=System.currentTimeMillis()) PreparedRouteReply.Failure(RouteError.NOT_CONFIGURED)
                else if(!plan.feasibleForHandoff(preferences,System.currentTimeMillis())) PreparedRouteReply.Failure(RouteError.TIME_EXCEEDED)
                else PreparedRouteContract.prepare(response,tripOrigin,plan.place.point,plan.place.id,
                    plan.place.name,plan.experience?.publicReason?:"${preferences.mood.label}의 기분과 ${preferences.companion.label} 떠나는 시간에 맞춘 공개 장소 후보입니다. 경관·차량 진입 적합성은 미검증입니다.",
                    nonce,System.currentTimeMillis(),trip,config,plan.experience?.selectedOptions)
            } catch(_:IOException) {PreparedRouteReply.Failure(RouteError.NETWORK)}
              catch(_:Exception) {PreparedRouteReply.Failure(RouteError.API)}
            runOnUiThread {
                if(version==requestVersion && allowed && plan===session.plan && !isFinishing && !isDestroyed) {
                    pending=null;session.busy=false
                    session.requestStatus=when(result) {
                        is PreparedRouteReply.Failure -> "준비 실패: ${result.error} · 자동 DEMO 전환 없음"
                        is PreparedRouteReply.Success -> if(!plan.feasibleForHandoff(preferences,System.currentTimeMillis())) "시간/신선도 변경 · 다시 평가 필요"
                        else when(PreparedNavigationHandoff.launch(this,result,plan.place.id,nonce)) {
                            PreparedNavigationHandoff.Result.LAUNCHED -> "PoC 준비 화면 전달 완료 · 직접 시작 전에는 길 안내 없음"
                            PreparedNavigationHandoff.Result.APP_MISSING -> "PoC 앱이 설치되지 않았습니다. nav-host-poc APK 설치 후 다시 준비하세요."
                            PreparedNavigationHandoff.Result.FAILED -> "PoC 전달 실패 · 비공개 패킷 삭제 · 다시 준비하세요."
                        }
                    }
                    render()
                }
            }
        }
    }
    private fun render() {
        root.removeAllViews()
        val header=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        root.addView(header)
        header.addView(label("S /  비밀 코스 준비",32,true),LinearLayout.LayoutParams(0,-2,1f))
        if(diagnosticsEnabled) header.addView(button(if(session.diagnosticsExpanded) "진단 닫기" else "개발 진단") {
            session.diagnosticsExpanded=!session.diagnosticsExpanded
        },LinearLayout.LayoutParams(-2,-2).apply {leftMargin=dp(16)})
        header.addView(ui.button("조건 화면으로") {finish()},LinearLayout.LayoutParams(-2,-2).apply {leftMargin=dp(16)})
        if(!allowed) {root.addView(label("안전하게 정차한 뒤 코스를 준비해 주세요",28,true));return}
        val columns=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        root.addView(columns,LinearLayout.LayoutParams(-1,0,1f))
        val left=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(0,dp(16),dp(16),0)}
        val right=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),0,0)}
        columns.addView(ScrollView(this).apply {addView(left)},LinearLayout.LayoutParams(0,-1,1f))
        columns.addView(ScrollView(this).apply {addView(right)},LinearLayout.LayoutParams(0,-1,1.2f))
        val p=session.preferences
        val preferences=ui.card();left.addView(preferences)
        preferences.addView(label("오늘의 드라이브",28,true))
        preferences.addView(label("${p.mood.label} · ${p.companion.label}\n${p.scope.label} · ${if(p.returnToStart) "출발지로 복귀" else "편도"}",24))
        preferences.addView(label("원하는 장소  ${p.categories.joinToString {it.label}.ifBlank {"특별한 조건 없음"}}\n피할 장소  ${p.excludedCategories.joinToString {it.label}.ifBlank {"없음"}}",20))
        preferences.addView(label("사용 가능한 시간",24,true))
        preferences.addView(label(p.availableMinutes?.let {"${it}분"}?:"귀가 희망 시각 기준",28,true))
        p.returnByEpochMillis?.let {preferences.addView(label("${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}까지 복귀 희망\n시간을 다시 선택하면 귀가 시각 제한을 대체해요",20))}
        val times=LinearLayout(this)
        listOf(30,60,180).forEach {minutes->times.addView(button("${minutes}분") {session.preferences=p.withAvailableBudget(minutes);reset()}.apply {
            ui.styleButton(this,selected=p.availableMinutes==minutes && p.returnByEpochMillis==null)
        },LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(4),0,dp(4),0)})}
        preferences.addView(times)
        preferences.addView(button(if(p.returnToStart) "왕복 코스 · 편도로 변경" else "편도 코스 · 왕복으로 변경") {session.preferences=p.copy(returnToStart=!p.returnToStart);reset()})
        preferences.addView(button("머무는 시간 ${session.stay}분 · 변경") {session.stay=if(session.stay==20) 40 else 20;reset()})
        preferences.addView(label("이동·체류·복귀 시간과 안전 여유 10분을 함께 확인해요\n교통 변화에 따라 귀가 예상 시각은 달라질 수 있어요",20))
        left.addView(ui.card().apply {
            addView(label("출발 전 알아두세요",24,true))
            addView(label("정차 에뮬레이터용 코스입니다\n출발지는 개발용 공개 시험 지점이며 현재 위치를 사용하지 않아요",20))
            addView(label("차량 진입·주차·운영시간은 확인이 필요해요\n검증된 경관 경유지가 없어 일반 도로 경로를 비교해요",20))
        })
        val course=ui.card();right.addView(course)
        if(session.busy) {
            course.addView(label("경로를 확인하고 있어요",28,true))
            course.addView(ProgressBar(this).apply {contentDescription="경로 조회 중"})
            course.addView(label("목적지는 비공개로 유지돼요\n완료되어도 안내가 자동으로 시작되지 않아요",20))
            course.addView(ui.button("조회 취소") {act {cancelPending()}})
        }
        when(val plan=session.plan) {
            is RoutePlan.Failure -> {
                course.addView(label("코스를 준비하지 못했어요",28,true))
                course.addView(label(plan.errors.joinToString("\n") {routeErrorMessage(it)},24).apply {setTextColor(ui.error)})
                course.addView(label("조건을 바꾸거나 다시 시도해 주세요\n모의 코스로 자동 전환하지 않아요",20))
                course.addView(button("실제 경로 다시 확인",true) {calculate(false)})
            }
            is RoutePlan.Success -> {
                val view=plan.presentation(session.revealed)
                course.addView(label(if(view.destination==null) "비밀 코스가 준비됐어요" else "조금 먼저 만나는 목적지",28,true))
                course.addView(label("총 ${view.totalMinutes}분 · ${"%.1f".format(view.distanceMeters/1000.0)}km",36,true))
                course.addView(label("출발 약 ${(plan.outbound.seconds+59)/60}분"+
                    (plan.inbound?.let {" · 복귀 약 ${(it.seconds+59)/60}분"}?:" · 편도")+
                    "\n체류 ${session.stay}분 + 안전 여유 10분 포함",20))
                course.addView(label(if(view.mode==RouteOrigin.LIVE_API) "실제 자동차 경로 조회 결과 · 조회 시점의 예상값" else "모의 경로 · 합성 시간과 거리, 실제 이동 예측 아님",20))
                course.addView(label("${p.mood.label}의 기분과 선택한 시간에 맞춰 준비했어요",24))
                course.addView(label("준비물",24,true))
                course.addView(label(SafePreparationPolicy.hints(p).joinToString("\n") {it.label},20))
                course.addView(label("날씨·주차·차량 진입은 출발 전에 확인해 주세요",20))
                if(!session.busy) preparationMessage()?.let {course.addView(ui.text(it,24,ui.warning))}
                if(view.mode==RouteOrigin.LIVE_API) {
                    course.addView(button("이 코스로 지도 안내 준비",true) {prepareNavigation(plan)})
                    course.addView(label("다음 화면에서 직접 시작해요\n실차 운행용으로 검증되지 않은 에뮬레이터 기능입니다",20))
                }
                if(view.destination==null) {
                    course.addView(button("목적지 지금 공개") {session.revealed=true})
                } else {
                    val place=view.destination
                    course.addView(label(place.name,32,true))
                    course.addView(label("${place.type}\n차량 진입·주차·운영시간 확인 필요",20))
                }
                course.addView(button("코스 다시 계산") {calculate(false)})
            }
            null -> if(!session.busy) {
                course.addView(label("어떤 코스가 기다릴까요",32,true))
                course.addView(label("선택한 조건으로 실제 자동차 경로를 비교해요\n목적지는 공개할 때까지 비밀입니다",24))
                course.addView(button("실제 경로로 코스 준비",true) {calculate(false)})
                course.addView(label("경로 서버에 연결할 수 없으면 알려드려요\n안내는 사용자가 직접 시작할 때만 활성화돼요",20))
            }
        }
        if(diagnosticsEnabled && session.diagnosticsExpanded) renderDiagnostics(left,right)
    }
    /** Debug-only detail retains validation tools without exposing raw contracts in product UI. */
    private fun renderDiagnostics(left:LinearLayout,right:LinearLayout) {
        val data=ui.card();left.addView(data)
        data.addView(label("개발 진단 · 정차 전용",24,true))
        data.addView(label("서울 공개 장소 5곳 · 서울특별시 야경명소 / 공공누리 1유형\n개발용 고정 출발지 · 현재 기기 좌표 미사용",18))
        data.addView(button("기기 위치 접근 상태 확인") {readGpsStatus()})
        data.addView(button("전경 위치 권한 요청") {requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION),700)})
        data.addView(label("GPS: ${session.gps}",18))
        val p=session.preferences
        data.addView(button("시험 조건: 혼자 · 장소 제한 없음") {
            session.preferences=p.copy(companion=DriveCompanion.SOLO,categories=emptySet(),excludedCategories=emptySet());reset()
        })
        data.addView(label("에뮬레이터 평가 기록 · 실제 방문 인증 아님",20,true))
        val visits=LiveNavigationStore.read(this).visits
        if(visits.isEmpty()) data.addView(label("저장한 에뮬레이터 평가가 없습니다",18))
        visits.asReversed().forEach {visit->
            val place=SeoulPilotCatalog.places.firstOrNull {it.id==visit.candidateId}
            data.addView(label("${place?.name?:visit.candidateId} · ${visit.rating}/5\n${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(visit.completedAtMillis))}\nDEVICE_API_EMULATOR",18))
        }
        val details=ui.card();right.addView(details)
        details.addView(label("경로 계약·비교",24,true))
        details.addView(label(session.requestStatus,18))
        details.addView(button("명시적으로 MOCK 경로 평가") {calculate(true)})
        details.addView(button(if(session.optimize) "통합 비교 사용 중 · 기존 평가로" else "기존 평가 사용 중 · 통합 비교로") {session.optimize=!session.optimize;reset()})
        details.addView(label("경관 연구 후보 2곳은 진입·운영·이용 조건 미확인으로 제외\n경관 UNKNOWN은 낮은 품질을 뜻하지 않습니다",18))
        when(val plan=session.plan) {
            is RoutePlan.Failure->details.addView(label(plan.errors.joinToString(),18))
            is RoutePlan.Success->{
                details.addView(label("출처 ${plan.routeOrigin}\n${plan.experience?.publicReason?:"기분·장소 선호 기반 / 경관 미평가"}",18))
                plan.experience?.let {experience->
                    details.addView(label("우선순위 ${experience.selectedOptions.priority} · 경유 ${experience.selectedOptions.waypoints.size}곳",18))
                    details.addView(label(experience.comparisons.mapIndexed {i,v->"비교 ${i+1} · ${v.label}: ${v.totalSeconds?.let {s->"${(s+59)/60}분"}?:"시간 없음"} · ${v.status}"}.joinToString("\n"),18))
                }
                plan.presentation(session.revealed).destination?.let {place->
                    details.addView(label("${place.name}\n대표 좌표 (경도, 위도): ${place.point.longitude}, ${place.point.latitude}\n${place.license} · 확인 ${place.checkedDate}\n${place.sourceUrl}\n주차/운영시간/접근성: UNKNOWN",18))
                }
            }
            null->Unit
        }
    }
    private fun preparationMessage():String?=when {
        session.requestStatus.startsWith("준비 실패") || session.requestStatus.startsWith("PoC 전달 실패") -> "지도 안내를 준비하지 못했어요 · 연결 상태를 확인하고 다시 시도해 주세요"
        session.requestStatus.startsWith("준비 불가") -> "경로 서버 연결 설정이 없거나 만료됐어요"
        session.requestStatus.startsWith("준비 중단") || session.requestStatus.startsWith("시간/신선도 변경") -> "시간이나 경로 정보가 바뀌었어요 · 코스를 다시 계산해 주세요"
        session.requestStatus.startsWith("PoC 앱이 설치") -> "지도 안내 앱이 설치되지 않았어요 · 개발 설정을 확인해 주세요"
        session.requestStatus.startsWith("PoC 준비 화면 전달") -> "지도 안내 준비 화면으로 전달했어요 · 직접 시작해 주세요"
        session.requestStatus.startsWith("조회 중단") -> "조회가 중단됐어요 · 필요하면 다시 요청해 주세요"
        else -> null
    }
    private fun routeErrorMessage(error:RouteError)=when(error) {
        RouteError.NOT_CONFIGURED->"경로 서버 연결 설정이 없거나 만료됐어요"
        RouteError.AUTH->"경로 서비스 인증을 확인해 주세요"
        RouteError.RATE_LIMIT->"요청이 많아요 · 잠시 뒤 다시 시도해 주세요"
        RouteError.NETWORK->"경로 서버에 연결할 수 없어요"
        RouteError.INVALID_COORDINATE->"출발지나 목적지 위치를 확인할 수 없어요"
        RouteError.NO_ROUTE->"연결 가능한 자동차 경로를 찾지 못했어요"
        RouteError.TIME_EXCEEDED->"이동·체류·복귀에 필요한 시간이 부족해요"
        RouteError.NO_CANDIDATE->"현재 장소·동행 조건에 맞는 후보가 없어요"
        RouteError.STALE_DATA->"경로 정보가 오래됐어요 · 다시 확인해 주세요"
        RouteError.UNSUPPORTED_OPTIONS->"현재 경로 서비스가 지원하지 않는 조건이에요"
        RouteError.API,RouteError.INVALID_RESPONSE->"경로 정보를 올바르게 받지 못했어요"
    }
    private fun label(value:String,size:Int,bold:Boolean=false)=ui.text(value,size,if(bold) ui.accent else ui.ink,bold)
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=ui.button(value,primary) {act(action)}.apply {isEnabled=!session.busy}
    private fun dp(value:Int)=ui.dp(value)
    companion object {
        fun intent(context:Context,p:DrivePreferences,recent:Set<String>)=Intent(context,RealRouteActivity::class.java)
            .putExtra("mood",p.mood.ordinal).putExtra("scope",p.scope.ordinal).putExtra("companion",p.companion.ordinal)
            .putExtra("categories",p.categories.map {it.name}.toTypedArray()).putExtra("minutes",p.availableMinutes?:0)
            .putExtra("excludedCategories",p.excludedCategories.map {it.name}.toTypedArray())
            .putExtra("deadline",p.returnByEpochMillis?:0).putExtra("returns",p.returnToStart)
            .putExtra("recent",recent.toTypedArray())
    }
}
