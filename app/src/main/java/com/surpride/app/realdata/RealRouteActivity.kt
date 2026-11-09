package com.surpride.app.realdata

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
    }
    private lateinit var session:Session
    private lateinit var ux:ParkedUxMonitor
    private lateinit var root:LinearLayout
    private var requiresDo:Boolean?=null
    private val executor=Executors.newSingleThreadExecutor()
    private var pending:Future<*>?=null
    private var requestVersion=0
    private val allowed get()=ParkedInteractionPolicy.allows(requiresDo)
    private val ink=Color.rgb(235,241,225)
    private val accent=Color.rgb(215,242,139)

    override fun onCreate(savedInstanceState:Bundle?) {
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
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(10,24,24))}
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
        header.addView(label("SURPRIDE / DATA LAB",25,true),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(this).apply {text="DEMO로 돌아가기";textSize=20f;minHeight=dp(68);setOnClickListener {finish()}})
        if(!allowed) {root.addView(label("정차 상태 확인 전 또는 주행 제한 중에는 조작할 수 없습니다.",26));return}
        val columns=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL}
        root.addView(columns,LinearLayout.LayoutParams(-1,0,1f))
        val left=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(0,dp(12),dp(28),0)}
        val right=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(24),dp(12),0,0)}
        columns.addView(ScrollView(this).apply {addView(left)},LinearLayout.LayoutParams(0,-1,1f))
        columns.addView(ScrollView(this).apply {addView(right)},LinearLayout.LayoutParams(0,-1,1.1f))
        left.addView(label("실제 장소 · 경로 검증",30,true))
        left.addView(label("임시 실제경로 · 정차 에뮬레이터 전용, 실제 운행 검증 없음",18))
        left.addView(label("서울 공개 장소 5곳 · 출처: 서울특별시 야경명소 정보 / 공공누리 1유형",18))
        left.addView(label("대표 좌표는 차량 진입점이 아닙니다. 주차·운영시간·접근성 UNKNOWN. 실도로 추천·길 안내를 제공하지 않습니다.",18))
        val p=session.preferences
        left.addView(label("피할 장소: ${p.excludedCategories.joinToString {it.label}.ifBlank {"없음"}}",20))
        left.addView(label("MVP 조건: ${p.mood.label} / ${p.scope.label} / ${p.companion.label}\n장소: ${p.categories.joinToString {it.label}.ifBlank {"특별한 조건 없음"}}\n가용 ${p.availableMinutes?:"미설정"}분 · ${if(p.returnToStart) "왕복" else "편도"}\n체류 ${session.stay}분 + 안전 여유 10분 (계획 가정)",20))
        p.returnByEpochMillis?.let {left.addView(label("귀가 시각 제한: ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}\n아래 가용 시간 선택 시 귀가 제한을 대체합니다.",18))}
        val times=LinearLayout(this)
        listOf(30,60,180).forEach {minutes->times.addView(button("${minutes}분") {session.preferences=p.withAvailableBudget(minutes);reset()},LinearLayout.LayoutParams(0,-2,1f))}
        left.addView(times)
        if(p.companion!=DriveCompanion.SOLO || p.categories.isNotEmpty() || p.excludedCategories.isNotEmpty()) left.addView(button("검증 조건: 혼자 · 장소 제한 없음") {
            session.preferences=p.copy(companion=DriveCompanion.SOLO,categories=emptySet(),excludedCategories=emptySet());reset()
        })
        left.addView(button(if(p.returnToStart) "왕복 → 편도로 변경" else "편도 → 왕복으로 변경") {session.preferences=p.copy(returnToStart=!p.returnToStart);reset()})
        left.addView(button("체류 ${session.stay}분 → 변경") {session.stay=if(session.stay==20) 40 else 20;reset()})
        left.addView(button("기기 위치 접근 상태 확인") {
            readGpsStatus()
        })
        left.addView(button("전경 위치 권한 요청 (선택)") {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION),700)
        })
        left.addView(label("GPS: ${session.gps}\n추천 출발지: 개발용 고정 공개 좌표\n현재 기기 위치를 사용하거나 저장하지 않습니다.",18))
        left.addView(label("LIVE AVD 기록 / DEVICE_API_EMULATOR",22,true))
        left.addView(label("실도로 방문 인증 아님 · DEMO 기록과 별도",17))
        val visits=LiveNavigationStore.read(this).visits
        if(visits.isEmpty()) left.addView(label("공개·중지 후 직접 저장한 에뮬레이터 평가가 없습니다.",18))
        visits.asReversed().forEach {visit->
            val place=SeoulPilotCatalog.places.firstOrNull {it.id==visit.candidateId}
            left.addView(label("${place?.name?:visit.candidateId} · ${visit.rating}/5\n${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(visit.completedAtMillis))}\nDEVICE_API_EMULATOR",18))
        }
        right.addView(label(if(session.mock) "REAL PLACES + MOCK ROUTE" else "SERVER PROXY / 응답 출처 확인",24,true))
        right.addView(label("실제 장소와 실제 경로는 별개입니다. 서버의 MOCK 응답은 실시간 교통 데이터가 아닙니다. 대표 좌표의 차량 진입점은 미확인입니다.",19))
        right.addView(button("서버 프록시로 경로 평가") {calculate(false)})
        right.addView(button("명시적으로 MOCK 경로 평가") {calculate(true)})
        right.addView(button(if(session.optimize) "경로·목적지 통합 비교 ON · 기존 평가로 변경" else "기존 평가 · 경로·목적지 통합 비교 켜기") {
            session.optimize=!session.optimize;reset()
        })
        right.addView(label("경관 연구 후보 2곳은 차량 진입·운영·이용 조건 미확인으로 제외합니다. 경관 UNKNOWN은 낮은 품질이라는 뜻이 아닙니다.",18))
        right.addView(label(session.requestStatus,18))
        if(session.busy) right.addView(Button(this).apply {
            text="조회 취소";textSize=20f;minHeight=dp(70);setOnClickListener {act {cancelPending()}}
        })
        when(val plan=session.plan) {
            is RoutePlan.Failure -> right.addView(label("추천 생성 안 됨: ${plan.errors.joinToString()}\n자동 DEMO 전환 없음. 동행 적합성 미확인 시 혼자 조건만 평가합니다. 조건은 MVP 설정에서 수정할 수 있습니다.",20))
            is RoutePlan.Success -> {
                val view=plan.presentation(session.revealed)
                val provenance=if(view.mode==RouteOrigin.LIVE_API) "공급자 조회 시점의 자동차 예상값 · 실제 길 안내 아님" else "시간·거리는 합성 Mock 값이며 실제 이동 예측이 아닙니다."
                right.addView(label("비밀 코스 선정 · ${view.mode}\n총 ${view.totalMinutes}분 / ${"%.1f".format(view.distanceMeters/1000.0)}km\n$provenance",23,true))
                right.addView(label(plan.experience?.publicReason?:"기분·장소 선호 점수로 선정. 경관 점수 없음. 날씨·계절·차량 주행 가능 거리 미확인. 복귀도 별도 조회 계약으로 검증합니다.",18))
                plan.experience?.let {experience->
                    val selected=experience.selectedOptions
                    val routeType=if(selected.waypoints.isNotEmpty()) "검증 경유" else when(selected.priority) {
                        RoutePriority.RECOMMEND->"기본";RoutePriority.TIME->"시간 우선";RoutePriority.DISTANCE->"거리 우선"
                    }
                    right.addView(label("선정 유형: $routeType / 경관 보장 없음\n총 이용시간은 출발+복귀+체류+여유이며 교통 상황에 따라 달라집니다.",18))
                    right.addView(label(experience.comparisons.mapIndexed {i,v->
                        "비교 ${i+1} · ${v.label}: ${v.totalSeconds?.let {s->"${(s+59)/60}분"}?:"시간 없음"} · ${v.status}"
                    }.joinToString("\n"),17))
                }
                if(view.mode==RouteOrigin.LIVE_API) {
                    right.addView(label("REAL ROUTE + DEVICE_API_EMULATOR\n선정 코스의 출발편 전체 안내를 PoC 준비 화면으로 전달합니다. 비교 경로는 같은 geometry를 유지하고 신선도·귀가 제한을 재검사합니다. PoC에서 직접 시작해야 합니다. 실도로 인증·자동 복귀 안내는 제공하지 않습니다. 차량 진입·주차 UNKNOWN.",18))
                    right.addView(button("선정 LIVE 코스로 내비 PoC 준비") {prepareNavigation(plan)})
                }
                if(view.destination==null) {
                    right.addView(label("목적지 이름·좌표 비공개",22,true))
                    right.addView(button("선정 장소 공개") {session.revealed=true})
                } else {
                    val place=view.destination
                    right.addView(label(place.name,28,true))
                    right.addView(label("${place.type}\n대표 좌표 (경도, 위도): ${place.point.longitude}, ${place.point.latitude}\n${place.license} · 확인 ${place.checkedDate}\n${place.sourceUrl}\n주차/운영시간/접근성: UNKNOWN",17))
                }
            }
            null -> right.addView(label("서버 키 미설정·연결 실패 시 오류로 표시합니다. MOCK은 실제 장소 후보와 합성 응답으로 시간 제약을 검사합니다. 실제 내비게이션은 시작하지 않습니다.",20))
        }
    }
    private fun label(value:String,size:Int,bold:Boolean=false)=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(if(bold) accent else ink);setPadding(0,dp(8),0,dp(8))
        if(bold) setTypeface(typeface,android.graphics.Typeface.BOLD)
    }
    private fun button(value:String,action:()->Unit)=Button(this).apply {
        text=value;textSize=20f;minHeight=dp(70);isAllCaps=false;isEnabled=!session.busy;setOnClickListener {act(action)}
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    companion object {
        fun intent(context:Context,p:DrivePreferences,recent:Set<String>)=Intent(context,RealRouteActivity::class.java)
            .putExtra("mood",p.mood.ordinal).putExtra("scope",p.scope.ordinal).putExtra("companion",p.companion.ordinal)
            .putExtra("categories",p.categories.map {it.name}.toTypedArray()).putExtra("minutes",p.availableMinutes?:0)
            .putExtra("excludedCategories",p.excludedCategories.map {it.name}.toTypedArray())
            .putExtra("deadline",p.returnByEpochMillis?:0).putExtra("returns",p.returnToStart)
            .putExtra("recent",recent.toTypedArray())
    }
}
