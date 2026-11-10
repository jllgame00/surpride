package com.surpride.app.realdata

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.util.Log
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object PreparedNavigationHandoff {
    const val AUTHORITY="com.surpride.app.navigationfiles"
    private const val POC_PACKAGE="com.surpride.navhostpoc"
    enum class Result { LAUNCHED, APP_MISSING, FAILED }
    internal fun remove(context:Context,file:File):Boolean {
        var revoked=true
        try {context.revokeUriPermission(Uri.parse("content://$AUTHORITY/prepared/${file.name}"),Intent.FLAG_GRANT_READ_URI_PERMISSION)}
        catch(_:Exception) {revoked=false}
        val deleted=!file.exists() || file.delete()
        if(!deleted || !revoked) Log.w("SurprideRetention","HANDOFF_CLEANUP_FAILED")
        return deleted && revoked
    }
    internal fun cleanup(context:Context,nonce:String?=null) {
        val now=System.currentTimeMillis()
        File(context.cacheDir,"prepared_routes").listFiles()?.filter {it.name.matches(Regex("[0-9a-f-]{36}\\.json"))}?.forEach {file->
            val packet=try {if(file.length() in 1..PreparedRouteContract.MAX_BYTES.toLong()) org.json.JSONObject(file.readText(Charsets.UTF_8)) else null} catch(_:Exception) {null}
            if(packet==null || now-file.lastModified() !in 0..PreparedRouteContract.TTL_MILLIS ||
                packet.optLong("expires_at_epoch_ms",0)<=now || (nonce!=null && packet.optString("session_nonce")==nonce)) remove(context,file)
        }
    }
    fun launch(activity:Activity,packet:PreparedRouteReply.Success,candidateId:String,nonce:String):Result {
        val now=System.currentTimeMillis()
        if(now-packet.preparedAtMillis !in 0..PreparedRouteContract.TTL_MILLIS) return Result.FAILED
        val target=ComponentName(POC_PACKAGE,"androidx.car.app.activity.CarAppActivity")
        try {
            val info=activity.packageManager.getActivityInfo(target,0)
            if(!info.exported || !info.enabled || !info.applicationInfo.enabled) return Result.APP_MISSING
        } catch(_:PackageManager.NameNotFoundException) {return Result.APP_MISSING}
        val directory=File(activity.cacheDir,"prepared_routes")
        if(!directory.exists() && !directory.mkdirs()) return Result.FAILED
        cleanup(activity)
        val file=File(directory,"${UUID.randomUUID()}.json")
        var granted:android.net.Uri?=null
        return try {
            file.writeText(packet.packet,Charsets.UTF_8)
            if(!file.setLastModified(packet.preparedAtMillis) ||
                !LiveNavigationStore.prepare(activity,PendingLiveNavigation(nonce,candidateId,packet.preparedAtMillis))) {
                file.delete();return Result.FAILED
            }
            val uri=FileProvider.getUriForFile(activity,AUTHORITY,file)
            granted=uri
            val handoff=Intent(Intent.ACTION_VIEW).apply {
                component=target
                data=uri
                clipData=ClipData.newRawUri("Prepared route",uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.grantUriPermission(POC_PACKAGE,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(handoff)
            Result.LAUNCHED
        } catch(_:ActivityNotFoundException) {
            LiveNavigationStore.cancel(activity,nonce);file.delete()
            granted?.let {activity.revokeUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            Result.APP_MISSING
        } catch(_:Exception) {
            LiveNavigationStore.cancel(activity,nonce);file.delete()
            granted?.let {activity.revokeUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            Result.FAILED
        }
    }
}
