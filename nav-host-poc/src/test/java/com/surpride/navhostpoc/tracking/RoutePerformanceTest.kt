package com.surpride.navhostpoc.tracking

import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.map.FollowCamera
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

/** Synthetic, single JVM worker. Not an Android frame/ANR or real-road performance certificate. */
class RoutePerformanceTest {
    @Volatile private var setupSink:RouteMatcher?=null
    private fun route(n:Int):GuidanceRoute {
        val points=List(n) {RouteCoordinate(0.0,it*0.000001)}
        return GuidanceRoute(RouteDataSource.MOCK,listOf(RouteSegment("Synthetic",10000,600,0,points)),
            listOf(Maneuver(100,0,0),Maneuver(2,5000,0),Maneuver(101,10000,0)),10000,600,points.last())
    }
    private fun fix(route:GuidanceRoute,i:Int)=PositionFix(route.geometry.atFraction(0.45+i*0.00001),
        5.0,100_000L+i*1000,1_800_000_000_000L,0.0,null,null,PositionSource.FAKE,false)
    @Test fun frozenMatcherAgreesOnAcquisitionAmbiguityLossReverseAndJump() {
        val paths=listOf(
            listOf(RouteCoordinate(0.0,0.0),RouteCoordinate(0.0,0.01)),
            listOf(RouteCoordinate(-0.001,-0.001),RouteCoordinate(0.001,0.001),RouteCoordinate(-0.001,0.001),RouteCoordinate(0.001,-0.001)))
        for(points in paths) {
            val r=GuidanceRoute(RouteDataSource.MOCK,listOf(RouteSegment("Synthetic",2000,600,0,points)),
                listOf(Maneuver(100,0,0),Maneuver(2,1000,0),Maneuver(101,2000,0)),2000,600,points.last())
            val old=LegacyRouteMatcher(r);val current=RouteMatcher(r)
            for(i in 0..160) {
                val fraction=when {i<40->i/160.0;i<60->0.24;i<80->0.90;i<100->0.1;else->(i-100)/100.0}
                val p=if(i in 40..49) RouteCoordinate(0.01,0.01) else r.geometry.atFraction(fraction)
                val fix=PositionFix(p,if(i==125) 100.0 else 5.0,100_000L+i*1000,1_800_000_000_000L,
                    if(i%3==0) 10.0 else 0.0,if(i%3==0) 180.0 else null,10.0,PositionSource.FAKE,false)
                if(i==60) {old.unavailable("TEST_LOSS");current.unavailable("TEST_LOSS")}
                assertEquals("Exact matcher output at synthetic step $i",old.accept(fix,fix.elapsedMillis),current.accept(fix,fix.elapsedMillis))
                if(i==120) assertEquals(old.expire(fix.elapsedMillis+60_000),current.expire(fix.elapsedMillis+60_000))
            }
        }
    }
    @Test fun geometryLookupMatchesTheOriginalLinearBoundaryContract() {
        val points=route(10000).geometry.points
        val r=GuidanceRoute(RouteDataSource.MOCK,listOf(
            RouteSegment("A",7000,300,0,points.subList(0,1001)),
            RouteSegment("B",2000,200,0,points.subList(1000,7001)),
            RouteSegment("C",1000,100,0,points.subList(7000,points.size))),
            listOf(Maneuver(100,0,0),Maneuver(101,10000,2)),10000,600,points.last())
        val geometry=r.geometry
        for(i in 0..1000) {
            val value=(i-20)/960.0;val target=value.coerceIn(0.0,1.0)*geometry.lengthMeters
            val next=geometry.cumulative.indexOfFirst {it>=target}.coerceAtLeast(1)
            val fraction=(target-geometry.cumulative[next-1])/(geometry.cumulative[next]-geometry.cumulative[next-1])
            val a=geometry.points[next-1];val b=geometry.points[next]
            assertEquals(RouteCoordinate(a.longitude+(b.longitude-a.longitude)*fraction,a.latitude+(b.latitude-a.latitude)*fraction),geometry.atFraction(value))
            assertEquals(r.geometryRoadIndices[geometry.cumulative.indexOfLast {it<=target+0.000001}.coerceIn(0,r.geometryRoadIndices.lastIndex)],
                r.roadIndexAtGeometry(target))
        }
    }
    @Test fun measureSyntheticMatcherAndCameraWithoutNetwork() {
        val bean=ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val thread=Thread.currentThread().id
        fun allocated()=if(bean?.isThreadAllocatedMemorySupported==true) bean.getThreadAllocatedBytes(thread) else -1L
        fun gc()=ManagementFactory.getGarbageCollectorMXBeans().sumOf {it.collectionCount.coerceAtLeast(0)}
        fun measure(action:(Int)->Unit):JSONObject {
            repeat(30,action)
            val beforeBytes=allocated();val beforeGc=gc()
            val samples=LongArray(100) {i->val start=System.nanoTime();action(i+30);System.nanoTime()-start}
            val bytes=allocated()-beforeBytes;val collections=gc()-beforeGc;samples.sort()
            return JSONObject().put("samples",100).put("p50_ns",samples[49]).put("p95_ns",samples[94])
                .put("p99_ns",samples[98]).put("max_ns",samples.last()).put("allocated_bytes_per_operation",if(beforeBytes>=0) bytes/100 else JSONObject.NULL)
                .put("gc_collections",collections)
        }
        val results=JSONArray()
        for(n in listOf(1000,10000,50000,100000)) {
            val route=route(n);val matcher=RouteMatcher(route);val fixes=List(130) {fix(route,it)}
            val setup=measure {_ -> setupSink=RouteMatcher(route)}
            val matching=measure {i->matcher.accept(fixes[i],fixes[i].elapsedMillis)}
            val camera=FollowCamera();val state=RouteMatcher(route).accept(fixes[0],fixes[0].elapsedMillis)
            val rendering=measure {i->assertNotNull(camera.update(route,fixes[i],state,1280,720))}
            results.put(JSONObject().put("points",n).put("matcher_setup",setup).put("matcher",matching).put("camera",rendering))
            val frozen=LegacyRouteMatcher(route);val equivalent=RouteMatcher(route)
            fixes.forEach {sample->assertEquals("Exact output at $n points",frozen.accept(sample,sample.elapsedMillis),equivalent.accept(sample,sample.elapsedMillis))}
        }
        val root=generateSequence(File(checkNotNull(System.getProperty("user.dir")))) {it.parentFile}.first {File(it,"settings.gradle.kts").isFile}
        File(root,".gradle/phase6-b5/route-current.json").apply {parentFile.mkdirs()}.writeText(
            JSONObject().put("environment","host JVM single worker, synthetic only").put("results",results).toString(2))
    }
}
