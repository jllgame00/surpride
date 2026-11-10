package com.surpride.app.realdata

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Provision with backend.dev through run-as stdin. Never read external storage, intent or APK keys. */
object ProxyConfiguration {
    fun load(context:Context):ProxySettings? {
        val file=File(context.cacheDir,"route-proxy-session.json")
        return try {
        require(file.length() in 1..4096)
        val json=JSONObject(file.readText())
        ProxySettings(json.getString("base_url"),json.getString("token"),json.getLong("expires_at_ms"))
            .also {require(it.expiresAtMillis>System.currentTimeMillis())}
        } catch(_:Exception) {
            if(file.exists() && !file.delete()) android.util.Log.w("SurprideRetention","CREDENTIAL_CLEANUP_FAILED")
            null
        }
    }
}
