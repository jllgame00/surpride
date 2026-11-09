package com.surpride.app.realdata

import android.graphics.Color
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

/** Exported callback entry: an opaque pending token authorizes review, never automatic rating. */
class LiveNavigationResultActivity:AppCompatActivity() {
    private lateinit var ux:ParkedUxMonitor
    private lateinit var root:LinearLayout
    private var requiresDo:Boolean?=null
    private var rating:Int?=null
    private var saved=false
    private var saveError=false
    private val allowed get()=ParkedInteractionPolicy.allows(requiresDo)
    private val nonce get()=intent.getStringExtra("session_nonce")
    private val candidateId get()=intent.getStringExtra("candidate_id")
    private val source get()=intent.getStringExtra("source")
    private val revealed get()=intent.getBooleanExtra("revealed",false)
    private val stopped get()=intent.getBooleanExtra("stopped",false)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        rating=savedInstanceState?.getInt("rating",0)?.takeIf {it in 1..5}
        saved=savedInstanceState?.getBoolean("saved",false)?:false
        ux=ParkedUxMonitor(applicationContext)
        WindowCompat.setDecorFitsSystemWindows(window,false)
        root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(10,24,24))}
        setContentView(ScrollView(this).apply {isFillViewport=true;setBackgroundColor(Color.rgb(10,24,24));addView(root)})
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
        outState.putInt("rating",rating?:0);outState.putBoolean("saved",saved);super.onSaveInstanceState(outState)
    }
    private fun valid():Boolean = LiveNavigationResultPolicy.accepts(LiveNavigationStore.read(this).pending,
        nonce,candidateId,source,revealed,stopped,System.currentTimeMillis()) &&
        SeoulPilotCatalog.places.any {it.id==candidateId}
    private fun render() {
        root.removeAllViews()
        root.addView(label("REAL ROUTE / DEVICE_API_EMULATOR",25))
        root.addView(label("임시 실제경로 · 정차 에뮬레이터 전용, 실제 운행 검증 없음",18))
        if(!allowed) {root.addView(label("정차 상태가 확인되면 결과를 검토할 수 있습니다.",24));return}
        root.addView(label("AVD 에뮬레이터 결과 · 실도로 방문 인증 아님\nDEMO 기록과 별도 보관합니다.",20))
        if(saved) {
            root.addView(label("LIVE 에뮬레이터 평가를 저장했습니다.",28))
        } else if(!valid()) {
            root.addView(label("결과를 확인할 수 없습니다. 세션이 만료되었거나 이미 저장되었거나, 공개·중지가 확인되지 않았습니다.",23))
        } else {
            val place=SeoulPilotCatalog.places.first {it.id==candidateId}
            root.addView(label(place.name,32))
            root.addView(label(if(intent.getBooleanExtra("arrived",false)) "도착 후 공개·중지 결과" else "명시적 공개·중지 결과 (도착 확인 아님)",20))
            root.addView(label("대표 좌표의 차량 진입·주차·접근성은 UNKNOWN입니다.\n경로/GPS/API 키를 기록에 저장하지 않습니다.",18))
            root.addView(label("이번 에뮬레이터 경험을 직접 평가해 주세요.",24))
            if(saveError) root.addView(label("저장하지 못했습니다. 세션과 저장 공간을 확인하고 다시 시도하세요.",20))
            val choices=LinearLayout(this)
            (1..5).forEach {value->choices.addView(button(if(rating==value) "✓ $value / 5" else "$value / 5") {
                if(allowed && valid()) {rating=value;render()}
            },LinearLayout.LayoutParams(0,-2,1f))}
            root.addView(choices)
            root.addView(button("선택한 평가를 LIVE 에뮬레이터 기록에 저장") {
                val selected=rating
                if(allowed && selected!=null && valid()) {
                    saved=LiveNavigationStore.complete(this,nonce,candidateId,source,revealed,stopped,selected,System.currentTimeMillis())
                    saveError=!saved
                    render()
                }
            }.apply {isEnabled=rating!=null})
        }
        root.addView(button("결과 화면 닫기") {if(allowed) finish()})
        root.addView(button("내비 PoC로 돌아가 복귀 준비") {if(allowed) {
            try {startActivity(android.content.Intent().setComponent(android.content.ComponentName("com.surpride.navhostpoc","androidx.car.app.activity.CarAppActivity")))}
            catch(_:Exception) {root.addView(label("내비 PoC 앱을 먼저 설치해 주세요.",20))}
        }})
    }
    private fun label(value:String,size:Int)=TextView(this).apply {
        text=value;textSize=size.toFloat();setTextColor(Color.rgb(235,241,225));setPadding(0,dp(12),0,dp(12))
    }
    private fun button(value:String,action:()->Unit)=Button(this).apply {
        text=value;textSize=20f;minHeight=dp(76);isAllCaps=false;setOnClickListener {action()}
    }
    private fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
}
