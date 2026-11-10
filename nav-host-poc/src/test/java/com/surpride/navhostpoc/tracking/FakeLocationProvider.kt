package com.surpride.navhostpoc.tracking

class FakeLocationProvider:PositionProvider {
    private var callback:((PositionEvent)->Unit)?=null
    val subscriberCount get()=if(callback==null) 0 else 1
    override fun subscribe(callback:(PositionEvent)->Unit):PositionSubscription {
        check(this.callback==null);this.callback=callback
        return PositionSubscription {if(this.callback===callback) this.callback=null}
    }
    fun emit(event:PositionEvent) {callback?.invoke(if(event is PositionEvent.Fix) PositionEvent.Fix(event.value.copy(source=PositionSource.FAKE)) else event)}
    fun captureCallback()=callback
}
