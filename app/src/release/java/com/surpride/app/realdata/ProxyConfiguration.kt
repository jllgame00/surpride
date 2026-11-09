package com.surpride.app.realdata

import android.content.Context

/** Local HTTP and developer credentials are unavailable in release builds. */
object ProxyConfiguration {
    fun load(context:Context):ProxySettings?=null
}
