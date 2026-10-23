param([string]$Serial, [switch]$SkipBackendUpdate)
$ErrorActionPreference = 'Stop'
$app = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $app '..\backend-spring'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw 'Android SDK platform-tools/adb.exe not found.' }
$devices = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if (-not $Serial) {
    if ($devices.Count -ne 1) { throw 'Connect exactly one phone with USB debugging authorized, or specify -Serial.' }
    $Serial = $devices[0]
}
if ($Serial -notin $devices) { throw 'Selected phone is not connected or USB debugging is not authorized.' }
if (-not $SkipBackendUpdate) {
    & (Join-Path $backend 'tools\Update-DevelopmentBackend.ps1')
}
foreach ($localPort in @(8082)) {
    $configured = [Environment]::GetEnvironmentVariable('TASK_PORT')
    $remotePort = if ($configured) { [int]$configured } else { $localPort }
    & $adb -s $Serial reverse "tcp:$localPort" "tcp:$remotePort"
    if ($LASTEXITCODE -ne 0) { throw "USB port forwarding failed for $localPort." }
}
& (Join-Path $app 'gradlew.bat') -p $app assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
& $adb -s $Serial install -r (Join-Path $app 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'Install failed. Do not uninstall: existing app data must be preserved.' }
& $adb -s $Serial shell am start -n vn.edu.taskmanager/.OnboardingActivity
if ($LASTEXITCODE -ne 0) { throw 'Could not open TaskMind.' }
Write-Host 'TaskMind opened. Use the existing task/list screens. MySQL persistence requires the backend to accept the request; local changes alone do not prove server persistence.'
