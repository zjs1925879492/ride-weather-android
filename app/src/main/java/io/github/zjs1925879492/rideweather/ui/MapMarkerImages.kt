package io.github.zjs1925879492.rideweather.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.ceil

internal class MapMarkerImages(context:Context) {
    private val metrics=context.resources.displayMetrics
    private val scale=metrics.density
    private fun bitmap(width:Float,height:Float)=Bitmap.createBitmap(ceil(width).toInt(),ceil(height).toInt(),Bitmap.Config.ARGB_8888).apply { density=metrics.densityDpi }
    fun label(text:String,solar:Boolean,color:String):Bitmap {
        val foreground=Color.parseColor(if(solar) color else "#284c40")
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=11.5f*scale; typeface=Typeface.create("sans-serif-medium",Typeface.NORMAL) }
        val left=(if(solar) 22 else 8)*scale
        val image=bitmap(paint.measureText(text)+left+8*scale,26*scale)
        val canvas=Canvas(image)
        val inset=scale
        paint.color=Color.parseColor(if(solar) "#fff2da" else "#fbfcf8")
        canvas.drawRoundRect(inset,inset,image.width-inset,image.height-inset,7*scale,7*scale,paint)
        paint.style=Paint.Style.STROKE; paint.strokeWidth=scale; paint.color=foreground
        canvas.drawRoundRect(inset,inset,image.width-inset,image.height-inset,7*scale,7*scale,paint)
        paint.style=Paint.Style.FILL
        if(solar) {
            canvas.drawCircle(12*scale,image.height/2f,4*scale,paint)
            paint.strokeWidth=scale
            canvas.drawLine(12*scale,6*scale,12*scale,8*scale,paint)
            canvas.drawLine(12*scale,18*scale,12*scale,20*scale,paint)
            canvas.drawLine(5*scale,13*scale,7*scale,13*scale,paint)
            canvas.drawLine(17*scale,13*scale,19*scale,13*scale,paint)
        }
        val font=paint.fontMetrics
        canvas.drawText(text,left,(image.height-font.ascent-font.descent)/2,paint)
        return image
    }
    fun arrow():Bitmap {
        val image=bitmap(22*scale,16*scale)
        val canvas=Canvas(image)
        val path=Path().apply { moveTo(7*scale,4*scale); lineTo(14*scale,8*scale); lineTo(7*scale,12*scale) }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeJoin=Paint.Join.ROUND; strokeCap=Paint.Cap.ROUND; color=Color.WHITE; strokeWidth=5*scale }
        canvas.drawPath(path,paint)
        paint.color=Color.parseColor("#284337"); paint.strokeWidth=2.2f*scale
        canvas.drawPath(path,paint)
        return image
    }
}
