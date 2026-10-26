package io.github.zjs1925879492.rideweather

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.zjs1925879492.rideweather.core.*
import io.github.zjs1925879492.rideweather.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

val BEIJING:ZoneId=ZoneId.of("Asia/Shanghai")
val PLAN_FORMAT:DateTimeFormatter=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)
fun nowText():String=LocalDateTime.now(BEIJING).format(PLAN_FORMAT)
data class UiState(val route:Route?=null,val km:Double=0.0,val samples:List<Sample> = emptyList(),val weather:List<Weather?> = emptyList(),
    val geoJson:String=routeGeoJson(null,emptyList(),emptyList()),val start:String=nowText(),val speed:String="22",val rest:String="30",val spacing:String="20",
    val solarEvents:List<SolarEvent> = emptyList(),
    val key:String="",val mapEnabled:Boolean=false,val busy:Boolean=false,val status:String="导入一条路线，看看沿途的天气。",val error:Boolean=false,val favorites:List<Favorite> = emptyList())
class MainViewModel(application:Application):AndroidViewModel(application) {
    private val mutable=MutableStateFlow(UiState()); val state=mutable.asStateFlow()
    private val db=RouteStore(application); private val secrets=MapKeyStore(application); private val http=PublicHttp()
    init { viewModelScope.launch(Dispatchers.IO) { val key=secrets.get(); mutable.update { it.copy(key=key,mapEnabled=secrets.enabled(key),favorites=db.list()) } } }
    fun plan(s:UiState=state.value):RidePlan = RidePlan(LocalDateTime.parse(s.start,PLAN_FORMAT).atZone(BEIJING).toEpochSecond(),s.speed.toDoubleOrNull() ?: error("请输入有效均速"),s.rest.toDoubleOrNull() ?: error("请输入有效休息时间"),s.spacing.toDoubleOrNull() ?: error("请输入有效采样间距"))
    fun field(name:String,value:String) { if(state.value.busy) return; mutable.update { s ->
        val changed=when(name) { "start" -> s.copy(start=value); "speed" -> s.copy(speed=value); "rest" -> s.copy(rest=value); "spacing" -> s.copy(spacing=value); else -> return@update s }
        changed.copy(weather=emptyList(),solarEvents=emptyList(),geoJson=routeGeoJson(s.route,s.samples,emptyList()))
    } }
    fun startNow()=field("start",nowText())
    fun message(value:String,error:Boolean=false) { mutable.update { it.copy(status=value,error=error) } }
    private fun task(label:String,block:suspend ()->Unit) {
        if(state.value.busy) return
        mutable.update { it.copy(busy=true,status=label,error=false) }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { message(e.message?.take(220) ?: "操作失败，请重试",true) }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    private fun setRoute(route:Route) {
        val spacing=state.value.spacing.toDoubleOrNull()?.takeIf { it in 1.0..200.0 } ?: 20.0
        val samples=sampleRoute(route,spacing)
        mutable.update { it.copy(route=route,km=route.lengthKm,samples=samples,weather=emptyList(),solarEvents=emptyList(),geoJson=routeGeoJson(route,samples,emptyList()),status="已载入 ${route.name} · ${route.pointCount} 个点",error=false) }
    }
    fun demo()=task("载入演示轨迹…") { setRoute(Gpx.parse(getApplication<Application>().assets.open("demo.gpx").bufferedReader().use { it.readText() })) }
    fun importFile(uri:Uri)=task("读取手机中的 GPX…") {
        val text=requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)) { "无法打开文件" }.use { readBounded(it,MAX_FILE_BYTES).toString(Charsets.UTF_8) }
        setRoute(Gpx.parse(text))
    }
    fun importShare(text:String,coordinates:Coordinates.System)=task("手机正在直接获取公开轨迹…") { setRoute(PublicRoutes.load(text,coordinates,http)) }
    fun export(uri:Uri)=task("导出 GPX…") {
        val route=requireNotNull(state.value.route) { "请先导入路线" }
        requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri)) { "无法写入文件" }.bufferedWriter().use { it.write(Gpx.write(route)) }
        message("GPX 已导出，保留轨迹分段和可用高程。")
    }
    fun saveFavorite()=task("保存到本机收藏…") {
        val s=state.value; plan(s); db.save(requireNotNull(s.route) { "请先导入路线" },s.speed,s.rest,s.spacing)
        mutable.update { it.copy(favorites=db.list(),status="已保存到手机，相同轨迹会更新。") }
    }
    fun loadFavorite(favorite:Favorite)=task("读取收藏…") {
        val route=db.load(favorite.id)
        mutable.update { it.copy(speed=favorite.speed,rest=favorite.rest,spacing=favorite.spacing) }
        setRoute(route)
    }
    fun deleteFavorite(favorite:Favorite)=task("删除收藏…") { db.delete(favorite.id); mutable.update { it.copy(favorites=db.list(),status="已删除 ${favorite.name}") } }
    fun enableMap(key:String)=task("验证天地图 Key…") {
        val value=key.trim(); require(Regex("[a-fA-F0-9]{32}").matches(value)) { "请输入 32 位天地图 Key" }
        val point=state.value.route?.segments?.firstOrNull()?.firstOrNull() ?: TrackPoint(31.74,119.6)
        // An actual WMTS tile is probed; a 200 XML/JSON authorization error is not a valid image.
        val bytes=http.bytes(tiandituTile(value,point),2*1024*1024)
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("天地图没有返回地图图片，请核对 Key 的服务类型与授权")
        bitmap.recycle(); secrets.save(value)
        mutable.update { it.copy(key=value,mapEnabled=true,status="天地图单瓦片验证成功。Key 仅保存在手机中。") }
    }
    fun disableMap(deleteKey:Boolean=false)=task("关闭在线底图…") {
        if(deleteKey) secrets.save("") else secrets.setEnabled(false)
        mutable.update { it.copy(key=if(deleteKey) "" else it.key,mapEnabled=false,status=if(deleteKey) "已删除本机地图 Key" else "已关闭在线底图，轨迹仍可查看") }
    }
    fun queryWeather()=task("直接查询沿途天气…") {
        val s=state.value; val route=requireNotNull(s.route) { "请先导入路线" }; val p=plan(s)
        val now=Instant.now().epochSecond
        require(p.start in (now-1800)..(now+15*86400)) { "请设置未来 15 天内的出发时间，可点击“现在”" }
        val samples=sampleRoute(route,p.spacingKm)
        val coordinateKey="solar-v1;"+samples.joinToString(";") { "${it.point.lat},${it.point.lon}" }
        val hash=MessageDigest.getInstance("SHA-256").digest(coordinateKey.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
        val dir=File(getApplication<Application>().cacheDir,"forecasts").apply { mkdirs() }
        val file=File(dir,"$hash.json")
        val cached=runCatching { if(file.length() in 1..(12L*1024*1024)) JSONObject(file.readText()) else null }.getOrNull()
        var at=cached?.optLong("at") ?: 0; var cacheUsed=cached!=null && System.currentTimeMillis()-at<600_000
        var stale=false
        val raw=if(cacheUsed) cached!!.getString("forecast") else try {
            val result=WeatherService.fetch(samples,http); at=System.currentTimeMillis()
            // Validate before replacing a good cache.
            WeatherService.match(result,samples,p,s.km)
            file.writeText(JSONObject().put("at",at).put("forecast",result).toString())
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
            result
        } catch(e:Exception) {
            if(e is CancellationException) throw e
            if(cached==null) throw e
            cacheUsed=true; stale=true; cached.getString("forecast")
        }
        val weather=WeatherService.match(raw,samples,p,s.km)
        val forecasts=WeatherService.items(raw,samples.size)
        val events=solarRouteEvents(samples,forecasts,p,s.km)
        require(weather.any { it!=null }) { "当前缓存没有覆盖这次行程，请联网更新天气" }
        val acquired=Instant.ofEpochMilli(at).atZone(BEIJING).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        val label=if(stale) "联网失败，使用旧缓存" else if(cacheUsed) "使用本机缓存" else "天气已更新"
        val missing=weather.count { it==null }
        mutable.update { it.copy(samples=samples,weather=weather,solarEvents=events,geoJson=routeGeoJson(route,samples,weather,events),status="$label · 数据获取于 $acquired（北京时间）${if(missing>0) " · $missing 个点暂无预报" else ""}",error=stale) }
    }
    override fun onCleared() { db.close(); super.onCleared() }
}
