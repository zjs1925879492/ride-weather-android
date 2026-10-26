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
    @Test fun screenshotRideHasOneSunsetBetweenFortyAndSixtyKilometres() {
        val mileage=listOf(0.0,20.18118075825846,40.237318995236066,60.28410589461554,80.4236398957285,100.78259556666896,102.45378077228679)
        val samples=mileage.mapIndexed { i,km -> Sample(TrackPoint(31.0,119.0),km,if(i<3) 0 else 1,0.0) }
        // Public-route regression reproduced from the reported afternoon trip.
        val sunset=1793006203L
        val forecasts=List(samples.size) { JSONObject().put("daily",JSONObject().put("time",JSONArray().put(1792972800L)).put("sunset",JSONArray().put(sunset))) }
        val plan=RidePlan(java.time.LocalDateTime.parse("2026-10-26 14:44",PLAN_FORMAT).atZone(BEIJING).toEpochSecond(),22.0,30.0,20.0)
        val events=solarRouteEvents(samples,forecasts,plan,mileage.last())
        assertEquals(1,events.size); assertEquals("sunset",events.single().type)
        assertEquals(sunset,events.single().time); assertTrue(events.single().km in 40.24..60.28)
        val timeline=weatherTimeline(samples,events)
        val index=timeline.indexOfFirst { it is WeatherTimelineEntry.Sun }
        assertEquals(2,(timeline[index-1] as WeatherTimelineEntry.Stop).index)
        assertEquals(3,(timeline[index+1] as WeatherTimelineEntry.Stop).index)
    }
    @Test fun solarEventsMatchWebsiteGoldenAndDaytimeRideHasNoReminder() {
        val samples=listOf(0.0,20.0,40.0).map { Sample(TrackPoint(31.0,119.0),it,0,0.0) }
        val forecasts=(0..2).map { i -> JSONObject().put("daily",JSONObject().put("time",JSONArray().put(86400)).put("sunrise",JSONArray().put(100900+i*100)).put("sunset",JSONArray().put(106000+i*100))) }
        // Captured by running the website's core.js solarRouteEvents on this fixture.
        val events=solarRouteEvents(samples,forecasts,RidePlan(100000,20.0,0.0,20.0),40.0)
        assertEquals(2,events.size)
        assertEquals(SolarEvent("sunrise",100926,5.142857142857142,"during",105),events[0])
        assertEquals("sunset",events[1].type); assertEquals(106171L,events[1].time)
        assertEquals(34.285714285714285,events[1].km,1e-9); assertEquals(17,events[1].remainingMinutes)
        // The trip is entirely between sunrise and sunset and not near either edge.
        val daytime=List(3) { JSONObject().put("daily",JSONObject().put("time",JSONArray().put(86400)).put("sunrise",JSONArray().put(90000)).put("sunset",JSONArray().put(120000))) }
        assertTrue(solarRouteEvents(samples,daytime,RidePlan(103000,80.0,0.0,20.0),40.0).isEmpty())
        val absent=JSONObject().put("daily",JSONObject().put("time",JSONArray().put(86400)).put("sunrise",JSONArray().put(JSONObject.NULL)).put("sunset",JSONArray().put(0)))
        assertTrue(solarRouteEvents(samples,List(3){absent},RidePlan(100000,20.0,0.0,20.0),40.0).isEmpty())
    }
    @Test fun solarTimelineInsertsEventsOnlyOnceAtTheirMileage() {
        val samples=listOf(0.0,20.0,40.0).map { Sample(TrackPoint(31.0,119.0),it,0,0.0) }
        val events=listOf(SolarEvent("sunrise",1,0.0,"before",0),SolarEvent("sunset",2,20.0,"during",0),SolarEvent("sunset",3,40.0,"after",0))
        val timeline=weatherTimeline(samples,events)
        assertEquals(listOf("sunrise","stop0","stop1","sunset","stop2","sunset"),timeline.map { when(it) { is WeatherTimelineEntry.Stop -> "stop${it.index}"; is WeatherTimelineEntry.Sun -> it.event.type } })
        assertEquals(3,weatherTimeline(samples,emptyList()).size)
        assertTrue(weatherTimeline(samples,emptyList()).none { it is WeatherTimelineEntry.Sun })
    }
    @Test fun solarCrossingsUseConsecutiveSamplesAcrossTrackSegmentsButNotMissingForecasts() {
        val samples=sampleRoute(Route("solar",listOf(listOf(TrackPoint(31.0,119.0),TrackPoint(31.1,119.0)))),200.0)
        val total=samples.last().km; val plan=RidePlan(100000,22.0,0.0,20.0); val end=plan.arrival(total,total)
        val a=100600L; val b=end-600
        fun forecast(time:Long)=JSONObject().put("daily",JSONObject().put("time",JSONArray().put(86400)).put("sunrise",JSONArray().put(time)).put("sunset",JSONArray().put(JSONObject.NULL)))
        val forecasts=listOf(forecast(a),forecast(b))
        val event=solarRouteEvents(samples,forecasts,plan,total).single()
        assertEquals("sunrise",event.type); assertEquals("during",event.phase)
        assertEquals(total/2,event.km,1e-9); assertEquals((plan.start+end)/2.0,event.time.toDouble(),1.0)
        assertEquals(event,solarRouteEvents(samples.mapIndexed { i,s -> s.copy(segment=i) },forecasts,plan,total).single())
        assertTrue(solarRouteEvents(samples,listOf(forecast(a),JSONObject()),plan,total).isEmpty())
        assertEquals("before",solarRouteEvents(samples,List(2){forecast(plan.start-900)},plan,total).single().phase)
        assertEquals("after",solarRouteEvents(samples,List(2){forecast(end+900)},plan,total).single().phase)
        assertTrue(solarRouteEvents(samples,List(2){forecast(end+1801)},plan,total).isEmpty())
    }
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
    @Test fun publicXingzheAfternoonRideIncludesSunset() {
        enabled()
        val route=PublicRoutes.load("https://www.imxingzhe.com/routes/5701846/",Coordinates.System.WGS84,PublicHttp())
        val samples=sampleRoute(route,20.0)
        val forecasts=WeatherService.items(WeatherService.fetch(samples,PublicHttp()),samples.size)
        // Center the trip on an upcoming actual sunset so this live regression
        // remains usable after the original 2026-10-26 screenshot's forecast expires.
        val sunsets=forecasts.first().getJSONObject("daily").getJSONArray("sunset")
        val sunset=(0 until sunsets.length()).map { sunsets.getLong(it) }.first { it>Instant.now().epochSecond+86400 }
        val offset=RidePlan(0,22.0,30.0,20.0).arrival(50.0,route.lengthKm)
        val plan=RidePlan(sunset-offset,22.0,30.0,20.0)
        val events=solarRouteEvents(samples,forecasts,plan,route.lengthKm)
        println("SUNSET_REGRESSION events=$events")
        assertEquals(1,events.count { it.type=="sunset" && it.phase=="during" })
        assertTrue(events.single { it.type=="sunset" }.km in 40.0..60.3)
    }
    @Test fun directOnelapPublicRoute() { enabled(); val route=PublicRoutes.load("https://www.onelap.cn/bikeroute/detail.html?id=54848&routeType=route",Coordinates.System.BD09,PublicHttp()); assertTrue(route.pointCount>2); assertTrue(route.lengthKm>1); println("LIVE_ONELAP points=${route.pointCount} km=${route.lengthKm}") }
    @Test fun directXingzhePublicRoute() { enabled(); val route=PublicRoutes.load("https://www.imxingzhe.com/routes/5701846/",Coordinates.System.WGS84,PublicHttp()); assertTrue(route.pointCount>2); assertTrue(route.segments.size>=1); println("LIVE_XINGZHE points=${route.pointCount} segments=${route.segments.size} km=${route.lengthKm}") }
    @Test fun directWeatherBatch() { enabled(); val route=Route("live weather",listOf(listOf(TrackPoint(31.7,119.5),TrackPoint(31.8,119.6)))); val samples=sampleRoute(route,20.0); val raw=WeatherService.fetch(samples,PublicHttp()); val start=Instant.now().epochSecond+3600; val weather=WeatherService.match(raw,samples,RidePlan(start,22.0,0.0,20.0),route.lengthKm); assertEquals(samples.size,weather.size); assertTrue(weather.all { it!=null }); assertTrue(WeatherService.items(raw,samples.size).all { it.getJSONObject("daily").getJSONArray("sunrise").length()>0 && it.getJSONObject("daily").getJSONArray("sunset").length()>0 }); println("LIVE_WEATHER samples=${weather.size} solar=available") }
}
