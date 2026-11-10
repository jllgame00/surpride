package com.surpride.app

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.app.realdata.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Native provider/descriptor tests in isolated cache and preferences, never real route files. */
@RunWith(AndroidJUnit4::class)
class RetentionDeviceTest {
    private val base get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(action:(Context)->Unit) {
        val name="b5-retention-${UUID.randomUUID()}"
        val root=File(base.cacheDir,name).apply {mkdirs()}
        val context=object:ContextWrapper(base) {
            override fun getCacheDir()=root
            override fun getSharedPreferences(ignored:String,mode:Int)=base.getSharedPreferences(name,mode)
        }
        try {action(context)} finally {
            root.walkBottomUp().forEach {assertTrue(it.delete())};assertTrue(base.deleteSharedPreferences(name))
        }
    }
    private val nonce="00000000-0000-4000-8000-000000000001"
    private val candidate get()=SeoulPilotCatalog.places.first().id
    private fun packet(context:Context,token:String=nonce):Pair<File,Uri> {
        val now=LiveNavigationStore.read(context).pending!!.preparedAtMillis
        val file=File(File(context.cacheDir,"prepared_routes").apply {mkdirs()},"${UUID.randomUUID()}.json")
        file.writeText(JSONObject().put("session_nonce",token).put("candidate_id",candidate)
            .put("prepared_at_epoch_ms",now).put("expires_at_epoch_ms",now+PreparedRouteContract.TTL_MILLIS)
            .put("geometry","synthetic-private-body").toString())
        return file to Uri.parse("content://${PreparedNavigationHandoff.AUTHORITY}/prepared/${file.name}")
    }
    private fun provider(context:Context)=NavigationFileProvider().apply {
        attachInfo(context,base.packageManager.getProviderInfo(ComponentName(base,NavigationFileProvider::class.java),PackageManager.GET_META_DATA))
    }
    @Test fun providerConsumesOneHandoffButTheAlreadyOpenedDescriptorRemainsReadable()=isolated {context->
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())))
        val (file,uri)=packet(context);val expected=file.readBytes();val provider=provider(context)
        provider.openFile(uri,"r").use {descriptor->
            assertFalse(file.exists());assertArrayEquals(expected,FileInputStream(descriptor.fileDescriptor).readBytes())
        }
        try {provider.openFile(uri,"r").close();fail("handoff reused")} catch(_:FileNotFoundException) {}
    }
    @Test fun aDifferentPendingNonceCannotReadThePrivatePacket()=isolated {context->
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())))
        val (_,uri)=packet(context,"00000000-0000-4000-8000-000000000002")
        try {provider(context).openFile(uri,"r").close();fail("wrong nonce accepted")} catch(_:FileNotFoundException) {}
    }
    @Test fun cancellationRemovesMatchingHandoffWithoutDeletingTheVisitJournal()=isolated {context->
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())))
        val (file,_)=packet(context)
        assertTrue(LiveNavigationStore.cancel(context,nonce));assertFalse(file.exists())
        assertNull(LiveNavigationStore.read(context).pending)
    }
    @Test fun completedReviewRemovesHandoffButKeepsRatingAndReceipt()=isolated {context->
        val now=System.currentTimeMillis()
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,now)))
        val (file,_)=packet(context)
        assertTrue(LiveNavigationStore.complete(context,nonce,candidate,LiveNavigationResultPolicy.SOURCE,true,true,4,now,true))
        assertFalse(file.exists());assertEquals(4,LiveNavigationStore.read(context).visits.single().rating)
        assertNotNull(LiveNavigationStore.read(context).completed)
    }
    @Test fun expiredDevelopmentCredentialsArePhysicallyRemoved()=isolated {context->
        val file=File(context.cacheDir,"route-proxy-session.json")
        file.writeText(JSONObject().put("base_url","http://10.0.2.2:8765").put("token","a".repeat(32))
            .put("expires_at_ms",System.currentTimeMillis()-1).toString())
        assertNull(ProxyConfiguration.load(context));assertFalse(file.exists())
    }
    @Test fun expiredHandoffIsRefusedAndRemoved()=isolated {context->
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())))
        val (file,uri)=packet(context);assertTrue(file.setLastModified(System.currentTimeMillis()-PreparedRouteContract.TTL_MILLIS-1000))
        try {provider(context).openFile(uri,"r").close();fail("expired handoff accepted")} catch(_:FileNotFoundException) {}
        assertFalse(file.exists())
    }
    @Test fun expiredOpaqueSessionMetadataIsPrunedWithoutRemovingRatings()=isolated {context->
        val old=System.currentTimeMillis()-PreparedRouteContract.TTL_MILLIS-1000
        val token=JSONObject().put("nonce",nonce).put("candidate_id",candidate).put("prepared_at_epoch_ms",old)
        val visit=JSONObject().put("candidate_id",candidate).put("rating",5).put("completed_at_epoch_ms",old).put("arrival_confirmed",true)
        val prefs=context.getSharedPreferences("ignored",0)
        assertTrue(prefs.edit().putString("journal",JSONObject().put("pending",token).put("completed",token)
            .put("visits",org.json.JSONArray().put(visit)).toString()).commit())
        val journal=LiveNavigationStore.read(context)
        assertNull(journal.pending);assertNull(journal.completed);assertEquals(5,journal.visits.single().rating)
        assertFalse(JSONObject(prefs.getString("journal",null)!!).has("pending"))
        assertFalse(JSONObject(prefs.getString("journal",null)!!).has("completed"))
    }
    @Test fun simultaneousOpensCanConsumeOnlyOneDescriptor()=isolated {context->
        assertTrue(LiveNavigationStore.prepare(context,PendingLiveNavigation(nonce,candidate,System.currentTimeMillis())))
        val (_,uri)=packet(context);val provider=provider(context);val start=CountDownLatch(1)
        val executor=Executors.newFixedThreadPool(2)
        try {
            val calls=(1..2).map {executor.submit<Boolean> {
                start.await(2,TimeUnit.SECONDS)
                try {provider.openFile(uri,"r").use {};true} catch(_:FileNotFoundException) {false}
            }}
            start.countDown();assertEquals(1,calls.count {it.get(3,TimeUnit.SECONDS)})
        } finally {executor.shutdownNow()}
    }
}
