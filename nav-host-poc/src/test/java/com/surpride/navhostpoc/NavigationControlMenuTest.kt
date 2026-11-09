package com.surpride.navhostpoc

import androidx.car.app.model.Row
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class NavigationControlMenuTest {
    @Test fun stopIsFirstAndReturningToMapIsAVisibleOfficialAction() {
        val template=NavigationControlMenu.build(true,false,false,false) {}
        assertEquals("안내 종료",(template.singleList!!.items.first() as Row).title.toString())
        assertEquals("지도로 돌아가기",template.actionStrip!!.actions.single().title.toString())
    }
    @Test fun arrivalAndReturnNeedTheirOwnVerifiedEligibility() {
        fun titles(arrival:Boolean,returnReady:Boolean)=NavigationControlMenu.build(true,false,arrival,returnReady) {}
            .singleList!!.items.map {(it as Row).title.toString()}
        assertFalse(titles(false,false).any {it.contains("도착 확인") || it.contains("복귀 경로")})
        assertTrue(titles(true,false).contains("정차·도착 확인"))
        assertTrue(titles(false,true).contains("출발지 복귀 경로 준비"))
    }
    @Test fun restrictedStateRetainsStopButDoesNotOfferDisclosureOrVoice() {
        val titles=NavigationControlMenu.build(false,false,true,true) {}.singleList!!.items.map {(it as Row).title.toString()}
        assertEquals("안내 종료",titles.first())
        assertFalse(titles.any {it=="목적지 즉시 공개" || it=="음성 안내 켜기" || it=="정차·도착 확인"})
    }
}
