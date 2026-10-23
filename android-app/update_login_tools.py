from pathlib import Path
r=Path('../backend-spring')
p=r/'tools/Update-DevelopmentBackend.ps1'
p.write_text('''param()
$ErrorActionPreference='Stop'
$backend=Split-Path $PSScriptRoot -Parent
$services=@('auth','task','productivity')
# Credentials belong to the developer terminal, never to the Android app.
foreach($service in $services){ & "$PSScriptRoot\\Set-DatabaseEnvironment.ps1" -Service $service }
$wrapper=Join-Path $backend '..\\android-app\\gradlew.bat'
& $wrapper -p $backend :auth-service:bootJar :task-service:bootJar :productivity-service:bootJar
if($LASTEXITCODE -ne 0){throw 'Build failed. Existing services have not been stopped.'}
& "$PSScriptRoot\\Stop-AllServices.ps1" -Services $services
foreach($service in $services){
    $port=@{auth=8081;task=8082;productivity=8083}[$service]
    $custom=[Environment]::GetEnvironmentVariable($service.ToUpperInvariant()+'_PORT')
    if($custom){$port=[int]$custom}
    if(@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq $port).Count){throw "Port $port is occupied by an unmanaged process. No process was killed."}
}
foreach($service in $services){
    $jar=Join-Path $backend "$service-service\\build\\libs\\$service-service-0.1.0.jar"
    & java -jar $jar --spring.profiles.active=migrate --spring.profiles.include=default --debug=false --trace=false
    if($LASTEXITCODE -ne 0){throw "Migration failed for $service. No clean or reset attempted."}
}
if(-not $env:AUTH_SERVICE_URL){
    $authPort=if($env:AUTH_PORT){[int]$env:AUTH_PORT}else{8081}
    $env:AUTH_SERVICE_URL="http://127.0.0.1:$authPort"
}
& "$PSScriptRoot\\Start-AllServices.ps1" -Services $services -DevelopmentApi
foreach($service in $services){
    $port=@{auth=8081;task=8082;productivity=8083}[$service]
    $custom=[Environment]::GetEnvironmentVariable($service.ToUpperInvariant()+'_PORT')
    if($custom){$port=[int]$custom}
    $path=if($service -eq 'auth'){'/api/auth/me'}else{'/api/native-workspace/00000000-0000-0000-0000-000000000000'}
    $code=0
    try {$response=Invoke-WebRequest "http://127.0.0.1:$port$path" -UseBasicParsing -TimeoutSec 10;$code=[int]$response.StatusCode}
    catch {if($_.Exception.Response){$code=[int]$_.Exception.Response.StatusCode}else{throw}}
    if($code -ne 401){throw "Expected login protection (401) from $service, got $code."}
    Write-Host "$service started; unauthenticated access rejected (HTTP 401)."
}
Write-Host 'Ready. Use android-app/tools/Run-Phone.ps1 -SkipBackendUpdate, then sign in on the phone.'
''',encoding='utf-8')
