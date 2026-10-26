package io.github.zjs1925879492.rideweather.ui

import android.content.Context
import android.view.MotionEvent
import android.widget.FrameLayout

// Own the entire native pointer stream from DOWN to the final UP/CANCEL.
// A POINTER_UP is only one finger leaving a pinch/rotation, not its completion.
internal class MapTouchContainer(context:Context,private val onGestureActive:(Boolean)->Unit):FrameLayout(context) {
    private var touching=false
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        if(event.actionMasked==MotionEvent.ACTION_DOWN && !touching) {
            touching=true
            onGestureActive(true)
        }
        if(touching) super.requestDisallowInterceptTouchEvent(true)
        val ownsStream=touching
        return try {
            super.dispatchTouchEvent(event) || ownsStream
        } finally {
            if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) releaseGesture()
        }
    }
    override fun requestDisallowInterceptTouchEvent(disallowIntercept:Boolean) {
        // MapLibre's children cannot release a still-active stream to Compose.
        // Ignore initialization-time requests when no finger is on the map.
        super.requestDisallowInterceptTouchEvent(touching)
    }
    fun releaseGesture() {
        if(touching) {
            touching=false
            super.requestDisallowInterceptTouchEvent(false)
            onGestureActive(false)
        }
    }
    override fun onDetachedFromWindow() { releaseGesture(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus:Boolean) { if(!hasWindowFocus) releaseGesture(); super.onWindowFocusChanged(hasWindowFocus) }
}
