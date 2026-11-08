package com.surpride.app

import android.os.Bundle
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.surpride.app.vehicle.ConnectionStatus
import com.surpride.app.vehicle.DataOrigin
import com.surpride.app.vehicle.PleosVehicleDataSource
import com.surpride.app.vehicle.VehicleDataSource
import com.surpride.app.vehicle.VehicleMetric
import com.surpride.app.vehicle.VehicleState
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import com.surpride.app.navigation.NavigationConnection
import com.surpride.app.navigation.NavigationDataSource
import com.surpride.app.navigation.NavigationOrigin
import com.surpride.app.navigation.NavigationState
import com.surpride.app.navigation.PleosNavigationDataSource

/** Startup identity is preserved; the live vehicle diagnostic panel is debug-only. */
class MainActivity : AppCompatActivity() {
    private var vehicleSource: VehicleDataSource? = null
    private var navigationSource: NavigationDataSource? = null
    private val diagnosticsEnabled get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.start_screen)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)

        if (diagnosticsEnabled) {
            findViewById<View>(R.id.vehicle_panel).visibility = View.VISIBLE
            findViewById<View>(R.id.vehicle_refresh).setOnClickListener {
                val source = vehicleSource ?: return@setOnClickListener
                if (source.state.connection == ConnectionStatus.STOPPED || source.state.connection == ConnectionStatus.FAILED) {
                    source.start(::renderVehicle)
                } else source.refresh()
            }
            findViewById<View>(R.id.vehicle_stop).setOnClickListener { stopVehicle() }
            findViewById<View>(R.id.navigation_panel).visibility = View.VISIBLE
            navigationSource = PleosNavigationDataSource(applicationContext).also { it.start(::renderNavigation) }
            findViewById<View>(R.id.navigation_refresh).setOnClickListener {
                navigationSource?.let { source ->
                    if (source.state.connection == NavigationConnection.DISCONNECTED || source.state.connection == NavigationConnection.FAILED) source.start(::renderNavigation)
                    else source.refresh()
                }
            }
            findViewById<View>(R.id.navigation_request).setOnClickListener { navigationSource?.requestTestRoute() }
            findViewById<View>(R.id.navigation_cancel).setOnClickListener { navigationSource?.cancelTestRoute() }
            findViewById<View>(R.id.navigation_show).setOnClickListener { navigationSource?.showNavigation() }
            findViewById<View>(R.id.navigation_history).setOnClickListener { navigationSource?.queryHistory() }
            findViewById<View>(R.id.navigation_stop).setOnClickListener { navigationSource?.stop() }
        }
    }

    override fun onStart() {
        super.onStart()
        if (diagnosticsEnabled) {
            vehicleSource = PleosVehicleDataSource(applicationContext).also { it.start(::renderVehicle) }
        }
    }

    override fun onStop() {
        stopVehicle()
        vehicleSource = null
        super.onStop()
    }

    override fun onDestroy() {
        navigationSource?.stop() // Release listeners only; never implicitly cancel a route.
        navigationSource = null
        super.onDestroy()
    }

    private fun renderNavigation(state: NavigationState) {
        findViewById<TextView>(R.id.navigation_diagnostics).text = buildString {
            appendLine(if (state.origin == NavigationOrigin.PLEOS_SERVICE) "실제 Pleos NaviHelper 3.0.5" else "MOCK · 테스트 데이터")
            appendLine("연결: ${state.connection} · 리스너 ${state.listenerCount}")
            appendLine("경로: ${state.sdkMode} · 활성 ${state.routeActive ?: "미확인"} · 탐색 중 ${state.routeBusy}")
            appendLine("약관 응답: ${state.termsAgreed ?: "미확인"}")
            appendLine("요청 단계: ${state.stage}")
            appendLine("이 세션 테스트 요청: ${state.ownsTestRequest}")
            appendLine("테스트 목적지 일치: ${state.destinationMatchesTest ?: "미확인"}")
            appendLine("남은 시간/거리 (SDK 원시값): ${state.remainTimeRaw ?: "미수신"} / ${state.remainDistanceRaw ?: "미수신"}")
            appendLine("TBT 항목 수: ${state.turnCount ?: "미수신"}")
            appendLine("최근 이력의 테스트 목적지: ${state.historyContainsTest ?: "미조회"}")
            appendLine("비공개 판정: ${state.privacy} · 외부 채널 별도 검증")
            appendLine("오류: ${state.error?.code ?: "없음"} · 정리 오류 ${state.cleanupErrors}")
            appendLine("이벤트: ${state.lastEvent}")
            append(state.recentEvents.joinToString(" → "))
        }
    }

    private fun stopVehicle() {
        vehicleSource?.let { source ->
            source.stop()
            Log.i("SurprideVehicle", "STOPPED subscriptions=${source.state.subscriptions} cleanupErrors=${source.state.cleanupErrors}")
        }
    }

    private fun renderVehicle(state: VehicleState) {
        val origin = if (state.origin == DataOrigin.PLEOS_SERVICE) "실제 Pleos SDK · 에뮬레이터/차량 서비스" else "MOCK · 테스트 데이터"
        val lines = buildString {
            appendLine(origin)
            appendLine("SDK 2.3.2 · 연결: ${state.connection}")
            appendLine("INITIALIZED는 서비스 연결 확인 아님")
            appendLine("구독 ${state.subscriptions} · 수신 콜백 ${state.receivedUpdates} · 정리 오류 ${state.cleanupErrors}")
            appendLine("마지막 SDK 응답: ${state.lastResponseMillis?.let { DateFormat.getTimeInstance().format(Date(it)) } ?: "없음"}")
            VehicleMetric.entries.forEach { metric ->
                val reading = state.readings.getValue(metric)
                val label = when (metric) {
                    VehicleMetric.BATTERY -> "배터리 SOC (%)"
                    VehicleMetric.FUEL -> "연료 (%)"
                    VehicleMetric.ODOMETER -> "누적 주행거리 (SDK 원시 단위)"
                    VehicleMetric.RANGE -> "주행 가능 거리"
                    VehicleMetric.GEAR -> "기어"
                    VehicleMetric.MOTION -> "정차 / 주행"
                }
                val value = reading.value?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"
                appendLine("\n$label: $value · ${reading.status}")
                appendLine("Capability: ${reading.capability}${if (reading.detail.isEmpty()) "" else " · ${reading.detail}"}")
            }
            if (state.connection == ConnectionStatus.STOPPED) appendLine("\n연결 해제됨 · 위 값은 마지막 수신 기록")
        }
        findViewById<TextView>(R.id.vehicle_diagnostics).text = lines
    }
}
