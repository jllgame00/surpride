package com.surpride.navhostpoc

import kotlin.math.ceil
import kotlin.math.floor

/** Pixel measurements supplied by actual Paint font metrics; no hard-coded text-height ratios. */
internal data class SurfaceFooterLayout(val rows:Int,val footerHeight:Int,val mapHeight:Int,val padding:Float) {
    companion object {
        fun measure(height:Int,lineHeight:Float,density:Float):SurfaceFooterLayout {
            val available=height.coerceAtLeast(0)
            if(!lineHeight.isFinite() || lineHeight<=0 || !density.isFinite() || density<=0)
                return SurfaceFooterLayout(0,0,available,0f)
            val padding=12f*density
            val mapMinimum=128f*density
            val preferredRows=floor((available-mapMinimum-padding*2)/lineHeight).toInt().coerceIn(0,3)
            // A readable safety/status line takes precedence over preferred map height.
            // Below one line + padding the app footer is unsupported; never shrink the font to fake support.
            val rows=if(preferredRows==0 && available>=ceil(lineHeight+padding*2).toInt()) 1 else preferredRows
            val footer=if(rows==0) 0 else ceil(rows*lineHeight+padding*2).toInt().coerceAtMost(available)
            return SurfaceFooterLayout(rows,footer,available-footer,padding)
        }
    }
}
