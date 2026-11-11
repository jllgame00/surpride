package com.surpride.app

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.ui.SurprideUi
import com.surpride.app.experience.ExperienceArt
import com.surpride.app.experience.core.DemoVisit
import com.surpride.app.experience.core.Mood
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MonochromeUiDeviceTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()

    @Test fun bothModesHaveNeutralReadableTextAndPrimaryActions() {
        instrumentation.runOnMainSync {
            for(dark in listOf(false,true)) {
                val base=instrumentation.targetContext
                val context=base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                    uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                })
                val ui=SurprideUi(context)
                assertEquals(Color.parseColor(if(dark) "#111214" else "#F0F0EE"),ui.background)
                assertEquals(Color.parseColor(if(dark) "#F2F2F2" else "#1C1C1C"),ui.ink)
                for(color in listOf(ui.surface,ui.raised,ui.accent,ui.accentFill,ui.muted,ui.divider)) {
                    val channels=listOf(Color.red(color),Color.green(color),Color.blue(color))
                    assertTrue("Non-neutral token: ${Integer.toHexString(color)}",channels.max()-channels.min()<=4)
                }
                assertTrue(ColorUtils.calculateContrast(ui.ink,ui.surface)>=4.5)
                assertTrue(ColorUtils.calculateContrast(ui.muted,ui.surface)>=4.5)
                assertTrue(ColorUtils.calculateContrast(ui.onAccent,ui.accentFill)>=4.5)
                val selected=ui.button("선택 상태") {};ui.styleButton(selected,selected=true)
                assertTrue(ColorUtils.calculateContrast(selected.currentTextColor,ui.raised)>=4.5)
                val primary=ui.button("계속",true) {}
                assertTrue(ColorUtils.calculateContrast(primary.currentTextColor,ui.accentFill)>=4.5)
                assertTrue(primary.minimumHeight>=ui.dp(76))
            }
        }
    }

    @Test fun controlsAreFlatAndSectionsDoNotNestRoundedBoxes() {
        instrumentation.runOnMainSync {
            val ui=SurprideUi(instrumentation.targetContext)
            val button=ui.button("조건 선택") {}
            val states=(button.background as RippleDrawable).getDrawable(0) as StateListDrawable
            for(i in 0 until states.stateCount) {
                val shape=states.getStateDrawable(i) as GradientDrawable
                assertTrue("Corner exceeds 6dp",shape.cornerRadius<=ui.dp(6))
            }
            assertEquals(0f,button.elevation)
            assertNull(button.stateListAnimator)
            val section=ui.card()
            assertNull("Sections use dividers rather than enclosing cards",section.background)
            assertTrue(section.childCount>0)
            assertEquals(ui.dp(1),section.getChildAt(0).layoutParams.height)
        }
    }

    @Test fun primaryFocusOutlineContrastsWithItsFillInBothModes() {
        instrumentation.runOnMainSync {
            for(dark in listOf(false,true)) {
                val base=instrumentation.targetContext
                val context=base.createConfigurationContext(Configuration(base.resources.configuration).apply {
                    uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                })
                val ui=SurprideUi(context)
                val background=ui.button("계속",true) {}.background
                background.state=intArrayOf(android.R.attr.state_enabled,android.R.attr.state_focused)
                val image=Bitmap.createBitmap(ui.dp(200),ui.dp(100),Bitmap.Config.ARGB_8888)
                background.setBounds(0,0,image.width,image.height)
                background.draw(Canvas(image))
                assertTrue("Primary keyboard focus must be visible, dark=$dark",
                    ColorUtils.calculateContrast(image.getPixel(ui.dp(1),image.height/2),ui.accentFill)>=3.0)
                image.recycle()
            }
        }
    }

    @Test fun historyLabelsClearTheCountAtDoubleTextScale() {
        instrumentation.runOnMainSync {
            val base=instrumentation.targetContext
            val context=base.createConfigurationContext(Configuration(base.resources.configuration).apply {fontScale=2f})
            val ui=SurprideUi(context)
            val art=ExperienceArt(context,listOf(DemoVisit("layout-test","fixture",Mood.HEALING,4,0,true)))
            val image=Bitmap.createBitmap(ui.dp(600),ui.dp(600),Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image);canvas.drawColor(ui.background)
            art.layout(0,0,image.width,image.height);art.draw(canvas)
            var countBottom=-1;var labelTop=image.height
            for(y in 0 until image.height/2) for(x in 0 until image.width/3) {
                val pixel=image.getPixel(x,y)
                if(pixel==ui.ink) countBottom=maxOf(countBottom,y)
                if(pixel==ui.muted) labelTop=minOf(labelTop,y)
            }
            assertTrue("Count was rendered",countBottom>=0)
            assertTrue("Count and label overlap: $countBottom / $labelTop",labelTop>=countBottom+ui.dp(8))
            image.recycle()
        }
    }
}
