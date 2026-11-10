package com.surpride.app.moodinput

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.*
import android.text.*
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.surpride.app.experience.ParkedUxMonitor
import com.surpride.app.experience.core.*
import com.surpride.app.moodinput.core.*
import com.surpride.app.moodinput.voice.*
import com.surpride.app.ui.SurprideUi
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors
import com.surpride.app.experience.core.Companion as DriveCompanion

/** Parked, local condition editor. Never searches places, starts routes, or sends conversations. */
class MoodInputActivity:AppCompatActivity() {
    class Draft:ViewModel() {
        var initial:DrivePreferences?=null;var preferences=DrivePreferences()
        var input="";var dirty=false;var interpretation:MoodInterpretation?=null
        override fun onCleared() {input="";interpretation=null}
    }
    private lateinit var draft:Draft
    private lateinit var ux:ParkedUxMonitor
    private lateinit var input:EditText
    private lateinit var status:TextView
    private lateinit var extraction:TextView
    private lateinit var summary:TextView
    private lateinit var hints:TextView
    private var debugDetails:TextView?=null
    private lateinit var applyButton:Button
    private val actions=mutableListOf<Button>()
    private data class Choice(val button:Button,val selected:()->Boolean,val label:()->String)
    private val choices=mutableListOf<Choice>()
    private val ui by lazy {SurprideUi(this)}
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var foreground=false;private var requiresDo:Boolean?=null
    private val allowed get()=foreground && ParkedInteractionPolicy.allows(requiresDo)
    private var voice:SpeechInputController?=null
    private var requests:InterpretationRequests?=null
    private var message="문장을 해석한 뒤 조건을 확인해 주세요"
    private val ink get()=ui.ink
    private val accent get()=ui.accent
    private val muted get()=ui.muted

    override fun onCreate(savedInstanceState:Bundle?) {
        SurprideUi.applyAppearance(this)
        super.onCreate(savedInstanceState)
        draft=ViewModelProvider(this)[Draft::class.java]
        if(draft.initial==null) {
            val initial=PreferenceInputContract.decode(intent.getByteArrayExtra(PreferenceInputContract.EXTRA))
            if(initial==null) {setResult(RESULT_CANCELED);finish();return}
            draft.initial=initial;draft.preferences=initial
        }
        ux=ParkedUxMonitor(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(ui.background)}
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) {v,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left+dp(32),bars.top+dp(20),bars.right+dp(32),bars.bottom+dp(20));insets
        }
        root.addView(ui.text("말로 정리하는 드라이브",32,ink,true))
        root.addView(label("SURPRIDE · 정차 중 입력 · 기기 안에서 규칙으로 조건을 정리해요",20,muted))
        val columns=LinearLayout(this)
        root.addView(columns,LinearLayout.LayoutParams(-1,0,1f).apply {topMargin=dp(12);bottomMargin=dp(16)})
        val left=ui.card()
        val right=ui.card()
        columns.addView(ScrollView(this).apply {isFillViewport=true;addView(left)},LinearLayout.LayoutParams(0,-1,1.1f))
        columns.addView(ScrollView(this).apply {isFillViewport=true;addView(right)},LinearLayout.LayoutParams(0,-1,1f).apply {leftMargin=dp(24)})
        left.addView(ui.text("1  마음과 시간 이야기",28,ink,true))
        input=EditText(this).apply {
            setTextColor(ink);setHintTextColor(muted);textSize=24f
            hint="예: 강을 보며 쉬고 싶어. 한 시간 반, 집으로 돌아와야 해"
            contentDescription="기분과 드라이브 조건 입력"
            minLines=3;maxLines=5;gravity=Gravity.TOP
            setPadding(dp(16),dp(16),dp(16),dp(16))
            backgroundTintList=null;background=ui.shape(ui.raised,12)
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions=imeOptions or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
            filters=arrayOf(InputFilter.LengthFilter(1000));isSaveEnabled=false;setText(draft.input)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                    draft.input=s.toString();draft.dirty=true
                    if(requests?.state?.phase==InterpretationPhase.WORKING) requests?.cancel()
                    if(this@MoodInputActivity::applyButton.isInitialized) {message="문장이 바뀌었어요. 다시 해석하거나 조건을 직접 수정해 주세요";update()}
                }
                override fun afterTextChanged(s:Editable?){}
            })
        };left.addView(input,LinearLayout.LayoutParams(-1,dp(180)).apply {topMargin=dp(12);bottomMargin=dp(12)})
        left.addView(button("문장 해석") {analyze()})
        val voiceActions=LinearLayout(this)
        voiceActions.addView(button("음성 입력") {startVoice()},LinearLayout.LayoutParams(0,-2,1f).apply {rightMargin=dp(8)})
        voiceActions.addView(button("입력 취소") {voice?.cancel();requests?.cancel();message="입력을 취소했어요. 현재 조건은 유지됩니다";update()},LinearLayout.LayoutParams(0,-2,1f))
        left.addView(voiceActions)
        left.addView(label("음성이 인식되지 않으면 직접 입력하거나 조건을 선택해 주세요",20,muted))
        status=label(message,20,accent).apply {accessibilityLiveRegion=android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE};left.addView(status)
        extraction=label("",22,ink);left.addView(extraction)
        left.addView(label("문장·음성은 저장하거나 외부 AI에 보내지 않아요.\n기분 분류는 건강 진단이 아닙니다",20,muted))

        right.addView(ui.text("2  추천 조건 확인",28,ink,true))
        summary=label("",24);right.addView(summary)
        right.addView(label("출발 전 확인",24,ink))
        hints=label("",20,muted);right.addView(hints)
        val corrections=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE}
        val correctionToggle=button("조건 직접 수정"){}
        correctionToggle.setOnClickListener {
            if(allowed) {
                val open=corrections.visibility!=android.view.View.VISIBLE
                corrections.visibility=if(open) android.view.View.VISIBLE else android.view.View.GONE
                correctionToggle.text=if(open) "조건 수정 접기" else "조건 직접 수정"
                correctionToggle.isSelected=open
            }
        }
        right.addView(correctionToggle);right.addView(corrections)
        corrections.addView(label("기분",24,ink))
        rows(corrections,Mood.entries.toList(),3,{it.label},{draft.preferences.mood==it}) {value->choose {it.copy(mood=value)}}
        corrections.addView(label("사용 시간",24,ink))
        rows(corrections,listOf(30,60,90,120,180),3,{"${it}분"},{draft.preferences.availableMinutes==it}) {value->choose {it.copy(availableMinutes=value)}}
        corrections.addView(button("귀가 희망 시각 해제") {choose {it.copy(returnByEpochMillis=null)}})
        corrections.addView(button("사용 시간에 맞는 이동 범위로 수정") {
            val suggested=PreferencePatch(availableMinutes=draft.preferences.availableMinutes).suggestedScope()
            if(suggested!=null) choose {it.copy(scope=suggested)}
        })
        corrections.addView(label("동행",24,ink))
        rows(corrections,DriveCompanion.entries.toList(),2,{it.label},{draft.preferences.companion==it}) {value->choose {it.copy(companion=value)}}
        val roundTrip=button("") {choose {it.copy(returnToStart=!it.returnToStart)}}
        corrections.addView(roundTrip);choices+=Choice(roundTrip,{draft.preferences.returnToStart},{if(draft.preferences.returnToStart) "출발지로 돌아오기 · 왕복" else "돌아오지 않기 · 편도"})
        corrections.addView(button("우연 정도 변경 · 취향 / 균형 / 발견") {
            val values=listOf(0,40,100);choose {it.copy(randomness=values[(values.indexOf(it.randomness)+1)%3])}
        })
        corrections.addView(label("장소 · 누를 때마다 선호 → 제외 → 미지정",20,muted))
        rows(corrections,PlaceCategory.entries.toList(),2,{MoodInputPresentation.categoryLabel(it,draft.preferences)},
            {it in draft.preferences.categories || it in draft.preferences.excludedCategories}) {category->
            val p=draft.preferences
            choose {when(category) {
                in p.categories->p.copy(categories=p.categories-category,excludedCategories=p.excludedCategories+category)
                in p.excludedCategories->p.copy(excludedCategories=p.excludedCategories-category)
                else->p.copy(categories=p.categories+category)
            }}
        }
        corrections.addView(button("처음 조건으로 되돌리기") {choose {draft.initial!!};message="처음 선택한 조건으로 되돌렸어요";update()})

        if(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE!=0) {
            val details=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;visibility=android.view.View.GONE}
            val toggle=button("개발용 입력 예시·진단"){}
            toggle.setOnClickListener {if(allowed) {details.visibility=if(details.visibility==android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE}}
            left.addView(toggle);left.addView(details)
            details.addView(button("텍스트 예시 넣기 · 음성 인식 아님") {
                input.setText("오늘 좀 답답해서 바람 쐬고 싶어. 바다나 강이 보이는 곳으로 한 시간 반만 쓰고 싶어. 집으로 돌아와야 해.")
            })
            details.addView(button("수정 예시 · 두 시간, 혼자") {input.setText("시간은 두 시간으로 바꿔줘. 혼자 갈 거야.")})
            debugDetails=label("",18,muted);details.addView(debugDetails)
        }
        val footer=LinearLayout(this)
        root.addView(footer,LinearLayout.LayoutParams(-1,-2))
        applyButton=button("이 조건으로 계속",true) {
            if(validForApply()) {
                setResult(RESULT_OK,Intent().putExtra(PreferenceInputContract.EXTRA,PreferenceInputContract.encode(draft.preferences)))
                draft.input="";finish()
            }
        };footer.addView(applyButton,LinearLayout.LayoutParams(0,-2,1.5f).apply {rightMargin=dp(16)})
        footer.addView(button("적용하지 않고 닫기") {setResult(RESULT_CANCELED);finish()},LinearLayout.LayoutParams(0,-2,1f))
        update()
    }
    override fun onStart() {
        super.onStart();if(!::ux.isInitialized) return
        foreground=true
        requests=InterpretationRequests(ConditionInterpreter {text,now,done->
            val task=worker.submit {
                val reply=try {InterpretationReply.Success(RuleBasedMoodInterpreter().extract(text,now))}
                    catch(_:Exception) {InterpretationReply.Failure(InterpretationFailure.PROVIDER_ERROR)}
                handler.post {done(reply)}
            };{task.cancel(true);Unit}
        }) {state->
            if(allowed) {
                when(state.phase) {
                    InterpretationPhase.WORKING->message="기기 안에서 조건을 정리하고 있어요"
                    InterpretationPhase.READY->{
                        val result=state.result!!;draft.interpretation=result
                        if(result.canApply) {draft.preferences=result.patch.applyTo(draft.preferences);message="정리한 조건을 확인하고 아래에서 계속해 주세요"}
                        else message="문장을 반영하지 않았어요. 표현을 바꾸거나 조건을 직접 수정해 주세요"
                    }
                    InterpretationPhase.FAILED->{draft.dirty=true;message=if(state.error==InterpretationFailure.TIMEOUT) "조건 정리가 늦어지고 있어요. 다시 시도하거나 직접 선택해 주세요" else "조건을 정리하지 못했어요. 직접 선택할 수 있어요"}
                    else->Unit
                }
            }
            Log.i("SurprideMood","interpretation=${state.phase} source=RULE_BASED error=${state.error}");update()
        }
        voice=SpeechInputController({PleosSpeechInputSource(applicationContext)}) {state->
            if(allowed) {
                message=when(state.phase) {
                    SpeechInputPhase.INITIALIZING->"음성 입력을 준비하고 있어요"
                    SpeechInputPhase.LISTENING->"듣고 있어요. 기분과 시간을 짧게 말해 주세요"
                    SpeechInputPhase.PROCESSING->"인식한 문장을 기다리고 있어요"
                    SpeechInputPhase.FINAL->{input.setText(state.finalText);"인식한 문장을 확인하고 ‘문장 해석’을 눌러 주세요"}
                    SpeechInputPhase.NO_SPEECH->"음성을 확인하지 못했어요. 직접 입력하거나 조건을 선택해 주세요"
                    SpeechInputPhase.ERROR->"음성 입력을 사용할 수 없어요. 직접 입력하거나 조건을 선택해 주세요"
                    SpeechInputPhase.CANCELLED->"음성 입력을 취소했어요"
                    else->message
                }
            }
            Log.i("SurprideMood","speech=${state.phase} source=${state.origin} error=${state.error}");update()
        }
        ux.start {value->requiresDo=value;if(!allowed) {voice?.cancel();requests?.cancel()};update()}
    }
    override fun onStop() {
        if(requests?.state?.phase==InterpretationPhase.WORKING) draft.dirty=true
        foreground=false;requiresDo=null;voice?.close();voice=null;requests?.close();requests=null
        handler.removeCallbacksAndMessages(null);if(::ux.isInitialized) ux.stop();super.onStop()
    }
    override fun onDestroy() {if(::ux.isInitialized) ux.stop();worker.shutdownNow();super.onDestroy()}
    private fun analyze() {
        if(!allowed) return
        voice?.cancel();hideKeyboard();draft.dirty=false;draft.interpretation=null
        val id=requests?.start(draft.input,System.currentTimeMillis())?:return
        handler.postDelayed({requests?.expire(id)},5000)
    }
    private fun startVoice() {
        if(!allowed) return
        requests?.cancel();hideKeyboard()
        val id=voice?.start()?:return
        handler.postDelayed({voice?.expire(id)},15_000)
    }
    private fun validForApply()=MoodInputPresentation.readyToApply(allowed,draft.dirty,draft.interpretation,
        requests?.state?.phase==InterpretationPhase.WORKING,
        voice?.state?.phase in setOf(SpeechInputPhase.INITIALIZING,SpeechInputPhase.LISTENING,SpeechInputPhase.PROCESSING),
        budgetMinutes(draft.preferences,System.currentTimeMillis()))

    /** An explicit selection replaces only the unreviewed text proposal, never other saved fields. */
    private fun choose(change:(DrivePreferences)->DrivePreferences) {
        if(!allowed) return
        requests?.cancel();voice?.cancel()
        draft.preferences=change(draft.preferences);draft.interpretation=null;draft.dirty=false
        message="문장 제안 대신 직접 고른 조건을 적용해요. 내용을 확인해 주세요"
        update()
    }
    private fun update() {
        if(!::status.isInitialized) return
        actions.forEach {it.isEnabled=allowed};input.isEnabled=allowed
        status.text=if(allowed) message else "정차 상태를 확인할 때까지 입력이 제한됩니다"
        val result=draft.interpretation;val p=draft.preferences
        extraction.text=MoodInputPresentation.extraction(result)
        extraction.setTextColor(if(result?.canApply==false) ui.error else ink)
        summary.text=MoodInputPresentation.summary(p)+p.returnByEpochMillis?.let {
            "\n귀가 희망 ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it))}"
        }.orEmpty()
        hints.text=SafePreparationPolicy.hints(p).joinToString("\n"){it.label.removeSuffix(".")}+
            "\n날씨·주차·차량 진입·주행 가능 거리는 미확인입니다"
        choices.forEach {choice->
            val selected=choice.selected();choice.button.text=choice.label()
            choice.button.isSelected=selected
            ViewCompat.setStateDescription(choice.button,if(selected) "선택됨" else "선택 안 됨")
        }
        debugDetails?.text="RULE_BASED · ON_DEVICE\n해석 ${requests?.state?.phase} / 오류 ${requests?.state?.error}\n"+
            "음성 ${voice?.state?.phase} / 오류 ${voice?.state?.error}\n검토 항목 ${result?.issues?.joinToString().orEmpty()}"
        applyButton.isEnabled=validForApply()
        applyButton.text=when {
            !allowed->"정차 후 계속할 수 있어요"
            draft.dirty->"문장을 해석하거나 조건을 직접 수정해 주세요"
            result?.canApply==false->"확인이 필요한 조건이 있어요"
            else->"이 조건으로 계속"
        }
    }
    private fun hideKeyboard() {input.clearFocus();getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken,0)}
    private fun label(value:String,size:Int,color:Int=ink)=ui.text(value,size,color).apply {setPadding(0,dp(8),0,dp(8))}
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=ui.button(value,primary) {if(allowed) action()}.also {actions+=it}
    private fun <T> rows(parent:LinearLayout,values:List<T>,count:Int,title:(T)->String,isSelected:(T)->Boolean,selected:(T)->Unit) {
        values.chunked(count).forEach {chunk->val row=LinearLayout(this);parent.addView(row)
            chunk.forEach {value->
                val item=button(title(value)){selected(value)}
                choices+=Choice(item,{isSelected(value)},{title(value)})
                row.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(4),dp(4),dp(4),dp(4))})
            }
        }
    }
    private fun dp(value:Int)=ui.dp(value)
}
