package com.surpride.app.realdata

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException

/** A single open descriptor consumes the private handoff; PoC persists its own atomic copy. */
class NavigationFileProvider:FileProvider() {
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        synchronized(lock) {
        if(mode!="r" || uri.scheme!="content" || uri.authority!=PreparedNavigationHandoff.AUTHORITY ||
            uri.pathSegments.size!=2 || uri.pathSegments[0]!="prepared" ||
            !uri.pathSegments[1].matches(Regex("[0-9a-f-]{36}\\.json"))) throw FileNotFoundException("Invalid prepared route URI")
        val file=File(context!!.cacheDir,"prepared_routes/${uri.pathSegments[1]}")
        if(!file.isFile || System.currentTimeMillis()-file.lastModified() !in 0..PreparedRouteContract.TTL_MILLIS) {
            PreparedNavigationHandoff.remove(context!!,file)
            throw FileNotFoundException("Prepared route expired")
        }
        try {
            require(file.canonicalFile.parentFile==File(context!!.cacheDir,"prepared_routes").canonicalFile)
            require(file.length() in 1..PreparedRouteContract.MAX_BYTES.toLong())
            val json=org.json.JSONObject(file.readText(Charsets.UTF_8))
            val pending=LiveNavigationStore.read(context!!).pending
            val prepared=json.getLong("prepared_at_epoch_ms");val now=System.currentTimeMillis()
            require(pending!=null && pending.nonce==json.getString("session_nonce") && pending.candidateId==json.getString("candidate_id") &&
                pending.preparedAtMillis==prepared && now-prepared in 0..PreparedRouteContract.TTL_MILLIS &&
                json.getLong("expires_at_epoch_ms") in (now+1)..(prepared+PreparedRouteContract.TTL_MILLIS))
            val descriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
            if(!PreparedNavigationHandoff.remove(context!!,file)) {descriptor.close();throw FileNotFoundException("Prepared route cleanup unavailable")}
            return descriptor
        } catch(_:Exception) {throw FileNotFoundException("Prepared route unavailable")}
        }
    }
    override fun getType(uri:Uri)="application/json"
    companion object {private val lock=Any()}
}
