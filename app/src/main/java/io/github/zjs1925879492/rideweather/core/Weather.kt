package io.github.zjs1925879492.rideweather.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class Weather(val time:Long,val temp:Double?,val feels:Double?,val rain:Double?,val amount:Double?,val wind:Double?,val gust:Double?,val direction:Double?,val code:Int?)
enum class WeatherKind(val title:String,val color:String) {
    SUNNY("晴","#e7853e"), CLOUDY("多云 / 阴","#7a8490"), RAIN("雨","#3185d5"), FOG("雾","#ffffff"), SNOW("雪","#b6a0dc"), THUNDER("雷暴","#b64c89"), UNKNOWN("暂无天气","#6c8276")
}
fun weatherKind(code:Int?):WeatherKind = when(code) {
    0 -> WeatherKind.SUNNY
    1,2,3 -> WeatherKind.CLOUDY
    45,48 -> WeatherKind.FOG
    51,53,55,56,57,61,63,65,66,67,80,81,82 -> WeatherKind.RAIN
    71,73,75,77,85,86 -> WeatherKind.SNOW
    95,96,99 -> WeatherKind.THUNDER
    else -> WeatherKind.UNKNOWN
}
fun weatherAt(data:JSONObject,epoch:Long):Weather? {
    val h=data.optJSONObject("hourly") ?: return null; val times=h.optJSONArray("time") ?: return null
    if(times.length()==0 || epoch<times.getLong(0)-1800 || epoch>times.getLong(times.length()-1)+1800) return null
    val i=(0 until times.length()).minBy { abs(times.getLong(it)-epoch) }
    fun value(key:String)=h.optJSONArray(key)?.let { if(it.isNull(i)) null else it.optDouble(i).takeIf(Double::isFinite) }
    return Weather(times.getLong(i),value("temperature_2m"),value("apparent_temperature"),value("precipitation_probability"),value("precipitation"),value("wind_speed_10m"),value("wind_gusts_10m"),value("wind_direction_10m"),value("weather_code")?.toInt())
}
// Sections meet at sample mileage midpoints, not actual precipitation boundaries.
// Track segments are never connected through their gaps.
fun routeGeoJson(route:Route?,samples:List<Sample>,weather:List<Weather?>):String {
    val features=JSONArray()
    if(route==null) return JSONObject().put("type","FeatureCollection").put("features",features).toString()
    val boundaries=samples.zipWithNext { a,b -> (a.km+b.km)/2 }
    var km=0.0
    route.segments.forEach { segment ->
        var activeIndex=-1; var activePoints:JSONArray?=null
        segment.zipWithNext().forEach { (a,b) ->
            val start=km; km+=haversine(a,b); val end=km
            if(end<=start) return@forEach
            val cuts=listOf(start)+boundaries.filter { it>start && it<end }+listOf(end)
            cuts.zipWithNext().forEach { (lo,hi) ->
                val index=boundaries.count { it<=(lo+hi)/2 }
                fun coordinate(distance:Double):JSONArray { val f=(distance-start)/(end-start); return JSONArray().put(a.lon+(b.lon-a.lon)*f).put(a.lat+(b.lat-a.lat)*f) }
                if(activePoints==null || activeIndex!=index) {
                    activeIndex=index; activePoints=JSONArray().put(coordinate(lo))
                    features.put(JSONObject().put("type","Feature").put("properties",JSONObject().put("kind","route").put("color",weatherKind(weather.getOrNull(index)?.code).color)).put("geometry",JSONObject().put("type","LineString").put("coordinates",activePoints)))
                }
                activePoints!!.put(coordinate(hi))
            }
        }
    }
    samples.forEachIndexed { index,s -> features.put(JSONObject().put("type","Feature").put("properties",JSONObject().put("kind","sample").put("index",index).put("color",weatherKind(weather.getOrNull(index)?.code).color)).put("geometry",JSONObject().put("type","Point").put("coordinates",JSONArray().put(s.point.lon).put(s.point.lat)))) }
    return JSONObject().put("type","FeatureCollection").put("features",features).toString()
}
