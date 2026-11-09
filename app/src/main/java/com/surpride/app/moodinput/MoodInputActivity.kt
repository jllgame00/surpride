package com.surpride.app.moodinput

import android.content.Intent
import android.graphics.Color
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
    private lateinit var applyButton:Button
    private val actions=mutableListOf<Button>()
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private var foreground=false;private var requiresDo:Boolean?=null
    private val allowed get()=foreground && ParkedInteractionPolicy.allows(requiresDo)
    private var voice:SpeechInputController?=null
    private var requests:InterpretationRequests?=null
    private var message="선택 입력도 계속 사용할 수 있습니다. 해석 후 조건을 직접 확인해 주세요."
    private val ink=Color.rgb(235,241,225);private val accent=Color.rgb(215,242,139);private val muted=Color.rgb(154,177,165)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        draft=ViewModelProvider(this)[Draft::class.java]
        if(draft.initial==null) {
            val initial=PreferenceInputContract.decode(intent.getByteArrayExtra(PreferenceInputContract.EXTRA))
            if(initial==null) {setResult(RESULT_CANCELED);finish();return}
            draft.initial=initial;draft.preferences=initial
        }
        ux=ParkedUxMonitor(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(10,24,24))}
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) {v,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left+dp(24),bars.top+dp(12),bars.right+dp(24),bars.bottom+dp(12));insets
        }
        root.addView(label("SURPRIDE / 말로 정리하는 드라이브",28,accent))
        root.addView(label("RULE_BASED · 기기 내 규칙 해석 / 외부 AI 모델 연결 없음",17,muted))
        val columns=LinearLayout(this)
        root.addView(columns,LinearLayout.LayoutParams(-1,0,1f))
        val left=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(0,0,dp(16),0)}
        val right=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,0,0)}
        columns.addView(ScrollView(this).apply {isFillViewport=true;addView(left)},LinearLayout.LayoutParams(0,-1,1.15f))
        columns.addView(ScrollView(this).apply {isFillViewport=true;addView(right)},LinearLayout.LayoutParams(0,-1,1f))
        left.addView(label("오늘의 기분과 시간을 말해 주세요",23))
        left.addView(label("원문·음성을 저장하지 않고 외부 AI에 보내지 않습니다. 감정 해석은 취향 분류이며 건강 진단이 아닙니다.",17,muted))
        input=EditText(this).apply {
            setTextColor(ink);setHintTextColor(muted);textSize=22f
            hint="예: 답답해서 강을 보고 싶어. 한 시간 반, 집으로 돌아와야 해."
            minLines=3;maxLines=4;gravity=Gravity.TOP
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters=arrayOf(InputFilter.LengthFilter(1000));isSaveEnabled=false;setText(draft.input)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){
                    draft.input=s.toString();draft.dirty=true
                    if(requests?.state?.phase==InterpretationPhase.WORKING) requests?.cancel()
                    if(this@MoodInputActivity::applyButton.isInitialized) {message="문장이 바뀌었어요. 다시 해석하거나 현재 선택을 확인해 주세요.";update()}
                }
                override fun afterTextChanged(s:Editable?){}
            })
        };left.addView(input,LinearLayout.LayoutParams(-1,dp(150)))
        left.addView(button("문장 해석 · 아직 조건 적용 안 함") {analyze()})
        left.addView(button("음성 입력 · Pleos ON_DEVICE") {startVoice()})
        left.addView(button("음성 / 해석 취소") {voice?.cancel();requests?.cancel();message="입력을 취소했어요. 현재 선택 조건은 유지됩니다.";update()})
        status=label(message,18,accent);left.addView(status)
        extraction=label("이번 문장: UNKNOWN / 아직 해석하지 않음",18,muted);left.addView(extraction)
        left.addView(button("텍스트 예시 넣기 · 음성 인식 아님") {
            input.setText("오늘 좀 답답해서 바람 쐬고 싶어. 바다나 강이 보이는 곳으로 한 시간 반만 쓰고 싶어. 집으로 돌아와야 해.")
        })
        left.addView(button("수정 예시 넣기 · 두 시간, 혼자") {input.setText("시간은 두 시간으로 바꿔줘. 혼자 갈 거야.")})
        left.addView(button("현재 선택 유지 · 해석 문제 해제") {
            requests?.cancel();draft.interpretation=null;draft.dirty=false;message="아래에 표시된 현재 선택을 유지합니다. 미지원·불확실한 요청은 반영하지 않습니다. 처음 선택으로 되돌리려면 복원을 눌러 주세요.";update()
        })
        right.addView(label("현재 조건 · 확인 후에만 적용",23,accent))
        summary=label("",21);right.addView(summary)
        hints=label("",18,muted);right.addView(hints)
        right.addView(label("기분 · 기존 6가지 분류",18,accent))
        rows(right,Mood.entries.toList(),3,{it.label}) {draft.preferences=draft.preferences.copy(mood=it);update()}
        rows(right,listOf(30,60,90,120,180),3,{"${it}분"}) {
            draft.preferences=draft.preferences.copy(availableMinutes=it);update()
        }
        right.addView(button("귀가 희망 시각 해제 · 명시적 선택") {draft.preferences=draft.preferences.copy(returnByEpochMillis=null);update()})
        right.addView(button("현재 시간에 맞는 이동 범위 제안 적용") {
            val suggested=PreferencePatch(availableMinutes=draft.preferences.availableMinutes).suggestedScope()
            if(suggested!=null) {draft.preferences=draft.preferences.copy(scope=suggested);message="시간에 따른 범위 제안입니다. 실제 이동 거리를 뜻하지 않습니다."};update()
        })
        rows(right,DriveCompanion.entries.toList(),2,{it.label}) {draft.preferences=draft.preferences.copy(companion=it);update()}
        right.addView(button("왕복 / 편도 전환") {draft.preferences=draft.preferences.copy(returnToStart=!draft.preferences.returnToStart);update()})
        right.addView(button("랜덤 정도 전환 · 0 / 40 / 100") {
            val values=listOf(0,40,100);draft.preferences=draft.preferences.copy(randomness=values[(values.indexOf(draft.preferences.randomness)+1)%3]);update()
        })
        right.addView(label("장소 조건: 누를 때마다 선호 → 제외 → 미지정",17,muted))
        rows(right,PlaceCategory.entries.toList(),2,{it.label}) {category->
            val p=draft.preferences
            draft.preferences=when(category) {
                in p.categories->p.copy(categories=p.categories-category,excludedCategories=p.excludedCategories+category)
                in p.excludedCategories->p.copy(excludedCategories=p.excludedCategories-category)
                else->p.copy(categories=p.categories+category)
            };update()
        }
        applyButton=button("확인한 조건 적용 · 기존 선택 화면으로") {
            if(validForApply()) {
                setResult(RESULT_OK,Intent().putExtra(PreferenceInputContract.EXTRA,PreferenceInputContract.encode(draft.preferences)))
                draft.input="";finish()
            }
        };right.addView(applyButton)
        right.addView(button("기존 선택으로 복원") {draft.preferences=draft.initial!!;draft.interpretation=null;draft.dirty=false;message="처음 선택한 조건을 복원했습니다.";update()})
        right.addView(button("적용하지 않고 닫기") {setResult(RESULT_CANCELED);finish()})
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
                    InterpretationPhase.WORKING->message="기기 내 조건 해석 중…"
                    InterpretationPhase.READY->{
                        val result=state.result!!;draft.interpretation=result
                        if(result.canApply) {draft.preferences=result.patch.applyTo(draft.preferences);message="해석한 조건을 아래에 제안했어요. 적용 전에 확인해 주세요."}
                        else message="모호하거나 미지원인 조건이 있어 반영하지 않았어요. 문장을 수정하거나 해석 제안을 버리고 선택해 주세요."
                    }
                    InterpretationPhase.FAILED->{draft.dirty=true;message="해석 실패: ${state.error} · 선택 입력을 계속 사용할 수 있어요."}
                    else->Unit
                }
            }
            Log.i("SurprideMood","interpretation=${state.phase} source=RULE_BASED error=${state.error}");update()
        }
        voice=SpeechInputController({PleosSpeechInputSource(applicationContext)}) {state->
            if(allowed) {
                message=when(state.phase) {
                    SpeechInputPhase.INITIALIZING->"Pleos 기기 내 음성 엔진 연결 중…"
                    SpeechInputPhase.LISTENING->"듣고 있어요 · 길게 대화하지 않아도 괜찮아요."
                    SpeechInputPhase.PROCESSING->"음성 인식 결과를 기다리고 있어요."
                    SpeechInputPhase.FINAL->{input.setText(state.finalText);"인식 결과를 확인하고 ‘문장 해석’을 눌러 주세요."}
                    SpeechInputPhase.NO_SPEECH->"음성을 확인하지 못했어요. 텍스트 또는 선택 입력을 사용해 주세요."
                    SpeechInputPhase.ERROR->"음성 입력 오류: ${state.error} · 텍스트/선택 입력은 사용할 수 있어요."
                    SpeechInputPhase.CANCELLED->"음성 입력을 취소했어요."
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
    private fun validForApply()=allowed && !draft.dirty && draft.interpretation?.canApply!=false && requests?.state?.phase!=InterpretationPhase.WORKING &&
        voice?.state?.phase !in setOf(SpeechInputPhase.INITIALIZING,SpeechInputPhase.LISTENING,SpeechInputPhase.PROCESSING) &&
        budgetMinutes(draft.preferences,System.currentTimeMillis())?.let {it>0}==true
    private fun update() {
        if(!::status.isInitialized) return
        actions.forEach {it.isEnabled=allowed};input.isEnabled=allowed
        status.text=if(allowed) message else "정차 상태를 확인할 때까지 입력이 제한됩니다."
        val result=draft.interpretation;val p=draft.preferences
        extraction.text=if(result==null) "이번 문장: UNKNOWN · 현재 선택과 구분합니다." else buildString {
            val patch=result.patch
            append("이번 문장 · RULE_BASED\n기분: ${patch.mood?.label?:"UNKNOWN"} / 동행: ${patch.companion?.label?:"UNKNOWN"}\n")
            append("가용: ${patch.availableMinutes?.let {"${it}분"}?:"UNKNOWN"} / 왕복: ${patch.returnToStart?:"UNKNOWN"}\n")
            append("범위: ${patch.scope?.label?:"AUTO / 미지정"} / 시간 기준 제안: ${patch.suggestedScope()?.label?:"UNKNOWN"}\n")
            if(result.issues.isNotEmpty()) append("확인 필요: ${result.issues.joinToString()}")
        }
        summary.text=buildString {
            append("${p.mood.label} · ${p.companion.label} · ${p.scope.label}\n")
            append("가용 ${p.availableMinutes?.let {"${it}분"}?:"UNKNOWN"} · ${if(p.returnToStart) "왕복" else "편도"} · 랜덤 ${p.randomness}\n")
            p.returnByEpochMillis?.let {append("귀가 희망: ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(it))}\n")}
            append("선호: ${p.categories.joinToString {it.label}.ifEmpty {"특별한 조건 없음"}}\n")
            append("제외: ${p.excludedCategories.joinToString {it.label}.ifEmpty {"없음"}}\n")
            append("이번 발화의 UNKNOWN은 추정하지 않았어요. 현재 선택을 직접 확인해 주세요.")
        }
        hints.text="준비 힌트\n"+SafePreparationPolicy.hints(p).joinToString("\n"){it.label}+"\n날씨·주차·차량 진입·주행 가능 거리는 미확인입니다."
        applyButton.isEnabled=validForApply()
    }
    private fun hideKeyboard() {input.clearFocus();getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(input.windowToken,0)}
    private fun label(value:String,size:Int,color:Int=ink)=TextView(this).apply {text=value;textSize=size.toFloat();setTextColor(color);setPadding(0,dp(9),0,dp(9))}
    private fun button(value:String,action:()->Unit)=Button(this).apply {
        text=value;textSize=18f;isAllCaps=false;minHeight=dp(70);setOnClickListener {if(allowed) action()};actions+=this
    }
    private fun <T> rows(parent:LinearLayout,values:List<T>,count:Int,title:(T)->String,selected:(T)->Unit) {
        values.chunked(count).forEach {chunk->val row=LinearLayout(this);parent.addView(row)
            chunk.forEach {value->row.addView(button(title(value)){selected(value)},LinearLayout.LayoutParams(0,-2,1f))}
        }
    }
    private fun dp(value:Int)=(resources.displayMetrics.density*value).toInt()
}
