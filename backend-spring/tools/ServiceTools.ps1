$BackendRoot = Split-Path $PSScriptRoot -Parent
$RuntimeRoot = Join-Path $BackendRoot 'build\services'
function Get-ServiceDefinitions {
    $index = 0
    foreach ($name in @('auth','task','productivity','ai')) {
        $prefix = $name.ToUpperInvariant()
        $port = 8081 + $index
        $custom = [Environment]::GetEnvironmentVariable("$($prefix)_PORT")
        if ($custom) { $port = [int]$custom }
        if ($port -lt 1 -or $port -gt 65535) { throw "Invalid port for $name" }
        [pscustomobject]@{ Name=$name; Prefix=$prefix; Port=$port; State=(Join-Path $RuntimeRoot "$name.json") }
        $index++
    }
}
function Enter-LifecycleLock {
    New-Item -ItemType Directory -Path $RuntimeRoot -Force | Out-Null
    try { [IO.File]::Open((Join-Path $RuntimeRoot 'lifecycle.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch { throw 'Another lifecycle command is running.' }
}
function Get-OwnedProcess($state) {
    if (-not $state) { return $null }
    $process = Get-Process -Id $state.ProcessId -ErrorAction SilentlyContinue
    if (-not $process) { return $null }
    if ($process.StartTime.ToUniversalTime().Ticks.ToString() -ne $state.StartTicks) { return $null }
    $info = Get-CimInstance Win32_Process -Filter "ProcessId=$($state.ProcessId)" -ErrorAction Stop
    if ($info.Name -notin @('java.exe','javaw.exe') -or -not $info.CommandLine) { return $null }
    if (-not $info.CommandLine.Contains("-Dtaskmind.launch=$($state.Token) ") -or
        -not $info.CommandLine.Contains('"' + $state.Jar + '"')) { return $null }
    return $process
}
function Read-ServiceState($service) {
    if (Test-Path -LiteralPath $service.State) { Get-Content -LiteralPath $service.State -Raw | ConvertFrom-Json }
}
function Get-PortOwners([int]$port) {
    @(Get-NetTCPConnection -State Listen -ErrorAction Stop |
        Where-Object LocalPort -eq $port | Select-Object -ExpandProperty OwningProcess -Unique)
}
function Test-ServiceReady($state) {
    $process = Get-OwnedProcess $state
    if (-not $process) { return $false }
    if ($process.Id -notin @(Get-PortOwners $state.Port)) { return $false }
    if (-not (Test-Path -LiteralPath $state.Output)) { return $false }
    return [bool](Select-String -LiteralPath $state.Output -Pattern 'Started \w+Application in ' -Quiet)
}
