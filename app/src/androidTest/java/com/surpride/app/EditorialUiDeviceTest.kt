package com.surpride.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.ui.SurprideUi
import com.surpride.app.experience.ExperienceActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorialUiDeviceTest {
    @Test fun primaryIsLargeAndSecondaryDoesNotCreateAnotherPanel() {
        val inst=InstrumentationRegistry.getInstrumentation()
        inst.runOnMainSync {
            val ui=SurprideUi(inst.targetContext)
            val primary=ui.button("계속",true) {}
            assertTrue("Primary touch height >=96dp",primary.minimumHeight>=ui.dp(96))
            val metrics=inst.targetContext.resources.displayMetrics
            fun sp(value:Float)=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,value,metrics)
            assertTrue("Primary text >=30sp with platform font scaling",primary.textSize>=sp(30f))
            val secondary=ui.button("기록 보기") {}
            assertTrue(secondary.textSize>=sp(28f))
            val image=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
            secondary.background.state=intArrayOf(android.R.attr.state_enabled)
            secondary.background.setBounds(0,0,200,100);secondary.background.draw(Canvas(image))
            assertEquals("Quiet actions have no enclosing panel",0,Color.alpha(image.getPixel(100,50)))
            image.recycle()
        }
    }

    @Test fun topNavigationCanBeSeenAndFocusedInTheRealViewport() {
        val inst=InstrumentationRegistry.getInstrumentation()
        fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap {views(v.getChildAt(it))} else emptyList()
        ActivityScenario.launch(ExperienceActivity::class.java).use {scenario->
            val end=SystemClock.uptimeMillis()+10_000;var ready=false
            while(!ready && SystemClock.uptimeMillis()<end) {
                scenario.onActivity {a->ready=views(a.window.decorView).filterIsInstance<TextView>().any {it.text=="정차 중 이용"}}
                if(!ready)SystemClock.sleep(100)
            }
            assertTrue(ready)
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            for(label in listOf("시작","코스","기분 기록","첫 화면")) {
                scenario.onActivity {a->
                    val button=views(a.window.decorView).filterIsInstance<Button>().single {it.text==label}
                    assertTrue(button.requestFocus())
                }
                inst.waitForIdleSync();SystemClock.sleep(400)
                scenario.onActivity {a->
                    val button=views(a.window.decorView).filterIsInstance<Button>().single {it.text==label}
                    val bounds=Rect()
                    assertTrue("Navigation clipped: $label",button.getGlobalVisibleRect(bounds))
                    assertTrue("Navigation target too narrow: $label",bounds.width()>=SurprideUi(a).dp(76))
                }
            }
        }
    }
}
