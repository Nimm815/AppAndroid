param()
$ErrorActionPreference='Stop'
$backend=Split-Path $PSScriptRoot -Parent
& "$PSScriptRoot\Set-DatabaseEnvironment.ps1" -Service task
& "$PSScriptRoot\Set-DatabaseEnvironment.ps1" -Service productivity
$wrapper=Join-Path $backend '..\android-app\gradlew.bat'
$services=@(
    @{Name='task';Module='task-service';Prefix='TASK'},
    @{Name='productivity';Module='productivity-service';Prefix='PRODUCTIVITY'}
)
& $wrapper -p $backend :task-service:bootJar :productivity-service:bootJar
if($LASTEXITCODE -ne 0){throw 'Build failed. Existing services were not stopped.'}
& "$PSScriptRoot\Stop-AllServices.ps1" -Services @('task','productivity')
foreach($servicePort in @(8082,8083)) {
    if($servicePort -eq 8082 -and $env:TASK_PORT){$servicePort=[int]$env:TASK_PORT}
    if($servicePort -eq 8083 -and $env:PRODUCTIVITY_PORT){$servicePort=[int]$env:PRODUCTIVITY_PORT}
    if(@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq $servicePort).Count){throw "Port $servicePort occupied by unmanaged process."}
}
foreach($service in $services) {
    $prefix=$service.Prefix
    $portName="${prefix}_PORT"
    $port=if([Environment]::GetEnvironmentVariable($portName)){[int][Environment]::GetEnvironmentVariable($portName)}elseif($prefix -eq 'TASK'){8082}else{8083}
    $jar=Join-Path $backend "$($service.Module)\build\libs\$($service.Module)-0.1.0.jar"
    & java -jar $jar --spring.profiles.active=migrate --spring.profiles.include=default --debug=false --trace=false
    if($LASTEXITCODE -ne 0){throw "$($service.Name) migration failed. No data reset attempted."}
}
$oldLocal=$env:TASKMIND_LOCAL_WORKSPACE
$oldAddress=$env:SERVER_ADDRESS
try {
    $env:TASKMIND_LOCAL_WORKSPACE='true'
    $env:SERVER_ADDRESS='127.0.0.1'
    & "$PSScriptRoot\Start-AllServices.ps1" -Services @('task','productivity') -DevelopmentApi
} finally {
    $env:TASKMIND_LOCAL_WORKSPACE=$oldLocal
    $env:SERVER_ADDRESS=$oldAddress
}
$productivityPort=if($env:PRODUCTIVITY_PORT){[int]$env:PRODUCTIVITY_PORT}else{8083}
$response=Invoke-WebRequest "http://127.0.0.1:$productivityPort/api/habits" -UseBasicParsing -TimeoutSec 10
if([int]$response.StatusCode -ne 200){throw 'Productivity habit API did not return HTTP 200.'}
$taskPort=if($env:TASK_PORT){[int]$env:TASK_PORT}else{8082}
$taskResponse=Invoke-WebRequest "http://127.0.0.1:$taskPort/api/lists" -UseBasicParsing -TimeoutSec 10
if([int]$taskResponse.StatusCode -ne 200){throw 'Task list API did not return HTTP 200; Task and list synchronization is not ready.'}
Write-Host 'Task and Productivity APIs are ready on localhost. Local development APIs use UserID 1; do not expose them to a network.'
