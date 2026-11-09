package com.surpride.navhostpoc

import android.os.Build
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.navigation.model.Maneuver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.surpride.navhostpoc.ownership.*

/** Read-only, local payload inspection. Never obtains a system navigation/focus manager. */
internal class OwnershipAuditScreen(context:CarContext):Screen(context) {
    private val ux=PocParkedMonitor(carContext.applicationContext)
    private val emulator=Build.MODEL=="Connect on x86_64 emulator" && Build.PRODUCT=="crp_avd"
    private var allowed=false
    init {lifecycle.addObserver(object:DefaultLifecycleObserver {
        override fun onStart(owner:LifecycleOwner) {if(emulator) ux.start {allowed=it==false;invalidate()}}
        override fun onStop(owner:LifecycleOwner) {allowed=false;ux.stop()}
        override fun onDestroy(owner:LifecycleOwner) {ux.stop();Log.i("SurprideOwnership","AUDIT_DESTROYED actualActivation=false")}
    })}
    override fun onGetTemplate():Template {
        val list=ItemList.Builder()
        if(!allowed) list.addItem(Row.Builder().setTitle("정차 확인 중 / 계약 감사 잠금").build()) else {
            // Synthetic, local-only contract fixture; no actual destination or route is used.
            val trip=PrivacyTripFactory.hidden(NextTurnMetadata("계약 검증용 우회전","검증 전용 도로",Maneuver.TYPE_TURN_NORMAL_RIGHT,
                300.0,60,System.currentTimeMillis()),DestinationIdentity("비공개 계약 식별자",null))
            list.addItem(Row.Builder().setTitle("실제 활성화 보류 · MOCK 로컬 계약")
                .addText("정책/호스트 노출 미확인 · 포커스·안내 요청 없음").build())
            list.addItem(Row.Builder().setTitle("Trip 목적지 ${trip.destinations.size} · 도착 ETA ${trip.destinationTravelEstimates.size}")
                .addText("Step ${trip.steps.size} · 다음 Step ETA ${trip.stepTravelEstimates.size} · 최종 도착 ETA 아님").build())
            list.addItem(Row.Builder().setTitle("소유권 모델 INACTIVE · 실제 연결 없음")
                .addText("공식 TestNavigationManager는 자동 테스트에서만 사용").build())
            list.addItem(Row.Builder().setTitle("시스템 Trip·계기판·HUD·알림·이력 UNVERIFIED")
                .addText("이 화면은 데이터 전송/실제 비공개 성공의 증거가 아님").build())
            list.addItem(Row.Builder().setTitle("PoC 종료").setOnClickListener {if(allowed) carContext.finishCarApp()}.build())
            Log.i("SurprideOwnership","LOCAL_AUDIT destinations=${trip.destinations.size} steps=${trip.steps.size} stepEstimates=${trip.stepTravelEstimates.size} model=INACTIVE actualActivation=false carApi=${carContext.carAppApiLevel}")
        }
        return ListTemplate.Builder().setTitle("소유권·비공개 계약 감사 · 정차 전용").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
