package com.surpride.app

import android.content.Intent
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

/** Real result Activity and durable journal; the original private journal is restored in finally. */
@RunWith(AndroidJUnit4::class)
class LiveReviewRestorationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val nonce="00000000-0000-4000-8000-000000000001"
    private val candidate="seoul-night-13"
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList()
    private fun result(value:String?=nonce)=Intent(context,LiveNavigationResultActivity::class.java).apply {
        if(value!=null) putExtra("session_nonce",value).putExtra("candidate_id",candidate)
            .putExtra("source","DEVICE_API_EMULATOR").putExtra("revealed",true).putExtra("stopped",true)
    }
    private fun preserve(action:()->Unit) {
        val prefs=context.getSharedPreferences("live_navigation_emulator",0);val old=prefs.getString("journal",null)
        try {action()} finally {assertTrue(prefs.edit().putString("journal",old).commit())}
    }
    private fun waitForButton(scenario:ActivityScenario<LiveNavigationResultActivity>,text:String) {
        val until=android.os.SystemClock.elapsedRealtime()+7000
        var ready=false
        while(!ready && android.os.SystemClock.elapsedRealtime()<until) {
            scenario.onActivity {a->ready=views(a.window.decorView).filterIsInstance<Button>().any {it.text.toString()==text && it.isEnabled}}
            if(!ready) android.os.SystemClock.sleep(100)
        }
        assertTrue("Parked result control unavailable",ready)
    }
    @Test fun savedReviewSurvivesActivityAndFreshEntryWithoutDuplicateVisit()=preserve {
        val now=System.currentTimeMillis();assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,now)))
        val before=LiveNavigationStore.read(context).visits.size
        ActivityScenario.launch<LiveNavigationResultActivity>(result()).use {scenario->
            waitForButton(scenario,"4 점")
            scenario.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().single {it.text=="4 점"}.performClick()}
            scenario.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().single {it.text=="이 경험 기록하기"}.performClick()}
            assertEquals(minOf(30,before+1),LiveNavigationStore.read(context).visits.size)
            scenario.recreate()
            waitForButton(scenario,"결과 화면 닫기")
            scenario.onActivity {a->assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any {it.text=="오늘의 경험을 기록했어요"})}
        }
        ActivityScenario.launch<LiveNavigationResultActivity>(result()).use {scenario->
            waitForButton(scenario,"출발지로 돌아가기 준비")
            scenario.onActivity {a->assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any {it.text=="오늘의 경험을 기록했어요"})}
        }
        assertFalse(LiveNavigationStore.complete(context,nonce,candidate,"DEVICE_API_EMULATOR",true,true,5,System.currentTimeMillis()))
        assertEquals(minOf(30,before+1),LiveNavigationStore.read(context).visits.size)
        val raw=org.json.JSONObject(context.getSharedPreferences("live_navigation_emulator",0).getString("journal","{}")!!)
        val visits=raw.getJSONArray("visits")
        val fixture=org.json.JSONObject().put("completed",raw.getJSONObject("completed"))
            .put("visits",org.json.JSONArray().put(visits.getJSONObject(visits.length()-1)))
        java.io.File(context.cacheDir,"b2-native-review-journal.json").writeText(fixture.toString())
    }
    @Test fun emptyAndOldNonceEntriesCannotSaveOrEnableReturn()=preserve {
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())))
        val before=LiveNavigationStore.read(context).visits
        for(value in listOf(null,"00000000-0000-4000-8000-000000000002")) {
            ActivityScenario.launch<LiveNavigationResultActivity>(result(value)).use {scenario->
                waitForButton(scenario,"결과 화면 닫기")
                scenario.onActivity {a->
                    val all=views(a.window.decorView)
                    assertFalse(all.filterIsInstance<Button>().single {it.text=="출발지로 돌아가기 준비"}.isEnabled)
                    assertFalse(all.filterIsInstance<TextView>().any {it.text=="이촌한강공원"})
                }
            }
        }
        assertEquals(before,LiveNavigationStore.read(context).visits)
    }
    @Test fun expiredPendingRequiresNewPreparation()=preserve {
        val now=System.currentTimeMillis()
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,now-3_600_001)))
        ActivityScenario.launch<LiveNavigationResultActivity>(result()).use {scenario->
            waitForButton(scenario,"결과 화면 닫기")
            scenario.onActivity {a->assertFalse(views(a.window.decorView).filterIsInstance<Button>().single {it.text=="출발지로 돌아가기 준비"}.isEnabled)}
        }
    }
    @Test fun savedActivityBundleCannotReplaceAMissingDurableReceipt()=preserve {
        val now=System.currentTimeMillis()
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,now)))
        ActivityScenario.launch<LiveNavigationResultActivity>(result()).use {scenario->
            waitForButton(scenario,"4 점")
            scenario.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().single {it.text=="4 점"}.performClick()}
            scenario.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().single {it.text=="이 경험 기록하기"}.performClick()}
            assertTrue(context.getSharedPreferences("live_navigation_emulator",0).edit().remove("journal").commit())
            scenario.recreate();waitForButton(scenario,"결과 화면 닫기")
            scenario.onActivity {a->
                val all=views(a.window.decorView)
                assertFalse(all.filterIsInstance<TextView>().any {it.text=="오늘의 경험을 기록했어요"})
                assertFalse(all.filterIsInstance<Button>().single {it.text=="출발지로 돌아가기 준비"}.isEnabled)
            }
        }
    }
    @Test fun failedJournalCommitRestoresItsPreviousInProcessState() {
        val name="b2-commit-faults"
        val real=context.getSharedPreferences(name,0)
        var fail=false
        val prefs=object:android.content.SharedPreferences by real {
            override fun edit():android.content.SharedPreferences.Editor {
                val editor=real.edit()
                return object:android.content.SharedPreferences.Editor by editor {
                    override fun putString(key:String?,value:String?):android.content.SharedPreferences.Editor {editor.putString(key,value);return this}
                    override fun commit():Boolean {
                        val committed=editor.commit()
                        return if(fail) {fail=false;false} else committed
                    }
                }
            }
        }
        val isolated=object:android.content.ContextWrapper(context) {
            override fun getSharedPreferences(key:String?,mode:Int)=prefs
        }
        try {
            val first=PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())
            assertTrue(LiveNavigationStore.prepare(isolated,first));fail=true
            assertFalse(LiveNavigationStore.prepare(isolated,first.copy(nonce="00000000-0000-4000-8000-000000000002")))
            assertEquals(first,LiveNavigationStore.read(isolated).pending)
            assertTrue(LiveNavigationStore.complete(isolated,nonce,candidate,"DEVICE_API_EMULATOR",true,true,4,System.currentTimeMillis()))
        } finally {assertTrue(context.deleteSharedPreferences(name))}
    }
    @Test fun malformedReceiptCannotHidePreviouslySavedVisits() {
        val name="b2-malformed-receipt"
        val prefs=context.getSharedPreferences(name,0)
        val isolated=object:android.content.ContextWrapper(context) {override fun getSharedPreferences(key:String?,mode:Int)=prefs}
        try {
            val data=org.json.JSONObject().put("completed",org.json.JSONObject().put("invalid",true))
                .put("visits",org.json.JSONArray().put(org.json.JSONObject().put("candidate_id",candidate).put("rating",4)
                    .put("completed_at_epoch_ms",System.currentTimeMillis())))
            assertTrue(prefs.edit().putString("journal",data.toString()).commit())
            assertEquals(1,LiveNavigationStore.read(isolated).visits.size)
            assertNull(LiveNavigationStore.read(isolated).pending)
        } finally {assertTrue(context.deleteSharedPreferences(name))}
    }
}
