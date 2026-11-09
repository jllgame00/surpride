package com.surpride.app.experience

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.surpride.app.experience.core.*
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlin.random.Random

/** Parked-only product DEMO. This Activity never calls a navigation/vehicle-control API. */
class ExperienceActivity : AppCompatActivity() {
    private val bg=Color.rgb(10,24,24)
    private val panel=Color.rgb(21,40,39)
    private val ink=Color.rgb(235,241,225)
    private val muted=Color.rgb(154,177,165)
    private val accent=Color.rgb(215,242,139)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store=DemoStateStore(this)
        val loaded=try {store.load()} catch (_: Exception) {restoreFailed=true;null}
        controller=TripController(DemoCatalog.candidates,System::currentTimeMillis,{Random.nextLong()},loaded)
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

    private fun act(action: () -> Unit) {
        if(!allowed || restoreFailed) return
        action()
        try {store.save(controller.state);saveFailed=false} catch (_: Exception) {saveFailed=true}
        render()
    }
    private fun render() {
        if(!::root.isInitialized) return
        root.removeAllViews()
        val rail=column().apply {setPadding(dp(24),dp(32),dp(20),dp(24))}
        root.addView(rail,LinearLayout.LayoutParams(dp(if(resources.configuration.screenWidthDp<1100) 170 else 220),-1))
        rail.addView(text("S /",42,accent,true))
        rail.addView(text("SURPRIDE",22,ink,true))
        rail.addView(text("기분 따라,\n조금 낯선 곳으로.",17,muted).apply {setPadding(0,dp(16),0,dp(32))})
        rail.addView(button("시작",controller.state.panel==ExperiencePanel.HOME){controller.home()})
        rail.addView(button("코스",controller.state.panel==ExperiencePanel.TRIP){showTrip()})
        rail.addView(button("기분 지도",controller.state.panel==ExperiencePanel.MAP){controller.openMap()})
        rail.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        rail.addView(text("PARKED DEMO\n실제 길 안내 아님",14,muted))
        rail.addView(TextView(this).apply {
            text="기존 화면으로";setTextColor(muted);textSize=18f;minHeight=dp(76);gravity=Gravity.CENTER_VERTICAL
            setOnClickListener {finish()}
        })
        content=column().apply {setPadding(dp(12),dp(28),dp(32),dp(20))}
        root.addView(content,LinearLayout.LayoutParams(0,-1,1f))
        val header=row()
        header.addView(text("당신의 다음 장면",18,muted),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(text(if(allowed) "●  정차용 UI 허용" else "●  조작 제한",14,if(allowed) accent else muted))
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
        if(saveFailed) content.addView(text("기기 저장을 완료하지 못했어요. 현재 시연은 유지되지만 재시작 시 복원이 제한될 수 있습니다.",16,accent))
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
        title("어디로 갈지는,\n잠시 비밀로.","목적지는 몰라도, 가는 길은 즐겁게.")
        val split=row();content.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        val artCard=card();split.addView(artCard,LinearLayout.LayoutParams(0,-1,1.2f))
        artCard.addView(text("A DRIVE FOR YOUR MOOD",14,accent,true))
        artCard.addView(ExperienceArt(this,controller.state.preferences.mood),LinearLayout.LayoutParams(-1,0,1f))
        val intro=card();split.addView(intro,LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(18)})
        intro.addView(text("오늘은 어떤 마음인가요?",28,ink,true))
        intro.addView(text("기분과 시간을 고르면, 비밀 코스가 준비됩니다. 도착의 순간에 다음 장면을 만나 보세요.",20,muted))
        intro.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        intro.addView(text("정차 중 전체 흐름을 체험하는 DEMO입니다. 장소·이동 시간은 가상 샘플이며 실제 경로·주차·영업 정보는 없습니다.",17,muted))
        intro.addView(button("드라이브 시작",true){controller.openSetup()})
        intro.addView(button("실제 장소 · 경로 검증",false){
            startActivity(com.surpride.app.realdata.RealRouteActivity.intent(this,controller.state.preferences,controller.state.history.map {it.candidateId}.toSet()))
        })
        if(controller.state.plan!=null && controller.state.phase!=TripPhase.COMPLETED) intro.addView(button("이전 DEMO 이어서",false){controller.showTrip()})
    }
    private fun trip() {
        when(controller.state.phase) {
            TripPhase.SETUP -> setup()
            TripPhase.PLANNING -> {
                title("비밀 코스를 준비해요","선택 조건과 가용 시간을 확인하고 있습니다.")
                content.addView(card().apply {addView(text("PLANNING  /  DEMO",30,accent,true));addView(text("실제 지도 검색이나 경로 탐색은 수행하지 않습니다.",20,muted))})
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
        if(s.error!=null) form.addView(text(s.error,18,accent))
        when(s.setupStep) {
            0 -> {
                form.addView(text("오늘의 기분",23,ink,true))
                choices(form,Mood.entries,3,{it.label},{it==p.mood}){v -> prefs(p.copy(mood=v))}
                form.addView(text("만나고 싶은 장소",23,ink,true).apply {setPadding(0,dp(20),0,0)})
                form.addView(text("함께 선택한 조건을 모두 만족하는 샘플만 찾습니다.",16,muted))
                choices(form,PlaceCategory.entries,2,{it.label},{it in p.categories}) {v -> prefs(p.copy(categories=if(v in p.categories) p.categories-v else p.categories+v))}
                form.addView(button("특별한 조건 없음",p.categories.isEmpty()){prefs(p.copy(categories=emptySet()))})
            }
            1 -> {
                form.addView(text("얼마나 멀리 떠나볼까요?",23,ink,true))
                choices(form,TravelScope.entries,2,{"${it.label}\n${it.note}"},{it==p.scope}) {v -> prefs(p.copy(scope=v,availableMinutes=v.defaultMinutes,returnByEpochMillis=null))}
                form.addView(text("시간 기준",23,ink,true).apply {setPadding(0,dp(18),0,0)})
                choices(form,listOf(false,true),2,{if(it) "귀가 희망 시각" else "사용 가능 시간"},{it==(p.returnByEpochMillis!=null)}) {v -> prefs(if(v) p.copy(availableMinutes=null,returnByEpochMillis=System.currentTimeMillis()+p.scope.defaultMinutes*60_000L) else p.copy(availableMinutes=p.scope.defaultMinutes,returnByEpochMillis=null))}
                val slots=listOf(30,60,90,180,2880)
                choices(form,slots,3,{minutes -> if(p.returnByEpochMillis!=null) deadlineLabel(System.currentTimeMillis()+minutes*60_000L) else duration(minutes)}, {minutes -> if(p.returnByEpochMillis!=null) false else p.availableMinutes==minutes}) {v -> prefs(if(p.returnByEpochMillis!=null) p.copy(returnByEpochMillis=System.currentTimeMillis()+v*60_000L) else p.copy(availableMinutes=v))}
                form.addView(button(if(p.returnToStart) "✓  출발지로 돌아오는 코스" else "도착지에서 마무리하는 코스",p.returnToStart){prefs(p.copy(returnToStart=!p.returnToStart))})
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
        val summary=card();split.addView(summary,LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(18)})
        summary.addView(text("YOUR DRIVE",14,accent,true));summary.addView(text(p.mood.label,32,ink,true))
        summary.addView(text("${p.scope.label} · ${p.scope.note}\n\n${p.categories.joinToString(" · "){it.label}.ifEmpty {"특별한 장소 조건 없음"}}\n\n${p.companion.label}\n${timeSummary(p)}\n${if(p.returnToStart) "출발지 복귀" else "도착지 마무리"}\n랜덤 ${p.randomness}%",19,muted))
        summary.addView(Space(this),LinearLayout.LayoutParams(1,0,1f));summary.addView(text("DEMO\n모든 장소와 시간은 가상 예시",16,accent))
    }
    private fun ready() {
        val view=controller.presentation()
        title("비밀 코스가 준비됐어요","이름은 아직 봉투 안에. 지금은 기대만 챙겨요.")
        val split=row();content.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        val visual=card();split.addView(visual,LinearLayout.LayoutParams(0,-1,1f))
        visual.addView(text("SECRET COURSE  /  DEMO",15,accent,true));visual.addView(ExperienceArt(this,controller.state.preferences.mood),LinearLayout.LayoutParams(-1,0,1f))
        val detail=card();split.addView(detail,LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(18)})
        detail.addView(text("${view.totalMinutes}분의 작은 전환",30,ink,true))
        detail.addView(text(view.reason,21,muted));detail.addView(text("준비물 힌트\n${view.preparation}",20,ink))
        detail.addView(text("합성 이동·체류·복귀 예시 / 실제 경로 미계산\n차량 주행 가능 거리·환경 조건 검증 전",16,muted))
        detail.addView(Space(this),LinearLayout.LayoutParams(1,0,1f))
        detail.addView(button("DEMO 드라이브 시작",true){controller.startDemo()})
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
            addView(text("ARRIVED  /  DEMO",17,accent,true));addView(text("봉투를 열어볼까요?",38,ink,true))
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
            val score=s.plan!!.score
            addView(text("추천 점수 ${score.total}  ·  기분 ${score.mood} / 장소 ${score.place} / 동행 ${score.companion}\n날씨·계절·실제 주행 거리 미검증",17,muted))
            if(s.phase==TripPhase.REVEALED) {
                addView(text("이 도착 경험은 어땠나요?",23,ink,true))
                choices(this,(1..5).toList(),5,{"$it 점"},{it==s.rating}){v -> controller.rate(v)}
                addView(button(if(s.rating==null) "평가를 선택해 주세요" else "평가와 DEMO 기록 저장",true){controller.complete()}.apply {isEnabled=s.rating!=null;alpha=if(isEnabled) 1f else .5f})
            } else {
                addView(text("${s.rating} / 5  ·  ${if(s.arrived) "모의 도착 후 기록" else "미리 공개 후 기록"}",24,ink,true))
                addView(button("기분 지도에서 보기",true){controller.openMap()})
                addView(button("새 DEMO 만들기",false){controller.openSetup()})
            }
        })},LinearLayout.LayoutParams(-1,0,1f))
    }
    private fun moodMap() {
        title("내 마음이 머문 장면들","기분별 DEMO 기록 개념도 · 실제 지리 지도/방문 인증 아님")
        val split=row();content.addView(split,LinearLayout.LayoutParams(-1,0,1f))
        val map=card();split.addView(map,LinearLayout.LayoutParams(0,-1,1f))
        map.addView(text("MOOD ATLAS",15,accent,true));map.addView(ExperienceArt(this,controller.state.preferences.mood,controller.state.history),LinearLayout.LayoutParams(-1,0,1f))
        val list=column();split.addView(ScrollView(this).apply {addView(list)},LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(18)})
        if(controller.state.history.isEmpty()) list.addView(card().apply {addView(text("첫 여정을 기다리고 있어요",27,ink,true));addView(text("DEMO를 공개하고 평가하면 기분별 기록이 여기에 모입니다.",19,muted));addView(button("첫 DEMO 만들기",true){controller.openSetup()})})
        controller.state.history.asReversed().forEach {visit -> list.addView(card().apply {
            addView(text("${visit.mood.label}  ·  ${visit.rating}/5",22,accent,true))
            addView(text(DemoCatalog.candidates.firstOrNull {it.id==visit.candidateId}?.name ?: "샘플 정보 없음",22,ink,true))
            addView(text("${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(visit.completedAtMillis))}\nDEMO · ${if(visit.simulatedArrival) "모의 도착" else "미리 공개"}",17,muted))
        })}
    }
    private fun prefs(p: DrivePreferences) = controller.updatePreferences(p)
    private fun title(value: String, subtitle: String) {
        content.addView(text(value,36,ink,true).apply {setPadding(0,dp(18),0,dp(8))})
        content.addView(text(subtitle,18,muted).apply {setPadding(0,0,0,dp(24))})
    }
    private fun column()=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(this).apply {orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun card()=column().apply {setPadding(dp(24),dp(24),dp(24),dp(24));background=shape(panel,22);layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(12);bottomMargin=dp(12)}}
    private fun text(value: String,size: Int,color: Int,bold: Boolean=false)=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(color);setLineSpacing(dp(5).toFloat(),1f)
        typeface=Typeface.create(if(bold) "sans-serif-medium" else "sans-serif",if(bold) Typeface.BOLD else Typeface.NORMAL)
        setPadding(0,dp(4),0,dp(8))
    }
    private fun button(label: String,primary: Boolean=false,action: () -> Unit) = buttonRaw(label,primary) {act(action)}
    private fun buttonRaw(label: String,primary: Boolean,action: () -> Unit)=TextView(this).apply {
        text=label;textSize=22f;typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        gravity=Gravity.CENTER;minHeight=dp(76);minWidth=dp(76);setPadding(dp(14),dp(12),dp(14),dp(12))
        setTextColor(if(primary) bg else ink);background=shape(if(primary) accent else panel,13)
        isClickable=true;isFocusable=true;contentDescription=label
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(4)}
        setOnClickListener {action()}
    }
    private fun <T> choices(parent: LinearLayout,options: List<T>,columns: Int,label: (T)->String,selected: (T)->Boolean,choose: (T)->Unit) {
        options.chunked(columns).forEach {chunk ->
            val line=row();parent.addView(line)
            chunk.forEach {value -> line.addView(button(label(value),selected(value)){choose(value)},weighted())}
            repeat(columns-chunk.size){line.addView(Space(this),weighted())}
        }
    }
    private fun weighted()=LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(3),dp(4),dp(3),dp(4))}
    private fun shape(color: Int,radius: Int)=GradientDrawable().apply {setColor(color);cornerRadius=dp(radius).toFloat();setStroke(dp(1),Color.rgb(45,65,56))}
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    private fun duration(minutes: Int)=if(minutes>=1440) "${minutes/1440}일" else if(minutes>=60) "${minutes/60}시간${if(minutes%60>0) " ${minutes%60}분" else ""}" else "$minutes 분"
    private fun deadlineLabel(at: Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(at))+" 귀가"
    private fun timeSummary(p: DrivePreferences)=p.returnByEpochMillis?.let(::deadlineLabel) ?: "사용 가능 ${duration(p.availableMinutes ?: 0)}"
}
