package io.github.zjs1925879492.rideweather.ui

import org.json.JSONArray
import org.json.JSONObject

internal val annotationLayerIds=listOf("direction-arrows","solar-points","route-labels")

// Rasterized local labels need no glyph URL or remotely hosted fonts. All
// symbol labels share a collision domain and solar events get first priority.
internal fun mapAnnotationLayers():List<JSONObject> {
    fun layer(id:String,type:String,filter:String,layout:JSONObject)=JSONObject().put("id",id).put("type",type).put("source","route").put("filter",JSONArray(filter)).put("layout",layout.put("visibility","none"))
    val arrows=layer("direction-arrows","symbol","[\"==\",[\"get\",\"kind\"],\"route\"]",JSONObject()
        .put("symbol-placement","line").put("symbol-spacing",150).put("icon-image","route-arrow")
        .put("icon-rotation-alignment","map").put("icon-keep-upright",false)
        .put("icon-allow-overlap",false).put("icon-ignore-placement",false).put("icon-padding",10))
    val solar=layer("solar-points","circle","[\"==\",[\"get\",\"kind\"],\"solar\"]",JSONObject())
        .put("paint",JSONObject().put("circle-radius",7).put("circle-color",JSONArray("[\"get\",\"color\"]")).put("circle-stroke-color","#ffffff").put("circle-stroke-width",2))
    val labels=layer("route-labels","symbol","[\"any\",[\"==\",[\"get\",\"kind\"],\"sample\"],[\"==\",[\"get\",\"kind\"],\"solar\"]]",JSONObject()
        .put("icon-image",JSONArray("[\"get\",\"marker\"]")).put("icon-anchor",JSONArray("[\"get\",\"anchor\"]"))
        .put("icon-offset",JSONArray("[\"get\",\"offset\"]")).put("icon-rotation-alignment","viewport")
        .put("symbol-sort-key",JSONArray("[\"get\",\"priority\"]"))
        .put("icon-allow-overlap",false).put("icon-ignore-placement",false).put("icon-padding",4))
    return listOf(arrows,solar,labels)
}
