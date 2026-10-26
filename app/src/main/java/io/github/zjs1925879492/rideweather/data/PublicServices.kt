package io.github.zjs1925879492.rideweather.data

import io.github.zjs1925879492.rideweather.core.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.math.*

class PublicHttp {
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(25,TimeUnit.SECONDS).build()
    fun bytes(url:String,limit:Int=8*1024*1024):ByteArray {
        val parsed=url.toHttpUrl()
        require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.port==443) { "仅支持 HTTPS 公共数据" }
        require(parsed.host in setOf("www.onelap.cn","www.imxingzhe.com","xingzhe.oss-cn-hangzhou.aliyuncs.com","api.open-meteo.com","t0.tianditu.gov.cn")) { "数据地址不在允许范围内" }
        client.newCall(Request.Builder().url(parsed).header("Accept","application/json,image/png,*/*").header("User-Agent","RideWeather-Android/0.1").build()).execute().use { response ->
            when(response.code) { 401,403 -> error("数据源拒绝访问或需要授权，请核对权限；路书可改用本地 GPX 导入") }
            require(response.isSuccessful) { "数据源返回 HTTP ${response.code}，请稍后重试" }
            val body=requireNotNull(response.body) { "数据源未返回内容" }
            require(body.contentLength()<=limit) { "数据响应过大" }
            return body.byteStream().use { readBounded(it,limit) }
        }
    }
    fun json(url:String,limit:Int=8*1024*1024)=JSONObject(bytes(url,limit).toString(Charsets.UTF_8))
}
object PublicRoutes {
    data class Share(val platform:String,val id:String,val type:String,val url:String)
    fun share(text:String):Share {
        require(text.length<=5000) { "分享内容过长" }
        val value=Regex("https://(?:www\\.)?(?:onelap\\.cn|imxingzhe\\.com)/[^\\s<>]+",RegexOption.IGNORE_CASE).find(text.trim())?.value?.trimEnd('，','。',')',']') ?: error("请粘贴顽鹿或行者的 HTTPS 公开路书链接")
        val url=URI(value)
        require(url.scheme=="https" && url.userInfo==null && url.port==-1 && url.fragment==null) { "分享链接格式无效" }
        return when(url.host) {
            "www.imxingzhe.com","imxingzhe.com" -> {
                val id=Regex("^/routes/([1-9][0-9]{0,11})/?$").matchEntire(url.path)?.groupValues?.get(1) ?: error("行者链接需包含 /routes/数字ID/")
                Share("xingzhe",id,"route","https://www.imxingzhe.com/routes/$id/")
            }
            "www.onelap.cn","onelap.cn" -> {
                val parsed=value.toHttpUrl(); val id=parsed.queryParameter("id").orEmpty(); val type=parsed.queryParameter("routeType") ?: "myroute"
                require((type=="route" && Regex("[1-9][0-9]{0,11}").matches(id)) || (type=="myroute" && Regex("[a-fA-F0-9]{24}").matches(id))) { "顽鹿链接需要公开路书 ID，暂不支持仅有 nid 的编辑链接" }
                Share("onelap",id,type,"https://www.onelap.cn/bikeroute/detail.html?id=$id&routeType=$type")
            }
            else -> error("不支持此分享平台")
        }
    }
    fun load(text:String,coordinates:Coordinates.System,http:PublicHttp):Route {
        val share=share(text)
        return if(share.platform=="xingzhe") xingzhe(share,http) else onelap(share,coordinates,http)
    }
    private fun onelap(share:Share,system:Coordinates.System,http:PublicHttp):Route {
        val path=if(share.type=="route") "/api/road/road_book/${share.id}" else "/indoor/v1/navigation/share/detail/${share.id}"
        val response=http.json("https://www.onelap.cn$path",3*1024*1024)
        require(response.optInt("code")==200) { "顽鹿未提供公开数据，请导入 GPX" }
        val data=response.getJSONObject("data")
        if(share.type=="route") require(data.optInt("status")==4) { "这条顽鹿路书尚未公开" }
        else require(data.optString("id")==share.id) { "返回路线与分享链接不一致" }
        val encoded=data.getString("points").replace(Regex("\\s"),"")
        require(encoded.length in 1..2*1024*1024) { "压缩轨迹无效或过大" }
        val decoded=GZIPInputStream(Base64.getDecoder().decode(encoded).inputStream()).use { readBounded(it,16*1024*1024).toString(Charsets.UTF_8) }
        val points=JSONArray(decoded); require(points.length() in 2..MAX_POINTS) { "轨迹点数超过限制" }
        fun point(value:JSONObject)=Coordinates.toWgs84(TrackPoint(value.getDouble("lat"),value.getDouble("lng")),system)
        val segment=(0 until points.length()).map { point(points.getJSONObject(it)) }
        val waypoints=data.optJSONArray("path_point") ?: JSONArray(); require(waypoints.length()<=2000)
        return Route(data.optString("name").ifBlank { "顽鹿路书 ${share.id}" }.take(160),listOf(segment),share.url,
            "源坐标按 ${system.name} 转为 WGS84。顽鹿默认 BD09 为既有样本推断，其他路线需自行确认；未实地核验。",
            (0 until waypoints.length()).map { val w=waypoints.getJSONObject(it); Waypoint(point(w),w.optString("name",w.optString("real_name","途经点")).take(120)) })
    }
    fun validatedRouteFile(meta:JSONObject):String {
        val uuid=meta.getString("uuid"); require(Regex("[a-fA-F0-9]{8}-(?:[a-fA-F0-9]{4}-){3}[a-fA-F0-9]{12}").matches(uuid)) { "轨迹文件标识无效" }
        val url=URI(meta.getString("file_url"))
        require(url.scheme=="https" && url.host=="xingzhe.oss-cn-hangzhou.aliyuncs.com" && url.userInfo==null && url.port==-1 && url.fragment==null && url.path=="/lushu-$uuid.json") { "轨迹文件地址与公开详情不一致" }
        return url.toString()
    }
    fun decodePolyline(encoded:String,remaining:Int=MAX_POINTS):List<TrackPoint> {
        require(encoded.isNotEmpty() && encoded.length<=8*1024*1024) { "轨迹编码无效" }
        var pos=0; var lat=0L; var lon=0L; val points=mutableListOf<TrackPoint>()
        fun delta():Long {
            var value=0L; var shift=0; var byte:Int
            do { require(pos<encoded.length && shift<=30) { "轨迹编码不完整或溢出" }; byte=encoded[pos++].code-63; require(byte in 0..63) { "轨迹编码字符无效" }; value=value or ((byte and 31).toLong() shl shift); shift+=5 } while(byte>=32)
            return if(value and 1L == 1L) -(value shr 1)-1 else value shr 1
        }
        while(pos<encoded.length) { lat+=delta(); lon+=delta(); require(points.size<remaining && abs(lat)<=9000000 && abs(lon)<=18000000) { "轨迹坐标或点数超出范围" }; points.add(TrackPoint(lat/1e5,lon/1e5)) }
        return points
    }
    private fun xingzhe(share:Share,http:PublicHttp):Route {
        val detail=http.json("https://www.imxingzhe.com/api/v1/lushu/${share.id}/?tunnels=true",256*1024)
        require(detail.optInt("code",-1)==0) { "行者未提供公开路书" }
        val meta=detail.getJSONObject("data").getJSONObject("lushu")
        require(meta.optString("id")==share.id) { "返回路书与分享 ID 不一致" }
        val data=if(meta.optString("type")=="4") http.json(validatedRouteFile(meta)) else http.json("https://www.imxingzhe.com/api/v1/lushu/${share.id}/version2/").getJSONObject("data")
        val lines=data.getJSONArray("polylines"); require(lines.length() in 1..2000)
        var count=0; var ei=0; val elevations=data.optJSONArray("elevations")
        val segments=(0 until lines.length()).map { i -> decodePolyline(lines.getString(i),MAX_POINTS-count).also { count+=it.size } }
        if(meta.has("points") && !meta.isNull("points")) require(meta.getInt("points")==count) { "公开轨迹点数与详情不一致" }
        val withElevation=segments.map { line -> line.map { p -> val value=if(elevations?.length()==count && !elevations.isNull(ei)) elevations.optDouble(ei) else Double.NaN; ei++; p.copy(elevation=value.takeIf { it.isFinite() && it in -12000.0..10000.0 }) } }
        val waypoints=data.optJSONArray("waypoints") ?: JSONArray(); require(waypoints.length()<=2000)
        return Route(meta.optString("title").ifBlank { "行者路书 ${share.id}" }.take(160),withElevation,share.url,
            "由行者匿名公开轨迹生成，保留分段与可用高程；按分享页逻辑读取为 WGS84，未实地核验。",
            (0 until waypoints.length()).map { val w=waypoints.getJSONObject(it); val c=w.getJSONArray("coordinate"); Waypoint(TrackPoint(c.getDouble(0),c.getDouble(1)),w.optString("name","途经点").take(120)) })
    }
}
object WeatherService {
    fun fetch(samples:List<Sample>,http:PublicHttp):String {
        require(samples.size in 1..18)
        val url="https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude",samples.joinToString(",") { it.point.lat.toString() })
            .addQueryParameter("longitude",samples.joinToString(",") { it.point.lon.toString() })
            .addQueryParameter("hourly","temperature_2m,apparent_temperature,precipitation_probability,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m")
            .addQueryParameter("forecast_days","16").addQueryParameter("timezone","UTC").addQueryParameter("timeformat","unixtime").addQueryParameter("wind_speed_unit","kmh").build()
        return http.bytes(url.toString(),8*1024*1024).toString(Charsets.UTF_8)
    }
    fun match(raw:String,samples:List<Sample>,plan:RidePlan,totalKm:Double):List<Weather?> {
        val items=if(raw.trim().startsWith("[")) JSONArray(raw) else JSONArray().put(JSONObject(raw))
        require(items.length()==samples.size) { "天气数据未覆盖全部采样点" }
        return samples.mapIndexed { i,s -> weatherAt(items.getJSONObject(i),plan.arrival(s.km,totalKm)) }
    }
}
fun tiandituTile(key:String,point:TrackPoint):String {
    require(Regex("[a-fA-F0-9]{32}").matches(key)) { "天地图 Key 应为 32 位十六进制字符" }
    val z=8; val n=2.0.pow(z); val lat=point.lat.coerceIn(-85.0,85.0)*PI/180
    val x=floor((point.lon+180)/360*n).toInt().coerceIn(0,255)
    val y=floor((1-ln(tan(lat)+1/cos(lat))/PI)/2*n).toInt().coerceIn(0,255)
    return "https://t0.tianditu.gov.cn/vec_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=vec&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX=$z&TILEROW=$y&TILECOL=$x&tk=$key"
}
