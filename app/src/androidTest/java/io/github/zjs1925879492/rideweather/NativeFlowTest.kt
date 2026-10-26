package io.github.zjs1925879492.rideweather

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.zjs1925879492.rideweather.data.RouteStore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeFlowTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var vm:MainViewModel
    @Before fun findModel() { ui.runOnIdle { vm=ViewModelProvider(ui.activity)[MainViewModel::class.java] } }
    private fun settled() { ui.waitUntil(45_000) { !vm.state.value.busy }; ui.waitForIdle() }
    private fun demo() {
        ui.onNodeWithTag("load_demo").performScrollTo().performClick(); settled()
        assertEquals(7,vm.state.value.route?.pointCount)
        ui.onNodeWithText("沿途演示轨迹").performScrollTo().assertIsDisplayed()
    }
    @Test fun demoRouteAndPlanNavigation() {
        demo()
        ui.onNodeWithTag("nav_weather").performClick()
        ui.onNodeWithText("安排这次出发").assertIsDisplayed()
        ui.onNodeWithTag("query_weather").performScrollTo().assertIsEnabled()
    }
    @Test fun invalidShareDoesNotReplaceCurrentRoute() {
        demo(); val before=vm.state.value.route!!.fingerprint()
        ui.onNodeWithTag("share_input").performScrollTo().performTextReplacement("https://evil.example/routes/1/")
        ui.onNodeWithTag("import_share").performScrollTo().performClick(); settled()
        assertTrue(vm.state.value.error); assertEquals(before,vm.state.value.route!!.fingerprint())
    }
    @Test fun favoritePersistsFullTrackOnDevice() {
        demo()
        val route=vm.state.value.route!!; val alreadySaved=vm.state.value.favorites.any { it.id==route.fingerprint() }
        ui.onNodeWithTag("save_favorite").performScrollTo().performClick(); settled()
        ui.onNodeWithTag("nav_favorites").performClick()
        ui.onNodeWithText("沿途演示轨迹").assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        RouteStore(context).use { store ->
            val saved=store.load(route.fingerprint())
            assertEquals(route,saved); assertEquals(7,saved.segments.single().count { it.elevation!=null })
            if(!alreadySaved) store.delete(route.fingerprint())
        }
    }
    @Test fun signingIdentityAndInvalidKeyFeedback() {
        ui.onNodeWithTag("nav_settings").performClick()
        ui.onNodeWithTag("map_key_input").performTextReplacement("invalid-key")
        ui.onNodeWithTag("verify_map").performScrollTo().performClick(); settled()
        assertTrue(vm.state.value.error); assertFalse(vm.state.value.mapEnabled)
        ui.onNodeWithTag("signing_identity").performScrollTo().assertTextContains("包名：io.github.zjs1925879492.rideweather",substring=true).assertTextContains("SHA-1：",substring=true).assertTextContains("SHA-256：",substring=true)
    }
    @Test fun directOnelapImportOnDevice() {
        ui.onNodeWithTag("share_input").performScrollTo().performTextReplacement("https://www.onelap.cn/bikeroute/detail.html?id=54848&routeType=route")
        ui.onNodeWithTag("import_share").performScrollTo().performClick(); settled()
        assertFalse(vm.state.value.status,vm.state.value.error)
        assertTrue(vm.state.value.route!!.pointCount>2)
        assertTrue(vm.state.value.route!!.source!!.startsWith("https://www.onelap.cn/"))
    }
    @Test fun directXingzheImportOnDevice() {
        ui.onNodeWithTag("share_input").performScrollTo().performTextReplacement("https://www.imxingzhe.com/routes/5701846/")
        ui.onNodeWithTag("import_share").performScrollTo().performClick(); settled()
        assertFalse(vm.state.value.status,vm.state.value.error)
        assertTrue(vm.state.value.route!!.pointCount>2)
        assertTrue(vm.state.value.route!!.source!!.startsWith("https://www.imxingzhe.com/"))
    }
    @Test fun weatherQueryOnDevice() {
        demo(); ui.onNodeWithTag("nav_weather").performClick()
        ui.onNodeWithTag("query_weather").performScrollTo().performClick(); settled()
        assertFalse(vm.state.value.status,vm.state.value.error)
        assertTrue(vm.state.value.weather.isNotEmpty())
        assertTrue(vm.state.value.weather.all { it!=null })
    }
}
