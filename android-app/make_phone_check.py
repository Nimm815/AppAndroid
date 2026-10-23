from pathlib import Path
s=Path('../backend-spring/tools/Test-MySql.ps1').read_text(encoding='utf-8-sig')
s=s.replace("$backend = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path", "$backend = (Resolve-Path (Join-Path $PSScriptRoot '..\\..\\backend-spring')).Path")
s=s.replace("'MYSQL_TEST_PASSWORD')", "'MYSQL_TEST_PASSWORD','TASK_DB_USERNAME','TASK_DB_PASSWORD')")
start=s.index('    & $wrapper -p $backend test --rerun-tasks')
end=s.index('} finally {\n    # On Windows',start)
s=s[:start]+'''    & $wrapper -p $backend :task-service:bootJar
    if($LASTEXITCODE -ne 0){throw 'Task build failed.'}
    if(@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 18082).Count){throw 'Test port 18082 occupied.'}
    $env:TASK_DB_USERNAME=$env:MYSQL_TEST_USERNAME
    $env:TASK_DB_PASSWORD=$env:MYSQL_TEST_PASSWORD
    $jar=Join-Path $backend 'task-service\\build\\libs\\task-service-0.1.0.jar'
    $url="jdbc:mysql://127.0.0.1:$Port/taskmind_task_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC"
    $api=Start-Process java.exe -WindowStyle Hidden -PassThru -ArgumentList "-jar `"$jar`" --spring.profiles.active=dev --server.address=127.0.0.1 --server.port=18082 --taskmind.local-workspace=true --spring.datasource.url=$url" -RedirectStandardOutput "$testDir\\task.log" -RedirectStandardError "$testDir\\task.err.log"
    $adb=Join-Path $env:LOCALAPPDATA 'Android\\Sdk\\platform-tools\\adb.exe'
    $oldReverse=@(& $adb -s RFCX31A77NY reverse --list | Where-Object {$_ -match 'tcp:8082 '})
    try {
        for($i=0;$i -lt 90;$i++){
            if($api.HasExited){throw 'Task service exited.'}
            if(Select-String -Path "$testDir\\task.log" -Pattern 'Started TaskApplication' -Quiet){break}
            Start-Sleep -Seconds 1
        }
        & $adb -s RFCX31A77NY reverse tcp:8082 tcp:18082
        if($LASTEXITCODE -ne 0){throw 'USB mapping failed.'}
        [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'phone-check.current'),$testDir)
        Write-Output "PHONE CHECK READY: $testDir"
        $query="SELECT TaskID,Title,Progress,Status FROM tasks WHERE LEFT(Title,9)='TM_CHECK_'; SELECT ListID,ListName FROM tasklists WHERE LEFT(ListName,9)='TM_CHECK_'; SELECT t.TaskID,t.Title,l.ListID,l.ListName FROM tasks t JOIN tasklistitems i ON i.TaskID=t.TaskID JOIN tasklists l ON l.ListID=i.ListID WHERE LEFT(t.Title,9)='TM_CHECK_'; SELECT DeviceID,Revision FROM device_workspaces;"
        $deadline=[DateTime]::UtcNow.AddMinutes(10)
        while([DateTime]::UtcNow -lt $deadline -and -not (Test-Path "$testDir\\stop")){
            $result=$query | & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 "--port=$Port" -u root --database=taskmind_task_db --table
            if($LASTEXITCODE -ne 0){throw 'Read-only snapshot failed.'}
            $result | Set-Content -Encoding UTF8 "$testDir\\snapshot.txt"
            Start-Sleep -Seconds 2
        }
    } finally {
        if(-not $api.HasExited){$api.Kill();[void]$api.WaitForExit(10000)}
        if($oldReverse.Count){$destination=($oldReverse[0] -split '\\s+')[-1];& $adb -s RFCX31A77NY reverse tcp:8082 $destination}
        else {& $adb -s RFCX31A77NY reverse --remove tcp:8082}
    }
'''+s[end:]
Path('tools/Test-PhoneTaskDatabase.ps1').write_text(s,encoding='utf-8')
