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
    & (Join-Path $backend 'tools\Start-TaskMindDev.ps1')
}
foreach ($servicePort in @(@{Local=8081;Name='AUTH_PORT'},@{Local=8082;Name='TASK_PORT'},@{Local=8083;Name='PRODUCTIVITY_PORT'})) {
    $localPort = $servicePort.Local
    $configured = [Environment]::GetEnvironmentVariable($servicePort.Name)
    $remotePort = if ($configured) { [int]$configured } else { $localPort }
    $reverseOutput = @(& $adb -s $Serial reverse "tcp:$localPort" "tcp:$remotePort" 2>&1)
    $reverseExitCode = $LASTEXITCODE
    $reverseList = @(& $adb -s $Serial reverse --list 2>&1)
    $isForwarded = $LASTEXITCODE -eq 0 -and $reverseList -match "tcp:$localPort\s+tcp:$remotePort"
    if ($reverseExitCode -ne 0 -or -not $isForwarded) {
        if ($localPort -eq 8081) {
            Write-Warning "Auth Service USB reverse chưa sẵn sàng; cổng điện thoại có thể đang được ứng dụng khác sử dụng. $($reverseOutput -join ' ')"
        } else {
            throw "USB port forwarding failed for $localPort. $($reverseOutput -join ' ')"
        }
    } else {
        Write-Host "ADB reverse đã xác nhận: tcp:$localPort -> tcp:$remotePort."
    }
}
& (Join-Path $app 'gradlew.bat') -p $app assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
& $adb -s $Serial install -r (Join-Path $app 'app\build\outputs\apk\debug\app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'Install failed. Do not uninstall: existing app data must be preserved.' }
& $adb -s $Serial shell am start -n vn.edu.taskmanager/.OnboardingActivity
if ($LASTEXITCODE -ne 0) { throw 'Could not open TaskMind.' }
Write-Host 'TaskMind opened. Use the existing task/list screens. MySQL persistence requires the backend to accept the request; local changes alone do not prove server persistence.'
