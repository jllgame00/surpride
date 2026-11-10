package com.surpride.navhostpoc

import android.util.AtomicFile
import androidx.car.app.testing.TestCarContext
import com.surpride.navhostpoc.guidance.GuidanceAccuracyFixture
import com.surpride.navhostpoc.guidance.GuidanceParser
import com.surpride.navhostpoc.routing.RouteRefreshResult
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.*

/** Injects faults at the actual Android file boundary, never at application data paths. */
@Implements(AtomicFile::class)
class FaultAtomicFileShadow {
    @RealObject lateinit var real:AtomicFile
    private var original:FileOutputStream?=null
    companion object {var fault="NONE"}
    @Implementation fun startWrite():FileOutputStream {
        if(fault=="START") throw IOException("test write start")
        val output=Shadow.directlyOn<FileOutputStream,AtomicFile>(real,AtomicFile::class.java,"startWrite")
        original=output
        return if(fault=="PARTIAL") object:FileOutputStream(output.fd) {
            override fun write(bytes:ByteArray) {super.write(bytes,0,bytes.size/2);throw IOException("test partial write")}
        } else output
    }
    @Implementation fun finishWrite(output:FileOutputStream?) {
        if(fault=="FINISH") throw IOException("test finish")
        if(fault=="SILENT_FINISH") return
        Shadow.directlyOn<Void,AtomicFile>(real,AtomicFile::class.java,"finishWrite",ClassParameter.from(FileOutputStream::class.java,output))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],shadows=[FaultAtomicFileShadow::class])
class AtomicRoutePersistenceTest {
    private val context=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
    private val file=File(context.cacheDir,"route-guidance.json")
    private val now=System.currentTimeMillis()
    private fun seed():ActiveRoutePacket {
        file.writeText(GuidanceAccuracyFixture.payload(now).toString())
        return ActiveRoutePacket.load(context)!!
    }
    @After fun clearFault() {FaultAtomicFileShadow.fault="NONE"}
    private fun failedStartKeepsCommit(fault:String) {
        val packet=seed();val old=file.readBytes()
        FaultAtomicFileShadow.fault=fault
        assertThrows(Exception::class.java) {packet.rememberStart(context,now)}
        FaultAtomicFileShadow.fault="NONE"
        assertArrayEquals(old,AtomicFile(file).openRead().use {it.readBytes()})
        assertNull(packet.startedAt)
        assertNull(ActiveRoutePacket.load(context)!!.startedAt)
        packet.rememberStart(context,now)
        assertEquals(now,packet.startedAt)
        assertEquals(now,ActiveRoutePacket.load(context)!!.startedAt)
    }
    @Test fun startFailurePreservesOldBytesAndMemoryAndCanRetry()=failedStartKeepsCommit("START")
    @Test fun partialWriteFailurePreservesOldBytesAndMemoryAndCanRetry()=failedStartKeepsCommit("PARTIAL")
    @Test fun finishFailurePreservesOldBytesAndMemoryAndCanRetry()=failedStartKeepsCommit("FINISH")
    @Test fun silentFinishFailureIsNotReportedAsSuccess()=failedStartKeepsCommit("SILENT_FINISH")
    @Test fun interruptedWriteReadsPreviousCommittedPacket() {
        seed();val old=file.readBytes()
        val atomic=AtomicFile(file);val unfinished=atomic.startWrite()
        unfinished.write("{\"broken\":".toByteArray());unfinished.flush()
        assertNotNull(ActiveRoutePacket.load(context))
        assertArrayEquals(old,AtomicFile(file).openRead().use {it.readBytes()})
        atomic.failWrite(unfinished)
    }
    @Test fun staleLoadedPacketCannotOverwriteNewerCommit() {
        val first=seed();val stale=ActiveRoutePacket.load(context)!!
        first.rememberStart(context,now)
        val committed=file.readBytes()
        assertThrows(Exception::class.java) {stale.rememberStart(context,now+1)}
        assertArrayEquals(committed,file.readBytes());assertNull(stale.startedAt)
    }
    @Test fun concurrentDepartureSavesCannotRedefineTheCommittedStart() {
        val packet=seed();val gate=java.util.concurrent.CountDownLatch(1)
        val successes=java.util.concurrent.atomic.AtomicInteger()
        val threads=(0..1).map {index->Thread {
            gate.await()
            try {packet.rememberStart(context,now+index);successes.incrementAndGet()} catch(_:Exception) {}
        }.apply {start()}}
        gate.countDown();threads.forEach {it.join()}
        assertEquals(1,successes.get())
        assertEquals(packet.startedAt,ActiveRoutePacket.load(context)!!.startedAt)
    }
    @Test fun staleReplacementCannotOverwriteNewerSession() {
        val old=seed()
        file.writeText(GuidanceAccuracyFixture.payload(now).put("private_reason","new session").toString())
        val committed=file.readBytes();val body=GuidanceAccuracyFixture.payload(now).toString()
        val reply=RouteRefreshResult.Success(body,GuidanceParser.parse(body,now),now,now+3_600_000)
        assertThrows(Exception::class.java) {old.replace(context,reply,false,now)}
        assertArrayEquals(committed,file.readBytes())
    }
    @Test fun replacementCannotChangeTheSessionDestinationOrDepartureTime() {
        val packet=seed();packet.rememberStart(context,now)
        val committed=file.readBytes()
        val wrongDestination=GuidanceAccuracyFixture.payload(now).apply {getJSONObject("destination").put("longitude",1.0)}.toString()
        val destinationReply=RouteRefreshResult.Success(wrongDestination,GuidanceParser.parse(wrongDestination,now),now,now+3_600_000)
        assertThrows(Exception::class.java) {packet.replace(context,destinationReply,false,now)}
        val body=GuidanceAccuracyFixture.payload(now).toString()
        val reply=RouteRefreshResult.Success(body,GuidanceParser.parse(body,now),now,now+3_600_000)
        assertThrows(Exception::class.java) {packet.replace(context,reply,false,now+1)}
        assertArrayEquals(committed,file.readBytes());assertEquals(now,packet.startedAt)
    }
    @Test fun truncatedJsonCannotLoadOrDestroyPreviousAtomicBackup() {
        seed();val old=file.readBytes()
        File(file.path+".bak").writeBytes(old);file.writeText("{\"partial\":")
        assertNotNull(ActiveRoutePacket.load(context))
        assertArrayEquals(old,file.readBytes())
        file.writeText("{\"partial\":")
        assertNull(ActiveRoutePacket.load(context))
    }
}
