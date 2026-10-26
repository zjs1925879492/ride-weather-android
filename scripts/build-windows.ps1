param(
    [Parameter(Mandatory=$true)][string]$WorkDirectory,
    [string]$SdkDirectory=$env:ANDROID_HOME,
    [string]$JdkDirectory=$env:JAVA_HOME,
    [switch]$LiveNetwork
)
$ErrorActionPreference='Stop'
$taskWorkDirectory=[IO.Path]::GetFullPath($WorkDirectory)
if($taskWorkDirectory.ToCharArray() | Where-Object {[int]$_ -gt 127}) { throw 'Choose an ASCII-only build working directory.' }
if(-not(Test-Path -LiteralPath (Join-Path $SdkDirectory 'platforms\android-36\android.jar'))) { throw 'Install Android SDK Platform 36 and set ANDROID_HOME, or pass -SdkDirectory.' }
if(-not(Test-Path -LiteralPath (Join-Path $JdkDirectory 'bin\java.exe'))) { throw 'Set JAVA_HOME or pass -JdkDirectory.' }
$env:JAVA_HOME=$JdkDirectory
$env:ANDROID_HOME=$SdkDirectory
$env:GRADLE_USER_HOME=Join-Path $taskWorkDirectory 'gradle-home'
$env:RIDEWEATHER_BUILD_ROOT=Join-Path $taskWorkDirectory 'outputs'
$env:RIDEWEATHER_LIVE_TESTS=if($LiveNetwork){'true'}else{'false'}
$taskProjectDirectory=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
Push-Location -LiteralPath $taskProjectDirectory
try {
    & '.\gradlew.bat' :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
    if($LASTEXITCODE -ne 0){throw "Build/check failed ($LASTEXITCODE)."}
    Write-Output ('APK: '+(Join-Path $env:RIDEWEATHER_BUILD_ROOT 'app\outputs\apk\debug\app-debug.apk'))
} finally { Pop-Location }
