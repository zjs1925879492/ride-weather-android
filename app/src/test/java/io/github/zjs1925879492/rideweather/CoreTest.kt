package io.github.zjs1925879492.rideweather

import io.github.zjs1925879492.rideweather.core.*
import io.github.zjs1925879492.rideweather.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant

class CoreTest {
    private fun route()=Route("test",listOf(listOf(TrackPoint(31.7,119.5),TrackPoint(31.8,119.6),TrackPoint(31.9,119.7)),listOf(TrackPoint(32.0,120.0),TrackPoint(32.1,120.1))))
    @Test fun websiteGoldenGeometrySamplingAndArrival() {
        // Captured directly from ride-weather/core.js with these same synthetic segments.
        val route=route(); assertEquals(43.76214742407049,route.lengthKm,1e-9)
        val samples=sampleRoute(route,20.0)
        val expected=listOf(0.0,29.185831890128107,43.76214742407049)
        assertEquals(expected.size,samples.size); expected.forEachIndexed { i,km -> assertEquals(km,samples[i].km,1e-9) }
        assertEquals(listOf(0,0,1),samples.map { it.segment })
        assertEquals(1800008961L,RidePlan(1800000000,22.0,30.0,20.0).arrival(route.lengthKm,route.lengthKm))
    }
    @Test fun websiteGoldenWindAndCoordinates() {
        assertEquals(14.142135623730951,headwind(20.0,45.0,90.0),1e-12)
        val bd=Coordinates.toWgs84(TrackPoint(31.7,119.6),Coordinates.System.BD09)
        assertEquals(119.58850367928555,bd.lon,1e-10); assertEquals(31.696408747552443,bd.lat,1e-10)
        val gcj=Coordinates.toWgs84(TrackPoint(31.7,119.6),Coordinates.System.GCJ02)
        assertEquals(119.59509420902697,gcj.lon,1e-10); assertEquals(31.70214150930792,gcj.lat,1e-10)
    }
    @Test fun gpxRoundTripRetainsSegmentsElevationAndWaypoints() {
        val route=Route("<雨 & 风>",listOf(listOf(TrackPoint(31.0,119.0,12.0),TrackPoint(31.1,119.1,13.0)),listOf(TrackPoint(32.0,120.0),TrackPoint(32.1,120.1,8.0))),"https://www.imxingzhe.com/routes/5701846/","source",listOf(Waypoint(TrackPoint(31.0,119.0,12.0),"A&B")))
        val restored=Gpx.parse(Gpx.write(route)); assertEquals(route,restored)
        assertEquals(route.fingerprint(),restored.fingerprint())
    }
    @Test fun rejectsUnsafeXmlAndInvalidCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { Gpx.parse("<!DOCTYPE gpx [<!ENTITY x SYSTEM 'file:///private'>]><gpx>&x;</gpx>") }
        assertThrows(Exception::class.java) { Gpx.parse("<gpx><trk><trkseg><trkpt lat='91' lon='0'/><trkpt lat='0' lon='0'/></trkseg></trk></gpx>") }
        assertThrows(Exception::class.java) { Gpx.parse("<html>not a route</html>") }
        assertThrows(Exception::class.java) { Gpx.parse("<gpx><trk><trkseg><trkpt lat='NaN' lon='0'/></trkseg></trk></gpx>") }
    }
    @Test fun rteFallbackAndEmptyTrackSegment() {
        val route=Gpx.parse("<gpx><trk><trkseg/></trk><rte><name>R</name><rtept lat='31' lon='119'/><rtept lat='31.1' lon='119.1'/></rte></gpx>")
        assertEquals(2,route.pointCount); assertEquals("R",route.name)
    }
    @Test fun boundedInputAndPointLimit() {
        assertThrows(IllegalArgumentException::class.java) { readBounded(ByteArray(20).inputStream(),10) }
        assertThrows(IllegalArgumentException::class.java) { Route("too many",listOf(List(MAX_POINTS+1){TrackPoint(31.0,119.0)})) }
        val points=(0..1000).map { TrackPoint(30.0+it*0.001,119.0) }
        assertTrue(sampleRoute(Route("long",listOf(points)),1.0).size<=18)
    }
    @Test fun routeColoringNeverConnectsSegmentGaps() {
        val route=route(); val samples=sampleRoute(route,20.0)
        val geo=JSONObject(routeGeoJson(route,samples,List(samples.size){Weather(0,null,null,null,null,null,null,null,61)}))
        val lines=geo.getJSONArray("features")
        var lineCount=0
        for(i in 0 until lines.length()) { val feature=lines.getJSONObject(i); if(feature.getJSONObject("geometry").getString("type")!="LineString") continue
            lineCount++; assertEquals("#3185d5",feature.getJSONObject("properties").getString("color"))
            val coords=feature.getJSONObject("geometry").getJSONArray("coordinates")
            val lons=(0 until coords.length()).map { coords.getJSONArray(it).getDouble(0) }
            assertFalse(lons.any { it<119.8 } && lons.any { it>=120.0 })
        }
        assertTrue(lineCount>=2)
    }
    @Test fun weatherUsesNearestHourAndPreservesMissingData() {
        val data=JSONObject("""{"hourly":{"time":[10000,13600],"temperature_2m":[null,23],"weather_code":[45,61]}}""")
        assertNull(weatherAt(data,8000)); assertNull(weatherAt(data,15500))
        assertNull(weatherAt(data,10000)!!.temp); assertEquals(23.0,weatherAt(data,13000)!!.temp!!,0.0)
        assertEquals(WeatherKind.FOG,weatherKind(45)); assertEquals(WeatherKind.UNKNOWN,weatherKind(null)); assertEquals(WeatherKind.UNKNOWN,weatherKind(999))
    }
    @Test fun shareLinksCannotChangeDataHosts() {
        assertEquals("5701846",PublicRoutes.share("骑这条路线 https://www.imxingzhe.com/routes/5701846/?from=app").id)
        assertThrows(Exception::class.java) { PublicRoutes.share("https://www.imxingzhe.com.evil.example/routes/5701846/") }
        assertThrows(Exception::class.java) { PublicRoutes.share("http://www.onelap.cn/bikeroute/detail.html?id=54848&routeType=route") }
        assertThrows(Exception::class.java) { PublicRoutes.share("https://attacker@www.imxingzhe.com/routes/5701846/") }
    }
    @Test fun signedFilesMustMatchDetailUuidAndOfficialHost() {
        val uuid="12345678-1234-1234-1234-123456789abc"
        val good=JSONObject().put("uuid",uuid).put("file_url","https://xingzhe.oss-cn-hangzhou.aliyuncs.com/lushu-$uuid.json?signature=example")
        assertTrue(PublicRoutes.validatedRouteFile(good).startsWith("https://xingzhe.oss-cn-hangzhou.aliyuncs.com/"))
        assertThrows(Exception::class.java) { PublicRoutes.validatedRouteFile(JSONObject(good.toString()).put("file_url","https://evil.example/lushu-$uuid.json")) }
        assertThrows(Exception::class.java) { PublicRoutes.validatedRouteFile(JSONObject(good.toString()).put("file_url","https://xingzhe.oss-cn-hangzhou.aliyuncs.com/other.json")) }
    }
    @Test fun decodesPublicPolylineAndRejectsTruncation() {
        val points=PublicRoutes.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3,points.size); assertEquals(38.5,points.first().lat,0.0); assertEquals(-126.453,points.last().lon,0.0)
        assertThrows(Exception::class.java) { PublicRoutes.decodePolyline("_p~iF") }
        assertThrows(Exception::class.java) { PublicRoutes.decodePolyline("_p~iF~ps|U",0) }
    }
    @Test fun planValidationAndTileKeyValidation() {
        assertThrows(Exception::class.java) { RidePlan(0,0.0,30.0,20.0) }
        assertThrows(Exception::class.java) { tiandituTile("not a key",TrackPoint(31.0,119.0)) }
        assertThrows(Exception::class.java) { LocalDateTimeForTest.parse("2026-02-30 09:00") }
    }
}
private object LocalDateTimeForTest { fun parse(value:String)=java.time.LocalDateTime.parse(value,PLAN_FORMAT) }
class LiveServicesTest {
    private fun enabled() { assumeTrue("Opt-in: RIDEWEATHER_LIVE_TESTS=true",System.getProperty("rideweather.live")=="true") }
    @Test fun directOnelapPublicRoute() { enabled(); val route=PublicRoutes.load("https://www.onelap.cn/bikeroute/detail.html?id=54848&routeType=route",Coordinates.System.BD09,PublicHttp()); assertTrue(route.pointCount>2); assertTrue(route.lengthKm>1); println("LIVE_ONELAP points=${route.pointCount} km=${route.lengthKm}") }
    @Test fun directXingzhePublicRoute() { enabled(); val route=PublicRoutes.load("https://www.imxingzhe.com/routes/5701846/",Coordinates.System.WGS84,PublicHttp()); assertTrue(route.pointCount>2); assertTrue(route.segments.size>=1); println("LIVE_XINGZHE points=${route.pointCount} segments=${route.segments.size} km=${route.lengthKm}") }
    @Test fun directWeatherBatch() { enabled(); val route=Route("live weather",listOf(listOf(TrackPoint(31.7,119.5),TrackPoint(31.8,119.6)))); val samples=sampleRoute(route,20.0); val raw=WeatherService.fetch(samples,PublicHttp()); val weather=WeatherService.match(raw,samples,RidePlan(Instant.now().epochSecond+3600,22.0,0.0,20.0),route.lengthKm); assertEquals(samples.size,weather.size); assertTrue(weather.all { it!=null }); println("LIVE_WEATHER samples=${weather.size}") }
}
