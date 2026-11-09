package com.surpride.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.surpride.app.R

/** Native automotive tokens. Unslop by Damir Sokolovsky, CC BY 4.0; adapted in docs/phase5. */
class SurprideUi(private val context: Context) {
    companion object {
        fun applyAppearance(context:Context) {
            val mode=context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getInt("night_mode",androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            if(androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode()!=mode)
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        }
        fun setAppearance(context:Context,mode:Int) {
            require(mode in listOf(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO,androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES))
            context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putInt("night_mode",mode).apply()
            applyAppearance(context)
        }
    }
    val background get()=context.getColor(R.color.surpride_background)
    val surface get()=context.getColor(R.color.surpride_surface)
    val raised get()=context.getColor(R.color.surpride_raised)
    val ink get()=context.getColor(R.color.surpride_ink)
    val muted get()=context.getColor(R.color.surpride_muted)
    val accent get()=context.getColor(R.color.surpride_accent)
    val accentFill get()=context.getColor(R.color.surpride_accent_fill)
    val onAccent get()=context.getColor(R.color.surpride_on_accent)
    val divider get()=context.getColor(R.color.surpride_divider)
    val error get()=context.getColor(R.color.surpride_error)
    val warning get()=context.getColor(R.color.surpride_warning)
    fun dp(value:Int)=(value*context.resources.displayMetrics.density+.5f).toInt()
    fun text(value:String,size:Int=24,color:Int=ink,heading:Boolean=false)=TextView(context).apply {
        text=value;textSize=size.toFloat();setTextColor(color)
        typeface=Typeface.create(Typeface.create("sans-serif",Typeface.NORMAL),if(heading) 600 else 400,false)
        setLineSpacing(dp(4).toFloat(),1.15f);setPadding(0,dp(4),0,dp(8))
        ViewCompat.setAccessibilityHeading(this,heading)
    }
    fun shape(fill:Int,radius:Int=16,stroke:Int=divider)=GradientDrawable().apply {
        setColor(fill);cornerRadius=dp(radius).toFloat();setStroke(dp(1),stroke)
    }
    fun card()=LinearLayout(context).apply {
        orientation=LinearLayout.VERTICAL
        val padding=context.resources.getDimensionPixelSize(R.dimen.surpride_card_padding)
        setPadding(padding,padding,padding,padding);background=shape(surface)
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(8)}
    }
    fun button(value:String,primary:Boolean=false,action:()->Unit)=Button(context).apply {
        text=value;styleButton(this,primary);setOnClickListener {action()}
        layoutParams=LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8);bottomMargin=dp(4)}
    }
    fun styleDialog(dialog:androidx.appcompat.app.AlertDialog) {
        dialog.window?.setBackgroundDrawable(shape(surface,16))
        dialog.findViewById<TextView>(android.R.id.message)?.apply {textSize=24f;setTextColor(ink)}
        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.apply {textSize=28f;setTextColor(ink)}
        listOf(android.content.DialogInterface.BUTTON_POSITIVE,android.content.DialogInterface.BUTTON_NEGATIVE,
            android.content.DialogInterface.BUTTON_NEUTRAL).forEach {which->
            dialog.getButton(which)?.let {styleButton(it,primary=which==android.content.DialogInterface.BUTTON_POSITIVE)}
        }
        // Choice lists are app-owned and need the same readable body size as other parked inputs.
        dialog.listView?.apply {
            setBackgroundColor(surface)
            post {for(i in 0 until childCount) (getChildAt(i) as? TextView)?.apply {textSize=24f;setTextColor(ink);minHeight=dp(76)}}
        }
    }
    fun styleButton(button:Button,primary:Boolean=false,selected:Boolean=false) = button.apply {
        isAllCaps=false;textSize=24f;letterSpacing=0f
        typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL)
        minHeight=context.resources.getDimensionPixelSize(R.dimen.surpride_control_height);minWidth=dp(76)
        minimumHeight=minHeight;minimumWidth=minWidth
        setPadding(dp(16),dp(12),dp(16),dp(12));gravity=Gravity.CENTER
        backgroundTintList=null;stateListAnimator=null;elevation=0f
        val disabled=context.getColor(R.color.surpride_disabled)
        val states=arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf(android.R.attr.state_selected),intArrayOf())
        setTextColor(ColorStateList(states,intArrayOf(disabled,accent,if(primary) onAccent else ink)))
        val surfaces=StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled),shape(raised,12))
            addState(intArrayOf(android.R.attr.state_focused),shape(if(primary) accentFill else raised,12,context.getColor(R.color.surpride_focus)).apply {setStroke(dp(3),context.getColor(R.color.surpride_focus))})
            addState(intArrayOf(android.R.attr.state_selected),shape(raised,12,accent).apply {setStroke(dp(2),accent)})
            addState(intArrayOf(),shape(if(primary) accentFill else surface,12,if(primary) accentFill else divider))
        }
        background=RippleDrawable(ColorStateList.valueOf(0x229AB1A5),surfaces,shape(ink,12))
        isSelected=selected
        setCompoundDrawablesRelativeWithIntrinsicBounds(if(selected) R.drawable.ic_selection_check else 0,0,0,0)
        compoundDrawablePadding=dp(8)
        compoundDrawableTintList=ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(disabled,accent))
        ViewCompat.setStateDescription(this,if(selected) "선택됨" else null)
    }
}
