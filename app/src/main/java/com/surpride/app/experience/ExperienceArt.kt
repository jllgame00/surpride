package com.surpride.app.experience

import android.content.Context
import android.graphics.*
import android.view.View
import com.surpride.app.experience.core.*

/** Static conceptual diagram, no geographic data, map tiles, navigation surface or game animation. */
class ExperienceArt(context: Context, private val mood: Mood, private val visits: List<DemoVisit>? = null) : View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w=width.toFloat();val h=height.toFloat()
        paint.style=Paint.Style.STROKE;paint.strokeWidth=2f;paint.color=Color.rgb(43,65,62)
        for(i in 1..5) canvas.drawLine(0f,h*i/6,w,h*i/6,paint)
        for(i in 1..7) canvas.drawLine(w*i/8,0f,w*i/8,h,paint)
        if(visits==null) {
            val path=Path().apply {moveTo(w*.12f,h*.78f);cubicTo(w*.7f,h*.9f,w*.15f,h*.15f,w*.82f,h*.23f)}
            paint.color=Color.rgb(205,235,143);paint.strokeWidth=6f;paint.strokeCap=Paint.Cap.ROUND;canvas.drawPath(path,paint)
            paint.style=Paint.Style.FILL;canvas.drawCircle(w*.12f,h*.78f,9f,paint)
            paint.color=Color.rgb(29,47,42);canvas.drawCircle(w*.82f,h*.23f,34f,paint)
            paint.color=Color.rgb(216,245,131);paint.textSize=34f;paint.textAlign=Paint.Align.CENTER
            canvas.drawText("?",w*.82f,h*.23f+12f,paint)
            paint.textAlign=Paint.Align.LEFT;paint.textSize=20f;paint.color=Color.rgb(153,174,164)
            canvas.drawText("DEMO / 코스 개념도 · 실제 지도 아님",w*.06f,h*.96f,paint)
        } else {
            Mood.entries.forEachIndexed { i, m ->
                val x=w*(.18f+(i%3)*.32f);val y=h*(.27f+(i/3)*.46f)
                val count=visits.count {it.mood==m}
                paint.style=Paint.Style.FILL;paint.color=if(count>0) Color.rgb(216,245,131) else Color.rgb(35,55,50)
                canvas.drawCircle(x,y,30f,paint)
                paint.color=if(count>0) Color.rgb(15,31,24) else Color.rgb(151,171,160)
                paint.textAlign=Paint.Align.CENTER;paint.textSize=22f;canvas.drawText(count.toString(),x,y+8f,paint)
                paint.color=Color.rgb(223,235,221);paint.textSize=20f;canvas.drawText(m.label,x,y+64f,paint)
            }
        }
    }
}
