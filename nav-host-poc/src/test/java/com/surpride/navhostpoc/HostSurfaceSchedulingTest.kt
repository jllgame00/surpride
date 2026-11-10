package com.surpride.navhostpoc

import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class HostSurfaceSchedulingTest {
    private val context=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
    private fun screen():ActiveMysteryScreen {
        ReflectionHelpers.setStaticField(Build::class.java,"MODEL","Connect on x86_64 emulator")
        ReflectionHelpers.setStaticField(Build::class.java,"PRODUCT","crp_avd")
        return ActiveMysteryScreen(context,null)
    }
    private fun <T> field(target:Any,key:String):T=ReflectionHelpers.getField(target,key)
    private fun refresh(screen:ActiveMysteryScreen,immediate:Boolean)=ReflectionHelpers.callInstanceMethod<Any?>(screen,"refresh",ClassParameter.from(Boolean::class.javaPrimitiveType!!,immediate))
    private fun container()=SurfaceContainer(Surface(SurfaceTexture(0)),1200,800,160)
    @Test fun immediateRefreshPromotesAnAlreadyScheduledNormalUpdate() {
        val screen=screen();ReflectionHelpers.setField(screen,"foreground",true)
        val now=SystemClock.elapsedRealtime();ReflectionHelpers.setField(screen,"lastRefresh",now-100)
        refresh(screen,false);refresh(screen,true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(now,field<Long>(screen,"lastRefresh"))
    }
    @Test fun aSurfaceDeliveredAfterStopIsReleasedAndCannotReattach() {
        val screen=screen();val lifecycle=ScreenController(screen).moveToState(Lifecycle.State.STARTED)
        val callback=field<SurfaceCallback>(screen,"surfaces")
        lifecycle.moveToState(Lifecycle.State.CREATED)
        val late=container();callback.onSurfaceAvailable(late)
        assertNull(field<SurfaceContainer?>(screen,"surface"))
        assertFalse(late.surface!!.isValid)
        lifecycle.moveToState(Lifecycle.State.DESTROYED)
    }
    @Test fun replacingAndStoppingReleasesEveryReceivedSurfaceHandle() {
        val screen=screen();val lifecycle=ScreenController(screen).moveToState(Lifecycle.State.STARTED)
        val callback=field<SurfaceCallback>(screen,"surfaces");val first=container();val second=container()
        callback.onSurfaceAvailable(first);callback.onSurfaceAvailable(second)
        assertFalse(first.surface!!.isValid)
        lifecycle.moveToState(Lifecycle.State.CREATED)
        assertFalse(second.surface!!.isValid)
        lifecycle.moveToState(Lifecycle.State.DESTROYED)
    }
    @Test fun callbacksFromPreviousLifetimeCannotClearTheNewSurface() {
        val screen=screen();val lifecycle=ScreenController(screen).moveToState(Lifecycle.State.STARTED)
        val old=field<SurfaceCallback>(screen,"surfaces");val first=container();old.onSurfaceAvailable(first)
        lifecycle.moveToState(Lifecycle.State.CREATED).moveToState(Lifecycle.State.STARTED)
        val current=field<SurfaceCallback>(screen,"surfaces");val second=container();current.onSurfaceAvailable(second)
        old.onSurfaceDestroyed(first)
        assertSame(second,field<SurfaceContainer?>(screen,"surface"))
        lifecycle.moveToState(Lifecycle.State.DESTROYED)
    }
    @Test fun staticFallbackAlsoReleasesItsReceivedSurfaceHandle() {
        screen() // emulator build fields
        val constructor=Class.forName("com.surpride.navhostpoc.MockNavigationScreen").getDeclaredConstructor(androidx.car.app.CarContext::class.java)
        constructor.isAccessible=true
        val fallback=constructor.newInstance(context) as androidx.car.app.Screen
        val lifecycle=ScreenController(fallback).moveToState(Lifecycle.State.STARTED)
        val container=container()
        field<SurfaceCallback>(fallback,"surfaceCallback").onSurfaceAvailable(container)
        assertFalse(container.surface!!.isValid)
        lifecycle.moveToState(Lifecycle.State.DESTROYED)
    }

}
