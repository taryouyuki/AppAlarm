<#
  Builds AppAlarm.apk without Gradle, using only the Android SDK build-tools.
  Why: this machine has JDK 26 (too new for the Gradle/AGP versions cached here)
  and HTTPS egress is blocked, so a plain javac -> d8 -> aapt -> apksigner path
  is the reliable way to produce an installable APK.
#>
param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$BuildToolsVersion = "34.0.0",
    # D8 8.2.2 (build-tools 34) crashes with an NPE on these class files, so dexing
    # uses the newer R8/d8 that ships with build-tools 36.1.0.
    [string]$DexBuildToolsVersion = "36.1.0",
    [string]$Platform = "android-34",
    [string]$JdkBin = "$env:JAVA_HOME\bin",
    [string]$Keystore = "$env:USERPROFILE\.android\debug.keystore",
    [switch]$KeepIntermediate
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$bt = Join-Path $Sdk "build-tools\$BuildToolsVersion"
$d8bt = Join-Path $Sdk "build-tools\$DexBuildToolsVersion"
$androidJar = Join-Path $Sdk "platforms\$Platform\android.jar"
$out = Join-Path $root "build"

foreach ($p in @($bt, $d8bt, $androidJar)) {
    if (-not (Test-Path $p)) { throw "Not found: $p" }
}

if (Test-Path $out) { Remove-Item $out -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$out\classes", "$out\dex", "$out\gen" | Out-Null

$resDir = Join-Path $root "res"
$genDir = Join-Path $out "gen"

# 0) resources -> R.java + packaged resource table
$resArgs = ""
if (Test-Path $resDir) {
    Write-Host "[0/5] packaging resources"
    & cmd /c "`"$bt\aapt.exe`" package -f -m -J `"$genDir`" -S `"$resDir`" -M `"$root\AndroidManifest.xml`" -I `"$androidJar`" 2>&1"
    if ($LASTEXITCODE -ne 0) { throw "aapt resource packaging failed ($LASTEXITCODE)" }
    $resArgs = "-S `"$resDir`""
}

# 1) javac against android.jar (source/target 8, as the platform expects)
$sources = @(Get-ChildItem (Join-Path $root "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if (Test-Path $genDir) {
    $sources += @(Get-ChildItem $genDir -Recurse -Filter *.java | ForEach-Object { $_.FullName })
}
Write-Host "[1/5] compiling $($sources.Count) java files"
& javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath $androidJar -d "$out\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)" }

# 2) dex
Write-Host "[2/5] dexing"
& "$JdkBin\jar.exe" cf "$out\classes.jar" -C "$out\classes" .
& cmd /c "`"$d8bt\d8.bat`" --lib `"$androidJar`" --min-api 26 --output `"$out\dex`" `"$out\classes.jar`""
if ($LASTEXITCODE -ne 0) { throw "d8 failed ($LASTEXITCODE)" }

# 3) manifest + resources
Write-Host "[3/5] packaging manifest"
& cmd /c "`"$bt\aapt.exe`" package -f -M `"$root\AndroidManifest.xml`" $resArgs -I `"$androidJar`" -F `"$out\unsigned.apk`""
if ($LASTEXITCODE -ne 0) { throw "aapt package failed ($LASTEXITCODE)" }

# 4) add classes.dex, align
Write-Host "[4/5] adding dex + aligning"
Copy-Item "$out\dex\classes.dex" "$out\classes.dex" -Force
Push-Location $out
try {
    & cmd /c "`"$bt\aapt.exe`" add `"$out\unsigned.apk`" classes.dex"
} finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { throw "aapt add failed ($LASTEXITCODE)" }
& cmd /c "`"$bt\zipalign.exe`" -f 4 `"$out\unsigned.apk`" `"$out\aligned.apk`""
if ($LASTEXITCODE -ne 0) { throw "zipalign failed ($LASTEXITCODE)" }

# 5) sign
Write-Host "[5/5] signing"
if (-not (Test-Path $Keystore)) { throw "No debug keystore at $Keystore" }
$apk = Join-Path $out "AppAlarm.apk"
& cmd /c "`"$bt\apksigner.bat`" sign --ks `"$Keystore`" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey --out `"$apk`" `"$out\aligned.apk`" 2>&1" |
    Where-Object { $_ -notmatch "WARNING|Restricted methods|^$" }
if ($LASTEXITCODE -ne 0) { throw "apksigner failed ($LASTEXITCODE)" }
& cmd /c "`"$bt\apksigner.bat`" verify `"$apk`" 2>&1" | Where-Object { $_ -notmatch "WARNING|Restricted methods|^$" }
if ($LASTEXITCODE -ne 0) { throw "signature verification failed" }

if (-not $KeepIntermediate) {
    Remove-Item "$out\classes", "$out\dex", "$out\classes.jar", "$out\classes.dex", "$out\unsigned.apk", "$out\aligned.apk" -Recurse -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "APK: $apk ($([math]::Round((Get-Item $apk).Length / 1KB, 1)) KB)"
