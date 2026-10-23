$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\ServiceTools.ps1"
foreach ($service in Get-ServiceDefinitions) {
    $state = Read-ServiceState $service
    $process = Get-OwnedProcess $state
    $port = $service.Port
    if ($process) { $port = $state.Port }
    $owners = @(Get-PortOwners $port)
    $status = 'STOPPED'
    if ($process) {
        $status = 'STARTING_OR_UNHEALTHY'
        if (Test-ServiceReady $state) { $status = 'RUNNING' }
    } elseif ($owners.Count) { $status = 'PORT_OCCUPIED_UNMANAGED' }
    [pscustomobject]@{ Service=$service.Name; Port=$port; Status=$status
        ProcessId= if ($process) { $process.Id } else { $null }
        PortOwners=($owners -join ','); Log= if ($state) { $state.Output } else { $null } }
}
