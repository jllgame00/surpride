package com.surpride.app.realdata

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException

/** URI grants cannot outlive the private packet's one-hour read window. */
class NavigationFileProvider:FileProvider() {
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if(mode!="r" || uri.scheme!="content" || uri.authority!=PreparedNavigationHandoff.AUTHORITY ||
            uri.pathSegments.size!=2 || uri.pathSegments[0]!="prepared" ||
            !uri.pathSegments[1].matches(Regex("[0-9a-f-]{36}\\.json"))) throw FileNotFoundException("Invalid prepared route URI")
        val file=File(context!!.cacheDir,"prepared_routes/${uri.pathSegments[1]}")
        if(!file.isFile || System.currentTimeMillis()-file.lastModified() !in 0..PreparedRouteContract.TTL_MILLIS) {
            file.delete()
            throw FileNotFoundException("Prepared route expired")
        }
        return super.openFile(uri,mode)?:throw FileNotFoundException("Prepared route unavailable")
    }
    override fun getType(uri:Uri)="application/json"
}
