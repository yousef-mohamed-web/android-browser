[CmdletBinding()]
param(
    [switch]$SkipSdkInstall,
    [switch]$BuildRelease
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ProjectRoot

function Fail([string]$Message) { Write-Host "ERROR: $Message" -ForegroundColor Red; exit 1 }
function Find-SdkManager([string]$Sdk) {
    $candidates = @(
        (Join-Path $Sdk 'cmdline-tools\latest\bin\sdkmanager.bat'),
        (Join-Path $Sdk 'cmdline-tools\bin\sdkmanager.bat'),
        (Join-Path $Sdk 'tools\bin\sdkmanager.bat')
    )
    foreach ($p in $candidates) { if (Test-Path $p) { return $p } }
    return $null
}

Write-Host '=== GeckoBrowser APK builder for Windows ===' -ForegroundColor Cyan

# Java 17 is required by the configured Android Gradle Plugin.
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) { Fail 'Java was not found. Install Android Studio (which includes JBR) or JDK 17, then reopen this window.' }
$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch '"17\.') { Write-Warning "The detected Java version may not be 17:`n$javaVersion" }

# Resolve Android SDK from environment or common Android Studio locations.
$sdk = $env:ANDROID_SDK_ROOT
if (-not $sdk) { $sdk = $env:ANDROID_HOME }
if (-not $sdk) {
    $possible = @(
        "$env:LOCALAPPDATA\Android\Sdk",
        "$env:USERPROFILE\AppData\Local\Android\Sdk"
    )
    foreach ($p in $possible) { if (Test-Path $p) { $sdk = $p; break } }
}
if (-not $sdk -or -not (Test-Path $sdk)) {
    Fail 'Android SDK not found. In Android Studio open Tools > SDK Manager, install Android SDK Platform 36 and Android SDK Command-line Tools, then rerun this script.'
}
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
$sdkManager = Find-SdkManager $sdk
if (-not $sdkManager) { Fail "sdkmanager.bat not found under $sdk. Install Android SDK Command-line Tools (latest) from Android Studio > SDK Manager > SDK Tools." }

# Accept licenses interactively and install the exact platform/build tools/native build dependency.
if (-not $SkipSdkInstall) {
    Write-Host 'Checking/installing required Android SDK packages...' -ForegroundColor Yellow
    & $sdkManager --sdk_root=$sdk 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0' 'cmake;3.22.1' 'ndk;27.2.12479018'
    if ($LASTEXITCODE -ne 0) { Fail 'SDK package installation failed. Check internet access and SDK Manager settings.' }
}

# This starter project does not include the Gradle wrapper JAR/scripts. Bootstrap Gradle 8.13 locally.
$gradleVersion = '8.13'
$toolsDir = Join-Path $ProjectRoot '.build-tools'
$gradleHome = Join-Path $toolsDir "gradle-$gradleVersion"
$gradleExe = Join-Path $gradleHome 'bin\gradle.bat'
if (-not (Test-Path $gradleExe)) {
    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    $zip = Join-Path $toolsDir "gradle-$gradleVersion-bin.zip"
    $url = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Write-Host "Downloading Gradle $gradleVersion..." -ForegroundColor Yellow
    try { Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing } catch { Fail "Could not download Gradle. Check internet access: $($_.Exception.Message)" }
    Expand-Archive -Path $zip -DestinationPath $toolsDir -Force
}
if (-not (Test-Path $gradleExe)) { Fail 'Gradle bootstrap did not produce gradle.bat.' }

# Point Gradle at this machine's SDK without hardcoding a user path into source control.
$sdkForLocal = $sdk.Replace('\','\\')
Set-Content -Path (Join-Path $ProjectRoot 'local.properties') -Value "sdk.dir=$($sdk.Replace('\','/'))" -Encoding ASCII

Write-Host 'Running Gradle build (debug APK is signed with the standard debug key)...' -ForegroundColor Cyan
& $gradleExe --no-daemon --stacktrace clean assembleDebug
if ($LASTEXITCODE -ne 0) { Fail 'Debug APK build failed. Read the Gradle error above and share the full output for diagnosis.' }

if ($BuildRelease) {
    Write-Host 'Building release variant (may be unsigned unless a release signingConfig is added)...' -ForegroundColor Yellow
    & $gradleExe --no-daemon --stacktrace assembleRelease
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Release build failed; debug APK is still available.' }
}

$apkRoot = Join-Path $ProjectRoot 'app\build\outputs\apk'
$apks = Get-ChildItem -Path $apkRoot -Filter '*.apk' -Recurse -ErrorAction SilentlyContinue
if (-not $apks) { Fail 'Gradle reported success but no APK was found under app\build\outputs\apk.' }
$dist = Join-Path $ProjectRoot 'dist-apk'
New-Item -ItemType Directory -Force -Path $dist | Out-Null
foreach ($apk in $apks) { Copy-Item $apk.FullName (Join-Path $dist $apk.Name) -Force }
Write-Host ''
Write-Host 'BUILD COMPLETED. APK files:' -ForegroundColor Green
Get-ChildItem $dist -Filter '*.apk' | ForEach-Object { Write-Host (" - " + $_.FullName) }
Write-Host 'Install the *debug* APK for initial testing. Release signing is a separate step.' -ForegroundColor Yellow
