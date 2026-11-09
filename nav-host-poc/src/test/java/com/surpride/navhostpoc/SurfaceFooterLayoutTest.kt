package com.surpride.navhostpoc

import org.junit.Assert.*
import org.junit.Test

class SurfaceFooterLayoutTest {
    @Test fun measuredRowsStayWithinFooterAndLeaveRoomForMap() {
        val layout=SurfaceFooterLayout.measure(height=900,lineHeight=44f,density=1f)
        assertEquals(3,layout.rows)
        assertTrue(layout.mapHeight>=128)
        assertEquals(900,layout.mapHeight+layout.footerHeight)
        assertTrue(layout.rows*44f+layout.padding*2<=layout.footerHeight)
    }

    @Test fun shortWindowDropsSupportingRowsInsteadOfClipping() {
        val layout=SurfaceFooterLayout.measure(height=220,lineHeight=44f,density=1f)
        assertEquals(1,layout.rows)
        assertTrue(layout.mapHeight>=128)
        assertTrue(layout.rows*44f+layout.padding*2<=layout.footerHeight)
    }

    @Test fun largeFontOrDensityCannotProduceNegativeMapOrFooter() {
        listOf(0,80,200,1000).forEach {height->
            val layout=SurfaceFooterLayout.measure(height,90f,2f)
            assertTrue(layout.mapHeight>=0)
            assertTrue(layout.footerHeight>=0)
            assertEquals(height,layout.mapHeight+layout.footerHeight)
            assertTrue(layout.rows in 0..3)
            if(layout.rows>0) assertTrue(layout.rows*90f+layout.padding*2<=layout.footerHeight)
        }
    }

    @Test fun unusableFontMetricsDoNotCreateDrawingRows() {
        assertEquals(0,SurfaceFooterLayout.measure(500,Float.NaN,1f).rows)
        assertEquals(0,SurfaceFooterLayout.measure(500,0f,1f).rows)
    }

    @Test fun onePriorityStatusRowWinsOverPreferredMapHeight() {
        val layout=SurfaceFooterLayout.measure(200,90f,1f)
        assertEquals(1,layout.rows)
        assertEquals(114,layout.footerHeight)
        assertEquals(86,layout.mapHeight)
    }

    @Test fun exactOneRowCapacityMayLeaveNoMapButDoesNotClipStatus() {
        val layout=SurfaceFooterLayout.measure(114,90f,1f)
        assertEquals(1,layout.rows)
        assertEquals(0,layout.mapHeight)
        assertEquals(114,layout.footerHeight)
    }

    @Test fun belowOneReadableRowIsExplicitlyUnsupported() {
        val layout=SurfaceFooterLayout.measure(113,90f,1f)
        assertEquals(0,layout.rows)
        assertEquals(0,layout.footerHeight)
        assertEquals(113,layout.mapHeight)
    }
}
