package com.surpride.navhostpoc.tracking

import com.surpride.navhostpoc.guidance.RouteCoordinate
import org.junit.Assert.*
import org.junit.Test

class LocationSessionTest {
    @Test fun fakeSubscriptionIsUniqueAndLateCallbacksAreIgnoredAfterPauseOrRestart() {
        val fake=FakeLocationProvider();val results=mutableListOf<PositionEvent>()
        val session=PositionSession(fake){results.add(it)}
        session.start();session.start();assertEquals(1,fake.subscriberCount)
        val late=fake.captureCallback()
        fake.emit(PositionEvent.Unavailable(PositionFailure.NO_DATA));assertEquals(1,results.size)
        session.stop();assertEquals(0,fake.subscriberCount)
        late?.invoke(PositionEvent.Unavailable(PositionFailure.NO_DATA));assertEquals(1,results.size)
        session.start();late?.invoke(PositionEvent.Unavailable(PositionFailure.PERMISSION_DENIED));assertEquals(1,results.size)
        fake.emit(PositionEvent.Unavailable(PositionFailure.PERMISSION_DENIED));assertEquals(2,results.size)
    }
    @Test fun permissionFailureAndProviderFailureAreDistinctAndNoCoordinatesAreLogged() {
        val fake=FakeLocationProvider();val events=mutableListOf<PositionEvent>()
        val session=PositionSession(fake){events.add(it)};session.start()
        fake.emit(PositionEvent.Unavailable(PositionFailure.PERMISSION_DENIED))
        fake.emit(PositionEvent.Unavailable(PositionFailure.NO_PROVIDER))
        assertNotEquals(events[0],events[1])
        val fix=PositionFix(RouteCoordinate(127.0,37.0),5.0,100,123,null,null,null,PositionSource.FAKE,false)
        assertFalse(fix.toString().contains("127.0"));session.stop()
    }
}
