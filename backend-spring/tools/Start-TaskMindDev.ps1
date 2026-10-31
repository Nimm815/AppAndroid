[CmdletBinding()]
param([switch]$PreflightOnly, [string]$DatabaseUsername)

$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
$toolsRoot = $PSScriptRoot
$runtimeRoot = Join-Path $backendRoot 'build\services'
$env:AUTH_PORT = '8081'
$env:TASK_PORT = '8082'
$env:PRODUCTIVITY_PORT = '8083'
$servicesAttempted = $false
$requiredCredentials = @(
    [pscustomobject]@{ Service='auth'; Prefix='AUTH'; Username='taskmind_auth' },
    [pscustomobject]@{ Service='task'; Prefix='TASK'; Username='taskmind_task' },
    [pscustomobject]@{ Service='productivity'; Prefix='PRODUCTIVITY'; Username='taskmind_productivity' }
)

function Start-MySqlService {
    $mysqlServices = @(Get-Service | Where-Object {
        $_.Name -match '^(?i:mysql)' -or $_.DisplayName -match '^(?i:mysql)'
    })
    if (-not $mysqlServices.Count) {
        throw 'Không tìm thấy Windows Service của MySQL. Hãy cài/đăng ký MySQL Windows Service hoặc khởi động MySQL trước, rồi chạy lại.'
    }

    $mysqlService = $mysqlServices | Where-Object Status -eq 'Running' | Select-Object -First 1
    if ($mysqlService) {
        Write-Host "MySQL Windows Service đang chạy: $($mysqlService.Name)."
        return
    }

    $mysqlService = $mysqlServices | Select-Object -First 1
    Write-Host "Đang khởi động MySQL Windows Service: $($mysqlService.Name)..."
    try {
        Start-Service -Name $mysqlService.Name -ErrorAction Stop
        $mysqlService = Get-Service -Name $mysqlService.Name
        $mysqlService.WaitForStatus('Running', [TimeSpan]::FromSeconds(30))
        Write-Host "MySQL Windows Service đã chạy: $($mysqlService.Name)."
    } catch {
        throw "Không khởi động được MySQL Windows Service '$($mysqlService.Name)': $($_.Exception.Message) Hãy mở PowerShell bằng Run as administrator và chạy Start-Service -Name '$($mysqlService.Name)', sau đó chạy lại."
    }
}

function Set-And-VerifyDatabaseCredentials([string]$SharedUsername) {
    if (-not [string]::IsNullOrWhiteSpace($SharedUsername)) {
        if ($SharedUsername -match '\s') { throw 'DatabaseUsername must not contain whitespace.' }
        $securePassword = Read-Host "MySQL password for shared user '$SharedUsername' (hidden)" -AsSecureString
        try {
            $sharedPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
            if ([string]::IsNullOrEmpty($sharedPassword)) { throw 'Password must not be empty.' }
            foreach ($credential in $requiredCredentials) {
                $prefix = $credential.Prefix
                [Environment]::SetEnvironmentVariable("${prefix}_DB_HOST", 'localhost', 'Process')
                [Environment]::SetEnvironmentVariable("${prefix}_DB_PORT", '3306', 'Process')
                [Environment]::SetEnvironmentVariable("${prefix}_DB_USERNAME", $SharedUsername, 'Process')
                [Environment]::SetEnvironmentVariable("${prefix}_DB_PASSWORD", $sharedPassword, 'Process')
                Write-Host "Configured $($credential.Service) with shared MySQL account '$SharedUsername' in this process."
            }
        } finally {
            $sharedPassword = $null
            if ($securePassword) { $securePassword.Dispose() }
        }
        return
    }

    $configureScript = Join-Path $toolsRoot 'Set-DatabaseEnvironment.ps1'
    foreach ($credential in $requiredCredentials) {
        $usernameVariable = "$($credential.Prefix)_DB_USERNAME"
        $passwordVariable = "$($credential.Prefix)_DB_PASSWORD"
        $existingUsername = [Environment]::GetEnvironmentVariable($usernameVariable, 'Process')
        if ($existingUsername -and $existingUsername -ne $credential.Username) {
            Write-Warning "Username $usernameVariable không đúng cấu hình mặc định; sẽ yêu cầu nhập lại mật khẩu cho username chuẩn."
        }

        if ($existingUsername -cne $credential.Username) {
            & $configureScript -Service $credential.Service -Username $credential.Username -ReplacePassword
        } else {
            & $configureScript -Service $credential.Service -Username $credential.Username
        }
        $actualUsername = [Environment]::GetEnvironmentVariable($usernameVariable, 'Process')
        if ($actualUsername -cne $credential.Username -or $actualUsername -match '\s') {
            throw "Username MySQL cho $($credential.Service) không khớp '$($credential.Username)'. Đã dừng trước khi khởi động backend."
        }
        $password = [Environment]::GetEnvironmentVariable($passwordVariable, 'Process')
        if ([string]::IsNullOrEmpty($password)) {
            throw "Chưa có mật khẩu MySQL cho $($credential.Service). Hãy chạy lại và nhập mật khẩu khi được hỏi."
        }
        $password = $null
        Write-Host "Đã xác nhận username cho $($credential.Service); mật khẩu chỉ tồn tại trong Terminal hiện tại."
    }
}

function Get-ExecutableJar([string]$serviceName) {
    $module = "$serviceName-service"
    $jarDirectory = Join-Path $backendRoot "$module\build\libs"
    $jars = @(Get-ChildItem -LiteralPath $jarDirectory -Filter '*.jar' -ErrorAction Stop |
        Where-Object Name -NotLike '*-plain.jar')
    if ($jars.Count -ne 1) {
        throw "Cần đúng một JAR chạy được trong '$jarDirectory'. Hãy build service khi source thay đổi; script này không build lại JAR."
    }
    return $jars[0]
}

function Stop-StaleManagedServices {
    . (Join-Path $toolsRoot 'ServiceTools.ps1')
    $serviceDefinitions = @(Get-ServiceDefinitions | Where-Object Name -In @('auth','task','productivity'))
    $restartNames = @()

    foreach ($service in $serviceDefinitions) {
        $state = Read-ServiceState $service
        $process = Get-OwnedProcess $state
        if (-not $process) { continue }

        $jar = Get-ExecutableJar $service.Name
        $currentHash = (Get-FileHash -LiteralPath $jar.FullName -Algorithm SHA256).Hash
        $managedJarName = [IO.Path]::GetFileNameWithoutExtension([string]$state.Jar)
        $expectedManagedJarName = "$($service.Name)-$currentHash"
        $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)" -ErrorAction Stop
        $commandLine = [string]$processInfo.CommandLine
        if ($service.Name -eq 'auth') {
            $isDevelopmentApi = $commandLine.Contains('--server.address=127.0.0.1')
        } else {
            $isDevelopmentApi = $commandLine.Contains('--spring.profiles.active=dev') -and
                $commandLine.Contains('--server.address=127.0.0.1')
        }

        if ($managedJarName -ceq $expectedManagedJarName -and $isDevelopmentApi -and
            (Test-ServiceReady $state)) {
            Write-Host "$($service.Name): đang RUNNING bằng đúng JAR được quản lý và profile DevelopmentApi; giữ nguyên tiến trình."
        } else {
            Write-Warning "$($service.Name): tiến trình được quản lý nhưng không khớp JAR hiện tại hoặc DevelopmentApi; sẽ khởi động lại dịch vụ này."
            $restartNames += $service.Name
        }
    }

    if ($restartNames.Count) {
        & (Join-Path $toolsRoot 'Stop-AllServices.ps1') -Services $restartNames
    }
}

function Show-ServiceFailureLogs {
    foreach ($serviceName in @('auth','task','productivity')) {
        $statePath = Join-Path $runtimeRoot "$serviceName.json"
        if (Test-Path -LiteralPath $statePath) {
            try {
                $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
                if ($state.Output) { Write-Host "$serviceName stdout: $($state.Output)" }
                if ($state.Error) { Write-Host "$serviceName stderr: $($state.Error)" }
            } catch {
                Write-Warning "Không đọc được state file '$statePath': $($_.Exception.Message)"
            }
        }
        $latestErrorLog = Get-ChildItem -LiteralPath $runtimeRoot -Filter "$serviceName-*.stderr.log" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latestErrorLog) { Write-Host "$serviceName log gần nhất: $($latestErrorLog.FullName)" }
    }
}

function Test-BackendApi([string]$serviceName, [int]$port, [string]$path,
                         [int]$ExpectedStatusCode = 200, [string]$ExpectedMessage) {
    $uri = "http://127.0.0.1:$port$path"
    try {
        $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 10
        if ([int]$response.StatusCode -ne $ExpectedStatusCode) {
            throw "HTTP $([int]$response.StatusCode)"
        }
        Write-Host "$serviceName API sẵn sàng: $uri (HTTP $ExpectedStatusCode)."
    } catch {
        if ($ExpectedStatusCode -ne 200 -and $_.Exception.Response -and
            [int]$_.Exception.Response.StatusCode -eq $ExpectedStatusCode) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            try { $body = $reader.ReadToEnd() } finally { $reader.Dispose() }
            if (-not $ExpectedMessage -or $body.Contains($ExpectedMessage)) {
                Write-Host "$serviceName API đã phản hồi HTTP $ExpectedStatusCode như dự kiến."
                return
            }
        }
        throw "$serviceName API chưa sẵn sàng tại ${uri}: $($_.Exception.Message)"
    }
}

function Invoke-AdbCommand([string]$AdbPath, [string[]]$Arguments) {
    # adb may write a normal daemon-start message to stderr; do not let the
    # script-wide Stop preference turn that message into a startup failure.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = @(& $AdbPath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    [pscustomobject]@{ Output=$output; ExitCode=$exitCode }
}

function Set-AndroidUsbReverse([switch]$CheckOnly) {
    $sdkPath = $env:ANDROID_SDK_ROOT
    if (-not $sdkPath) { $sdkPath = $env:ANDROID_HOME }
    if (-not $sdkPath) {
        $localProperties = Join-Path (Split-Path $backendRoot -Parent) 'android-app\local.properties'
        if (Test-Path -LiteralPath $localProperties) {
            $sdkLine = Get-Content -LiteralPath $localProperties |
                Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
            if ($sdkLine) {
                $sdkPath = $sdkLine -replace '^sdk\.dir=', ''
                $sdkPath = $sdkPath -replace '\\\\', '\' -replace '\\:', ':'
            }
        }
    }
    if (-not $sdkPath) {
        Write-Warning 'Không xác định được Android SDK (ANDROID_SDK_ROOT/ANDROID_HOME/local.properties); bỏ qua ADB.'
        return
    }
    if (-not (Test-Path -LiteralPath $sdkPath -PathType Container)) {
        Write-Warning "Không tìm thấy thư mục Android SDK '$sdkPath'; backend vẫn tiếp tục chạy."
        return
    }

    $adb = Join-Path $sdkPath 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adb)) {
        Write-Warning "Không tìm thấy ADB tại '$adb'; backend vẫn tiếp tục chạy."
        return
    }

    $deviceResult = Invoke-AdbCommand -AdbPath $adb -Arguments @('devices')
    $deviceOutput = $deviceResult.Output
    if ($deviceResult.ExitCode -ne 0) {
        Write-Warning "Không chạy được ADB devices: $($deviceOutput -join ' '). Backend vẫn tiếp tục chạy."
        return
    }
    $devices = @($deviceOutput | ForEach-Object {
        if ("$_" -match '^(\S+)\s+device$') { $Matches[1] }
    })
    if (-not $devices.Count) {
        Write-Warning 'Không phát hiện điện thoại Android đã được cấp quyền ADB qua USB; backend vẫn tiếp tục chạy.'
        return
    }

    foreach ($device in $devices) {
        if (-not $CheckOnly) {
            foreach ($port in @(8081,8082,8083)) {
                $reverseResult = Invoke-AdbCommand -AdbPath $adb -Arguments @('-s', $device, 'reverse', "tcp:$port", "tcp:$port")
                $reverseOutput = $reverseResult.Output
                if ($reverseResult.ExitCode -ne 0) {
                    Write-Warning "Không tạo được ADB reverse cho $device cổng $port`: $($reverseOutput -join ' ')."
                    continue
                }
            }
        }
        $reverseListResult = Invoke-AdbCommand -AdbPath $adb -Arguments @('-s', $device, 'reverse', '--list')
        $reverseList = $reverseListResult.Output
        if ($reverseListResult.ExitCode -ne 0) {
            Write-Warning "Không xác minh được ADB reverse của $device`: $($reverseList -join ' ')."
            continue
        }
        foreach ($port in @(8081,8082,8083)) {
            if ($reverseList -match "tcp:$port\s+tcp:$port") {
                if ($CheckOnly) {
                    Write-Host "ADB reverse đã có trên $device`: tcp:$port -> tcp:$port."
                } else {
                    Write-Host "ADB reverse đã xác nhận trên $device`: tcp:$port -> tcp:$port."
                }
            } else {
                $action = if ($CheckOnly) { 'chưa được thiết lập' } else { 'chưa được xác nhận' }
                Write-Warning "ADB reverse tcp:$port $action trên $device."
            }
        }
    }
}

try {
    Start-MySqlService
    Set-And-VerifyDatabaseCredentials $DatabaseUsername

    if ($PreflightOnly) {
        Write-Host 'Preflight hoàn tất. Không khởi động/restart backend và không gửi yêu cầu tới database.'
        Set-AndroidUsbReverse -CheckOnly
        return
    }

    Stop-StaleManagedServices
    $servicesAttempted = $true
    & (Join-Path $toolsRoot 'Start-AllServices.ps1') -Services @('auth','task','productivity') -DevelopmentApi -NoPrompt
    if ($LASTEXITCODE -ne 0) { throw 'Không khởi động được đủ Auth, Task và Productivity.' }

    $checkResults = @(& (Join-Path $toolsRoot 'Check-Services.ps1'))
    foreach ($serviceName in @('auth','task','productivity')) {
        $serviceStatus = $checkResults | Where-Object Service -eq $serviceName | Select-Object -First 1
        if (-not $serviceStatus -or $serviceStatus.Status -ne 'RUNNING') {
            throw "$serviceName không ở trạng thái RUNNING; trạng thái: $(if ($serviceStatus) { $serviceStatus.Status } else { 'không có kết quả' })."
        }
        Write-Host "$serviceName`: RUNNING trên cổng $($serviceStatus.Port), PID $($serviceStatus.ProcessId)."
    }

    Test-BackendApi 'Auth Service' 8081 '/api/users/me' 401
    Test-BackendApi 'Task Service' 8082 '/api/tasks' 401
    Test-BackendApi 'Productivity Service' 8083 '/api/habits' 401
    Set-AndroidUsbReverse
    Write-Host 'TaskMind Development đã sẵn sàng. Auth, Task và Productivity Service được khởi động; Auth chỉ bind localhost.'
} catch {
    if ($servicesAttempted) { Show-ServiceFailureLogs }
    Write-Host "LỖI: $($_.Exception.Message)" -ForegroundColor Red
    throw
}
