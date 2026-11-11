package com.surpride.app

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.experience.*
import com.surpride.app.experience.core.*
import com.surpride.app.moodinput.MoodInputActivity
import com.surpride.app.moodinput.PreferenceInputContract
import com.surpride.app.ui.SurprideUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Same native UI fixtures on both APKs; these captures do not assert a real drive or arrival. */
@RunWith(AndroidJUnit4::class)
class StructuralUiCaptureTest {
    @Test fun fixedConditionsCoverTheFiveScreensAndEmptyHistory() {
        val inst=InstrumentationRegistry.getInstrumentation();val context=inst.targetContext
        val prefix=InstrumentationRegistry.getArguments().getString("capture_set")?:"after"
        require(prefix in listOf("before","after","large"))
        val directory=File(context.cacheDir,"phase7b-captures").apply {mkdirs()}
        val demo=File(context.noBackupFilesDir,"surpride-demo-v1.bin")
        val original=demo.takeIf {it.exists()}?.readBytes()
        val prefs=context.getSharedPreferences("live_navigation_emulator",0);val journal=prefs.getString("journal",null)
        val appearance=context.getSharedPreferences("appearance",0);val mode=appearance.getInt("night_mode",-1)
        val now=1794376800000L
        val conditions=DrivePreferences(mood=Mood.HEALING,categories=emptySet(),availableMinutes=180,returnToStart=true)
        val store=DemoStateStore(context)
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
        fun <T:android.app.Activity> waitFor(s:ActivityScenario<T>,text:String) {
            var ready=false;val end=SystemClock.uptimeMillis()+10_000
            while(!ready && SystemClock.uptimeMillis()<end) {
                s.onActivity {a->ready=views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().contains(text)}}
                if(!ready)SystemClock.sleep(100)
            }
            assertTrue("Native screen not ready: $text",ready)
        }
        fun capture(name:String) {
            inst.waitForIdleSync();SystemClock.sleep(1800)
            // Pleos API34 can return a null hardware screenshot buffer; use the native shell capture.
            fun frame()=android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p")).use {
                checkNotNull(android.graphics.BitmapFactory.decodeStream(it))
            }
            var image=frame()
            fun rendered():Boolean {
                var ink=0;var dark=0
                for(y in 120 until image.height-160 step 4) for(x in image.width/3+40 until image.width-60 step 4) {
                    val pixel=image.getPixel(x,y)
                    if(pixel==android.graphics.Color.rgb(242,242,242)) ink++
                    if(android.graphics.Color.red(pixel)<64 && android.graphics.Color.green(pixel)<64 && android.graphics.Color.blue(pixel)<64) dark++
                }
                return ink>=100 && dark>=1000
            }
            val deadline=SystemClock.uptimeMillis()+8000
            while(!rendered() && SystemClock.uptimeMillis()<deadline) {
                image.recycle();SystemClock.sleep(500);image=frame()
            }
            assertTrue("Native frame blank or still fading: $name",rendered())
            File(directory,"$prefix-$name.png").outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))};image.recycle()
        }
        fun experience(state:MysteryTripState,name:String,ready:String,detail:Boolean=false) {
            store.save(state)
            ActivityScenario.launch(ExperienceActivity::class.java).use {s->
                waitFor(s,ready);capture(name)
                if(detail) {
                    s.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().first {it.contentDescription?.startsWith("기록 자세히 보기")==true}.performClick()}
                    capture("10-record-detail");inst.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                }
            }
        }
        try {
            inst.runOnMainSync {SurprideUi.setAppearance(context,2)}
            assertTrue(prefs.edit().remove("journal").commit())
            ActivityScenario.launch(MainActivity::class.java).use {s->waitFor(s,"드라이브 준비");capture("01-start")}
            experience(MysteryTripState(panel=ExperiencePanel.TRIP,preferences=conditions),"02-conditions","정차 중 이용")
            ActivityScenario.launch<MoodInputActivity>(Intent(context,MoodInputActivity::class.java)
                .putExtra(PreferenceInputContract.EXTRA,PreferenceInputContract.encode(conditions))).use {s->
                waitFor(s,"문장 해석")
                s.onActivity {a->views(a.window.decorView).filterIsInstance<EditText>().single().setText("강을 보며 쉬고 싶어. 한 시간 반, 집으로 돌아와야 해")}
                SystemClock.sleep(200)
                s.onActivity {a->views(a.window.decorView).filterIsInstance<Button>().single {it.text=="문장 해석"}.performClick()}
                waitFor(s,"정리한 조건을 확인");capture("03-natural")
            }
            val controller=TripController(DemoCatalog.candidates,{now},{42L})
            controller.updatePreferences(conditions);controller.beginPlanning();controller.finishPlanning()
            assertEquals(TripPhase.READY,controller.state.phase)
            if(prefix!="before") experience(controller.state,"09-ready","비밀 코스가 준비됐어요")
            controller.startDemo()
            if(prefix!="before") experience(controller.state,"07-simulation","모의 진행")
            controller.arrive()
            if(prefix!="before") experience(controller.state,"08-arrival","모의 도착")
            controller.revealArrival()
            experience(controller.state,"04-reveal",controller.presentation().destination!!.name)
            val history=listOf(
                DemoVisit("editorial-a",DemoCatalog.candidates[0].id,Mood.HEALING,4,now-86400000,true),
                DemoVisit("editorial-b",DemoCatalog.candidates[1].id,Mood.EXCITEMENT,5,now,true))
            experience(MysteryTripState(panel=ExperiencePanel.MAP,preferences=conditions,history=history),"05-history","정차 중 이용",detail=prefix!="before")
            experience(MysteryTripState(panel=ExperiencePanel.MAP,preferences=conditions),"06-empty","첫 DEMO 만들기")
            File(directory,"$prefix-fixture.txt").writeText("density=${context.resources.displayMetrics.densityDpi};font=${context.resources.configuration.fontScale};mode=dark;fixtureTime=$now;healing;180min;roundTrip;seed=42;history=2;no-live-journal")
        } finally {
            if(original==null)demo.delete() else demo.writeBytes(original)
            assertTrue(prefs.edit().putString("journal",journal).commit())
            inst.runOnMainSync {SurprideUi.setAppearance(context,mode)}
        }
    }
}
