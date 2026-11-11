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
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
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

/** Static entry; route replay and parked GPS experiments require explicit separate-screen selection. No active navigation focus/audio. */
class MockNavigationService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.Builder(this).build()

    override fun onCreateSession(): Session {
        Log.i(TAG, "SESSION_CREATED_STATIC_ONLY")
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                Log.i(TAG, "HOST_CONNECTED carApi=${carContext.carAppApiLevel}")
                return if(intent.data!=null || intent.action==ActiveRoutePacket.RESUME_ACTION) ActiveMysteryScreen(carContext,ActiveRoutePacket.load(carContext,intent)) else MockNavigationScreen(carContext)
            }
            override fun onNewIntent(intent:Intent) {
                if(intent.data!=null || intent.action==ActiveRoutePacket.RESUME_ACTION) {
                    val screens=carContext.getCarService(androidx.car.app.ScreenManager::class.java)
                    screens.popToRoot()
                    screens.push(ActiveMysteryScreen(carContext,ActiveRoutePacket.load(carContext,intent)))
                }
            }
        }
    }
}

private class MockNavigationScreen(context: CarContext) : Screen(context) {
    private val emulator = Build.MODEL == "Connect on x86_64 emulator" && Build.PRODUCT == "crp_avd"
    private var foreground=false
    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            val surface = surfaceContainer.surface ?: return
            try {
            if (!foreground || !surface.isValid) return
            val canvas = try {surface.lockCanvas(null)} catch(_:Exception) {return}
            try {
                canvas.drawColor(Color.rgb(17, 18, 20))
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f }
                // Blank schematic surface, never an actual map/road or a route.
                canvas.drawText("MOCK ONLY / NO GPS / NO ROUTE", 40f, canvas.height * 0.60f, paint)
            } finally { try {surface.unlockCanvasAndPost(canvas)} catch(_:Exception) {} }
            Log.i(TAG, "STATIC_SURFACE_DRAWN")
            } finally {surface.release()}
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground=true
                if (emulator) carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
            }
            override fun onStop(owner: LifecycleOwner) {
                foreground=false
                if (emulator) carContext.getCarService(AppManager::class.java).setSurfaceCallback(null)
            }
            override fun onDestroy(owner: LifecycleOwner) {
                foreground=false
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
                .addAction(Action.Builder().setTitle("실험 선택").setOnClickListener {
                    screenManager.push(ExperimentChooserScreen(carContext) {selection->
                        screenManager.pop()
                        screenManager.push(when(selection) {0->DynamicNavigationScreen(carContext);2->GpsTrackingScreen(carContext);3->OwnershipAuditScreen(carContext);4->ActiveMysteryScreen(carContext);else->ExplanationScreen(carContext)})
                    })
                }.build())
                .build())
            .build()
    }
}

/** Preserve both experiments on hosts that show only the first custom navigation strip action. */
private class ExperimentChooserScreen(context:CarContext,private val choose:(Int)->Unit):Screen(context) {
    override fun onGetTemplate():Template=ListTemplate.Builder().setTitle("SURPRIDE · 개발 실험 선택").setHeaderAction(Action.BACK)
        .setSingleList(ItemList.Builder()
            .addItem(Row.Builder().setTitle("실경로 동적 DEMO · 정차 전용").setOnClickListener {choose(0)}.build())
            .addItem(Row.Builder().setTitle("기존 정적 PoC 설명").setOnClickListener {choose(1)}.build())
            .addItem(Row.Builder().setTitle("GPS 경로 추적 · 정차 실험").setOnClickListener {choose(2)}.build())
            .addItem(Row.Builder().setTitle("소유권·비공개 계약 감사 · 활성화 없음").setOnClickListener {choose(3)}.build())
            .addItem(Row.Builder().setTitle("실제 지도 · 활성 미스터리 안내 · 정차 AVD").setOnClickListener {choose(4)}.build()).build()).build()
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
