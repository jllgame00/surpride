package com.surpride.app.experience

import android.content.Context
import android.graphics.*
import android.view.View
import com.surpride.app.experience.core.*
import com.surpride.app.ui.SurprideUi

/** Static conceptual diagram, no geographic data, map tiles, navigation surface or game animation. */
class ExperienceArt(context: Context, private val mood: Mood, private val visits: List<DemoVisit>? = null, private val revealed:Boolean=false) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val ui=SurprideUi(context)
    init {
        contentDescription=visits?.let {list->"기분별 체험 기록. "+Mood.entries.joinToString {"${it.label} ${list.count {v->v.mood==it}}회"}}
            ?: if(revealed) "목적지를 공개한 여정의 경로 모티프" else "끝에 물음표를 남긴 비밀 코스 개념도. 실제 지도 아님"
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        if(visits!=null) minimumHeight=ui.dp(320)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat();val h=height.toFloat()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=ui.dp(1).toFloat();paint.color=ui.divider
        for(i in 1..2) canvas.drawLine(0f,h*i/3,w,h*i/3,paint)
        if(visits==null) {
            val path=Path().apply {moveTo(w*.12f,h*.78f);cubicTo(w*.7f,h*.9f,w*.15f,h*.15f,w*.82f,h*.23f)}
            paint.color=ui.accent;paint.strokeWidth=ui.dp(4).toFloat();paint.strokeCap=Paint.Cap.ROUND;canvas.drawPath(path,paint)
            paint.style=Paint.Style.FILL;canvas.drawCircle(w*.12f,h*.78f,ui.dp(8).toFloat(),paint)
            paint.color=ui.raised;canvas.drawCircle(w*.82f,h*.23f,ui.dp(if(revealed) 12 else 28).toFloat(),paint)
            paint.color=ui.accent;paint.textSize=28*resources.displayMetrics.scaledDensity;paint.textAlign=Paint.Align.CENTER
            if(revealed) canvas.drawCircle(w*.82f,h*.23f,ui.dp(8).toFloat(),paint)
            else canvas.drawText("?",w*.82f,h*.23f-(paint.ascent()+paint.descent())/2,paint)
            // Meaning is exposed through an accessibility description; no tiny pixel-only caption.
        } else {
            Mood.entries.forEachIndexed { i, m ->
                val x=w*(.18f+(i%3)*.32f);val y=h*(.27f+(i/3)*.46f)
                val count=visits.count {it.mood==m}
                val radius=minOf(ui.dp(28).toFloat(),w*.1f,h*.12f)
                paint.style=Paint.Style.FILL;paint.color=if(count>0) ui.accentFill else ui.raised
                canvas.drawCircle(x,y,radius,paint)
                paint.color=if(count>0) ui.onAccent else ui.muted
                paint.textAlign=Paint.Align.CENTER;paint.textSize=24*resources.displayMetrics.scaledDensity
                canvas.drawText(count.toString(),x,y-(paint.ascent()+paint.descent())/2,paint)
                paint.color=ui.ink;paint.textSize=20*resources.displayMetrics.scaledDensity
                canvas.drawText(m.label,x,y+radius+ui.dp(28),paint)
            }
        }
    }
}
