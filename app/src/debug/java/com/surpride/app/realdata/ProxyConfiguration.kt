package com.surpride.app.realdata

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Provision with backend.dev through run-as stdin. Never read external storage, intent or APK keys. */
object ProxyConfiguration {
    fun load(context:Context):ProxySettings?=try {
        val file=File(context.cacheDir,"route-proxy-session.json")
        require(file.length() in 1..4096)
        val json=JSONObject(file.readText())
        ProxySettings(json.getString("base_url"),json.getString("token"),json.getLong("expires_at_ms"))
            .takeIf {it.expiresAtMillis>System.currentTimeMillis()}
    } catch(_:Exception) {null}
}
