param([ValidateSet('auth','task','productivity')][string[]]$Services = @('auth','task','productivity'), [switch]$DevelopmentApi, [switch]$ApplyMigrations, [switch]$NoPrompt, [ValidateRange(5,600)][int]$TimeoutSeconds=90)
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\ServiceTools.ps1"
if ($ApplyMigrations) {
    throw 'Migrations của schema cũ đã bị vô hiệu hóa. Backend chỉ kết nối các bảng đang có và không được tự tạo/thay đổi schema.'
}
$lock = Enter-LifecycleLock
$failures = 0
try {
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    $version = (& $java --version | Out-String)
    if ($version -notmatch '(?:openjdk|java) 21[.]') { throw 'Java 21 is required on PATH.' }
    $definitions = @(Get-ServiceDefinitions | Where-Object Name -In $Services)
    if (@($definitions.Port | Select-Object -Unique).Count -ne $definitions.Count) { throw 'Service ports must be distinct.' }
    $pending = @()
    foreach ($service in $definitions) {
        try {
            $previous = Read-ServiceState $service
            if (Get-OwnedProcess $previous) {
                Write-Host "$($service.Name): already managed on port $($previous.Port); use Check-Services.ps1."
                continue
            }
            if (@(Get-PortOwners $service.Port).Count) { throw "Port $($service.Port) is occupied. No duplicate launched." }
            $jars = @(Get-ChildItem (Join-Path $BackendRoot "$($service.Name)-service\build\libs") -Filter '*.jar' |
                Where-Object Name -NotLike '*-plain.jar')
            if ($jars.Count -ne 1) { throw 'Expected exactly one executable JAR. Run Gradle bootJar.' }
            $jar = $jars[0].FullName
            $manual = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
                Where-Object { $_.CommandLine -and $_.CommandLine.Contains($jar) })
            if ($manual.Count) { throw 'This JAR is already running outside the script.' }
            $prefix = $service.Prefix
            if (-not [Environment]::GetEnvironmentVariable("$($prefix)_DB_USERNAME") -or
                -not [Environment]::GetEnvironmentVariable("$($prefix)_DB_PASSWORD")) {
                if ($NoPrompt) { throw "Missing $($prefix)_DB_USERNAME or $($prefix)_DB_PASSWORD in this terminal." }
                & "$PSScriptRoot\Set-DatabaseEnvironment.ps1" -Service $service.Name
            }
            $sourceJar = $jar
            $digest = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash
            $artifactDirectory = Join-Path $RuntimeRoot 'artifacts'
            New-Item -ItemType Directory -Path $artifactDirectory -Force | Out-Null
            $jar = Join-Path $artifactDirectory "$($service.Name)-$digest.jar"
            if (-not (Test-Path -LiteralPath $jar)) { Copy-Item -LiteralPath $sourceJar -Destination $jar }
            $token = [guid]::NewGuid().ToString('N')
            $output = Join-Path $RuntimeRoot "$($service.Name)-$token.stdout.log"
            $errors = Join-Path $RuntimeRoot "$($service.Name)-$token.stderr.log"
            # Only use existing schema; Hibernate DDL and SQL initialization stay disabled.
            $profile = 'default'
            if ($DevelopmentApi -and $service.Name -in @('task','productivity')) { $profile = 'dev' }
            $localArguments = ''
            if ($DevelopmentApi -and $service.Name -in @('auth','task','productivity')) {
                $localArguments = ' --server.address=127.0.0.1'
            }
            $arguments = "-Dtaskmind.launch=$token -jar ""$jar"" --spring.profiles.active=$profile --spring.profiles.include=default --spring.main.web-application-type=servlet --server.port=$($service.Port)$localArguments --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none --spring.sql.init.mode=never"
            $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $BackendRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput $output -RedirectStandardError $errors
            $state = [pscustomobject]@{ ProcessId=$process.Id; StartTicks=$process.StartTime.ToUniversalTime().Ticks.ToString()
                Token=$token; Jar=$jar; Port=$service.Port; Output=$output; Error=$errors }
            $state | ConvertTo-Json | Set-Content -LiteralPath $service.State -Encoding UTF8
            $pending += [pscustomobject]@{ Service=$service; State=$state; Deadline=(Get-Date).AddSeconds($TimeoutSeconds); StableSince=$null }
            Write-Host "$($service.Name): launched PID $($process.Id), port $($service.Port)."
        } catch { $failures++; Write-Warning "$($service.Name): $($_.Exception.Message)" }
    }
    while ($pending.Count) {
        $remaining = @()
        foreach ($item in $pending) {
            $state = $item.State
            if (-not (Get-OwnedProcess $state)) {
                $failures++; Write-Warning "$($item.Service.Name): exited. Inspect $($state.Output) and $($state.Error)."
            } elseif (Test-ServiceReady $state) {
                if (-not $item.StableSince) { $item.StableSince = Get-Date }
                if (((Get-Date) - $item.StableSince).TotalSeconds -ge 5) {
                    Write-Host "$($item.Service.Name): STARTED, port $($state.Port), stable for 5 seconds."
                } else { $remaining += $item }
            } elseif ((Get-Date) -gt $item.Deadline) {
                $failures++; Write-Warning "$($item.Service.Name): timeout; process retained for diagnosis. Log: $($state.Output)"
            } else { $item.StableSince = $null; $remaining += $item }
        }
        $pending = $remaining
        if ($pending.Count) { Start-Sleep -Seconds 1 }
    }
} finally { $lock.Dispose() }
if ($failures) { throw "$failures service(s) failed or require attention." }
