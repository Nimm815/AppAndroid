# Runs only inside Test-MySql.ps1 -ApiChecks, never against the user's databases.
$ErrorActionPreference='Stop'
if ($env:RUN_MYSQL_TESTS -ne 'true' -or -not $env:MYSQL_TEST_PASSWORD) { throw 'Isolated database required.' }
$backend=Split-Path $PSScriptRoot -Parent
$runDir=Join-Path $backend ('build\http-it-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $runDir | Out-Null
$processes=[Collections.Generic.List[System.Diagnostics.Process]]::new()
$saved=@{}
function Start-TestApi([string]$service,[int]$port,[string]$profile='dev') {
    if (@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq $port).Count) { throw "Test port $port occupied." }
    $prefix=$service.ToUpperInvariant()
    foreach($key in @('USERNAME','PASSWORD')) {
        $name="$($prefix)_DB_$key"
        if(-not $saved.ContainsKey($name)){ $saved[$name]=[Environment]::GetEnvironmentVariable($name) }
        [Environment]::SetEnvironmentVariable($name,[Environment]::GetEnvironmentVariable("MYSQL_TEST_$key"),'Process')
    }
    $jar=Join-Path $backend "$service-service\build\libs\$service-service-0.1.0.jar"
    $log=Join-Path $runDir "$service-$([guid]::NewGuid().ToString('N'))"
    $url="jdbc:mysql://127.0.0.1:$($env:MYSQL_TEST_PORT)/taskmind_$($service)_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC"
    $args="-jar `"$jar`" --spring.profiles.active=$profile --spring.profiles.include=default --debug=false --trace=false --server.address=127.0.0.1 --server.port=$port --spring.datasource.url=$url --spring.jpa.hibernate.ddl-auto=validate --taskmind.auth-url=http://127.0.0.1:18081"
    $process=Start-Process java.exe -WindowStyle Hidden -WorkingDirectory $backend -PassThru -ArgumentList $args -RedirectStandardOutput "$log.stdout.log" -RedirectStandardError "$log.stderr.log"
    $processes.Add($process)
    $ready=$false
    for($attempt=0;$attempt -lt 90;$attempt++) {
        if($process.HasExited){throw "Test API exited: $log"}
        if((Test-Path "$log.stdout.log") -and (Select-String "$log.stdout.log" -Pattern 'Started .*Application in ' -Quiet)){ $ready=$true;break }
        Start-Sleep -Seconds 1
    }
    if(-not $ready){throw "Test API timeout: $log"}
    return $process
}
function Http([string]$method,[int]$port,[string]$path,$body=$null,[int]$expected=200){
    $options=@{Uri="http://127.0.0.1:$port$path";Method=$method;UseBasicParsing=$true;TimeoutSec=10}
    if($script:sessionToken){$options.Headers=@{Authorization="Bearer $script:sessionToken"}}
    if($null -ne $body){$options.ContentType='application/json; charset=utf-8';$options.Body=[Text.Encoding]::UTF8.GetBytes(($body|ConvertTo-Json -Depth 8))}
    try {$response=Invoke-WebRequest @options} catch {throw "HTTP check failed: $method $path"}
    if([int]$response.StatusCode -ne $expected){throw "Unexpected HTTP status for $path"}
    if($response.Content){return $response.Content|ConvertFrom-Json}
}
try {
    $disabled=Start-TestApi 'task' 18082 'default'
    $code=0
    try { $response=Invoke-WebRequest 'http://127.0.0.1:18082/api/tasks' -UseBasicParsing; $code=[int]$response.StatusCode }
    catch { if($_.Exception.Response){$code=[int]$_.Exception.Response.StatusCode} else {throw} }
    if($code -ne 404){throw 'Development API unexpectedly accessible without dev profile.'}
    $disabled.Kill();[void]$disabled.WaitForExit(15000)
    Write-Output 'Profile guard PASS: /api/tasks returns 404 without dev profile.'
    $null=Start-TestApi 'auth' 18081
    $account=Http POST 18081 '/api/auth/register' @{email=([guid]::NewGuid().ToString('N')+'@test.invalid');password=([guid]::NewGuid().ToString('N'));fullName='HTTP test'} 201
    $script:sessionToken=$account.token
    $taskProcess=Start-TestApi 'task' 18082
    $habitProcess=Start-TestApi 'productivity' 18083
    $device=[guid]::NewGuid().ToString()
    $native=Http PUT 18082 "/api/native-workspace/$device" @{revision=1;tasks=@(@{id='local1';title='Native authenticated';scheduledDate='2026-10-23';priority=2;progress=35;note=''});lists=@()}
    $null=Http PUT 18083 "/api/native-workspace/$device" @{revision=1;habits=@(@{id='habit1';title='Native habit';body='';created='2026-10-23';progress=@{'2026-10-23'=25}})}
    $firstToken=$script:sessionToken
    $other=Http POST 18081 '/api/auth/register' @{email=([guid]::NewGuid().ToString('N')+'@test.invalid');password=([guid]::NewGuid().ToString('N'));fullName='Other account'} 201
    $script:sessionToken=$other.token
    foreach($port in @(18082,18083)){
        $code=0
        try { $null=Invoke-WebRequest "http://127.0.0.1:$port/api/native-workspace/$device" -UseBasicParsing -Headers @{Authorization="Bearer $script:sessionToken"} }
        catch {if($_.Exception.Response){$code=[int]$_.Exception.Response.StatusCode}else{throw}}
        if($code -ne 404){throw 'Another account could access the native workspace.'}
        $code=0
        try {$null=Invoke-WebRequest "http://127.0.0.1:$port/api/native-workspace/$device" -UseBasicParsing}
        catch {if($_.Exception.Response){$code=[int]$_.Exception.Response.StatusCode}else{throw}}
        if($code -ne 401){throw 'Native workspace accessible without login.'}
    }
    $script:sessionToken=$firstToken
    Write-Output 'Authenticated native sync PASS: real Auth token, both services, account isolation and anonymous rejection.'
    $a=Http POST 18082 '/api/lists' @{name='HTTP isolated A';color='#176B64'} 201
    $b=Http POST 18082 '/api/lists' @{name='HTTP isolated B';color='#176B64'} 201
    $task=Http POST 18082 '/api/tasks' @{title='Persistent HTTP task';priority='HIGH';listId=$a.id;notes='persist';description='test';startDate='2026-01-01T09:00:00';dueDate='2026-01-02T09:00:00'} 201
    $null=Http PUT 18082 "/api/tasks/$($task.id)/list" @{listId=$b.id}
    $null=Http PATCH 18082 "/api/tasks/$($task.id)/completion" @{completed=$true}
    $old=Http GET 18082 "/api/tasks?listId=$($a.id)"
    if($null -ne $old -and $old.Count -ne 0){throw 'Move left a task in original list.'}
    $today=[TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([DateTime]::UtcNow,'SE Asia Standard Time').ToString('yyyy-MM-dd')
    $habit=Http POST 18083 '/api/habits' @{name='Persistent HTTP habit';description='test';icon='book';color='#176B64';weekdays=127;startDate=$today} 201
    $null=Http PUT 18083 "/api/habits/$($habit.id)/check-ins/$today" @{completed=$true}
    # Restart only processes created above. The test MySQL instance remains running.
    $taskProcess.Kill();[void]$taskProcess.WaitForExit(15000)
    $habitProcess.Kill();[void]$habitProcess.WaitForExit(15000)
    $null=Start-TestApi 'task' 18082
    $null=Start-TestApi 'productivity' 18083
    $persisted=Http GET 18082 "/api/tasks/$($task.id)"
    if($persisted.progress -ne 100 -or $persisted.listId -ne $b.id -or $persisted.notes -ne 'persist'){throw 'Task not persisted across restart.'}
    $persistedHabit=Http GET 18083 "/api/habits/$($habit.id)"
    if(-not $persistedHabit.completedToday -or $persistedHabit.currentStreak -ne 1){throw 'Check-in not persisted across restart.'}
    $null=Http PUT 18083 "/api/habits/$($habit.id)/check-ins/$today" @{completed=$false}
    $null=Http DELETE 18082 "/api/tasks/$($task.id)" $null 204
    $null=Http DELETE 18082 "/api/lists/$($a.id)" $null 204
    $null=Http DELETE 18082 "/api/lists/$($b.id)" $null 204
    $null=Http DELETE 18083 "/api/habits/$($habit.id)" $null 204
    Write-Output 'HTTP persistence PASS: CRUD, move, check-in/undo and data retained after both JVMs restarted. Ports 18082/18083, isolated MySQL only.'
} finally {
    $script:sessionToken=$null
    foreach($process in $processes){if(-not $process.HasExited){$process.Kill();[void]$process.WaitForExit(15000)}}
    foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
