package io.github.zjs1925879492.rideweather

import io.github.zjs1925879492.rideweather.core.*
import io.github.zjs1925879492.rideweather.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class IgpsportTest {
    private val link="https://analyse.zh.igpsport.com/new/my/road/book/share?routeId=70714&language=zh-Hans"
    private fun data()=JSONObject("""{"code":0,"data":{"summary":{"roadId":70714,"title":"路线<&","distance":1000},"routeInfo":{"tracks":[{"latitude":31,"longitute":120,"alt":0},{"latitude":31.01,"longitute":120.01,"alt":-4.4},{"latitude":31.02,"longitute":120.02,"alt":null}]},"roadBookAuxiliaryPoint":[{"latitude":31,"longitude":120,"name":"水<&"}]}}""")
    @Test fun parsesBothOfficialPathsAndShareText() {
        val share=PublicRoutes.share(link)
        assertEquals("igpsport",share.platform); assertEquals("70714",share.id); assertEquals(link,share.url)
        assertEquals(share,PublicRoutes.share("一起骑行 $link".replace("&language","&amp;language")))
        assertEquals(share,PublicRoutes.share(link.replace("/my/","/")))
    }
    @Test fun rejectsWrongHostsPathsAndAmbiguousIds() {
        for(value in listOf(link.replace("https:","http:"),link.replace(".com/",".com.evil.test/"),link.replace("https://","https://user:pass@"),link.replace(".com/",".com:8443/"),link.replace("70714","0"),"$link&routeId=2",link.replace("70714","70714x"),link.replace("/share","/edit"),link.replace("analyse.zh","prod.zh"),link.replace("70714","9999999999999"),"$link#fragment",link.replace("routeId=70714","routeId=70714&route%49d=2"))) {
            assertThrows(value,Exception::class.java) { PublicRoutes.share(value) }
        }
    }
    @Test fun preservesRawWgs84ElevationAndWaypointsThroughGpx() {
        val route=PublicRoutes.parseIgpsport(data(),PublicRoutes.share(link))
        assertEquals("路线<&",route.name); assertEquals(link,route.source)
        assertEquals(listOf(TrackPoint(31.0,120.0,0.0),TrackPoint(31.01,120.01,-4.4),TrackPoint(31.02,120.02)),route.segments.single())
        assertEquals(listOf(Waypoint(TrackPoint(31.0,120.0),"水<&")),route.waypoints)
        assertEquals(route,Gpx.parse(Gpx.write(route)))
        assertTrue(route.lengthKm>2.0) // Compute geometry; do not replace it with summary.distance.
    }
    @Test fun validatesResponseIdentityAndCoordinateTypes() {
        val mutations:List<(JSONObject)->Unit> = listOf(
            { it.put("code",401) }, { it.remove("code") }, { it.put("code","0") },
            { it.getJSONObject("data").getJSONObject("summary").put("roadId",2) },
            { it.getJSONObject("data").getJSONObject("routeInfo").put("tracks",JSONArray()) },
            { it.getJSONObject("data").getJSONObject("routeInfo").getJSONArray("tracks").getJSONObject(0).put("latitude",JSONObject.NULL) },
            { it.getJSONObject("data").getJSONObject("routeInfo").getJSONArray("tracks").getJSONObject(0).put("latitude","31") },
            { it.getJSONObject("data").getJSONObject("routeInfo").getJSONArray("tracks").getJSONObject(0).put("longitute",181) },
            { it.getJSONObject("data").getJSONArray("roadBookAuxiliaryPoint").getJSONObject(0).put("longitude",JSONObject.NULL) },
            { it.getJSONObject("data").put("roadBookAuxiliaryPoint",JSONObject()) }
        )
        mutations.forEach { mutate -> val response=data(); mutate(response); assertThrows(Exception::class.java) { PublicRoutes.parseIgpsport(response,PublicRoutes.share(link)) } }
    }
    @Test fun enforcesPointAndWaypointBoundsAndKeepsUnknownElevationMissing() {
        val response=data(); val routeData=response.getJSONObject("data")
        val point=routeData.getJSONObject("routeInfo").getJSONArray("tracks").getJSONObject(0)
        routeData.getJSONObject("routeInfo").put("tracks",JSONArray().apply { repeat(MAX_POINTS+1) { put(point) } })
        assertThrows(Exception::class.java) { PublicRoutes.parseIgpsport(response,PublicRoutes.share(link)) }
        val tooMany=data(); tooMany.getJSONObject("data").put("roadBookAuxiliaryPoint",JSONArray().apply { repeat(2001) { put(point) } })
        assertThrows(Exception::class.java) { PublicRoutes.parseIgpsport(tooMany,PublicRoutes.share(link)) }
        val unknown=data(); unknown.getJSONObject("data").getJSONObject("routeInfo").getJSONArray("tracks").getJSONObject(0).put("alt",10001)
        assertNull(PublicRoutes.parseIgpsport(unknown,PublicRoutes.share(link)).segments.first().first().elevation)
    }
    @Test fun liveOfficialRouteMatchesWebsiteGpxAndSupportsWeather() {
        assumeTrue(System.getProperty("rideweather.live")=="true")
        val http=PublicHttp(); val route=PublicRoutes.load(link,Coordinates.System.BD09,http)
        assertEquals(3946,route.pointCount); assertEquals("环太湖310公里路书",route.name)
        assertEquals(310.36,route.lengthKm,0.01)
        System.getenv("RIDEWEATHER_IGP_REFERENCE_GPX")?.let { path ->
            val reference=Gpx.parse(File(path).readText())
            assertEquals(reference.segments,route.segments); assertEquals(reference.waypoints,route.waypoints)
            println("IGP_REFERENCE all coordinates, elevations and waypoints match website GPX")
        }
        assertEquals(route,Gpx.parse(Gpx.write(route)))
        val samples=sampleRoute(route,20.0)
        val weather=WeatherService.match(WeatherService.fetch(samples,http),samples,RidePlan(Instant.now().epochSecond+3600,22.0,30.0,20.0),route.lengthKm)
        assertEquals(samples.size,weather.size); assertTrue(weather.all { it!=null })
        println("LIVE_IGPSPORT points=${route.pointCount} km=${route.lengthKm} weather=${weather.size}")
    }
}
