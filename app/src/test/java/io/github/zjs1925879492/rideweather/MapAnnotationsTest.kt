package io.github.zjs1925879492.rideweather

import io.github.zjs1925879492.rideweather.core.*
import io.github.zjs1925879492.rideweather.ui.mapAnnotationLayers
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MapAnnotationsTest {
    private val route=Route("turns and gaps",listOf(listOf(TrackPoint(0.0,0.0),TrackPoint(0.0,0.1),TrackPoint(0.1,0.1)),listOf(TrackPoint(30.0,120.0),TrackPoint(30.0,120.1))))
    private fun features(json:String):List<JSONObject> { val list=JSONObject(json).getJSONArray("features"); return (0 until list.length()).map { list.getJSONObject(it) } }
    @Test fun mileageInterpolationFollowsTurnsRatherThanCuttingCorners() {
        val leg=haversine(route.segments.first()[0],route.segments.first()[1])
        val p=pointAtMileage(route,leg*1.5)
        assertEquals(0.05,p.lat,1e-9); assertEquals(0.1,p.lon,1e-9)
        assertEquals(route.segments.first().first(),pointAtMileage(route,-1.0))
        assertEquals(route.segments.last().last(),pointAtMileage(route,100000.0))
    }
    @Test fun solarMarkerStaysOnTrackAfterSegmentGap() {
        val first=Route("first",listOf(route.segments.first())).lengthKm
        val second=Route("second",listOf(route.segments.last())).lengthKm
        val event=SolarEvent("sunset",1793006203,first+second/2,"during",90)
        val solar=features(routeGeoJson(route,sampleRoute(route,10.0),emptyList(),listOf(event))).single { it.getJSONObject("properties").getString("kind")=="solar" }
        val position=solar.getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(120.05,position.getDouble(0),1e-9); assertEquals(30.0,position.getDouble(1),1e-9)
        assertEquals("日落 17:16",solar.getJSONObject("properties").getString("label"))
        assertEquals(0,solar.getJSONObject("properties").getInt("priority"))
    }
    @Test fun mileageLabelsAreBoundToActualSamplesAndEndpointsHavePriority() {
        val samples=sampleRoute(route,10.0)
        val stops=features(routeGeoJson(route,samples,emptyList())).filter { it.getJSONObject("properties").getString("kind")=="sample" }
        assertEquals(samples.size,stops.size)
        stops.forEachIndexed { i,node ->
            assertEquals(mapMileage(samples[i].km),node.getJSONObject("properties").getString("label"))
            assertEquals(samples[i].point.lon,node.getJSONObject("geometry").getJSONArray("coordinates").getDouble(0),0.0)
        }
        assertEquals("0 km",stops.first().getJSONObject("properties").getString("label"))
        assertTrue(stops.first().getJSONObject("properties").getInt("priority")<stops[1].getJSONObject("properties").getInt("priority"))
    }
    @Test fun beforeAndAfterEventsStayAtEndpointsAndNameTheirPhase() {
        val events=listOf(SolarEvent("sunrise",1793006203,0.0,"before",50),SolarEvent("sunset",1793006203,route.lengthKm,"after",0))
        val markers=features(routeGeoJson(route,emptyList(),emptyList(),events)).filter { it.getJSONObject("properties").getString("kind")=="solar" }
        assertEquals(2,markers.size)
        assertTrue(markers[0].getJSONObject("properties").getString("label").contains("出发前"))
        assertTrue(markers[1].getJSONObject("properties").getString("label").contains("结束后"))
        assertEquals(120.1,markers.last().getJSONObject("geometry").getJSONArray("coordinates").getDouble(0),1e-9)
        assertTrue(features(routeGeoJson(route,emptyList(),emptyList())).none { it.getJSONObject("properties").getString("kind")=="solar" })
    }
    @Test fun arrowsCannotFlipAgainstTravelDirectionAndLabelsUseCollisionAvoidance() {
        val layers=mapAnnotationLayers().associateBy { it.getString("id") }
        val arrow=layers.getValue("direction-arrows").getJSONObject("layout")
        assertEquals("line",arrow.getString("symbol-placement"))
        assertFalse(arrow.getBoolean("icon-keep-upright"))
        assertFalse(arrow.getBoolean("icon-allow-overlap")); assertTrue(arrow.getInt("symbol-spacing")>=120)
        val labels=layers.getValue("route-labels").getJSONObject("layout")
        assertFalse(labels.getBoolean("icon-allow-overlap")); assertFalse(labels.getBoolean("icon-ignore-placement"))
        assertEquals("priority",labels.getJSONArray("symbol-sort-key").getString(1))
        assertEquals("viewport",labels.getString("icon-rotation-alignment"))
    }
}
