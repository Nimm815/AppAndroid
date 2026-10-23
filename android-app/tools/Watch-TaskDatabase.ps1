param(
    [string]$MySql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
    [ValidateRange(0,2147483647)][int]$UserId = 0,
    [switch]$Once
)
$ErrorActionPreference = 'Stop'
$backend = Join-Path (Split-Path $PSScriptRoot -Parent) '..\backend-spring'
if (-not (Test-Path -LiteralPath $MySql)) { throw 'MySQL client not found. Specify -MySql with mysql.exe path.' }
& (Join-Path $backend 'tools\Set-DatabaseEnvironment.ps1') -Service task
$previousPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:TASK_DB_PASSWORD
$dbHost = if ($env:TASK_DB_HOST) { $env:TASK_DB_HOST } else { 'localhost' }
$dbPort = if ($env:TASK_DB_PORT) { $env:TASK_DB_PORT } else { '3306' }
$sql = @"
SELECT UTC_TIMESTAMP() AS CheckedAtUTC;
SELECT ListID, UserID, ListName
FROM tasklists WHERE LEFT(ListName,9)='TM_CHECK_' AND ($UserId=0 OR UserID=$UserId)
ORDER BY ListID;
SELECT t.TaskID,t.UserID,t.Title,t.Progress,t.Status,l.ListID,l.ListName
FROM tasks t LEFT JOIN tasklistitems i ON i.TaskID=t.TaskID
LEFT JOIN tasklists l ON l.ListID=i.ListID
WHERE LEFT(t.Title,9)='TM_CHECK_' AND ($UserId=0 OR t.UserID=$UserId)
ORDER BY t.TaskID,l.ListID;
SELECT t.TaskID,COUNT(i.ListID) AS ListCount
FROM tasks t LEFT JOIN tasklistitems i ON i.TaskID=t.TaskID
WHERE LEFT(t.Title,9)='TM_CHECK_' AND ($UserId=0 OR t.UserID=$UserId)
GROUP BY t.TaskID;
"@
try {
    Write-Host 'Read-only database view. Create test items with prefix TM_CHECK_ in the original Android screens.'
    Write-Host 'ListCount must be 1 for an assigned task, or 0 for an unassigned task. Ctrl+C stops watching.'
    do {
        $sql | & $MySql --no-defaults --protocol=TCP "--host=$dbHost" "--port=$dbPort" "--user=$env:TASK_DB_USERNAME" --database=taskmind_task_db --default-character-set=utf8mb4 --table
        if ($LASTEXITCODE -ne 0) { throw 'Read-only database query failed. No data was modified.' }
        if (-not $Once) { Start-Sleep -Seconds 3 }
    } while (-not $Once)
} finally { $env:MYSQL_PWD = $previousPassword }
