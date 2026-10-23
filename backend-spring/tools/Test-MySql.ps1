param(
    [string]$MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin',
    [int]$Port = 13316,
    [ValidateSet('auth','task','productivity','ai')][string[]]$LifecycleServices = @('auth','task','productivity','ai'),
    [switch]$LifecycleChecks,
    [switch]$ApiChecks
)
$ErrorActionPreference = 'Stop'
$backend = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
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
$variables = @('MYSQL_PWD', 'RUN_MYSQL_TESTS', 'MYSQL_TEST_PORT', 'MYSQL_TEST_USERNAME', 'MYSQL_TEST_PASSWORD')
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
    if ($LifecycleChecks) {
        & $wrapper -p $backend bootJar
        if ($LASTEXITCODE -ne 0) { throw 'bootJar failed.' }
        & "$PSScriptRoot\Test-ServiceLifecycle.ps1" -Services $LifecycleServices -StageProductivityAtVersionTwo
    }
    & $wrapper -p $backend test --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'MySQL integration tests failed. Check service build/reports/tests/test.' }
    # Run a second time to verify existing Flyway histories are accepted without replaying DDL.
    & $wrapper -p $backend test --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'Second migration/validation pass failed.' }
    & $mysql --no-defaults --protocol=TCP -h 127.0.0.1 "--port=$Port" -u root --execute="SELECT TABLE_SCHEMA, TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA LIKE 'taskmind_%' ORDER BY TABLE_SCHEMA,TABLE_NAME;"
    if ($ApiChecks) {
        & $wrapper -p $backend bootJar
        if ($LASTEXITCODE -ne 0) { throw 'bootJar failed.' }
        & "$PSScriptRoot\Test-ApiPersistence.ps1"
    }
    Write-Output 'Verified isolated MySQL databases. Existing MySQL service was not modified.'
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
