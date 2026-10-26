param([ValidateSet('auth','task','productivity','ai')][string[]]$Services = @('auth','task','productivity','ai'), [switch]$StageProductivityAtVersionTwo)
# Called only from Test-MySql.ps1 -LifecycleChecks, against its isolated MySQL.
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\ServiceTools.ps1"
if ($env:RUN_MYSQL_TESTS -ne 'true' -or -not $env:MYSQL_TEST_PASSWORD) { throw 'Isolated MySQL test environment required.' }
$selectedServices = @(Get-ServiceDefinitions | Where-Object Name -In $Services)
if (-not $selectedServices.Count) { throw 'Select at least one service for lifecycle checks.' }
foreach ($service in $selectedServices) {
    if ((Get-OwnedProcess (Read-ServiceState $service)) -or @(Get-PortOwners $service.Port).Count) {
        throw 'Lifecycle tests require unused service ports and no managed services.'
    }
}
$savedEnv = @{}
try {
    foreach ($service in $selectedServices) {
        $prefix = $service.Prefix
        $values = @{ DB_HOST='127.0.0.1'; DB_PORT=$env:MYSQL_TEST_PORT; DB_USERNAME=$env:MYSQL_TEST_USERNAME
            DB_PASSWORD=$env:MYSQL_TEST_PASSWORD; DB_SSL_MODE='DISABLED'; DB_ALLOW_PUBLIC_KEY_RETRIEVAL='true' }
        foreach ($key in $values.Keys) {
            $name = "$($prefix)_$key"
            $savedEnv[$name] = [Environment]::GetEnvironmentVariable($name)
            [Environment]::SetEnvironmentVariable($name,$values[$key],'Process')
        }
    }
    if ($StageProductivityAtVersionTwo) {
        if ($Services -notcontains 'productivity') { throw 'Version-two staging requires Productivity in the selected services.' }
        $jar = Join-Path $BackendRoot 'productivity-service\build\libs\productivity-service-0.1.0.jar'
        & java.exe -jar $jar --spring.profiles.active=migrate --spring.main.web-application-type=none --spring.flyway.target=2 --spring.flyway.clean-disabled=true --spring.flyway.baseline-on-migrate=false --spring.jpa.hibernate.ddl-auto=none --spring.sql.init.mode=never
        if ($LASTEXITCODE -ne 0) { throw 'Could not stage the isolated Productivity schema at Flyway version 2.' }
    }
    & "$PSScriptRoot\Start-AllServices.ps1" -Services $Services -NoPrompt -TimeoutSeconds 120
    $before = @(& "$PSScriptRoot\Check-Services.ps1" | Where-Object Service -In $Services)
    if (@($before | Where-Object Status -ne 'RUNNING').Count) { throw 'Not all services running.' }
    if ($StageProductivityAtVersionTwo) {
        $state = Read-ServiceState (Get-ServiceDefinitions | Where-Object Name -eq 'productivity')
        foreach ($version in @(3,4,5)) {
            $pattern = 'Migrating schema .* to version "' + $version + ' - '
            if (-not (Select-String -LiteralPath $state.Output -Pattern $pattern -Quiet)) {
                throw "Productivity startup did not apply migration V$version from the staged V2 database."
            }
        }
    }
    & "$PSScriptRoot\Start-AllServices.ps1" -Services $Services -NoPrompt
    $after = @(& "$PSScriptRoot\Check-Services.ps1" | Where-Object Service -In $Services)
    if (Compare-Object $before.ProcessId $after.ProcessId) { throw 'Duplicate start changed process IDs.' }
    foreach ($service in $selectedServices) {
        # Root is intentionally unmapped: 404 proves HTTP transport, NOT a business API.
        $request = [System.Net.HttpWebRequest]::Create("http://127.0.0.1:$($service.Port)/")
        $request.Timeout = 5000
        try { $response = $request.GetResponse() }
        catch [System.Net.WebException] {
            if (-not $_.Exception.Response) { throw }
            $response = $_.Exception.Response
        }
        try {
            if ([int]$response.StatusCode -ne 404) { throw 'Expected unmapped root HTTP 404.' }
            Write-Output "$($service.Name): HTTP 404 confirmed (no REST controller implemented)."
        } finally { $response.Dispose() }
    }
    Start-Sleep -Seconds 10
    $stable = @(& "$PSScriptRoot\Check-Services.ps1" | Where-Object Service -In $Services)
    if (@($stable | Where-Object Status -ne 'RUNNING').Count) { throw 'Service stopped unexpectedly.' }
    Write-Output "Lifecycle PASS: selected services stayed running; duplicate start preserved all PIDs: $($Services -join ', ')."
} finally {
    & "$PSScriptRoot\Stop-AllServices.ps1" -Services $Services
    foreach ($name in $savedEnv.Keys) { [Environment]::SetEnvironmentVariable($name,$savedEnv[$name],'Process') }
}
foreach ($service in $selectedServices) {
    if (@(Get-PortOwners $service.Port).Count) { throw "Port still occupied: $($service.Port)" }
}
Write-Output "Stop PASS: selected managed service ports released: $($Services -join ', ')."
