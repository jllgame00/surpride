package com.surpride.app.overlay

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Throwaway debug permission probe. Does not create windows or request routes. */
class OverlayPermissionProbeActivity : Activity() {
    private lateinit var status: TextView
    private var permissionScreen = "NOT_REQUESTED"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionScreen = savedInstanceState?.getString("screen") ?: "NOT_REQUESTED"
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            setBackgroundColor(Color.rgb(16, 24, 32))
        }
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 24f }
        panel.addView(status)
        panel.addView(Button(this).apply {
            text = "일반 오버레이 권한 설정 열기"
            setOnClickListener {
                // API 30+ opens the generic list; do not target hidden Settings components.
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                    permissionScreen = "OPENED"
                } catch (_: ActivityNotFoundException) {
                    permissionScreen = "SETTINGS_UNAVAILABLE"
                } catch (_: SecurityException) {
                    permissionScreen = "SETTINGS_DENIED"
                }
                render()
            }
        })
        panel.addView(Button(this).apply { text = "권한 상태 새로고침"; setOnClickListener { render() } })
        panel.addView(Button(this).apply { text = "진단 종료"; setOnClickListener { finish() } })
        setContentView(panel)
    }

    override fun onResume() { super.onResume(); render() }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("screen", permissionScreen)
        super.onSaveInstanceState(outState)
    }

    private fun render() {
        val allowed = Settings.canDrawOverlays(this)
        status.text = "SURPRIDE · Phase 1C-1 권한 진단\n" +
            "Android API: ${Build.VERSION.SDK_INT}\n" +
            "TYPE_APPLICATION_OVERLAY: ${WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY}\n" +
            "SYSTEM_ALERT_WINDOW: debug Manifest 선언\n" +
            "Settings.canDrawOverlays: $allowed\n" +
            "권한 설정 요청: $permissionScreen\n" +
            "오버레이 생성: 미실시 · 권한 확인 전 실험 중단\n" +
            "주행 검증/목적지 비공개 성공을 뜻하지 않습니다."
        Log.i("SurprideOverlayProbe", "canDrawOverlays=$allowed settings=$permissionScreen overlayCreated=false")
    }
}
