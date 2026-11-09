package com.surpride.navhostpoc

import androidx.car.app.model.*

/** API 4 parked controls for the SAME Screen. No ScreenManager, ownership or position side effects. */
internal object NavigationControlMenu {
    fun build(allowed:Boolean,voiceOn:Boolean,canConfirmArrival:Boolean,canReturn:Boolean,command:(String)->Unit):ListTemplate {
        val list=ItemList.Builder()
        fun row(title:String,id:String)=list.addItem(Row.Builder().setTitle(title).setOnClickListener {command(id)}.build())
        row("안내 종료","stop")
        if(!allowed) list.addItem(Row.Builder().setTitle("정차 상태를 확인한 뒤 조작할 수 있어요").build())
        else {
            row("목적지 즉시 공개","reveal")
            row(if(voiceOn) "음성 안내 끄기" else "음성 안내 켜기","voice")
            if(canConfirmArrival) row("정차·도착 확인","arrival")
            if(canReturn) row("출발지 복귀 경로 준비","return")
        }
        row("지도로 돌아가기","map")
        val back=ActionStrip.Builder().addAction(Action.Builder().setTitle("지도로 돌아가기").setOnClickListener {command("map")}.build()).build()
        // APP_ICON is intentionally non-interactive: Action.BACK would pop the navigation Screen.
        return ListTemplate.Builder().setTitle("안내 조작 · 정차 에뮬레이터").setHeaderAction(Action.APP_ICON)
            .setSingleList(list.build()).setActionStrip(back).build()
    }
}
