@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package io.github.zjs1925879492.rideweather

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.zjs1925879492.rideweather.core.*
import io.github.zjs1925879492.rideweather.data.Favorite
import io.github.zjs1925879492.rideweather.ui.RouteMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest
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
            listOf("路线" to "↗","沿途天气" to "☀","收藏" to "♡","设置" to "⚙").forEachIndexed { i,(name,icon) ->
                NavigationBarItem(selected=page==i,onClick={page=i},modifier=Modifier.testTag(listOf("nav_route","nav_weather","nav_favorites","nav_settings")[i]),icon={Text(icon,fontSize=23.sp)},label={Text(name)})
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
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(page) {
                    0 -> {
                        Card {
                            RouteMap(s.route,s.geoJson,s.key,s.mapEnabled,s.weather.isNotEmpty(),Modifier.fillMaxWidth().height(310.dp)) { vm.message("底图暂不可用，请检查网络及天地图 Key 授权。轨迹仍可使用。",true) }
                            Text(if(s.mapEnabled) "© 天地图 · 国家地理信息公共服务平台" else "未启用在线底图 · 可在设置中填写自己的天地图 Key",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(12.dp))
                        }
                        s.route?.let { route ->
                            Section(route.name) {
                                Text("${number(s.km)} km  /  ${route.pointCount} 点  /  ${route.segments.size} 段",fontWeight=FontWeight.SemiBold)
                                Text(route.note,style=MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick=vm::saveFavorite,enabled=!s.busy,modifier=Modifier.testTag("save_favorite")) { Text("收藏到本机") }
                                    OutlinedButton(onClick={ export.launch(route.name.replace(Regex("[\\\\/:*?\"<>|]"),"_").take(80)+".gpx") },enabled=!s.busy) { Text("导出 GPX") }
                                }
                                Button(onClick={page=1},modifier=Modifier.fillMaxWidth()) { Text("设置行程与天气") }
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
                            Text("路线在手机中解析。演示轨迹为合成数据，不用于实际骑行导航。",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    1 -> {
                        Section("安排这次出发") {
                            OutlinedTextField(s.start,{vm.field("start",it)},label={Text("出发时间 · 北京时间")},supportingText={Text("格式：2026-10-26 08:00")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=!s.busy)
                            TextButton(onClick=vm::startNow,enabled=!s.busy) { Text("使用现在的时间") }
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                PlanField("均速 km/h",s.speed,{vm.field("speed",it)},Modifier.weight(1f),!s.busy)
                                PlanField("休息 min",s.rest,{vm.field("rest",it)},Modifier.weight(1f),!s.busy)
                                PlanField("间距 km",s.spacing,{vm.field("spacing",it)},Modifier.weight(1f),!s.busy)
                            }
                            Text("按恒定均速估算，休息时间沿里程均摊；最多查询 18 个采样点。",style=MaterialTheme.typography.bodySmall)
                            Button(onClick=vm::queryWeather,enabled=s.route!=null && !s.busy,modifier=Modifier.fillMaxWidth().testTag("query_weather")) { Text("查询沿途天气") }
                        }
                        if(s.route==null) Text("请先在路线页导入 GPX，或载入演示轨迹。")
                        else {
                            Text("${s.route.name} · ${number(s.km)} km",fontWeight=FontWeight.SemiBold)
                            val plan=runCatching { vm.plan(s) }.getOrNull()
                            s.samples.forEachIndexed { i,sample -> WeatherCard(i,sample,s.weather.getOrNull(i),plan?.arrival(sample.km,s.km)) }
                            Text("预报按最近整点匹配。路线颜色表示采样点预计经过时刻的天气，以里程中点分区，并非实际雨区边界。天气数据：Open-Meteo。",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                    2 -> {
                        Text("只存在这台手机里的路线",style=MaterialTheme.typography.titleLarge)
                        Text("收藏保留完整分段和可用高程。载入时保留当前出发时间，天气需重新查询。",style=MaterialTheme.typography.bodySmall)
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
@Composable private fun Section(title:String,content:@Composable ColumnScope.()->Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold); content() } }
}
@Composable private fun PlanField(label:String,value:String,onChange:(String)->Unit,modifier:Modifier,enabled:Boolean) {
    OutlinedTextField(value,onChange,label={Text(label,fontSize=11.sp)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=modifier,enabled=enabled)
}
@Composable private fun WeatherCard(index:Int,sample:Sample,w:Weather?,eta:Long?) {
    val kind=weatherKind(w?.code)
    Section("${index+1}. ${number(sample.km)} km · ${eta?.let(::clock) ?: "时间待设置"}") {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(14.dp).background(Color(android.graphics.Color.parseColor(kind.color))))
            Text(kind.title,fontWeight=FontWeight.SemiBold)
            Text(number(w?.temp," °C"),fontWeight=FontWeight.Bold)
        }
        Text(String.format(Locale.US,"%.5f, %.5f",sample.point.lat,sample.point.lon),style=MaterialTheme.typography.labelSmall)
        if(w!=null) {
            Text("体感 ${number(w.feels," °C")}  ·  降雨概率 ${number(w.rain,"%")}  ·  降水 ${number(w.amount," mm")}")
            Text("风速 ${number(w.wind," km/h")}  ·  阵风 ${number(w.gust," km/h")}")
            if(w.wind!=null && w.direction!=null) {
                val component=headwind(w.wind,w.direction,sample.heading)
                Text("${if(component>=0) "迎风" else "顺风"}分量 ${number(kotlin.math.abs(component)," km/h")}")
            }
            Text("匹配预报：${clock(w.time)}",style=MaterialTheme.typography.labelSmall)
        } else Text("尚未查询，或预报未覆盖此时段。",style=MaterialTheme.typography.bodySmall)
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
@Suppress("DEPRECATION")
private fun signingIdentity(context:Context):String {
    val info=if(Build.VERSION.SDK_INT>=28) context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES) else context.packageManager.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES)
    val signatures=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
    fun fingerprint(bytes:ByteArray,algorithm:String)=MessageDigest.getInstance(algorithm).digest(bytes).joinToString(":") { "%02X".format(it.toInt() and 255) }
    return buildString { append("包名：${context.packageName}\n"); signatures.orEmpty().forEach { append("SHA-1：${fingerprint(it.toByteArray(),"SHA-1")}\nSHA-256：${fingerprint(it.toByteArray(),"SHA-256")}\n") } }
}
@Composable private fun SettingsPage(s:UiState,vm:MainViewModel) {
    var key by remember(s.key) { mutableStateOf(s.key) }; var show by rememberSaveable { mutableStateOf(false) }
    val context=LocalContext.current; val clipboard=LocalClipboard.current; val scope=rememberCoroutineScope()
    var showLicenses by remember { mutableStateOf(false) }
    val identity=remember(context) { runCatching { signingIdentity(context) }.getOrDefault("暂时无法读取安装包签名") }
    Section("使用你自己的天地图") {
        OutlinedTextField(key,{key=it.trim()},label={Text("天地图 Key")},modifier=Modifier.fillMaxWidth().testTag("map_key_input"),singleLine=true,enabled=!s.busy,visualTransformation=if(show) VisualTransformation.None else PasswordVisualTransformation())
        Row { Checkbox(show,{show=it}); Text("显示 Key",modifier=Modifier.padding(top=12.dp)) }
        Button(onClick={vm.enableMap(key)},enabled=!s.busy,modifier=Modifier.fillMaxWidth().testTag("verify_map")) { Text("验证并启用底图") }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={vm.disableMap()},enabled=!s.busy) { Text("关闭底图") }
            TextButton(onClick={vm.disableMap(true);key=""},enabled=!s.busy) { Text("删除本机 Key") }
        }
        Text("当前通过天地图 WMTS 瓦片服务加载矢量底图与中文注记。适用 Key 类型和权限以天地图控制台为准；这不是天地图官方 Android SDK 的签名鉴权。",style=MaterialTheme.typography.bodySmall)
        Text("Key 使用 Android Keystore 加密后保存在手机中。只有启用底图时才向天地图发送请求。",style=MaterialTheme.typography.bodySmall)
    }
    Section("应用身份") {
        Text(identity,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("signing_identity"))
        OutlinedButton(onClick={scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("应用身份",identity)));vm.message("包名和证书指纹已复制") }}) { Text("复制包名与签名指纹") }
        Text("这里读取的是当前安装包实际使用的签名证书。开发版与以后正式版的指纹可能不同。",style=MaterialTheme.typography.bodySmall)
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
