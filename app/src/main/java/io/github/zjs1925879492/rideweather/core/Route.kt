package io.github.zjs1925879492.rideweather.core

import java.io.InputStream
import java.io.StringReader
import java.security.MessageDigest
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import kotlin.math.*

const val MAX_POINTS = 150_000
const val MAX_FILE_BYTES = 15 * 1024 * 1024
data class TrackPoint(val lat: Double, val lon: Double, val elevation: Double? = null) {
    init { require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { "轨迹包含无效坐标" } }
}
data class Waypoint(val point: TrackPoint, val name: String)
data class Route(val name: String, val segments: List<List<TrackPoint>>, val source: String? = null,
                 val note: String = "WGS84 坐标", val waypoints: List<Waypoint> = emptyList()) {
    init { require(segments.sumOf { it.size } in 2..MAX_POINTS && segments.size <= 2000) { "轨迹需包含 2–150,000 个点，最多 2,000 段" } }
    val pointCount get() = segments.sumOf { it.size }
    val lengthKm: Double get() = segments.sumOf { segment -> segment.zipWithNext().sumOf { (a,b) -> haversine(a,b) } }
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        segments.forEach { segment ->
            digest.update("segment\n".toByteArray())
            segment.forEach { digest.update("${it.lat},${it.lon},${it.elevation}\n".toByteArray()) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
data class Sample(val point: TrackPoint, val km: Double, val segment: Int, val heading: Double)
data class RidePlan(val start: Long, val speed: Double, val restMinutes: Double, val spacingKm: Double) {
    init { require(speed.isFinite() && speed in 1.0..80.0 && restMinutes.isFinite() && restMinutes in 0.0..1440.0 && spacingKm.isFinite() && spacingKm in 1.0..200.0) { "均速需为 1–80 km/h，休息为 0–1440 分钟，采样间距为 1–200 km" } }
    fun arrival(km: Double, totalKm: Double): Long = start + (km / speed * 3600 + if (totalKm > 0) km / totalKm * restMinutes * 60 else 0.0).toLong()
}
fun haversine(a: TrackPoint,b: TrackPoint): Double {
    val r = PI / 180
    val v = sin((b.lat-a.lat)*r/2).pow(2) + cos(a.lat*r)*cos(b.lat*r)*sin((b.lon-a.lon)*r/2).pow(2)
    return 6371.0088 * 2 * asin(sqrt(v.coerceIn(0.0,1.0)))
}
fun bearing(a: TrackPoint,b: TrackPoint): Double {
    val r = PI / 180
    return (atan2(sin((b.lon-a.lon)*r)*cos(b.lat*r),cos(a.lat*r)*sin(b.lat*r)-sin(a.lat*r)*cos(b.lat*r)*cos((b.lon-a.lon)*r))/r+360)%360
}
fun headwind(speed: Double, from: Double, heading: Double): Double = speed*cos((from-heading)*PI/180)
fun sampleRoute(route: Route, spacingKm: Double, max: Int = 18): List<Sample> {
    require(spacingKm.isFinite() && spacingKm > 0 && max >= 2)
    var km = 0.0
    val points = route.segments.flatMapIndexed { si, segment -> segment.mapIndexed { i,p ->
        if(i > 0) km += haversine(segment[i-1],p)
        val heading = if(i < segment.lastIndex) bearing(p,segment[i+1]) else if(i>0) bearing(segment[i-1],p) else 0.0
        Sample(p,km,si,heading)
    } }
    val step = maxOf(spacingKm,km/(max-1))
    val result = mutableListOf(points.first())
    var next = step
    points.forEach { if(it.km >= next && it.km < km) { result.add(it); next = it.km+step } }
    if(result.last().point != points.last().point || result.last().km != points.last().km) result.add(points.last())
    return result
}
fun readBounded(stream: InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while(true) { val size = stream.read(buffer); if(size < 0) break; require(output.size()+size <= limit) { "文件或解压数据过大" }; output.write(buffer,0,size) }
    return output.toByteArray()
}
object Gpx {
    fun parse(text: String, fallback: String = "导入的路线"): Route {
        require(text.toByteArray().size <= MAX_FILE_BYTES) { "GPX 文件超过 15 MB" }
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)",RegexOption.IGNORE_CASE).containsMatchIn(text)) { "GPX 不支持文档类型或外部实体" }
        val tracks = mutableListOf<MutableList<TrackPoint>>()
        val routes = mutableListOf<MutableList<TrackPoint>>()
        val waypoints = mutableListOf<Waypoint>()
        val stack = mutableListOf<String>()
        var name = ""; var description = ""; var source: String? = null; var count = 0
        var currentPoint: TrackPoint? = null; var pointName = ""; var elevation: Double? = null
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities").forEach { runCatching { factory.setFeature(it,false) } }
        val buffer = StringBuilder()
        factory.newSAXParser().parse(InputSource(StringReader(text.removePrefix("\uFEFF"))),object: DefaultHandler() {
            override fun startElement(uri:String?,localName:String?,qName:String?,attributes:Attributes) {
                val tag = localName?.takeIf { it.isNotEmpty() } ?: qName.orEmpty().substringAfter(':')
                require(stack.size < 64) { "GPX 层级过深" }
                if(stack.isEmpty()) require(tag == "gpx") { "请选择 GPX 文件" }
                stack.add(tag); buffer.setLength(0)
                when(tag) {
                    "trkseg" -> { require(tracks.size < 2000); tracks.add(mutableListOf()) }
                    "rte" -> { require(routes.size < 2000); routes.add(mutableListOf()) }
                    "trkpt", "rtept", "wpt" -> {
                        currentPoint = TrackPoint(attributes.getValue("lat")?.toDoubleOrNull() ?: error("缺少纬度"),attributes.getValue("lon")?.toDoubleOrNull() ?: error("缺少经度"))
                        pointName = ""; elevation = null
                    }
                    "link" -> if(stack.getOrNull(stack.lastIndex-1)=="metadata") {
                        val value = attributes.getValue("href").orEmpty()
                        if(value.startsWith("https://") && value.length<=3000) source=value
                    }
                }
            }
            override fun characters(ch:CharArray,start:Int,length:Int) { require(buffer.length+length <= 65536) { "GPX 文本字段过长" }; buffer.append(ch,start,length) }
            override fun endElement(uri:String?,localName:String?,qName:String?) {
                val tag=stack.last(); val parent=stack.getOrNull(stack.lastIndex-1); val value=buffer.toString().trim()
                when(tag) {
                    "name" -> if(parent=="wpt") pointName=value.take(120) else if(parent in listOf("trk","rte","metadata") && name.isBlank()) name=value.take(160)
                    "desc" -> if(parent in listOf("trk","metadata") && description.isBlank()) description=value.take(1000)
                    "ele" -> if(parent in listOf("trkpt","rtept","wpt")) elevation=value.toDoubleOrNull()?.takeIf { it.isFinite() && it in -12000.0..10000.0 }
                    "trkpt","rtept","wpt" -> {
                        val p=requireNotNull(currentPoint).copy(elevation=elevation)
                        if(tag=="wpt") { require(waypoints.size<2000); waypoints.add(Waypoint(p,pointName.ifBlank { "途经点" })) }
                        else { require(++count<=MAX_POINTS) { "轨迹超过 150,000 个点" }; if(tag=="trkpt") {require(tracks.isNotEmpty()); tracks.last().add(p)} else {require(routes.isNotEmpty()); routes.last().add(p)} }
                        currentPoint=null
                    }
                }
                stack.removeAt(stack.lastIndex); buffer.setLength(0)
            }
        })
        val segments=(if(tracks.any { it.isNotEmpty() }) tracks else routes).filter { it.isNotEmpty() }
        return Route(name.ifBlank { fallback.take(160) },segments,source,description.ifBlank { "本地 GPX · 按 WGS84 读取" },waypoints)
    }
    private fun xml(value:String):String = value.filter { it >= ' ' || it in "\n\r\t" }.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
    fun write(route: Route): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"RideWeather Android\" xmlns=\"http://www.topografix.com/GPX/1/1\"><metadata><name>${xml(route.name)}</name><desc>${xml(route.note)}</desc>")
        route.source?.let { append("<link href=\"${xml(it)}\"/>") }; append("</metadata>")
        route.waypoints.forEach { w -> append("<wpt lat=\"${w.point.lat}\" lon=\"${w.point.lon}\">"); w.point.elevation?.let { append("<ele>$it</ele>") }; append("<name>${xml(w.name)}</name></wpt>") }
        append("<trk><name>${xml(route.name)}</name>")
        route.segments.forEach { segment -> append("<trkseg>"); segment.forEach { p -> append("<trkpt lat=\"${p.lat}\" lon=\"${p.lon}\">"); p.elevation?.let { append("<ele>$it</ele>") }; append("</trkpt>") }; append("</trkseg>") }
        append("</trk></gpx>")
    }
}
