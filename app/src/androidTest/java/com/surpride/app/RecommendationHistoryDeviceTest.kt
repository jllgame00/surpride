package com.surpride.app

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.experience.core.DrivePreferences
import com.surpride.app.realdata.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Parked Activity/journal wiring. MOCK routes only; no navigation start or external API. */
@RunWith(AndroidJUnit4::class)
class RecommendationHistoryDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val nonce="00000000-0000-4000-8000-000000000001"
    private val candidate get()=SeoulPilotCatalog.places.first().id
    private fun preserve(action:()->Unit) {
        val prefs=context.getSharedPreferences("live_navigation_emulator",0)
        val old=prefs.getString("journal",null)
        try {action()} finally {assertTrue(prefs.edit().putString("journal",old).commit())}
    }
    private fun descendants(view:View):List<View> = listOf(view)+
        if(view is ViewGroup) (0 until view.childCount).flatMap {descendants(view.getChildAt(it))} else emptyList()
    private fun waitUntil(action:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+8000
        while(SystemClock.elapsedRealtime()<deadline) {if(action()) return;SystemClock.sleep(100)}
        fail("Parked Activity contract unavailable")
    }
    @Test fun legacyVisitAndNewArrivalEvidenceRoundTripWithoutLosingRatings() {
        val name="b4-native-live_navigation_emulator"
        val exists=File(context.applicationInfo.dataDir,"shared_prefs/$name.xml").exists()
        val prefs=context.getSharedPreferences(name,0);val old=prefs.getString("journal",null)
        val isolated=object:ContextWrapper(context) {
            override fun getSharedPreferences(name:String,mode:Int)=context.getSharedPreferences("b4-native-$name",mode)
        }
        try {
            val now=System.currentTimeMillis()
            val legacy=JSONObject().put("candidate_id",candidate).put("rating",3).put("completed_at_epoch_ms",now-1000)
            assertTrue(prefs.edit().putString("journal",JSONObject().put("visits",JSONArray().put(legacy)).toString()).commit())
            assertNull(LiveNavigationStore.read(isolated).visits.single().arrivalConfirmed)
            assertTrue(LiveNavigationStore.prepare(isolated,PendingLiveNavigation(nonce,candidate,now)))
            assertTrue(LiveNavigationStore.complete(isolated,nonce,candidate,LiveNavigationResultPolicy.SOURCE,true,true,5,now,true))
            val loaded=LiveNavigationStore.read(isolated)
            assertEquals(listOf(3,5),loaded.visits.map {it.rating})
            assertNull(loaded.visits.first().arrivalConfirmed);assertEquals(true,loaded.visits.last().arrivalConfirmed)
            assertEquals(setOf(candidate),loaded.recommendationContext(now,false,emptySet()).recentIds)
            assertFalse(LiveNavigationStore.complete(isolated,nonce,candidate,LiveNavigationResultPolicy.SOURCE,true,true,1,now,true))
            assertEquals(2,LiveNavigationStore.read(isolated).visits.size)
        } finally {
            if(exists) assertTrue(prefs.edit().putString("journal",old).commit()) else assertTrue(context.deleteSharedPreferences(name))
        }
    }
    @Test fun realResultActivityPersistsExplicitArrivalFlagAndRestoresReview()=preserve {
        val now=System.currentTimeMillis()
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,now)))
        val intent=Intent(context,LiveNavigationResultActivity::class.java).putExtra("session_nonce",nonce)
            .putExtra("candidate_id",candidate).putExtra("source",LiveNavigationResultPolicy.SOURCE)
            .putExtra("revealed",true).putExtra("stopped",true).putExtra("arrived",true)
        ActivityScenario.launch<LiveNavigationResultActivity>(intent).use {scenario->
            waitUntil {var ready=false;scenario.onActivity {a->ready=descendants(a.window.decorView).filterIsInstance<Button>()
                .any {it.text.toString()=="4 점" && it.isEnabled}};ready}
            scenario.onActivity {a->descendants(a.window.decorView).filterIsInstance<Button>().single {it.text.toString()=="4 점"}.performClick()}
            scenario.onActivity {a->descendants(a.window.decorView).filterIsInstance<Button>().single {it.text.toString()=="이 경험 기록하기"}.performClick()}
            val visit=LiveNavigationStore.read(context).visits.last()
            assertEquals(candidate,visit.candidateId);assertEquals(4,visit.rating);assertEquals(true,visit.arrivalConfirmed)
            scenario.recreate()
            assertTrue(candidate in LiveNavigationStore.read(context).recommendationContext(System.currentTimeMillis(),false,emptySet()).recentIds)
        }
    }
    @Test fun malformedPendingAndVisitRowsCannotEraseIndependentValidHistory() {
        val name="b4-native-malformed"
        val exists=File(context.applicationInfo.dataDir,"shared_prefs/$name.xml").exists()
        val prefs=context.getSharedPreferences(name,0);val old=prefs.getString("journal",null)
        val isolated=object:ContextWrapper(context) {override fun getSharedPreferences(name:String,mode:Int)=prefs}
        try {
            val now=System.currentTimeMillis()
            val valid=JSONObject().put("candidate_id",candidate).put("rating",4).put("completed_at_epoch_ms",now).put("arrival_confirmed",true)
            val invalid=JSONObject().put("candidate_id",candidate).put("completed_at_epoch_ms",now)
            val rows=JSONArray().put(valid).put(invalid).put("not an object")
                .put(JSONObject().put("candidate_id",candidate).put("rating",5).put("completed_at_epoch_ms","invalid"))
                .put(JSONObject().put("candidate_id",candidate).put("rating",4.5).put("completed_at_epoch_ms",now))
            for(pending in listOf(false,true)) {
                val json=JSONObject().put("visits",rows)
                if(pending) json.put("pending",JSONObject().put("candidate_id",candidate))
                assertTrue(prefs.edit().putString("journal",json.toString()).commit())
                val loaded=LiveNavigationStore.read(isolated)
                assertEquals("Valid rating must survive unrelated malformed rows",listOf(4),loaded.visits.map {it.rating})
                assertNull(loaded.pending)
                assertEquals(setOf(candidate),loaded.recommendationContext(now,false,emptySet()).recentIds)
                assertTrue(LiveNavigationStore.prepare(isolated,PendingLiveNavigation(nonce,candidate,now)))
                assertEquals(listOf(4),LiveNavigationStore.read(isolated).visits.map {it.rating})
            }
        } finally {
            if(exists) assertTrue(prefs.edit().putString("journal",old).commit()) else assertTrue(context.deleteSharedPreferences(name))
        }
    }
    @Test fun realRecommendationActivityKeepsDemoIdsSeparateFromLiveCompletionOnRecalculation()=preserve {
        val now=System.currentTimeMillis()
        val visit=JSONObject().put("candidate_id",candidate).put("rating",5).put("completed_at_epoch_ms",now).put("arrival_confirmed",true)
        assertTrue(context.getSharedPreferences("live_navigation_emulator",0).edit()
            .putString("journal",JSONObject().put("visits",JSONArray().put(visit)).toString()).commit())
        val demoExcluded=SeoulPilotCatalog.places.map {it.id}.toSet()-candidate
        val intent=RealRouteActivity.intent(context,DrivePreferences(availableMinutes=180,returnToStart=false),demoExcluded)
        ActivityScenario.launch<RealRouteActivity>(intent).use {scenario->
            waitUntil {var ready=false;scenario.onActivity {a->ready=a.javaClass.getDeclaredField("requiresDo").apply {isAccessible=true}.get(a)==false};ready}
            fun calculate() {scenario.onActivity {a->a.javaClass.getDeclaredMethod("calculate",Boolean::class.javaPrimitiveType!!)
                .apply {isAccessible=true}.invoke(a,true)}}
            fun result():RoutePlan? {var value:RoutePlan?=null;scenario.onActivity {a->
                value=(a.javaClass.getDeclaredField("session").apply {isAccessible=true}.get(a) as RealRouteActivity.Session).plan};return value}
            calculate();waitUntil {result()!=null}
            val first=result() as RoutePlan.Success
            assertEquals(candidate,first.place.id);assertEquals(RouteOrigin.MOCK,first.routeOrigin);assertNull(first.presentation(false).destination)
            scenario.onActivity {a->
                val state=a.javaClass.getDeclaredField("session").apply {isAccessible=true}.get(a) as RealRouteActivity.Session
                state.preferences=state.preferences.copy(availableMinutes=60)
            }
            calculate();waitUntil {result()!=null}
            assertEquals(candidate,(result() as RoutePlan.Success).place.id)
            assertEquals(listOf(5),LiveNavigationStore.read(context).visits.map {it.rating})
        }
    }
}
