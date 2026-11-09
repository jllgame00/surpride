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

/** Parked-only ephemeral validation. Never starts guidance or writes positions/history to disk. */
class RealRouteActivity:AppCompatActivity() {
    class Session:ViewModel() {
        var initialized=false
        var preferences=DrivePreferences()
        var recentIds:Set<String> = emptySet()
        var plan:RoutePlan?=null
        var revealed=false
        var mock=false
        var stay=20
        var gps="아직 확인하지 않음"
    }
    private lateinit var session:Session
    private lateinit var ux:ParkedUxMonitor
    private lateinit var root:LinearLayout
    private var requiresDo:Boolean?=null
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
                returnToStart=intent.getBooleanExtra("returns",true),randomness=0)
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
    override fun onStart() {super.onStart();ux.start {requiresDo=it;render()}}
    override fun onStop() {requiresDo=null;ux.stop();super.onStop()}
    override fun onDestroy() {ux.stop();super.onDestroy()}
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
    private fun reset() {session.plan=null;session.revealed=false}
    private fun calculate(mock:Boolean) {
        reset();session.mock=mock
        if(!mock) {session.plan=RoutePlan.Failure(setOf(RouteError.NOT_CONFIGURED));return}
        val location=DevelopmentLocationProvider().current()
        session.plan=RouteRecommendationEngine(SeoulPilotCatalog.places,
            if(mock) MockRouteProvider(System::currentTimeMillis) else UnconfiguredRouteProvider()).recommend(
            location.point,session.preferences,RecommendationContext(System.currentTimeMillis(),recentIds=session.recentIds),session.stay,10,1)
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
        left.addView(label("서울 공개 장소 5곳 · 출처: 서울특별시 야경명소 정보 / 공공누리 1유형",18))
        left.addView(label("대표 좌표는 차량 진입점이 아닙니다. 주차·운영시간·접근성 UNKNOWN. 실도로 추천·길 안내를 제공하지 않습니다.",18))
        val p=session.preferences
        left.addView(label("MVP 조건: ${p.mood.label} / ${p.scope.label} / ${p.companion.label}\n장소: ${p.categories.joinToString {it.label}.ifBlank {"특별한 조건 없음"}}\n가용 ${p.availableMinutes?:"미설정"}분 · ${if(p.returnToStart) "왕복" else "편도"}\n체류 ${session.stay}분 + 안전 여유 10분 (계획 가정)",20))
        p.returnByEpochMillis?.let {left.addView(label("귀가 시각 제한: ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}\n아래 가용 시간 선택 시 귀가 제한을 대체합니다.",18))}
        val times=LinearLayout(this)
        listOf(30,60,180).forEach {minutes->times.addView(button("${minutes}분") {session.preferences=p.withAvailableBudget(minutes);reset()},LinearLayout.LayoutParams(0,-2,1f))}
        left.addView(times)
        if(p.companion!=DriveCompanion.SOLO || p.categories.isNotEmpty()) left.addView(button("검증 조건: 혼자 · 장소 제한 없음") {
            session.preferences=p.copy(companion=DriveCompanion.SOLO,categories=emptySet());reset()
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
        right.addView(label(if(session.mock) "REAL PLACES + MOCK ROUTE" else "REAL DATA / 경로 미연결",24,true))
        right.addView(label("실제 장소와 실제 도로 데이터는 별개입니다. 인증키·서버 프록시가 없어 실시간 경로 요청은 실행되지 않습니다.",19))
        right.addView(button("REAL DATA 연결 확인") {calculate(false)})
        right.addView(button("명시적으로 MOCK 경로 평가") {calculate(true)})
        when(val plan=session.plan) {
            is RoutePlan.Failure -> right.addView(label("추천 생성 안 됨: ${plan.errors.joinToString()}\n자동 DEMO 전환 없음. 동행 적합성 미확인 시 혼자 조건만 평가합니다. 조건은 MVP 설정에서 수정할 수 있습니다.",20))
            is RoutePlan.Success -> {
                val view=plan.presentation(session.revealed)
                right.addView(label("비밀 코스 선정 · ${view.mode}\n총 ${view.totalMinutes}분 / ${"%.1f".format(view.distanceMeters/1000.0)}km\n시간·거리는 합성 Mock 값이며 실제 이동 예측이 아닙니다.",23,true))
                right.addView(label("기분·장소 선호 점수로 선정. 경관 점수 없음. 날씨·계절·차량 주행 가능 거리 미확인. 복귀도 별도 조회 계약으로 검증합니다.",18))
                if(view.destination==null) {
                    right.addView(label("목적지 이름·좌표 비공개",22,true))
                    right.addView(button("선정 장소 공개") {session.revealed=true})
                } else {
                    val place=view.destination
                    right.addView(label(place.name,28,true))
                    right.addView(label("${place.type}\n대표 좌표 (경도, 위도): ${place.point.longitude}, ${place.point.latitude}\n${place.license} · 확인 ${place.checkedDate}\n${place.sourceUrl}\n주차/운영시간/접근성: UNKNOWN",17))
                }
            }
            null -> right.addView(label("REAL DATA는 미구성 오류를 확인합니다. MOCK은 실제 장소 후보와 합성 응답으로 시간 제약을 검사합니다. 실제 내비게이션은 시작하지 않습니다.",20))
        }
    }
    private fun label(value:String,size:Int,bold:Boolean=false)=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(if(bold) accent else ink);setPadding(0,dp(8),0,dp(8))
        if(bold) setTypeface(typeface,android.graphics.Typeface.BOLD)
    }
    private fun button(value:String,action:()->Unit)=Button(this).apply {
        text=value;textSize=20f;minHeight=dp(70);isAllCaps=false;setOnClickListener {act(action)}
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
    companion object {
        fun intent(context:Context,p:DrivePreferences,recent:Set<String>)=Intent(context,RealRouteActivity::class.java)
            .putExtra("mood",p.mood.ordinal).putExtra("scope",p.scope.ordinal).putExtra("companion",p.companion.ordinal)
            .putExtra("categories",p.categories.map {it.name}.toTypedArray()).putExtra("minutes",p.availableMinutes?:0)
            .putExtra("deadline",p.returnByEpochMillis?:0).putExtra("returns",p.returnToStart)
            .putExtra("recent",recent.toTypedArray())
    }
}
