package com.surpride.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.realdata.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** UI fixture only: no Host ownership, navigation, GPS, or claim of an actual arrival. */
@RunWith(AndroidJUnit4::class)
class MonochromeLiveCaptureTest {
    @Test fun liveReviewStatesRenderWithoutStartingNavigation() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("live_navigation_emulator",0)
        val original=prefs.getString("journal",null)
        val nonce="00000000-0000-4000-8000-000000000007"
        val candidate="seoul-night-13"
        fun views(view:View):List<View> = listOf(view)+
            if(view is ViewGroup) (0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList()
        fun waitFor(scenario:ActivityScenario<LiveNavigationResultActivity>,label:String) {
            var ready=false
            val deadline=SystemClock.uptimeMillis()+10_000
            while(!ready && SystemClock.uptimeMillis()<deadline) {
                scenario.onActivity {ready=views(it.window.decorView).filterIsInstance<TextView>().any {v->v.text.toString()==label}}
                if(!ready) SystemClock.sleep(100)
            }
            assertTrue("Result state missing: $label",ready)
        }
        fun capture(name:String) {
            instrumentation.waitForIdleSync();SystemClock.sleep(700)
            val image=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            val directory=File(context.cacheDir,"phase7-captures").apply {mkdirs()}
            File(directory,"fixture-$name.png").outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}
            image.recycle()
        }
        fun entry()=Intent(context,LiveNavigationResultActivity::class.java).apply {
            putExtra("session_nonce",nonce);putExtra("candidate_id",candidate)
            putExtra("source","DEVICE_API_EMULATOR");putExtra("revealed",true)
            putExtra("stopped",true);putExtra("arrived",true)
        }
        try {
            assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())))
            ActivityScenario.launch<LiveNavigationResultActivity>(entry()).use {scenario->
                waitFor(scenario,"4 점");capture("live-review-unselected")
                scenario.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().single {it.text=="4 점"}.performClick()}
                waitFor(scenario,"이 경험 기록하기");capture("live-review-selected")
                scenario.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().single {it.text=="이 경험 기록하기"}.performClick()}
                waitFor(scenario,"오늘의 경험을 기록했어요");capture("live-review-saved")
            }
            ActivityScenario.launch<LiveNavigationResultActivity>(entry().putExtra("session_nonce","invalid")).use {scenario->
                waitFor(scenario,"지금은 결과를 확인할 수 없어요");capture("live-invalid-session")
                scenario.onActivity {a->assertFalse(views(a.window.decorView).filterIsInstance<Button>().single {it.text=="출발지로 돌아가기 준비"}.isEnabled)}
            }
        } finally {assertTrue(prefs.edit().putString("journal",original).commit())}
    }
}
