package com.surpride.app.realdata

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.surpride.app.experience.ParkedUxMonitor
import com.surpride.app.experience.core.ParkedInteractionPolicy
import com.surpride.app.ui.SurprideUi

/** Exported callback entry: an opaque pending token authorizes review, never automatic rating. */
class LiveNavigationResultActivity:AppCompatActivity() {
    private lateinit var ux:ParkedUxMonitor
    private lateinit var root:LinearLayout
    private var requiresDo:Boolean?=null
    private var rating:Int?=null
    private var saved=false
    private var saveError=false
    private val ui by lazy {SurprideUi(this)}
    private val allowed get()=ParkedInteractionPolicy.allows(requiresDo)
    private val nonce get()=intent.getStringExtra("session_nonce")
    private val candidateId get()=intent.getStringExtra("candidate_id")
    private val source get()=intent.getStringExtra("source")
    private val revealed get()=intent.getBooleanExtra("revealed",false)
    private val stopped get()=intent.getBooleanExtra("stopped",false)

    override fun onCreate(savedInstanceState:Bundle?) {
        SurprideUi.applyAppearance(this)
        super.onCreate(savedInstanceState)
        rating=savedInstanceState?.getInt("rating",0)?.takeIf {it in 1..5}
        saved=completed()
        ux=ParkedUxMonitor(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(ui.background)}
        setContentView(ScrollView(this).apply {isFillViewport=true;setBackgroundColor(ui.background);addView(root)})
        ViewCompat.setOnApplyWindowInsetsListener(root) {view,insets->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left+dp(32),bars.top+dp(20),bars.right+dp(32),bars.bottom+dp(20));insets
        }
        render()
    }
    override fun onStart() {super.onStart();ux.start {requiresDo=it;render()}}
    override fun onStop() {requiresDo=null;ux.stop();super.onStop()}
    override fun onDestroy() {ux.stop();super.onDestroy()}
    override fun onSaveInstanceState(outState:Bundle) {
        outState.putInt("rating",rating?:0);super.onSaveInstanceState(outState)
    }
    private fun completed()=LiveNavigationStore.read(this).hasCompleted(nonce,candidateId,source,revealed,stopped,System.currentTimeMillis()) &&
        SeoulPilotCatalog.places.any {it.id==candidateId}
    private fun valid():Boolean = LiveNavigationResultPolicy.accepts(LiveNavigationStore.read(this).pending,
        nonce,candidateId,source,revealed,stopped,System.currentTimeMillis()) &&
        SeoulPilotCatalog.places.any {it.id==candidateId}
    private fun render() {
        saved=completed()
        root.removeAllViews()
        root.addView(ui.text("S /  오늘의 발견",32,ui.accent,true))
        root.addView(label("실제 경로 · 정차 에뮬레이터 체험 기록",20))
        if(!allowed) {root.addView(label("안전하게 정차한 뒤 결과를 확인해 주세요",28));return}
        val result=ui.card();root.addView(result)
        if(saved) {
            result.addView(ui.text("오늘의 경험을 기록했어요",36,ui.accent,true))
            result.addView(label("기분 지도에서 다시 만나 보세요\n실제 방문 인증과는 다른 에뮬레이터 평가입니다",24))
        } else if(!valid()) {
            result.addView(ui.text("지금은 결과를 확인할 수 없어요",28,ui.error,true))
            result.addView(label("세션이 만료되었거나 이미 저장됐어요\n목적지 공개와 안내 종료가 확인되어야 평가할 수 있어요",24))
        } else {
            val place=SeoulPilotCatalog.places.first {it.id==candidateId}
            result.addView(ui.text(place.name,36,ui.accent,true))
            result.addView(label(if(intent.getBooleanExtra("arrived",false)) "도착을 확인하고 만난 목적지" else "직접 공개한 목적지 · 도착 확인 전",24))
            result.addView(label("${place.type}\n선택한 드라이브 조건으로 준비한 실제 장소입니다",24))
            result.addView(label("차량 진입·주차·운영시간은 확인이 필요해요",20))
            result.addView(label("이번 드라이브 체험은 어땠나요?",28))
            if(saveError) result.addView(ui.text("기록을 저장하지 못했어요 · 다시 시도해 주세요",24,ui.error))
            val choices=LinearLayout(this)
            (1..5).forEach {value->choices.addView(button("$value 점") {
                if(allowed && valid()) {rating=value;render()}
            }.apply {ui.styleButton(this,selected=rating==value)},LinearLayout.LayoutParams(0,-2,1f).apply {setMargins(dp(4),0,dp(4),0)})}
            result.addView(choices)
            result.addView(button(if(rating==null) "평가를 선택해 주세요" else "이 경험 기록하기",true) {
                val selected=rating
                if(allowed && selected!=null && valid()) {
                    saved=LiveNavigationStore.complete(this,nonce,candidateId,source,revealed,stopped,selected,System.currentTimeMillis(),intent.getBooleanExtra("arrived",false))
                    saveError=!saved
                    render()
                }
            }.apply {isEnabled=rating!=null})
            result.addView(label("별도 에뮬레이터 기록으로 보관해요\n실도로 방문 인증이 아니며 경로와 위치는 저장하지 않아요",20))
        }
        root.addView(button("출발지로 돌아가기 준비") {if(allowed && (valid() || completed())) {
            try {startActivity(android.content.Intent("com.surpride.navhostpoc.RESUME_SESSION")
                .setComponent(android.content.ComponentName("com.surpride.navhostpoc","androidx.car.app.activity.CarAppActivity"))
                .putExtra("session_nonce",nonce).putExtra("candidate_id",candidateId))}
            catch(_:Exception) {root.addView(ui.text("지도 안내 앱을 찾을 수 없어요",24,ui.error))}
        }}.apply {isEnabled=valid() || saved})
        root.addView(label("복귀 코스를 확인한 뒤 직접 시작해요\n시간 내 귀가를 보장하지 않아요",20))
        root.addView(button("결과 화면 닫기") {if(allowed) finish()})
    }
    private fun label(value:String,size:Int)=ui.text(value,size)
    private fun button(value:String,primary:Boolean=false,action:()->Unit)=ui.button(value,primary,action)
    private fun dp(value:Int)=ui.dp(value)

}
