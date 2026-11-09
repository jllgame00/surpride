package com.surpride.navhostpoc.ownership

/** Native ownership can arrive later than the request. Recheck safety before starting the host. */
class ReadinessFocusPort(private val native:FocusPort,private val ready:()->Boolean):FocusPort {
    override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {
        if(!ready()) return false
        return native.request(id,{if(ready()) granted() else lost()},lost)
    }
    override fun abandon(id:Long)=native.abandon(id)
}
