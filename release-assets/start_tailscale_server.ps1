param(
    [int] $Port = 8787
)

$ErrorActionPreference = "Stop"

$tailscaleIp = $null
if (Get-Command tailscale -ErrorAction SilentlyContinue) {
    $tailscaleIp = (& tailscale ip -4 2>$null | Select-Object -First 1)
}

Write-Host "Syx Duo Realm server URL:"
if ([string]::IsNullOrWhiteSpace($tailscaleIp)) {
    Write-Host "http://<your-tailscale-ip>:$Port/api/state"
    Write-Host "Run 'tailscale ip -4' to find the host IP."
} else {
    Write-Host "http://$tailscaleIp`:$Port/api/state"
}
Write-Host ""
Write-Host "Keep this window open while playing."
Write-Host ""

& (Join-Path $PSScriptRoot "start_server_host.ps1") -HostAddress "0.0.0.0" -Port $Port
