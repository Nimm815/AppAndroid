param(
    [Parameter(Mandatory=$true)][ValidateSet('auth','task','productivity','ai')][string]$Service,
    [string]$Username, [string]$HostName,
    [ValidateRange(1,65535)][int]$Port,
    [switch]$ReplacePassword
)
$prefix = $Service.ToUpperInvariant()
$oldUser = [Environment]::GetEnvironmentVariable("$($prefix)_DB_USERNAME")
if (-not $Username) { $Username = $oldUser }
if (-not $Username) { $Username = Read-Host "MySQL username for $Service" }
if ([string]::IsNullOrWhiteSpace($Username)) { throw 'Username is required.' }
$oldHost = [Environment]::GetEnvironmentVariable("$($prefix)_DB_HOST")
$oldPort = [Environment]::GetEnvironmentVariable("$($prefix)_DB_PORT")
$changed = ($oldUser -and $oldUser -ne $Username) -or
    ($HostName -and $oldHost -ne $HostName) -or ($Port -and $oldPort -ne [string]$Port)
if (-not $HostName) { $HostName = $oldHost }
if (-not $HostName) { $HostName = 'localhost' }
if (-not $Port) { if ($oldPort) { $Port = [int]$oldPort } else { $Port = 3306 } }
if ($ReplacePassword -or $changed -or -not [Environment]::GetEnvironmentVariable("$($prefix)_DB_PASSWORD")) {
    $secure = Read-Host "MySQL password for $Service (hidden)" -AsSecureString
    try {
        $plain = [System.Net.NetworkCredential]::new('', $secure).Password
        if (-not $plain) { throw 'Password must not be empty.' }
        [Environment]::SetEnvironmentVariable("$($prefix)_DB_PASSWORD", $plain, 'Process')
    } finally { $plain = $null; if ($secure) { $secure.Dispose() } }
}
[Environment]::SetEnvironmentVariable("$($prefix)_DB_HOST", $HostName, 'Process')
[Environment]::SetEnvironmentVariable("$($prefix)_DB_PORT", [string]$Port, 'Process')
[Environment]::SetEnvironmentVariable("$($prefix)_DB_USERNAME", $Username, 'Process')
Write-Host "Configured $Service in this terminal only; credentials were not printed or saved."
