package io.github.zjs1925879492.rideweather.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.zjs1925879492.rideweather.core.Route
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.log.Logger
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

private data class MapRender(val route:Route?,val geoJson:String,val key:String,val enabled:Boolean,val colored:Boolean)
private class MapController(context:Context,onError:()->Unit) {
    val view:MapView
    private var map:MapLibreMap?=null
    private var render:MapRender?=null
    private var styleKey:String?=null
    private var fitted:Route?=null
    private var renderedGeoJson:String?=null
    init {
        MapLibre.getInstance(context)
        // Map URLs contain the user's key. Do not emit SDK HTTP messages to logcat.
        Logger.setVerbosity(Logger.NONE)
        view=MapView(context).apply { onCreate(null); addOnDidFailLoadingMapListener { onError() } }
        view.getMapAsync { m ->
            map=m
            m.cameraPosition=CameraPosition.Builder().target(LatLng(31.74,119.6)).zoom(8.0).build()
            m.uiSettings.isLogoEnabled=false
            render?.let { update(it) }
        }
    }
    fun update(value:MapRender) {
        render=value
        val map=map ?: return
        val key=if(value.enabled) value.key else ""
        if(key!=styleKey) {
            styleKey=key
            renderedGeoJson=null
            map.setStyle(Style.Builder().fromJson(style(key,value.geoJson))) { applyData() }
        } else applyData()
    }
    private fun applyData() {
        val m=map ?: return; val value=render ?: return
        m.getStyle { s ->
            if(renderedGeoJson!=value.geoJson) {
                s.getSourceAs<GeoJsonSource>("route")?.setGeoJson(value.geoJson)
                renderedGeoJson=value.geoJson
            }
            s.getLayerAs<LineLayer>("route-line")?.setProperties(if(value.colored) lineColor(Expression.get("color")) else lineColor("#397762"))
            s.getLayerAs<CircleLayer>("samples")?.setProperties(if(value.colored) circleColor(Expression.get("color")) else circleColor("#397762"))
            val route=value.route
            if(route!=null && fitted !== route) {
                fitted=route
                val points=route.segments.flatten()
                val minLat=points.minOf { it.lat }; val maxLat=points.maxOf { it.lat }; val minLon=points.minOf { it.lon }; val maxLon=points.maxOf { it.lon }
                if(maxLat-minLat<0.00001 && maxLon-minLon<0.00001) m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(minLat,minLon),13.0))
                else m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().include(LatLng(minLat,minLon)).include(LatLng(maxLat,maxLon)).build(),55))
            }
        }
    }
    private fun style(key:String,geoJson:String):String {
        val sources=JSONObject().put("route",JSONObject().put("type","geojson").put("data",JSONObject(geoJson)))
        val layers=JSONArray().put(JSONObject().put("id","background").put("type","background").put("paint",JSONObject().put("background-color","#e6ebdf")))
        if(key.isNotBlank()) {
            // Tianditu Web Mercator (WGS84) WMTS. This does not emulate SDK signature authentication.
            listOf("vec","cva").forEach { name ->
                val url="https://t0.tianditu.gov.cn/${name}_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=$name&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=$key"
                sources.put(name,JSONObject().put("type","raster").put("tiles",JSONArray().put(url)).put("tileSize",256).put("minzoom",1).put("maxzoom",18).put("attribution","© 天地图 国家地理信息公共服务平台"))
                layers.put(JSONObject().put("id",name).put("type","raster").put("source",name))
            }
        }
        val lineFilter=JSONArray("[\"==\",[\"get\",\"kind\"],\"route\"]")
        layers.put(JSONObject().put("id","route-outline").put("type","line").put("source","route").put("filter",lineFilter).put("layout",JSONObject().put("line-cap","round").put("line-join","round")).put("paint",JSONObject().put("line-color","#354b40").put("line-width",7)))
        layers.put(JSONObject().put("id","route-line").put("type","line").put("source","route").put("filter",lineFilter).put("layout",JSONObject().put("line-cap","round").put("line-join","round")).put("paint",JSONObject().put("line-color","#397762").put("line-width",4)))
        layers.put(JSONObject().put("id","samples").put("type","circle").put("source","route").put("filter",JSONArray("[\"==\",[\"get\",\"kind\"],\"sample\"]")).put("paint",JSONObject().put("circle-radius",5).put("circle-color","#397762").put("circle-stroke-color","#354b40").put("circle-stroke-width",2)))
        return JSONObject().put("version",8).put("sources",sources).put("layers",layers).toString()
    }
}
@Composable
fun RouteMap(route:Route?,geoJson:String,key:String,enabled:Boolean,colored:Boolean,modifier:Modifier=Modifier,onError:()->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val error by rememberUpdatedState(onError)
    val controller=remember(context) { MapController(context) { error() } }
    DisposableEffect(controller,lifecycle) {
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.view.onStart()
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.view.onResume()
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_START -> controller.view.onStart()
            Lifecycle.Event.ON_RESUME -> controller.view.onResume()
            Lifecycle.Event.ON_PAUSE -> controller.view.onPause()
            Lifecycle.Event.ON_STOP -> controller.view.onStop()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); controller.view.onPause(); controller.view.onStop(); controller.view.onDestroy() }
    }
    AndroidView(factory={controller.view},modifier=modifier,update={controller.update(MapRender(route,geoJson,key,enabled,colored))})
}
