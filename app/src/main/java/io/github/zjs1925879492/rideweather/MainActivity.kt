@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.zjs1925879492.rideweather

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.zjs1925879492.rideweather.core.*
import io.github.zjs1925879492.rideweather.data.Favorite
import io.github.zjs1925879492.rideweather.ui.RouteMap
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity:ComponentActivity() {
    private val shared=MutableStateFlow("")
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); receive(intent)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF234E40),secondary=Color(0xFF8B6339),background=Color(0xFFF4F5EF),surface=Color(0xFFFAFBF5),surfaceVariant=Color(0xFFE4EADB))) {
                val vm:MainViewModel=viewModel()
                val s by vm.state.collectAsStateWithLifecycle()
                val text by shared.collectAsStateWithLifecycle()
                RideApp(vm,s,text)
            }
        }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent); receive(intent) }
    private fun receive(intent:Intent?) { if(intent?.action==Intent.ACTION_SEND) shared.value=intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(5000) }
}
private fun number(value:Double?,suffix:String=""):String = value?.let { String.format(Locale.CHINA,"%.1f%s",it,suffix) } ?: "—"
private fun clock(epoch:Long):String = Instant.ofEpochSecond(epoch).atZone(BEIJING).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

@Composable
private fun RideApp(vm:MainViewModel,s:UiState,shared:String) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var link by rememberSaveable { mutableStateOf("") }
    var system by rememberSaveable { mutableStateOf(Coordinates.System.BD09.name) }
    LaunchedEffect(shared) { if(shared.isNotBlank()) { link=shared; page=0 } }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importFile) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { it?.let(vm::export) }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,
        contentWindowInsets=WindowInsets.safeDrawing,
        bottomBar={ NavigationBar {
            listOf("路线" to "","沿途天气" to "☀","收藏" to "","设置" to "⚙").forEachIndexed { i,(name,icon) ->
                NavigationBarItem(selected=page==i,onClick={page=i},modifier=Modifier.testTag(listOf("nav_route","nav_weather","nav_favorites","nav_settings")[i]),icon={
                    when(i) {
                        0 -> Icon(painterResource(R.drawable.ic_bicycle),contentDescription=null,modifier=Modifier.size(24.dp))
                        2 -> Icon(painterResource(R.drawable.ic_star),contentDescription=null,modifier=Modifier.size(24.dp))
                        else -> Text(icon,fontSize=23.sp)
                    }
                },label={Text(name)})
            }
        } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).semantics { testTagsAsResourceId=true }) {
            Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("沿途",fontSize=30.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
                Text("骑行天气 · 开发预览",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(top=12.dp))
            }
            if(s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Surface(color=if(s.error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp),shape=MaterialTheme.shapes.medium) {
                Text(s.status,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(12.dp).testTag("status"))
            }
            key(page) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(page) {
                    0 -> {
                        Card {
                            RouteMap(s.route,s.geoJson,s.key,s.mapEnabled,false,Modifier.fillMaxWidth().height(310.dp).testTag("route_map")) { vm.message("底图暂不可用，请检查网络及天地图 Key 授权。轨迹仍可使用。",true) }
                            Text(if(s.mapEnabled) "© 天地图 · 国家地理信息公共服务平台" else "未启用在线底图 · 可在设置中填写自己的天地图 Key",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(12.dp))
                        }
                        s.route?.let { route ->
                            Section(route.name) {
                                Text("${number(s.km)} km  /  ${route.pointCount} 点  /  ${route.segments.size} 段",fontWeight=FontWeight.SemiBold)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick=vm::saveFavorite,enabled=!s.busy,modifier=Modifier.testTag("save_favorite")) { Text("收藏到本机") }
                                    OutlinedButton(onClick={ export.launch(route.name.replace(Regex("[\\\\/:*?\"<>|]"),"_").take(80)+".gpx") },enabled=!s.busy) { Text("导出 GPX") }
                                }
                            }
                        }
                        Section("带上你的路线") {
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={ import.launch(arrayOf("*/*")) },enabled=!s.busy) { Text("导入 GPX") }
                                OutlinedButton(onClick=vm::demo,enabled=!s.busy,modifier=Modifier.testTag("load_demo")) { Text("载入演示") }
                            }
                            OutlinedTextField(link,{link=it},label={Text("顽鹿 / 行者公开分享链接")},modifier=Modifier.fillMaxWidth().testTag("share_input"),enabled=!s.busy,minLines=2,maxLines=4)
                            if(link.contains("onelap.cn")) {
                                Text("顽鹿源坐标：默认选项来自既有样本推断，请按路线实际情况选择。",style=MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Coordinates.System.entries.forEach { coordinateSystem -> FilterChip(selected=system==coordinateSystem.name,onClick={system=coordinateSystem.name},label={Text(coordinateSystem.name)},enabled=!s.busy) }
                                }
                            }
                            Button(onClick={vm.importShare(link,Coordinates.System.valueOf(system))},enabled=!s.busy && link.isNotBlank(),modifier=Modifier.fillMaxWidth().testTag("import_share")) { Text("直接导入公开路线") }
                        }
                        Section("安排这次出发") {
                            OutlinedTextField(s.start,{vm.field("start",it)},label={Text("出发时间 · 北京时间")},supportingText={Text("格式：2026-10-26 08:00")},modifier=Modifier.fillMaxWidth().testTag("plan_start"),singleLine=true,enabled=!s.busy)
                            TextButton(onClick=vm::startNow,enabled=!s.busy) { Text("使用现在的时间") }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                PlanField("均速 km/h",s.speed,{vm.field("speed",it)},Modifier.weight(1f).testTag("plan_speed"),!s.busy)
                                PlanField("休息 min",s.rest,{vm.field("rest",it)},Modifier.weight(1f).testTag("plan_rest"),!s.busy)
                                PlanField("间距 km",s.spacing,{vm.field("spacing",it)},Modifier.weight(1f),!s.busy)
                            }
                            Text("按恒定均速估算，休息时间沿里程均摊；最多查询 18 个采样点。",style=MaterialTheme.typography.bodySmall)
                            Button(onClick={vm.queryWeather();page=1},enabled=s.route!=null && !s.busy,modifier=Modifier.fillMaxWidth().testTag("query_weather")) { Text("查询沿途天气并查看") }
                        }
                    }
                    1 -> {
                        if(s.route==null) Text("请先在路线页导入 GPX，或载入演示轨迹。")
                        else {
                            Text("${s.route.name} · ${number(s.km)} km",fontWeight=FontWeight.SemiBold)
                            Card {
                                RouteMap(s.route,s.geoJson,s.key,s.mapEnabled,s.weather.isNotEmpty(),Modifier.fillMaxWidth().height(310.dp).testTag("weather_map")) { vm.message("底图暂不可用，请检查网络及天地图 Key 授权。轨迹仍可使用。",true) }
                                Text(if(s.mapEnabled) "© 天地图 · 沿途天气分区" else "沿途天气分区 · 未启用在线底图",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(12.dp))
                            }
                            OutlinedButton(onClick={page=0},modifier=Modifier.testTag("edit_plan")) { Text("返回路线页调整行程") }
                            if(s.weather.isEmpty()) Text(if(s.busy) "正在查询沿途天气…" else "尚无本次行程天气，请返回路线页查询。")
                            val plan=runCatching { vm.plan(s) }.getOrNull()
                            weatherTimeline(s.samples,s.solarEvents).forEach { entry -> when(entry) {
                                is WeatherTimelineEntry.Sun -> SolarEventCard(entry.event)
                                is WeatherTimelineEntry.Stop -> WeatherCard(s.samples[entry.index],s.weather.getOrNull(entry.index),plan?.arrival(entry.km,s.km))
                            }
                            }
                            Text("预报按最近整点匹配。路线颜色表示采样点预计经过时刻的天气，以里程中点分区，并非实际雨区边界。天气数据：Open-Meteo。",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    2 -> {
                        if(s.favorites.isEmpty()) Text("还没有收藏。导入路线后，点击“收藏到本机”。")
                        s.favorites.forEach { favorite -> FavoriteCard(favorite,s.busy,onLoad={vm.loadFavorite(favorite);page=0},onDelete={vm.deleteFavorite(favorite)}) }
                    }
                    3 -> SettingsPage(s,vm)
                }
                Spacer(Modifier.height(8.dp))
            }
            }
        }
    }
}
@Composable private fun Section(title:String,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold); content() } }
}
@Composable private fun PlanField(label:String,value:String,onChange:(String)->Unit,modifier:Modifier,enabled:Boolean) {
    OutlinedTextField(value,onChange,label={Text(label,fontSize=11.sp)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=modifier,enabled=enabled)
}
@Composable private fun SolarEventCard(event:SolarEvent) {
    val title=if(event.type=="sunrise") "☀↑ 日出" else "☀↓ 日落"
    Section("$title · ${clock(event.time)}") {
        Text("${number(event.km)} km · ${when(event.phase) { "before" -> "出发前 30 分钟内"; "after" -> "结束后 30 分钟内"; else -> "预计骑行途中" }}",modifier=Modifier.testTag("solar_event"))
        Text(when {
            event.type=="sunset" && event.phase=="during" -> "日落后预计仍有 ${event.remainingMinutes} 分钟行程，建议准备车灯。"
            event.type=="sunrise" && event.phase=="during" -> "日出前的路段需要照明，建议准备车灯。"
            event.type=="sunset" && event.phase=="before" -> "出发时已在日落后，建议准备车灯。"
            else -> "请为光照变化预留行程余量。"
        },style=MaterialTheme.typography.bodySmall)
        Text("北京时间 · 按沿途天文时间估算，实际光照受天气与地形影响。",style=MaterialTheme.typography.labelSmall)
    }
}
@Composable private fun WeatherMetric(label:String,value:String,modifier:Modifier=Modifier) {
    Row(modifier,horizontalArrangement=Arrangement.spacedBy(5.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,softWrap=false)
        Text(value,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Medium,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
    }
}
@Composable internal fun WeatherCard(sample:Sample,w:Weather?,eta:Long?) {
    val kind=weatherKind(w?.code)
    Card(Modifier.fillMaxWidth().testTag("weather_card")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("${number(sample.km)} km",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,softWrap=false,modifier=Modifier.weight(1f).testTag("weather_mileage"))
                Text(eta?.let(::clock) ?: "时间待设置",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,softWrap=false,modifier=Modifier.testTag("weather_eta"))
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(Color(android.graphics.Color.parseColor(kind.color))))
                Text("${kind.title}  ${number(w?.temp," °C")}",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium,maxLines=1,softWrap=false,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Text("体感 ${number(w?.feels," °C")}",style=MaterialTheme.typography.bodySmall,maxLines=1,softWrap=false)
            }
            if(w!=null) {
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    WeatherMetric("降雨概率",number(w.rain,"%"),Modifier.weight(1f))
                    WeatherMetric("降水",number(w.amount," mm"),Modifier.weight(1f))
                }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    WeatherMetric("风速",number(w.wind," km/h"),Modifier.weight(1f))
                    WeatherMetric("阵风",number(w.gust," km/h"),Modifier.weight(1f))
                }
                if(w.wind!=null && w.direction!=null) {
                    val component=headwind(w.wind,w.direction,sample.heading)
                    WeatherMetric(if(component>=0) "迎风分量" else "顺风分量",number(kotlin.math.abs(component)," km/h"))
                }
            } else Text("尚未查询，或预报未覆盖此时段。",style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun FavoriteCard(favorite:Favorite,busy:Boolean,onLoad:()->Unit,onDelete:()->Unit) {
    var confirm by remember { mutableStateOf(false) }
    Section(favorite.name) {
        Text("${number(favorite.km)} km · ${favorite.points} 个点")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=onLoad,enabled=!busy) { Text("载入") }
            TextButton(onClick={confirm=true},enabled=!busy) { Text("删除") }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("删除这条收藏？")},text={Text(favorite.name)},confirmButton={TextButton(onClick={confirm=false;onDelete()}) { Text("删除") }},dismissButton={TextButton(onClick={confirm=false}) { Text("取消") }})
}
@Composable private fun SettingsPage(s:UiState,vm:MainViewModel) {
    var key by remember(s.key) { mutableStateOf(s.key) }; var show by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current
    var showLicenses by remember { mutableStateOf(false) }
    Section("使用你自己的天地图") {
        OutlinedTextField(key,{key=it.trim()},label={Text("天地图 Key")},modifier=Modifier.fillMaxWidth().testTag("map_key_input"),singleLine=true,enabled=!s.busy,visualTransformation=if(show) VisualTransformation.None else PasswordVisualTransformation())
        Row { Checkbox(show,{show=it}); Text("显示 Key",modifier=Modifier.padding(top=12.dp)) }
        Button(onClick={vm.enableMap(key)},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("verify_map")) { Text("验证并启用底图") }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={vm.disableMap()},enabled=!s.busy) { Text("关闭底图") }
            TextButton(onClick={vm.disableMap(true);key=""},enabled=!s.busy) { Text("删除本机 Key") }
        }
    }
    Section("数据与连接") {
        Text("路线、行程计算、收藏及导出在手机上完成。公开路线、天气和地图由手机直接请求第三方服务，没有我们的中转服务器。")
        Text("查询天气会发送沿途采样坐标；启用地图会发送瓦片范围和你填写的 Key。应用不请求定位权限，也不含统计 SDK。",style=MaterialTheme.typography.bodySmall)
        Text("天气：Open-Meteo，免费接口限个人非商业用途。地图：天地图。MapLibre Native © MapLibre contributors，BSD-2-Clause。坐标转换参考 gcoord（MIT）。",style=MaterialTheme.typography.bodySmall)
        TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://open-meteo.com/")))}) { Text("天气数据来源：Open-Meteo.com") }
        TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.tianditu.gov.cn/")))}) { Text("地图数据来源：天地图") }
        Text("沿途 ${BuildConfig.VERSION_NAME} · 开发预览",style=MaterialTheme.typography.labelSmall)
        TextButton(onClick={showLicenses=true}) { Text("查看开源许可") }
    }
    if(showLicenses) {
        val text=remember(context) { listOf("rideweather.txt","maplibre.txt","gcoord.txt","apache-2.0.txt").joinToString("\n\n") { name -> context.assets.open("licenses/$name").bufferedReader().use { it.readText() } } }
        AlertDialog(onDismissRequest={showLicenses=false},title={Text("开源许可")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) { Text(text,style=MaterialTheme.typography.bodySmall) }},confirmButton={TextButton(onClick={showLicenses=false}) { Text("关闭") }})
    }
}
