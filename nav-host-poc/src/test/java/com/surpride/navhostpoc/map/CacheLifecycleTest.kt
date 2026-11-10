package com.surpride.navhostpoc.map

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CacheLifecycleTest {
    @get:Rule val temp=TemporaryFolder()
    private val key=TileKey(2,1,1)
    @Test fun noCacheRequiresValidationEvenWhenMaxAgeIsPositive() {
        var requests=0
        val store=TileStore(temp.root,TileTransport {_,_->requests++;TileResponse(200,byteArrayOf(1),mapOf("Cache-Control" to "no-cache, max-age=600"))})
        store.load(key,1000);store.load(key,1001);assertEquals(2,requests)
    }
    @Test fun aNewTwoHundredResponseDoesNotInheritOldValidators() {
        var requests=0
        val store=TileStore(temp.root,TileTransport {_,headers->when(++requests) {
            1->TileResponse(200,byteArrayOf(1),mapOf("ETag" to "old","Last-Modified" to "old-date","Cache-Control" to "max-age=0"))
            2->TileResponse(200,byteArrayOf(2),mapOf("Cache-Control" to "max-age=0"))
            else->{assertNull(headers["If-None-Match"]);assertNull(headers["If-Modified-Since"]);TileResponse(200,byteArrayOf(3),emptyMap())}
        }})
        store.load(key,1000);store.load(key,1001);store.load(key,1002);assertEquals(3,requests)
    }
    @Test fun threeOhFourWithoutNewCachePolicyRetainsTheOriginalLifetime() {
        var requests=0
        val store=TileStore(temp.root,TileTransport {_,_->if(++requests==1)
            TileResponse(200,byteArrayOf(1),mapOf("ETag" to "v1","Cache-Control" to "max-age=10"))
            else TileResponse(304,null,emptyMap())})
        store.load(key,1000);store.load(key,12000);store.load(key,23000)
        assertEquals(3,requests)
    }
    @Test fun noStoreRemovesAnOlderPersistentBodyAndMetadata() {
        var requests=0
        val store=TileStore(temp.root,TileTransport {_,_->if(++requests==1)
            TileResponse(200,byteArrayOf(1),mapOf("Cache-Control" to "max-age=0"))
            else TileResponse(200,byteArrayOf(2),mapOf("Cache-Control" to "no-store"))})
        store.load(key,1000);assertTrue(temp.root.listFiles()!!.isNotEmpty())
        assertArrayEquals(byteArrayOf(2),store.load(key,1001).bytes)
        assertTrue(temp.root.listFiles()!!.isEmpty())
    }
    @Test fun oldExpiredDiskEntriesArePrunedButServerFreshEntriesSurvive() {
        fun entry(stem:String,expires:Long) {
            File(temp.root,"$stem.png").apply {writeBytes(byteArrayOf(1));setLastModified(1)}
            File(temp.root,"$stem.meta").apply {
                outputStream().use {Properties().apply {setProperty("expires",expires.toString())}.store(it,null)}
                setLastModified(1)
            }
        }
        entry("2_0_0",0);entry("2_0_1",365L*86400000)
        val store=TileStore(temp.root,TileTransport {_,_->TileResponse(200,byteArrayOf(2),emptyMap())})
        store.load(key,40L*86400000)
        assertFalse(File(temp.root,"2_0_0.png").exists());assertFalse(File(temp.root,"2_0_0.meta").exists())
        assertTrue(File(temp.root,"2_0_1.png").exists());assertTrue(File(temp.root,"2_0_1.meta").exists())
    }
    @Test fun ageAndExpiresAreHonoredAndClosingPreventsAnyNewRequest() {
        var requests=0;var closes=0
        val store=TileStore(temp.root,object:TileTransport {
            override fun fetch(key:TileKey,headers:Map<String,String>):TileResponse {
                requests++;return TileResponse(200,byteArrayOf(1),mapOf("Cache-Control" to "max-age=20","Age" to "5"))
            }
            override fun close() {closes++}
        })
        assertEquals(16000L,store.load(key,1000).expiresAtMillis)
        store.load(key,15999);assertEquals(1,requests)
        store.load(key,16000);assertEquals(2,requests)
        store.close();store.close();assertNull(store.load(key,16001).bytes)
        assertEquals(2,requests);assertEquals(1,closes)
        val expires=TileStore(temp.newFolder(),TileTransport {_,_->TileResponse(200,byteArrayOf(1),
            mapOf("Expires" to "Thu, 01 Jan 1970 00:01:00 GMT"))})
        assertEquals(60000L,expires.load(key,1000).expiresAtMillis)
    }
    @Test fun closeDoesNotWaitForThePruningMonitor() {
        val store=TileStore(temp.root,TileTransport {_,_->TileResponse(200,byteArrayOf(1),emptyMap())})
        val entered=CountDownLatch(1);val release=CountDownLatch(1);val executor=Executors.newFixedThreadPool(2)
        try {
            executor.submit {synchronized(store) {entered.countDown();release.await(3,TimeUnit.SECONDS)}}
            assertTrue(entered.await(1,TimeUnit.SECONDS))
            executor.submit {store.close()}.get(1,TimeUnit.SECONDS)
            assertEquals("TILE_STORE_CLOSED",store.load(key).failure)
        } finally {release.countDown();executor.shutdownNow()}
    }
    @Test fun noCacheAndMustRevalidateNeverServeStaleBytesWhenValidationFails() {
        for(policy in listOf("no-cache, max-age=0","must-revalidate, max-age=0")) {
            var requests=0
            val store=TileStore(temp.newFolder(),TileTransport {_,_->
                if(++requests==1) TileResponse(200,byteArrayOf(1),mapOf("Cache-Control" to policy))
                else throw java.io.IOException("synthetic network failure")
            })
            assertNotNull(store.load(key,1000).bytes)
            val failed=store.load(key,1001)
            assertNull(failed.bytes);assertTrue(failed.requiresValidation);assertNotNull(failed.failure)
        }
    }
}
