package com.surpride.navhostpoc.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Looper
import com.surpride.navhostpoc.guidance.RouteCoordinate
import com.surpride.navhostpoc.tracking.PositionFix
import com.surpride.navhostpoc.tracking.PositionSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutorService
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowBitmapFactory
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ViewportCacheLifecycleTest {
    private fun <T> field(value:Any,name:String):T=ReflectionHelpers.getField(value,name)
    private fun call(value:Any,name:String,vararg args:ClassParameter<*>)=ReflectionHelpers.callInstanceMethod<Any?>(value,name,*args)
    private fun want(tiles:Any,key:TileKey) {
        call(tiles,"want",ClassParameter.from(Set::class.java,setOf(key)))
        val deadline=System.nanoTime()+3_000_000_000L
        while(System.nanoTime()<deadline) {
            val empty=synchronized(field<Any>(tiles,"monitor")) {field<Set<*>>(tiles,"pending").isEmpty()}
            if(empty) return
            Thread.sleep(5)
        }
        fail("Viewport worker did not finish")
    }
    @Test fun successfulDecodeStillRevalidatesExpiredMemoryTiles() {
        val bytes=ByteArrayOutputStream().apply {Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        val context=RuntimeEnvironment.getApplication();val renderer=RoadMapRenderer(context) {}
        val tiles=field<Any>(renderer,"tiles");val requests=AtomicInteger();val key=TileKey(2,1,1)
        val directory=File(context.cacheDir,"b5-memory-test").apply {mkdirs()}
        ReflectionHelpers.setField(tiles,"store",TileStore(directory,TileTransport {_,_->
            if(requests.incrementAndGet()==1) TileResponse(200,bytes,mapOf("ETag" to "one","Cache-Control" to "max-age=0"))
            else TileResponse(304,null,mapOf("Cache-Control" to "max-age=3600"))
        }))
        try {
            want(tiles,key);assertEquals(1,requests.get());assertNotNull(call(tiles,"peek",ClassParameter.from(TileKey::class.java,key)))
            want(tiles,key);assertEquals(2,requests.get())
            want(tiles,key);assertEquals(2,requests.get())
        } finally {renderer.close()}
    }
    @Test fun noStoreIsAOneFrameBufferAndNeverPersistsOrLoopsBeforeConsumption() {
        val bytes=ByteArrayOutputStream().apply {Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        val context=RuntimeEnvironment.getApplication();val renderer=RoadMapRenderer(context) {}
        val tiles=field<Any>(renderer,"tiles");val requests=AtomicInteger();val key=TileKey(2,1,1)
        val directory=File(context.cacheDir,"b5-no-store").apply {mkdirs()}
        ReflectionHelpers.setField(tiles,"store",TileStore(directory,TileTransport {_,_->
            requests.incrementAndGet();TileResponse(200,bytes,mapOf("Cache-Control" to "no-store"))
        }))
        try {
            want(tiles,key);want(tiles,key);assertEquals(1,requests.get())
            assertNotNull(call(tiles,"peek",ClassParameter.from(TileKey::class.java,key)))
            assertNull(call(tiles,"peek",ClassParameter.from(TileKey::class.java,key)))
            assertTrue(directory.listFiles()!!.isEmpty())
            want(tiles,key);assertEquals(2,requests.get())
            call(tiles,"want",ClassParameter.from(Set::class.java,emptySet<TileKey>()))
            assertNull(call(tiles,"peek",ClassParameter.from(TileKey::class.java,key)))
        } finally {renderer.close()}
    }
    @Test fun closeCancelsQueuedViewportWorkAndLateWorkersCannotPublishImages() {
        val context=RuntimeEnvironment.getApplication();val renderer=RoadMapRenderer(context) {}
        val tiles=field<Any>(renderer,"tiles");val entered=CountDownLatch(2);val release=CountDownLatch(1)
        val requests=AtomicInteger();val closes=AtomicInteger()
        ReflectionHelpers.setField(tiles,"store",TileStore(File(context.cacheDir,"b5-close"),object:TileTransport {
            override fun fetch(key:TileKey,headers:Map<String,String>):TileResponse {
                requests.incrementAndGet();entered.countDown()
                try {release.await(3,TimeUnit.SECONDS)} catch(_:InterruptedException) {}
                return TileResponse(200,byteArrayOf(1),emptyMap())
            }
            override fun close() {closes.incrementAndGet();release.countDown()}
        }))
        try {
            call(tiles,"want",ClassParameter.from(Set::class.java,(0..99).map {TileKey(8,it,1)}.toSet()))
            assertTrue(entered.await(3,TimeUnit.SECONDS));renderer.close();renderer.close()
            assertTrue(field<ExecutorService>(tiles,"executor").awaitTermination(3,TimeUnit.SECONDS))
            assertEquals(2,requests.get());assertEquals(1,closes.get())
            assertTrue(field<Set<*>>(tiles,"pending").isEmpty());assertTrue(field<Map<*,*>>(tiles,"images").isEmpty())
        } finally {release.countDown();renderer.close()}
    }
    @Test fun failedDecodeRetriesAfterBackoffRegardlessOfTheResponsesLongLifetime() {
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        val valid=ByteArrayOutputStream().apply {Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        val context=RuntimeEnvironment.getApplication();val renderer=RoadMapRenderer(context) {}
        val tiles=field<Any>(renderer,"tiles");val requests=AtomicInteger();val key=TileKey(2,1,1)
        ReflectionHelpers.setField(tiles,"store",TileStore(File(context.cacheDir,"b5-decode-retry"),TileTransport {_,_->
            TileResponse(200,if(requests.incrementAndGet()==1) byteArrayOf(1) else valid,mapOf("Cache-Control" to "max-age=604800"))
        }))
        try {
            want(tiles,key);want(tiles,key);assertEquals(1,requests.get())
            val image=field<Map<*,*>>(tiles,"images")[key]!!
            ReflectionHelpers.setField(image,"loadedAt",System.currentTimeMillis()-31_000)
            want(tiles,key);assertEquals(2,requests.get())
            assertNotNull(field<Any?>(field<Map<*,*>>(tiles,"images")[key]!!,"bitmap"))
        } finally {renderer.close();ShadowBitmapFactory.setAllowInvalidImageData(true)}
    }
    @Test fun immediatelyExpiredTilesDoNotDriveTheirOwnRendererRepaintRequestLoop() {
        val valid=ByteArrayOutputStream().apply {Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray()
        val context=RuntimeEnvironment.getApplication();lateinit var paint:()->Unit
        val renderer=RoadMapRenderer(context) {paint()};val tiles=field<Any>(renderer,"tiles");val requests=AtomicInteger()
        ReflectionHelpers.setField(tiles,"store",TileStore(File(context.cacheDir,"b5-repaint-loop"),TileTransport {_,_->
            requests.incrementAndGet();TileResponse(200,valid,mapOf("Cache-Control" to "no-cache, max-age=0"))
        }))
        val canvas=Canvas(Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888))
        val fix=PositionFix(RouteCoordinate(0.0,0.0),5.0,1000,1000,0.0,null,null,PositionSource.FAKE,false)
        paint={renderer.render(canvas,Rect(0,0,256,256),null,fix,null,"synthetic")}
        fun idle() {
            val deadline=System.nanoTime()+3_000_000_000L
            while(System.nanoTime()<deadline) {
                if(synchronized(field<Any>(tiles,"monitor")) {field<Set<*>>(tiles,"pending").isEmpty()}) return
                Thread.sleep(5)
            }
            fail("Viewport worker did not finish")
        }
        try {
            paint();idle();val initial=requests.get();assertTrue(initial>0)
            repeat(8) {Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));idle()}
            assertTrue("Stationary completion repaints must stop fetching",requests.get()<=initial*2)
        } finally {renderer.close()}
    }
}
