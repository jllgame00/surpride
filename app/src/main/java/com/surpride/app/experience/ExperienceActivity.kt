package com.surpride.app.experience

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.surpride.app.experience.core.*
import com.surpride.app.moodinput.MoodInputActivity
import com.surpride.app.moodinput.PreferenceInputContract
import com.surpride.app.ui.SurprideUi
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.random.Random

/** Parked-only product DEMO. This Activity never calls a navigation/vehicle-control API. */
class ExperienceActivity : AppCompatActivity() {
    private val ui by lazy {SurprideUi(this)}
    private val bg get()=ui.background
    private val panel get()=ui.surface
    private val ink get()=ui.ink
    private val muted get()=ui.muted
    private val accent get()=ui.accent
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var nextControlId=0
    private lateinit var controller: TripController
    private lateinit var store: DemoStateStore
    private lateinit var ux: ParkedUxMonitor
    private var requiresDo: Boolean? = null
    private var restoreFailed=false
    private var saveFailed=false
    private var planning: Runnable? = null
    private var recoveryDialog: AlertDialog? = null
    private val handler=Handler(Looper.getMainLooper())
    private val allowed get()=ParkedInteractionPolicy.allows(requiresDo)
    private var pendingMoodPreferences:DrivePreferences?=null
    private val moodInput=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        if(result.resultCode==RESULT_OK) {
            pendingMoodPreferences=PreferenceInputContract.decode(result.data?.getByteArrayExtra(PreferenceInputContract.EXTRA))
            applyMoodPreferences()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        SurprideUi.applyAppearance(this)
        super.onCreate(savedInstanceState)
        store=DemoStateStore(this)
        val loaded=try {store.load()} catch (_: Exception) {restoreFailed=true;null}
        controller=TripController(DemoCatalog.candidates,System::currentTimeMillis,{Random.nextLong()},loaded)
        pendingMoodPreferences=PreferenceInputContract.decode(savedInstanceState?.getByteArray(PreferenceInputContract.EXTRA))
        ux=ParkedUxMonitor(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        root=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;setBackgroundColor(bg)}
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left,bars.top,bars.right,bars.bottom);insets
        }
        ViewCompat.requestApplyInsets(root)
        render()
    }
    override fun onStart() {
        super.onStart()
        ux.start { value ->
            requiresDo=value
            if(!allowed) {recoveryDialog?.dismiss();recoveryDialog=null}
            applyMoodPreferences()
            render()
        }
    }
    override fun onStop() {
        planning?.let(handler::removeCallbacks);planning=null
        recoveryDialog?.dismiss();recoveryDialog=null
        requiresDo=null;ux.stop()
        super.onStop()
    }
    override fun onDestroy() {ux.stop();super.onDestroy()}
    override fun onSaveInstanceState(outState:Bundle) {
        pendingMoodPreferences?.let {outState.putByteArray(PreferenceInputContract.EXTRA,PreferenceInputContract.encode(it))}
        super.onSaveInstanceState(outState)
    }
    private fun applyMoodPreferences() {
        val p=pendingMoodPreferences?:return
        if(!allowed || !::controller.isInitialized || restoreFailed) return
        pendingMoodPreferences=null;act {controller.updatePreferences(p)}
    }
    private fun openMoodInput() {
        if(allowed) moodInput.launch(android.content.Intent(this,MoodInputActivity::class.java)
            .putExtra(PreferenceInputContract.EXTRA,PreferenceInputContract.encode(controller.state.preferences)))
    }

    private fun act(action: () -> Unit) {
        if(!allowed || restoreFailed) return
        val focused=(currentFocus as? Button)?.tag
        val prior=controller.state
        action()
        try {store.save(controller.state);saveFailed=false} catch (_: Exception) {saveFailed=true}
        render()
        if(focused!=null && controller.state.panel==prior.panel && controller.state.phase==prior.phase && controller.state.setupStep==prior.setupStep) {
            root.findViewWithTag<Button>(focused)?.requestFocus()
        }
    }
    private fun render() {
        if(!::root.isInitialized) return
        nextControlId=0
        root.removeAllViews()
        val rail=column().apply {setPadding(dp(24),dp(32),dp(20),dp(24))}
        root.addView(ScrollView(this).apply {isFillViewport=true;addView(rail)},LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp<1100) 170 else 220),-1))
        rail.addView(text("S /",40,accent,true))
        rail.addView(text("SURPRIDE",22,ink,true))
        rail.addView(text("기분 따라,\n조금 낯선 곳으로",20,muted).apply {setPadding(0,dp(16),0,dp(24))})
        fun nav(label:String,selected:Boolean,action:()->Unit)=button(label,false,action).apply {ui.styleButton(this,selected=selected)}
        rail.addView(nav("시작",controller.state.panel==ExperiencePanel.HOME){controller.home()})
        rail.addView(nav("코스",controller.state.panel==ExperiencePanel.TRIP){showTrip()})
        rail.addView(nav("기분 기록",controller.state.panel==ExperiencePanel.MAP){controller.openMap()})
        rail.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        if(applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE!=0) {
            rail.addView(buttonRaw("코스 진단",false) {
                val score=controller.state.plan?.score
                showRecord("코스 진단",if(score==null) "아직 체험 코스가 없습니다" else "추천 점수 ${score.total}\n기분 ${score.mood} / 장소 ${score.place} / 동행 ${score.companion}\n날씨·계절·실제 주행 거리 미검증")
            })
        }
        rail.addView(text("정차용 체험\n가상 장소·모의 주행",20,muted))
        rail.addView(buttonRaw("첫 화면",false){finish()})
        content=column().apply {setPadding(dp(12),dp(24),dp(32),dp(24))}
        root.addView(content,LinearLayout.LayoutParams(0,-1,1f))
        val header=row()
        header.addView(text("당신의 다음 장면",20,muted),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text(if(allowed) "정차 중 이용" else "조작 제한",20,muted))
        content.addView(header)
        if(!allowed) {
            content.addView(card().apply {
                addView(text(if(requiresDo==null) "차량 UX 상태를 확인하고 있어요" else "정차 후 다시 만나요",32,ink,true))
                addView(text("상태를 확인할 수 없거나 주행 제한이 활성화되면 DEMO 입력을 잠급니다. 실제 안내는 시작되지 않습니다.",20,muted))
            })
            return
        }
        if(restoreFailed) {
            content.addView(card().apply {
                addView(text("이전 DEMO를 복원하지 못했어요",30,ink,true))
                addView(text("저장 내용은 덮어쓰지 않았습니다. 새로 시작하면 이 기기의 DEMO 저장만 초기화합니다.",19,muted))
                addView(buttonRaw("새 DEMO로 초기화",true) {
                    if(!allowed) return@buttonRaw
                    recoveryDialog=AlertDialog.Builder(this@ExperienceActivity).setMessage("저장된 DEMO 선택과 기록을 지우고 새로 시작할까요?")
                        .setNegativeButton("유지",null).setPositiveButton("초기화") {_,_ ->
                            ParkedInteractionPolicy.runIfAllowed({requiresDo}) {
                                store.reset();restoreFailed=false;controller=TripController(DemoCatalog.candidates,System::currentTimeMillis,{Random.nextLong()});render()
                            }
                        }.show()
                })
            });return
        }
        if(saveFailed) content.addView(text("기기 저장을 완료하지 못했어요. 현재 시연은 유지되지만 재시작 시 복원이 제한될 수 있습니다.",16,ui.error))
        when(controller.state.panel) {
            ExperiencePanel.HOME -> home()
            ExperiencePanel.MAP -> moodMap()
            ExperiencePanel.TRIP -> trip()
        }
        if(controller.state.phase==TripPhase.PLANNING && planning==null) {
            planning=Runnable {planning=null;if(allowed && !isFinishing) act {controller.finishPlanning()}}
            handler.postDelayed(planning!!,550)
        }
    }
    private fun showTrip() {
        if(controller.state.panel!=ExperiencePanel.TRIP) {
            // Resume a saved course instead of silently replacing it.
            controller.showTrip()
        }
    }
    private fun home() {
        title("어디로 갈지는, 잠시 비밀로","목적지는 몰라도, 가는 길은 즐겁게.")
        val split=row();content.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        val artCard=card();split.addView(artCard,LinearLayout.LayoutParams(0,-1,1.2f))
        artCard.addView(text("오늘의 기분",20,muted))
        artCard.addView(text(controller.state.preferences.mood.label,56,ink,true))
        artCard.addView(text(timeSummary(controller.state.preferences),28,muted))
        artCard.addView(text("기분과 시간으로\n드라이브를 준비해요",32,ink))
        val intro=card();split.addView(ScrollView(this).apply {isFillViewport=true;addView(intro)},LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(24)})
        intro.addView(text("오늘은 어떤 마음인가요?",28,ink,true))
        intro.addView(text("기분과 시간을 골라 보세요. 이름을 남겨 둔 코스를 준비해요.",24,muted))
        intro.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        intro.addView(text("가상 코스 체험과 실제 경로 준비를 구분해 이용할 수 있어요. 모두 정차 에뮬레이터용입니다.",20,muted))
        intro.addView(button("드라이브 조건 선택",true){controller.openSetup()})
        intro.addView(button("문장으로 조건 정리",false){openMoodInput()})
        intro.addView(button("실제 경로로 준비",false){
            startActivity(com.surpride.app.realdata.RealRouteActivity.intent(this,controller.state.preferences,controller.state.history.map {it.candidateId}.toSet()))
        })
        if(controller.state.plan!=null && controller.state.phase!=TripPhase.COMPLETED) intro.addView(button("이전 DEMO 이어서",false){controller.showTrip()})
    }
    private fun trip() {
        when(controller.state.phase) {
            TripPhase.SETUP -> setup()
            TripPhase.PLANNING -> {
                title("비밀 코스를 준비해요","선택 조건과 가용 시간을 확인하고 있습니다.")
                content.addView(card().apply {addView(text("체험 코스를 고르고 있어요",28,ink,true));addView(text("가상 장소와 예시 시간으로 준비합니다. 실제 경로 탐색은 하지 않습니다.",24,muted))})
            }
            TripPhase.READY -> ready()
            TripPhase.DEMO_DRIVING -> demo()
            TripPhase.ARRIVED -> arrived()
            TripPhase.REVEALED,TripPhase.COMPLETED -> revealed()
        }
    }
    private fun setup() {
        val s=controller.state;val p=s.preferences
        title("당신에게 맞는 드라이브","${s.setupStep+1} / 3  ·  선택해서 준비하는 나만의 코스")
        val split=row();content.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        val form=column();split.addView(ScrollView(this).apply {isFillViewport=true;addView(form)},LinearLayout.LayoutParams(0,-1,1.7f))
        form.addView(button("문장으로 조건 수정",false){openMoodInput()})
        if(s.error!=null) form.addView(text(s.error,18,ui.error))
        when(s.setupStep) {
            0 -> {
                form.addView(text("오늘의 기분",23,ink,true))
                choices(form,Mood.entries,3,{it.label},{it==p.mood}){v -> prefs(p.copy(mood=v))}
                form.addView(text("만나고 싶은 장소",23,ink,true).apply {setPadding(0,dp(20),0,0)})
                form.addView(text("함께 선택한 조건을 모두 만족하는 샘플만 찾습니다.",16,muted))
                choices(form,PlaceCategory.entries,2,{it.label},{it in p.categories}) {v -> prefs(p.copy(categories=if(v in p.categories) p.categories-v else p.categories+v,excludedCategories=p.excludedCategories-v))}
                form.addView(text("제외 조건: ${p.excludedCategories.joinToString {it.label}.ifEmpty {"없음"}}",17,muted))
                form.addView(button("특별한 조건 없음 · 선택 해제",false){prefs(p.copy(categories=emptySet(),excludedCategories=emptySet()))}.apply {ui.styleButton(this,selected=p.categories.isEmpty() && p.excludedCategories.isEmpty())})
            }
            1 -> {
                form.addView(text("얼마나 멀리 떠나볼까요?",23,ink,true))
                choices(form,TravelScope.entries,2,{"${it.label}\n${it.note}"},{it==p.scope}) {v -> prefs(p.copy(scope=v,availableMinutes=v.defaultMinutes,returnByEpochMillis=null))}
                form.addView(text("시간 기준",23,ink,true).apply {setPadding(0,dp(20),0,0)})
                choices(form,listOf(false,true),2,{if(it) "귀가 희망 시각" else "사용 가능 시간"},{it==(p.returnByEpochMillis!=null)}) {v -> prefs(if(v) p.copy(availableMinutes=null,returnByEpochMillis=System.currentTimeMillis()+p.scope.defaultMinutes*60_000L) else p.copy(availableMinutes=p.scope.defaultMinutes,returnByEpochMillis=null))}
                val slots=listOf(30,60,90,180,2880)
                choices(form,slots,3,{minutes -> if(p.returnByEpochMillis!=null) deadlineLabel(System.currentTimeMillis()+minutes*60_000L) else duration(minutes)}, {minutes -> if(p.returnByEpochMillis!=null) false else p.availableMinutes==minutes}) {v -> prefs(if(p.returnByEpochMillis!=null) p.copy(returnByEpochMillis=System.currentTimeMillis()+v*60_000L) else p.copy(availableMinutes=v))}
                form.addView(button(if(p.returnToStart) "왕복 · 출발지로 돌아오기" else "편도 · 도착지에서 마무리",false){prefs(p.copy(returnToStart=!p.returnToStart))}.apply {ui.styleButton(this,selected=p.returnToStart)})
                form.addView(text("이동·머무름·복귀·여유 시간을 포함한 합성 예시입니다. 실제 귀가 시각을 보장하지 않습니다.",16,muted))
            }
            2 -> {
                form.addView(text("누구와 함께인가요?",23,ink,true))
                choices(form,Companion.entries,2,{it.label},{it==p.companion}){v -> prefs(p.copy(companion=v))}
                form.addView(text("어느 정도의 우연을 원하나요?",23,ink,true).apply {setPadding(0,dp(24),0,0)})
                choices(form,listOf(0,40,100),3,{when(it){0->"취향 중심";40->"적당한 우연";else->"새로운 발견"}},{it==p.randomness}){v -> prefs(p.copy(randomness=v))}
                form.addView(text("랜덤 정도를 높여도 시간·장소·동반자 조건은 완화하지 않습니다.",18,muted))
                form.addView(card().apply {addView(text("차량과 환경 데이터",21,ink,true));addView(text("주행 가능 거리 미확인\n날씨·계절 적합성 미확인\nSOC로 거리를 추정하지 않습니다.",18,muted))})
            }
        }
        form.addView(button(if(s.setupStep==2) "비밀 코스 준비" else "다음 조건",true){if(s.setupStep==2) controller.beginPlanning() else controller.setupStep(s.setupStep+1)})
        if(s.setupStep>0) form.addView(button("이전 조건",false){controller.setupStep(s.setupStep-1)})
        val summary=card();split.addView(ScrollView(this).apply {isFillViewport=true;addView(summary)},LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(24)})
        summary.addView(text("나의 드라이브",20,accent));summary.addView(text(p.mood.label,32,ink,true))
        summary.addView(text("${p.scope.label} · ${p.scope.note}\n\n선호: ${p.categories.joinToString(" · "){it.label}.ifEmpty {"특별한 장소 조건 없음"}}\n제외: ${p.excludedCategories.joinToString {it.label}.ifEmpty {"없음"}}\n\n${p.companion.label}\n${timeSummary(p)}\n${if(p.returnToStart) "출발지 복귀" else "도착지 마무리"}\n랜덤 ${p.randomness}%",19,muted))
        summary.addView(Space(this),LinearLayout.LayoutParams(1,0,1f));summary.addView(text("체험 코스\n장소와 시간은 가상 예시",20,muted))
    }
    private fun ready() {
        val view=controller.presentation()
        title("비밀 코스가 준비됐어요","목적지는 아직 비밀입니다 · 가상 코스 체험")
        val split=row();content.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        val visual=card();split.addView(visual,LinearLayout.LayoutParams(0,-1,1f))
        visual.addView(text("DEMO 코스 · 가상 시간",20,muted))
        visual.addView(text("약 ${view.totalMinutes}분",56,ink,true))
        visual.addView(text("목적지는 도착 후 공개",28,muted))
        val detail=card();split.addView(ScrollView(this).apply {isFillViewport=true;addView(detail)},LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(24)})
        detail.addView(text("약 ${view.totalMinutes}분",32,ink,true))
        detail.addView(text(if(controller.state.preferences.returnToStart) "왕복 · 이동과 체류, 여유 시간 포함" else "편도 · 이동과 체류, 여유 시간 포함",20,muted))
        detail.addView(text(view.reason,24,muted));detail.addView(text("준비물 힌트\n${view.preparation}",24,ink))
        detail.addView(text("가상 시간이며 실제 경로를 계산하지 않았어요. 날씨·주차·차량 진입은 확인이 필요해요.",20,muted))
        detail.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        detail.addView(button("가상 코스 체험 시작",true){controller.startDemo()})
        detail.addView(button("지금 목적지 공개",false){controller.revealNow()})
        detail.addView(button("조건 수정",false){controller.updatePreferences(controller.state.preferences)})
    }
    private fun demo() {
        val s=controller.state
        title("가는 길도, 오늘의 경험","DEMO 시뮬레이션 · 실제 도로에서 따라가지 마세요")
        val box=card();content.addView(box,LinearLayout.LayoutParams(-1,0,1f))
        box.addView(text("${s.progress+1} / 4   ·   모의 진행",17,accent,true))
        val turns=listOf("↱  300m 앞 우회전 예시","↑  500m 직진 예시","↰  200m 앞 좌회전 예시","○  도착 직전 예시")
        box.addView(text(turns[s.progress],38,ink,true))
        val minutes=max(1,((s.plan?.totalMinutes ?: 1)*(4-s.progress))/4)
        box.addView(text("모의 잔여 $minutes 분",26,muted))
        box.addView(ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {max=4;progress=s.progress+1;progressTintList=android.content.res.ColorStateList.valueOf(accent)},LinearLayout.LayoutParams(-1,dp(12)).apply {topMargin=dp(20);bottomMargin=dp(20)})
        if(s.hintVisible) box.addView(text(controller.presentation().preparation,22,accent))
        box.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        val controls=row();box.addView(controls)
        controls.addView(button("다음 예시",true){controller.advanceDemo()},weighted())
        controls.addView(button(if(s.hintVisible) "힌트 접기" else "힌트 보기",false){controller.toggleHint()},weighted())
        controls.addView(button("도착 시뮬레이션",false){controller.arrive()},weighted())
        val secondary=row();box.addView(secondary)
        secondary.addView(button("목적지 즉시 공개",false){controller.revealNow()},weighted())
        secondary.addView(button("DEMO 취소",false){controller.cancel()},weighted())
    }
    private fun arrived() {
        title("다음 장면에 도착했어요","도착 이벤트를 시뮬레이션했습니다. 실제 위치 확인은 수행하지 않았습니다.")
        content.addView(card().apply {
            addView(text("모의 도착 · 실제 위치 확인 아님",20,muted));addView(text("어떤 장면이 기다릴까요?",36,ink,true))
            addView(text("목적지 이름은 직접 공개할 때까지 표시하지 않습니다.",21,muted))
            addView(button("도착한 목적지 공개",true){controller.revealArrival()})
        })
    }
    private fun revealed() {
        val s=controller.state;val destination=controller.presentation().destination ?: return
        title(if(s.phase==TripPhase.COMPLETED) "오늘의 기분을 남겼어요" else if(s.arrived) "당신을 위한 도착의 장면" else "조금 먼저 만나는 목적지","가상 샘플 공개 · 실제 장소 방문이나 경로 안내 기록이 아닙니다")
        content.addView(ScrollView(this).apply {isFillViewport=true;addView(card().apply {
            addView(text(destination.name,34,accent,true));addView(text(destination.description,19,muted))
            addView(text(controller.presentation().reason,22,ink))
            if(s.phase==TripPhase.REVEALED) {
                addView(text("이 도착 경험은 어땠나요?",23,ink,true))
                choices(this,(1..5).toList(),5,{"$it 점"},{it==s.rating}){v -> controller.rate(v)}
                addView(button(if(s.rating==null) "평가를 고르면 저장할 수 있어요" else "평가와 체험 기록 저장",true){controller.complete()}.apply {isEnabled=s.rating!=null})
            } else {
                addView(text("${s.rating} / 5  ·  ${if(s.arrived) "모의 도착 후 기록" else "미리 공개 후 기록"}",24,ink,true))
                addView(button("기분 지도에서 보기",true){controller.openMap()})
                addView(button("새 DEMO 만들기",false){controller.openSetup()})
            }
        })},LinearLayout.LayoutParams(-1,0,1f))
    }
    private fun moodMap() {
        title("내 마음이 머문 장면들","기분별 체험 기록 · 실제 지리 지도나 방문 인증이 아닙니다")
        val split=row();content.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        val map=card();split.addView(map,LinearLayout.LayoutParams(0,-1,1f))
        map.addView(text("여섯 기분의 기록",20,accent));map.addView(ExperienceArt(this,controller.state.history),LinearLayout.LayoutParams(-1,0,1f))
        val list=column();split.addView(ScrollView(this).apply {addView(list)},LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(24)})
        val live=com.surpride.app.realdata.LiveNavigationStore.read(this).visits
        if(live.isNotEmpty()) list.addView(card().apply {
            addView(text("실제 경로 체험 기록",28,ink,true))
            addView(text("정차 에뮬레이터 평가 · 실도로 방문 인증 아님\nDEMO 기분 개념도와 별도 기록입니다.",17,muted))
            live.asReversed().take(10).forEach {visit->
                val place=com.surpride.app.realdata.SeoulPilotCatalog.places.firstOrNull {it.id==visit.candidateId}
                addView(text("${place?.name?:"공개 장소"} · ${visit.rating}/5",21,ink,true))
                addView(text("에뮬레이터 기록 · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(visit.completedAtMillis))}",20,muted))
                addView(button("기록 자세히 보기",false){showRecord(place?.name?:"공개 장소","평가 ${visit.rating} / 5\n에뮬레이터 체험 · 실제 방문 인증 아님\n${DateFormat.getDateTimeInstance().format(Date(visit.completedAtMillis))}\n기분과 이동 시간은 이 기록에 저장하지 않았어요")})
            }
        })
        if(controller.state.history.isEmpty()) list.addView(card().apply {addView(text("첫 여정을 기다리고 있어요",27,ink,true));addView(text("DEMO를 공개하고 평가하면 기분별 기록이 여기에 모입니다.",19,muted));addView(button("첫 DEMO 만들기",true){controller.openSetup()})})
        controller.state.history.asReversed().forEach {visit -> list.addView(card().apply {
            addView(text("${visit.mood.label}  ·  ${visit.rating}/5",22,accent,true))
            addView(text(DemoCatalog.candidates.firstOrNull {it.id==visit.candidateId}?.name ?: "샘플 정보 없음",22,ink,true))
            addView(text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(visit.completedAtMillis))}\nDEMO · ${if(visit.simulatedArrival) "모의 도착" else "미리 공개"}",17,muted))
            addView(button("기록 자세히 보기",false){showRecord("${visit.mood.label}의 드라이브","${DemoCatalog.candidates.firstOrNull {it.id==visit.candidateId}?.name?:"가상 장소"}\n평가 ${visit.rating} / 5 · ${if(visit.simulatedArrival) "모의 도착" else "미리 공개"}\n${DateFormat.getDateTimeInstance().format(Date(visit.completedAtMillis))}\n가상 코스 체험 · 이동 시간 기록 없음")})
        })}
    }
    private fun showRecord(title:String,detail:String) {
        if(!allowed) return
        recoveryDialog=AlertDialog.Builder(this).setTitle(title).setMessage(detail).setPositiveButton("닫기",null).create()
        recoveryDialog?.show()
        recoveryDialog?.let(ui::styleDialog)
    }
    private fun prefs(p: DrivePreferences) = controller.updatePreferences(p)
    private fun title(value: String, subtitle: String) {
        content.addView(text(value,32,ink,true).apply {setPadding(0,dp(16),0,dp(8))})
        content.addView(text(subtitle,20,muted).apply {setPadding(0,0,0,dp(16))})
    }
    private fun column()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun card()=ui.card()
    private fun text(value: String,size: Int,color: Int,bold: Boolean=false)=ui.text(value,max(20,size),color,bold)
    private fun button(label: String,primary: Boolean=false,action: () -> Unit) = buttonRaw(label,primary) {act(action)}
    private fun buttonRaw(label: String,primary: Boolean,action: () -> Unit)=ui.button(label,primary,action).apply {tag=nextControlId++}
    private fun <T> choices(parent: LinearLayout,options: List<T>,columns: Int,label: (T)->String,selected: (T)->Boolean,choose: (T)->Unit) {
        options.chunked(columns).forEach {chunk ->
            val line=row();parent.addView(line)
            chunk.forEach {value -> line.addView(button(label(value),false){choose(value)}.apply {ui.styleButton(this,selected=selected(value))},weighted())}
            repeat(columns-chunk.size){line.addView(Space(this),weighted())}
        }
    }
    private fun weighted()=LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(4),dp(4),dp(4),dp(4))}
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun duration(minutes: Int)=if(minutes>=1440) "${minutes/1440}일" else if(minutes>=60) "${minutes/60}시간${if(minutes%60>0) " ${minutes%60}분" else ""}" else "$minutes 분"
    private fun deadlineLabel(at: Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(at))+" 귀가"
    private fun timeSummary(p: DrivePreferences)=p.returnByEpochMillis?.let(::deadlineLabel) ?: "사용 가능 ${duration(p.availableMinutes ?: 0)}"
}
