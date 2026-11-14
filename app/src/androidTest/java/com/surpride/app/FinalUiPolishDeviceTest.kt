package com.surpride.app

import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.experience.*
import com.surpride.app.experience.core.*
import com.surpride.app.moodinput.MoodInputActivity
import com.surpride.app.moodinput.PreferenceInputContract
import com.surpride.app.ui.SurprideUi
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FinalUiPolishDeviceTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val store get()=DemoStateStore(context)
    private val demo get()=File(context.noBackupFilesDir,"surpride-demo-v1.bin")
    private var original:ByteArray?=null
    private var mode=-1
    private var developer:Boolean?=null
    private val conditions=DrivePreferences(mood=Mood.HEALING,availableMinutes=180,returnToStart=true)
    private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
    private fun buttons(a:android.app.Activity)=views(a.window.decorView).filterIsInstance<Button>()
    @Before fun preserve() {
        original=demo.takeIf {it.exists()}?.readBytes()
        val prefs=context.getSharedPreferences("appearance",0)
        mode=prefs.getInt("night_mode",-1)
        developer=if(prefs.contains("developer_tools")) prefs.getBoolean("developer_tools",false) else null
        assertTrue(prefs.edit().putBoolean("developer_tools",false).commit())
        inst.runOnMainSync {SurprideUi.setAppearance(context,2)}
    }
    @After fun restore() {
        if(original==null) demo.delete() else demo.writeBytes(original!!)
        val edit=context.getSharedPreferences("appearance",0).edit()
        if(developer==null) edit.remove("developer_tools") else edit.putBoolean("developer_tools",developer!!)
        assertTrue(edit.commit());inst.runOnMainSync {SurprideUi.setAppearance(context,mode)}
    }
    private fun <T:android.app.Activity> waitFor(s:ActivityScenario<T>,label:String) {
        var ready=false;val end=SystemClock.uptimeMillis()+10_000
        while(!ready && SystemClock.uptimeMillis()<end) {
            s.onActivity {a->ready=views(a.window.decorView).filterIsInstance<TextView>().any {it.text.toString().contains(label)}}
            if(!ready)SystemClock.sleep(100)
        }
        assertTrue("Screen missing: $label",ready)
    }
    private fun experience(state:MysteryTripState,action:(ActivityScenario<ExperienceActivity>)->Unit) {
        store.save(state)
        ActivityScenario.launch(ExperienceActivity::class.java).use {s->waitFor(s,"정차 중 이용");action(s)}
    }
    private fun reveal():MysteryTripState {
        val c=TripController(DemoCatalog.candidates,{1794376800000L},{42L})
        c.updatePreferences(conditions);c.beginPlanning();c.finishPlanning();c.startDemo();c.arrive();c.revealArrival()
        return c.state
    }
    private fun editor(action:(ActivityScenario<MoodInputActivity>)->Unit) {
        ActivityScenario.launch<MoodInputActivity>(Intent(context,MoodInputActivity::class.java)
            .putExtra(PreferenceInputContract.EXTRA,PreferenceInputContract.encode(conditions))).use {s->
                waitFor(s,"문장 해석");action(s)
            }
    }
    private fun interpret(s:ActivityScenario<MoodInputActivity>) {
        s.onActivity {a->views(a.window.decorView).filterIsInstance<EditText>().single().setText("강을 보며 쉬고 싶어. 한 시간 반, 집으로 돌아와야 해")}
        s.onActivity {a->buttons(a).single {it.text=="문장 해석"}.performClick()}
        waitFor(s,"정리한 조건을 확인")
    }
    private fun last(a:ExperienceActivity)=views(a.window.decorView).filterIsInstance<ScrollView>().single().let {views(it).filterIsInstance<Button>().last()}
    @Test fun finalChoicesKeepTheirFullTargetAndGapAfterKeyboardSelection() {
        for(step in 0..2) experience(MysteryTripState(panel=ExperiencePanel.TRIP,preferences=conditions,setupStep=step)) {s->
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            s.onActivity {a->assertTrue(last(a).requestFocus())}
            inst.waitForIdleSync();SystemClock.sleep(400)
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_SPACE)
            inst.waitForIdleSync();SystemClock.sleep(400)
            s.onActivity {a->
                val target=last(a);assertSame(target,a.currentFocus)
                val bounds=Rect();assertTrue(target.getGlobalVisibleRect(bounds))
                assertEquals("Last option must be fully visible",target.height,bounds.height())
                assertTrue(target.height>=SurprideUi(a).dp(88))
                if(step==1) assertFalse("Round trip toggles off",target.isSelected)
                else assertTrue("Chosen option is visibly selected",target.isSelected)
                val footer=buttons(a).single {it.text==if(step==2) "비밀 코스 준비" else "다음 조건"}
                val cta=Rect();assertTrue(footer.getGlobalVisibleRect(cta))
                assertTrue("Last choice needs16dp separation from CTA",bounds.bottom+SurprideUi(a).dp(16)<=cta.top)
            }
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            s.onActivity {a->assertEquals(if(step==2) "비밀 코스 준비" else "다음 조건",(a.currentFocus as Button).text.toString())}
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            s.onActivity {a->assertSame(last(a),a.currentFocus)}
        }
    }
    @Test fun ratingsExposeCheckedMeaningAndKeepKeyboardFocus()=experience(reveal()) {s->
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        s.onActivity {a->
            val rating=buttons(a).single {it.text=="2 점"}
            assertTrue("Rating needs native checkable semantics",rating.createAccessibilityNodeInfo().isCheckable)
            assertTrue(rating.requestFocus())
        }
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
        s.onActivity {a->assertEquals("3 점",(a.currentFocus as Button).text.toString())}
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_SPACE);inst.waitForIdleSync();SystemClock.sleep(300)
        s.onActivity {a->
            val chosen=buttons(a).single {it.text=="3 점"}
            assertSame(chosen,a.currentFocus);assertTrue(chosen.isSelected)
            assertTrue(chosen.createAccessibilityNodeInfo().isChecked)
            assertEquals("만족도 3점, 5점 만점",chosen.contentDescription)
            val targets=(1..5).map {n->buttons(a).single {it.text=="$n 점"}}
            targets.forEach {assertTrue(it.height>=SurprideUi(a).dp(112));assertTrue(it.width>=SurprideUi(a).dp(76))}
            targets.zipWithNext().forEach {(left,right)->assertTrue(right.left-left.right>=SurprideUi(a).dp(16))}
            assertTrue(buttons(a).single {it.text=="평가와 체험 기록 저장"}.isEnabled)
        }
    }
    @Test fun primaryActionsWrapWithin640dpAndKeep104dpHeight()=experience(reveal()) {s->
        s.onActivity {a->val b=buttons(a).single {it.text=="평가를 고르면 저장할 수 있어요"};assertTrue("CTA too wide",b.width<=SurprideUi(a).dp(640));assertTrue(b.height>=SurprideUi(a).dp(104))}
    }
    @Test fun interpretedMoodTimeAndSceneryLeadWithoutRepeatedExtraction()=editor {s->
        interpret(s)
        s.onActivity {a->
            val text=views(a.window.decorView).filterIsInstance<TextView>()
            assertTrue("Mood/time must lead",text.any {it.text.toString().startsWith("힐링 · 90분") && it.text.contains("바다")})
            assertFalse(text.any {it.visibility==View.VISIBLE && it.text.toString().startsWith("이번 문장에서 정리한 조건")})
            assertTrue(buttons(a).any {it.text=="해석 내용 보기"})
            val b=buttons(a).single {it.text=="이 조건으로 계속"};assertTrue(b.width<=SurprideUi(a).dp(640));assertTrue(b.height>=SurprideUi(a).dp(104))
        }
    }
    @Test fun developerControlsStayOutOfNormalDemoAndRemainOptIn() {
        ActivityScenario.launch(MainActivity::class.java).use {s->
            waitFor(s,"드라이브 준비")
            s.onActivity {a->assertFalse("Debug control leaked into demo",a.findViewById<View>(R.id.debug_toggle).isShown)}
            assertTrue(context.getSharedPreferences("appearance",0).edit().putBoolean("developer_tools",true).commit())
            s.recreate();s.onActivity {a->assertTrue(a.findViewById<View>(R.id.debug_toggle).isShown)}
        }
    }
    private fun capture(name:String) {
        inst.waitForIdleSync();SystemClock.sleep(1800)
        val image=android.os.ParcelFileDescriptor.AutoCloseInputStream(inst.uiAutomation.executeShellCommand("screencap -p")).use {checkNotNull(android.graphics.BitmapFactory.decodeStream(it))}
        val prefix=InstrumentationRegistry.getArguments().getString("polish_set")?:"after"
        require(prefix in listOf("before","after","large","dense"))
        val folder=File(context.cacheDir,"phase7c-captures").apply {mkdirs()}
        File(folder,"$prefix-fixture.txt").writeText("density=${context.resources.displayMetrics.densityDpi};font=${context.resources.configuration.fontScale};mode=dark;fixtureTime=1794376800000;healing;180min;roundTrip;seed=42;developerTools=false")
        File(folder,"$prefix-$name.png").outputStream().use {assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};image.recycle()
    }
    @Test fun captureParkedPolishFixturesWithoutRealNavigation() {
        for(step in 0..2) experience(MysteryTripState(panel=ExperiencePanel.TRIP,preferences=conditions,setupStep=step)) {s->
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            s.onActivity {a->last(a).requestFocus()};inst.waitForIdleSync();SystemClock.sleep(400)
            capture("last-step$step")
        }
        experience(reveal()) {s->
            s.onActivity {a->buttons(a).single {it.text=="4 점"}.performClick()};capture("rating-selected")
        }
        editor {s->interpret(s);capture("natural")}
        ActivityScenario.launch(MainActivity::class.java).use {s->waitFor(s,"드라이브 준비");capture("start")}
    }
}
