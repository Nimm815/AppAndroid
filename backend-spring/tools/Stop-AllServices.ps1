param([ValidateSet('auth','task','productivity','ai')][string[]]$Services = @('auth','task','productivity','ai'))
$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\ServiceTools.ps1"
$lock = Enter-LifecycleLock
try {
    foreach ($service in (Get-ServiceDefinitions | Where-Object Name -In $Services)) {
        $state = Read-ServiceState $service
        $process = Get-OwnedProcess $state
        if ($process) {
            $process.Kill()
            if (-not $process.WaitForExit(15000)) { throw "Could not stop $($service.Name)." }
            Write-Host "$($service.Name): stopped PID $($state.ProcessId)."
        } else { Write-Host "$($service.Name): no verified managed process; nothing stopped." }
        if (Test-Path -LiteralPath $service.State) { Remove-Item -LiteralPath $service.State }
    }
} finally { $lock.Dispose() }
