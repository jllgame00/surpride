package com.surpride.app.experience

import android.content.Context
import android.graphics.*
import android.view.View
import com.surpride.app.experience.core.DemoVisit
import com.surpride.app.experience.core.Mood
import com.surpride.app.ui.SurprideUi

/** Static conceptual diagram, no geographic data, map tiles, navigation surface or game animation. */
class ExperienceArt(context: Context, private val visits: List<DemoVisit>) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val ui=SurprideUi(context)
    init {
        contentDescription="기분별 체험 기록. "+Mood.entries.joinToString {"${it.label} ${visits.count {v->v.mood==it}}회"}
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        minimumHeight=ui.dp(320)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat();val h=height.toFloat()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=ui.dp(1).toFloat();paint.color=ui.divider
        for(i in 1..2) canvas.drawLine(0f,h*i/3,w,h*i/3,paint)
        Mood.entries.forEachIndexed { i, m ->
                val x=w*(.18f+(i%3)*.32f);val y=h*(.27f+(i/3)*.46f)
                val count=visits.count {it.mood==m}
                paint.style=Paint.Style.FILL;paint.color=if(count>0) ui.ink else ui.muted
                paint.textAlign=Paint.Align.CENTER;paint.textSize=32*resources.displayMetrics.scaledDensity
                canvas.drawText(count.toString(),x,y-(paint.ascent()+paint.descent())/2,paint)
                val countBottom=y+(paint.descent()-paint.ascent())/2
                paint.color=ui.muted;paint.textSize=20*resources.displayMetrics.scaledDensity
                canvas.drawText(m.label,x,countBottom+ui.dp(12)-paint.ascent(),paint)
        }
    }
}
