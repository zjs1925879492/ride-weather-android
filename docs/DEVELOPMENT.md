# 构建与真机测试

## 环境

- JDK 21，Android SDK Platform 36、Build-Tools 36.0.0、Platform-Tools。
- Gradle 8.13 Wrapper、AGP 8.13.2、Kotlin 2.2.21 已在工程固定。
- arm64-v8a、Android 8.0 / API 26 及以上设备；目标与编译 API 36。
- 不需要全局 Gradle、Kotlin、Python、Node.js、NDK 或本项目云服务器。
- 首次构建需要访问 Google Maven、Maven Central 和 Gradle 分发站。

设置 JAVA_HOME 与 ANDROID_HOME。也可用不提交的 local.properties 指定 SDK；Windows 盘符冒号需按 Java properties 规则转义。Windows 中文用户路径可能导致 JVM 工作进程加载失败，脚本将缓存与编译输出放在指定的纯 ASCII 目录。

```powershell
.\scripts\build-windows.ps1 -WorkDirectory C:\AndroidBuild\RideWeather
```

执行 APK 构建、JVM 单元测试与 lint。APK 位于指定工作目录的 outputs/app/outputs/apk/debug/app-debug.apk。加 -LiveNetwork 启用公开第三方接口测试，默认跳过联网测试。

交付精简包时加 `-Optimized`，生成 `outputs/app/outputs/apk/preview/app-preview.apk`。`preview` 基于发布构建启用 R8 代码优化和资源裁剪，非 debuggable，使用已有开发签名兼容测试安装；`release` 不内置正式签名。Compose 预览 / 调试工具依赖已移除。所有变体只包含 arm64；不再支持 x86 模拟器或 32 位手机。

也可直接运行 `./gradlew :app:assemblePreview :app:testDebugUnitTest :app:lintPreview`。JVM 测试验证业务逻辑，优化 APK 的地图 JNI、启动及导入流程仍需独立真机验收。保留 R8 mapping 与配置报告用于定位混淆后的错误，不将其作为运行时资源打入 APK。

其他系统可设置 SDK、JDK 后使用 `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`。RIDEWEATHER_BUILD_ROOT 可选，用于指定编译输出根目录。

## 真机原生回归

先通过 USB 或无线 ADB 配对并连接自己的测试设备，确认 `adb devices -l` 显示 device。无线配对端口与连接端口不同，IP、端口、配对码和设备日志不要提交。

沿用上述构建环境；手工调用 Wrapper 时先设置 JAVA_HOME、ANDROID_HOME、GRADLE_USER_HOME，需要时设置 RIDEWEATHER_BUILD_ROOT。多设备时用 ANDROID_SERIAL 指定目标。解锁手机并保持亮屏，然后执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

测试会安装开发 APK 与测试 APK，使用 Compose 语义节点执行操作和断言，无需逐帧识图。包含真实第三方请求，服务变化或断网会使相关用例失败；天气查询可能命中应用缓存。收藏用例只清理本次新增的演示收藏。

该 Gradle 流程可能在结束时卸载被测应用，建议使用专用测试设备。需要保留日常使用设备的 Key 和收藏时，先构建两个 APK，再用 `adb install -r` 覆盖安装，执行 `adb shell am instrument -w io.github.zjs1925879492.rideweather.test/androidx.test.runner.AndroidJUnitRunner`，不要卸载被测应用或清除其数据。地图持久化用例使用单独测试偏好文件，不替换用户 Key。

报告在编译输出根目录的 app/reports/androidTests/connected；JVM 报告在 app/reports/tests/testDebugUnitTest。UI 使用稳定 testTag 并向 UiAutomator 暴露资源标识。系统文件选择器、分享入口等跨应用流程可后续使用 UiAutomator；地图绘制与视觉布局仍需截图验收。

## Skills / MCP

当前原生回归无需额外 MCP。Gradle、ADB、Compose Test 足够运行固定用例；MCP 可作为探索式设备操作入口，但本身不保证更快或更稳定。可选评估 [Android CLI](https://developer.android.com/tools/agents/android-cli) 与 [Mobile MCP](https://github.com/mobile-next/mobile-mcp)，未将其设为工程依赖。

## 架构与密钥

core 负责轨迹、坐标、时间与天气匹配；data 负责有界网络请求与本地存储；MainViewModel 管理异步状态；Compose 与原生 MapLibre 负责呈现。无项目运行时后端。

天地图接入 WMTS 服务，用户需自行提供相应权限的 Key。Key 使用 AndroidKeyStore 加密保存，排除备份，不写入源码或日志。地图验证不能替代服务授权核验。应用无定位权限。

发布私钥不进入 Git。开发签名与商店分发签名不同，需要证书指纹时应使用 apksigner 等工具读取实际 APK，授权时按实际安装版本核对。设置页不再展示应用身份。第三方许可见 THIRD_PARTY_NOTICES.md。
