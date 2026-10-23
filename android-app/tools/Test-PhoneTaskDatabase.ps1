param(
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
    [int]$Port = 13316,
    [switch]$LifecycleChecks,
    [switch]$ApiChecks
)
$ErrorActionPreference = 'Stop'
$backend = (Resolve-Path (Join-Path $PSScriptRoot '..\..\backend-spring')).Path
$wrapper = Join-Path $backend '..\android-app\gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapper)) { $wrapper = Join-Path $backend '..\gradlew.bat' }
if (-not (Test-Path -LiteralPath $wrapper)) { throw 'Gradle wrapper not found.' }
$mysqld = Join-Path $MySqlBin 'mysqld.exe'
$mysql = Join-Path $MySqlBin 'mysql.exe'
if (-not (Test-Path -LiteralPath $mysqld)) { throw 'MySQL Server binaries not found.' }
$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
try { $listener.Start() } finally { $listener.Stop() }
$testDir = Join-Path $backend ('build\mysql-it-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDir | Out-Null
$dataDir = Join-Path $testDir 'data'
$baseDir = Split-Path $MySqlBin -Parent
$server = $null
$ready = $false
$saved = @{}
$variables = @('MYSQL_PWD', 'RUN_MYSQL_TESTS', 'MYSQL_TEST_PORT', 'MYSQL_TEST_USERNAME', 'MYSQL_TEST_PASSWORD','TASK_DB_USERNAME','TASK_DB_PASSWORD')
foreach ($name in $variables) { $saved[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
try {
    & $mysqld --no-defaults "--basedir=$baseDir" "--datadir=$dataDir" --initialize-insecure "--log-error=$testDir\initialize.log"
    if ($LASTEXITCODE -ne 0) { throw "MySQL initialization failed. See $testDir" }
    $server = Start-Process -FilePath $mysqld -WindowStyle Hidden -PassThru -ArgumentList @(
        '--no-defaults', "`"--basedir=$baseDir`"", "`"--datadir=$dataDir`"", "--port=$Port",
        '--bind-address=127.0.0.1', '--mysqlx=OFF', '--skip-log-bin', "`"--log-error=$testDir\server.log`"")
    $env:MYSQL_PWD = $null
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if ($server.HasExited) { throw "Test MySQL stopped. See $testDir" }
        $connection = [System.Net.Sockets.TcpClient]::new()
        try { $connection.Connect('127.0.0.1', $Port); $ready = $true; break }
        catch { Start-Sleep -Seconds 1 }
        finally { $connection.Dispose() }
    }
    if (-not $ready) { throw 'Test MySQL did not become ready.' }
    $secret = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    # Password exists only in this process; never put it in command-line arguments or logs.
    "ALTER USER 'root'@'localhost' IDENTIFIED BY '$secret';" | & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 "--port=$Port" -u root
    if ($LASTEXITCODE -ne 0) { throw 'Failed to secure temporary MySQL.' }
    $env:MYSQL_PWD = $secret
    Get-Content -Raw -Encoding UTF8 (Join-Path $backend 'database\00_create_databases.sql') |
        & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 "--port=$Port" -u root
    if ($LASTEXITCODE -ne 0) { throw 'Failed to create isolated test databases.' }
    $env:RUN_MYSQL_TESTS = 'true'
    $env:MYSQL_TEST_PORT = [string]$Port
    $env:MYSQL_TEST_USERNAME = 'root'
    $env:MYSQL_TEST_PASSWORD = $secret
    & $wrapper -p $backend :task-service:bootJar
    if($LASTEXITCODE -ne 0){throw 'Task build failed.'}
    if(@(Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 18082).Count){throw 'Test port 18082 occupied.'}
    $env:TASK_DB_USERNAME=$env:MYSQL_TEST_USERNAME
    $env:TASK_DB_PASSWORD=$env:MYSQL_TEST_PASSWORD
    $jar=Join-Path $backend 'task-service\build\libs\task-service-0.1.0.jar'
    $url="jdbc:mysql://127.0.0.1:$Port/taskmind_task_db?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectionTimeZone=UTC"
    $api=Start-Process java.exe -WindowStyle Hidden -PassThru -ArgumentList "-jar `"$jar`" --spring.profiles.active=dev --server.address=127.0.0.1 --server.port=18082 --taskmind.local-workspace=true --spring.datasource.url=$url" -RedirectStandardOutput "$testDir\task.log" -RedirectStandardError "$testDir\task.err.log"
    $adb=Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    $oldReverse=@(& $adb -s RFCX31A77NY reverse --list | Where-Object {$_ -match 'tcp:8082 '})
    try {
        for($i=0;$i -lt 90;$i++){
            if($api.HasExited){throw 'Task service exited.'}
            if(Select-String -Path "$testDir\task.log" -Pattern 'Started TaskApplication' -Quiet){break}
            Start-Sleep -Seconds 1
        }
        & $adb -s RFCX31A77NY reverse tcp:8082 tcp:18082
        if($LASTEXITCODE -ne 0){throw 'USB mapping failed.'}
        [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'phone-check.current'),$testDir)
        Write-Output "PHONE CHECK READY: $testDir"
        $query="SELECT TaskID,Title,Progress,Status FROM tasks WHERE LEFT(Title,9)='TM_CHECK_'; SELECT ListID,ListName FROM tasklists WHERE LEFT(ListName,9)='TM_CHECK_'; SELECT t.TaskID,t.Title,l.ListID,l.ListName FROM tasks t JOIN tasklistitems i ON i.TaskID=t.TaskID JOIN tasklists l ON l.ListID=i.ListID WHERE LEFT(t.Title,9)='TM_CHECK_'; SELECT DeviceID,Revision FROM device_workspaces;"
        $deadline=[DateTime]::UtcNow.AddMinutes(10)
        while([DateTime]::UtcNow -lt $deadline -and -not (Test-Path "$testDir\stop")){
            $result=$query | & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 "--port=$Port" -u root --database=taskmind_task_db --table
            if($LASTEXITCODE -ne 0){throw 'Read-only snapshot failed.'}
            $result | Set-Content -Encoding UTF8 "$testDir\snapshot.txt"
            Start-Sleep -Seconds 2
        }
    } finally {
        if(-not $api.HasExited){$api.Kill();[void]$api.WaitForExit(10000)}
        if($oldReverse.Count){$destination=($oldReverse[0] -split '\s+')[-1];& $adb -s RFCX31A77NY reverse tcp:8082 $destination}
        else {& $adb -s RFCX31A77NY reverse --remove tcp:8082}
    }
} finally {
    # On Windows mysqld can spawn a worker process; shut down the owned instance via SQL first.
    if ($ready) {
        & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 "--port=$Port" -u root --execute=SHUTDOWN
    }
    if ($null -ne $server) {
        $server.Refresh()
        if (-not $server.HasExited) { [void]$server.WaitForExit(10000) }
        # Only processes referencing this unique test datadir can be stopped.
        Get-CimInstance Win32_Process -Filter "Name = 'mysqld.exe'" |
            Where-Object { $_.CommandLine -and $_.CommandLine.Contains($dataDir) } |
            ForEach-Object { Stop-Process -Id $_.ProcessId }
    }
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $saved[$name], 'Process') }
    $secret = $null
    # Keep only test artifacts in ignored build/ for diagnosis; never delete user databases.
}

