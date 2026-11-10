package com.surpride.app

import android.content.Intent
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.experience.core.DrivePreferences
import com.surpride.app.experience.ExperienceActivity
import com.surpride.app.experience.DemoStateStore
import com.surpride.app.experience.core.*
import com.surpride.app.moodinput.MoodInputActivity
import com.surpride.app.moodinput.PreferenceInputContract
import com.surpride.app.ui.SurprideUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android views; these checks do not claim TalkBack speech or Korean IME composition. */
@RunWith(AndroidJUnit4::class)
class AutomotiveUiValidationTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun editor()=ActivityScenario.launch<MoodInputActivity>(
        Intent(instrumentation.targetContext,MoodInputActivity::class.java)
            .putExtra(PreferenceInputContract.EXTRA,PreferenceInputContract.encode(DrivePreferences()))).also {scenario->
                scenario.onActivity {activity->
                    val config=activity.resources.configuration
                    println("UI_CONFIG fontScale=${config.fontScale} densityDpi=${config.densityDpi} widthDp=${config.screenWidthDp} heightDp=${config.screenHeightDp}")
                }
            }
    private fun descendants(view:View):List<View> = listOf(view)+
        if(view is ViewGroup) (0 until view.childCount).flatMap {descendants(view.getChildAt(it))} else emptyList()

    @Test fun koreanMultilineInputSurvivesRecreationWithoutApplyingUnreviewedText() {
        editor().use {scenario->
            val sentence="강을 보며 쉬고 싶어. 두 시간 동안 혼자 갈 거야.\n집으로 돌아와야 해."
            scenario.onActivity {activity->descendants(activity.window.decorView).filterIsInstance<EditText>().single().setText(sentence)}
            scenario.recreate()
            scenario.onActivity {activity->
                val views=descendants(activity.window.decorView)
                assertEquals(sentence,views.filterIsInstance<EditText>().single().text.toString())
                assertTrue(views.filterIsInstance<Button>().any {it.text.contains("문장을 해석하거나") && !it.isEnabled})
            }
        }
    }

    @Test fun longInputKeepsTheExistingThousandCharacterBoundary() {
        editor().use {scenario->scenario.onActivity {activity->
            val input=descendants(activity.window.decorView).filterIsInstance<EditText>().single()
            input.setText("가".repeat(1100))
            assertEquals(1000,input.length())
            assertEquals("기분과 드라이브 조건 입력",input.contentDescription.toString())
        }}
    }

    @Test fun emptyInputDoesNotEnableApplyAfterRecreation() {
        editor().use {scenario->
            scenario.onActivity {activity->descendants(activity.window.decorView).filterIsInstance<EditText>().single().setText("")}
            scenario.recreate()
            scenario.onActivity {activity->
                assertTrue(descendants(activity.window.decorView).filterIsInstance<Button>()
                    .any {it.text.contains("문장을 해석하거나") && !it.isEnabled})
            }
        }
    }

    @Test fun actionAndSelectionRemainNamedFocusableAndDistinctFromDisabled() {
        instrumentation.runOnMainSync {
            val ui=SurprideUi(instrumentation.targetContext)
            val button=ui.button("기분 선택") {}
            ui.styleButton(button,selected=true)
            assertTrue(button.isSelected)
            assertTrue(button.isFocusable)
            assertEquals("기분 선택",button.text.toString())
            assertEquals("선택됨",ViewCompat.getStateDescription(button).toString())
            button.isEnabled=false
            assertFalse(button.createAccessibilityNodeInfo().isEnabled)
        }
    }

    @Test fun enlargedKoreanActionWrapsWithoutEllipsisAndKeepsTouchTarget() {
        instrumentation.runOnMainSync {
            for(scale in listOf(1f,1.3f,2f)) {
                val context=instrumentation.targetContext.createConfigurationContext(
                    Configuration(instrumentation.targetContext.resources.configuration).apply {fontScale=scale})
                val ui=SurprideUi(context)
                val button=ui.button("목적지를 지금 공개하고 안내 종료") {}
                button.measure(View.MeasureSpec.makeMeasureSpec(ui.dp(260),View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                button.layout(0,0,button.measuredWidth,button.measuredHeight)
                assertTrue(button.measuredHeight>=ui.dp(76))
                assertTrue("scale=$scale singleLine=${button.isSingleLine} lines=${button.layout.lineCount}",button.layout.lineCount>1)
                assertEquals(0,(0 until button.layout.lineCount).sumOf {button.layout.getEllipsisCount(it)})
                assertTrue(button.layout.height<=button.height-button.compoundPaddingTop-button.compoundPaddingBottom)
            }
        }
    }

    @Test fun headingsExposeSemanticReadingBoundaries() {
        instrumentation.runOnMainSync {
            val ui=SurprideUi(instrumentation.targetContext)
            val heading:TextView=ui.text("출발 전 확인",28,heading=true)
            assertTrue(ViewCompat.isAccessibilityHeading(heading))
            assertFalse(ViewCompat.isAccessibilityHeading(ui.text("주차 정보는 미확인입니다")))
        }
    }

    @Test fun editorRequestsKeyboardSpaceForTheConditionActions() {
        editor().use {scenario->scenario.onActivity {activity->
            val input=descendants(activity.window.decorView).filterIsInstance<EditText>().single()
            val info=EditorInfo()
            assertNotNull(input.onCreateInputConnection(info))
            assertTrue(info.imeOptions and EditorInfo.IME_FLAG_NO_FULLSCREEN!=0)
        }}
    }

    @Test fun moodSelectionKeepsKeyboardFocusAfterRerender() {
        ActivityScenario.launch(ExperienceActivity::class.java).use {scenario->
            fun button(label:String):Button {
                var result:Button?=null
                val deadline=SystemClock.uptimeMillis()+10_000
                while(result==null && SystemClock.uptimeMillis()<deadline) {
                    scenario.onActivity {activity->result=descendants(activity.window.decorView).filterIsInstance<Button>()
                        .firstOrNull {it.text.toString()==label && it.isEnabled &&
                            descendants(activity.window.decorView).filterIsInstance<TextView>().any {view->view.text.toString()=="정차 중 이용"}}}
                    if(result==null) SystemClock.sleep(100)
                }
                return checkNotNull(result) {"Parked control unavailable: $label"}
            }
            var control=button("시작")
            scenario.onActivity {control.performClick()}
            control=button("드라이브 조건 선택")
            scenario.onActivity {control.performClick()}
            control=button("설렘")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.onActivity {activity->
                assertTrue(control.requestFocus())
                control.performClick()
                assertEquals("설렘",(activity.currentFocus as? Button)?.text?.toString())
            }
            control=button("다음 조건")
            scenario.onActivity {control.performClick()}
            scenario.onActivity {activity->
                val toggle=descendants(activity.window.decorView).filterIsInstance<Button>()
                    .first {it.text.startsWith("왕복 ·") || it.text.startsWith("편도 ·")}
                val expected=if(toggle.text.startsWith("왕복")) "편도 · 도착지에서 마무리" else "왕복 · 출발지로 돌아오기"
                assertTrue(toggle.requestFocus())
                toggle.performClick()
                assertEquals(expected,(activity.currentFocus as? Button)?.text?.toString())
            }
        }
    }

    @Test fun repeatedRecordLabelsKeepFocusOnTheInvokedRecord() {
        val store=DemoStateStore(instrumentation.targetContext)
        val original=store.load()
        val history=listOf("focus-a","focus-b").mapIndexed {index,id->
            DemoVisit(id,DemoCatalog.candidates[index].id,Mood.HEALING,4,System.currentTimeMillis(),true)
        }
        try {
            store.save(MysteryTripState(panel=ExperiencePanel.MAP,history=history))
            ActivityScenario.launch(ExperienceActivity::class.java).use {scenario->
                var ready=false
                val deadline=SystemClock.uptimeMillis()+10_000
                while(!ready && SystemClock.uptimeMillis()<deadline) {
                    scenario.onActivity {activity->
                        val views=descendants(activity.window.decorView)
                        ready=views.filterIsInstance<Button>().count {b->b.text.toString()=="기록 자세히 보기"}>=2 &&
                            history.all {visit->views.filterIsInstance<TextView>().any {view->view.text.toString()==DemoCatalog.candidates.first {it.id==visit.candidateId}.name}}
                    }
                    if(!ready) SystemClock.sleep(100)
                }
                assertTrue(ready)
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
                scenario.onActivity {activity->
                    val records=descendants(activity.window.decorView).filterIsInstance<Button>().filter {it.text.toString()=="기록 자세히 보기"}.takeLast(2)
                    assertTrue(records[1].requestFocus())
                    records[1].performClick()
                }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                instrumentation.waitForIdleSync()
                scenario.onActivity {activity->
                    val records=descendants(activity.window.decorView).filterIsInstance<Button>().filter {it.text.toString()=="기록 자세히 보기"}.takeLast(2)
                    assertSame(records[1],activity.currentFocus)
                }
            }
        } finally {
            if(original==null) store.reset() else store.save(original)
        }
    }
}
