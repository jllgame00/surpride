package com.surpride.navhostpoc

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.Maneuver
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.TimeZone

private const val TAG = "SurprideNavPoc"

/** Host rendering spike: no GPS, real destination, route, audio or navigation focus. */
class MockNavigationService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.Builder(this).build()

    override fun onCreateSession(): Session {
        Log.i(TAG, "SESSION_CREATED_STATIC_ONLY")
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                Log.i(TAG, "HOST_CONNECTED carApi=${carContext.carAppApiLevel}")
                return MockNavigationScreen(carContext)
            }
        }
    }
}

private class MockNavigationScreen(context: CarContext) : Screen(context) {
    private val emulator = Build.MODEL == "Connect on x86_64 emulator" && Build.PRODUCT == "crp_avd"
    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            val surface = surfaceContainer.surface ?: return
            if (!surface.isValid) return
            val canvas = surface.lockCanvas(null)
            try {
                canvas.drawColor(Color.rgb(20, 30, 40))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f }
                // Blank schematic surface, never an actual map/road or a route.
                canvas.drawText("MOCK ONLY / NO GPS / NO ROUTE", 40f, canvas.height * 0.60f, paint)
            } finally { surface.unlockCanvasAndPost(canvas) }
            Log.i(TAG, "STATIC_SURFACE_DRAWN")
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                if (emulator) carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
            }
            override fun onDestroy(owner: LifecycleOwner) {
                if (emulator) carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
                Log.i(TAG, "SCREEN_DESTROYED")
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (!emulator) {
            return androidx.car.app.model.MessageTemplate.Builder("MOCK PoC: Pleos emulator only")
                .setHeaderAction(Action.APP_ICON).build()
        }
        val icon = CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_poc)).build()
        val step = Step.Builder("MOCK · 300m 앞 우회전 · 실제 안내 아님")
            .setManeuver(Maneuver.Builder(Maneuver.TYPE_TURN_NORMAL_RIGHT).setIcon(icon).build())
            .build()
        val estimate = TravelEstimate.Builder(
            Distance.create(2.0, Distance.UNIT_KILOMETERS),
            DateTimeWithZone.create(System.currentTimeMillis() + 600_000, TimeZone.getDefault())
        ).setRemainingTimeSeconds(600).build()
        Log.i(TAG, "NAVIGATION_TEMPLATE_STATIC_MOCK")
        return NavigationTemplate.Builder()
            .setNavigationInfo(RoutingInfo.Builder().setCurrentStep(step, Distance.create(300.0, Distance.UNIT_METERS)).build())
            .setDestinationTravelEstimate(estimate)
            .setActionStrip(ActionStrip.Builder()
                .addAction(Action.Builder().setTitle("설명").setOnClickListener {
                    screenManager.push(ExplanationScreen(carContext))
                }.build())
                .build())
            .build()
    }
}

private class ExplanationScreen(context: CarContext) : Screen(context) {
    override fun onGetTemplate(): Template {
        Log.i(TAG, "EXPLANATION_TEMPLATE")
        return androidx.car.app.model.MessageTemplate.Builder("MOCK · 템플릿 표시 실험\n실제 위치·목적지·경로·음성 없음")
            .setHeaderAction(Action.BACK)
            .addAction(Action.Builder().setTitle("PoC 종료").setOnClickListener {
                Log.i(TAG, "FINISH_REQUESTED_NO_ROUTE")
                carContext.finishCarApp()
            }.build())
            .build()
    }
}
