# 开发准备

## 必需环境

1. Android Studio 稳定版：用于工程管理、布局预览、调试和 SDK 管理。
2. Android SDK：所选 compileSdk 对应的 SDK Platform、兼容 Build-Tools、Platform-Tools 和 Command-line Tools。版本随工程锁定，不要求安装全部历史版本。
3. 兼容的 JDK：优先检查现有 JDK 或 Android Studio 自带 JDK，与 Gradle / Android Gradle Plugin 匹配。
4. Git 与可写的新仓库目录。Gradle 使用工程自带 Wrapper，不必全局安装 Gradle 或 Kotlin。
5. 允许访问 Google Maven、Maven Central、Gradle 分发站和 Android SDK 下载站的网络。
6. 至少一个测试设备：安卓真机或 Emulator。真机联调需开启开发者选项和 USB 调试，并由设备主人确认本机授权。

不需要为本项目安装 Python 环境、Node.js 后端、数据库服务器或云服务器。

## 推荐测试条件

- 一台实际使用的安卓手机，用于地图、文件分享入口和顽鹿 / 行者网络联调。
- 一个可正常启动的模拟器，用于不同屏幕尺寸和 Android 版本回归。
- 先为 SDK、模拟器镜像、依赖缓存预留约 30–50 GB，实际需求随镜像数量变化；这是项目规划值，不是官方最低要求。
- 天地图开发者账户与适用的测试 Key。用户自行完成账号验证；Key 只填写在本地配置或 App 中。

## Skills / MCP

- 不以额外 skill 或 MCP 为开发前提。文件、终端、Git、Gradle 和 ADB 足以支持编码、构建、安装、日志及大部分调试工作。
- 已有 GitHub 连接可用于代码和 PR 协作；其仓库访问范围需包含新项目。
- 后续如需要更高效的设备截图、点击和 UI 自动化，可评估基于 ADB 的设备控制 MCP；先验证兼容性和可用能力，再决定是否安装。
- 项目约定保存在根目录 AGENTS.md。只有产生稳定、重复的专门流程后，才考虑提炼成 skill。

## 构建与发布

- 初期先验证本机构建和安装，再配置自动检查；不把 GitHub Actions 当作应用运行时服务。
- 正式签名私钥单独保管，不进入 Git。开源代码不包含发布私钥。
- 应用包名尚未确定，应在申请绑定地图 Key 和首次正式发布前固定。
- 若使用应用商店托管签名，应按用户实际安装包的签名证书配置地图授权，不混用上传证书。

## 初始化检查记录（2026-10-26）

- 已找到 Git、Microsoft OpenJDK 21.0.8 和 ADB 33.0.0。
- PATH 中的默认 java 调用未能正常完成；直接调用已安装 Microsoft JDK 的 java 可以读取版本。后续构建应明确选择兼容 JDK。
- 配置中存在 Android SDK 路径，但该目录不存在；默认用户 SDK 目录及常见 Android Studio 安装位置均未找到。尚不能认为具有可构建的 Android 环境。
- 尚未执行 Android 构建、模拟器启动或真机联调。

参考：[Android Studio 安装](https://developer.android.com/studio/install)、[应用签名](https://developer.android.com/studio/publish/app-signing)。
