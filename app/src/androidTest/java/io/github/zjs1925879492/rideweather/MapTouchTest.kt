package io.github.zjs1925879492.rideweather

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.zjs1925879492.rideweather.ui.MapTouchContainer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapTouchTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun nativeMultitouchOwnsStreamAndPageScrollingRecoversAfterUpOrCancel() {
        lateinit var scroll:ScrollState
        lateinit var container:MapTouchContainer
        var active=false
        var maxPointers=0
        ui.runOnIdle { ui.activity.setContent {
            var touching by remember { mutableStateOf(false) }
            scroll=rememberScrollState()
            MaterialTheme {
                Column(Modifier.fillMaxWidth().height(400.dp).padding(top=32.dp).verticalScroll(scroll,enabled=!touching)) {
                    Box(Modifier.fillMaxWidth().height(80.dp).testTag("page_scroll_handle"))
                    AndroidView(factory={ context ->
                        MapTouchContainer(context) { touching=it; active=it }.also { holder ->
                            container=holder
                            holder.addView(object:View(context) {
                                override fun onTouchEvent(event:MotionEvent):Boolean {
                                    maxPointers=maxOf(maxPointers,event.pointerCount)
                                    if(event.actionMasked==MotionEvent.ACTION_UP) performClick()
                                    return true
                                }
                                override fun performClick():Boolean { super.performClick(); return true }
                            },ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT))
                        }
                    },modifier=Modifier.fillMaxWidth().height(200.dp).testTag("map_touch_test"))
                    Box(Modifier.fillMaxWidth().height(1000.dp))
                }
            }
        } }
        ui.onNodeWithTag("map_touch_test").performTouchInput {
            down(0,Offset(width*0.3f,height*0.5f)); down(1,Offset(width*0.7f,height*0.5f))
            moveTo(0,Offset(width*0.2f,height*0.25f)); moveTo(1,Offset(width*0.8f,height*0.75f))
            up(1)
        }
        ui.runOnIdle { assertTrue(active); assertEquals(0,scroll.value); assertEquals(2,maxPointers) }
        ui.onNodeWithTag("map_touch_test").performTouchInput { up(0) }
        ui.runOnIdle { assertFalse(active); assertEquals(0,scroll.value) }
        ui.onNodeWithTag("page_scroll_handle").performTouchInput { swipeUp() }
        ui.runOnIdle { assertTrue(scroll.value>0) }
        ui.runOnIdle {
            val time=SystemClock.uptimeMillis()
            fun event(action:Int) { val e=MotionEvent.obtain(time,time,action,20f,20f,0); try { container.dispatchTouchEvent(e) } finally { e.recycle() } }
            event(MotionEvent.ACTION_DOWN); assertTrue(active)
            event(MotionEvent.ACTION_CANCEL); assertFalse(active)
        }
    }
}
