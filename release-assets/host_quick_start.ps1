param(
    [string] $PlayerName = $env:USERNAME,
    [string] $RoomCode = "duo-test",
    [int] $Port = 8787,
    [string] $DataDir = (Join-Path $PSScriptRoot "server-data")
)

$ErrorActionPreference = "Stop"

function Get-FirstLanIp {
    try {
        $addresses = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction Stop |
            Where-Object {
                $_.IPAddress -notlike "127.*" -and
                $_.IPAddress -notlike "169.254.*" -and
                $_.PrefixOrigin -ne "WellKnown"
            } |
            Select-Object -ExpandProperty IPAddress
        return $addresses | Select-Object -First 1
    } catch {
        return $null
    }
}

function Get-TailscaleIp {
    if (-not (Get-Command tailscale -ErrorAction SilentlyContinue)) {
        return $null
    }

    $value = (& tailscale ip -4 2>$null | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $null
    }
    return $value.Trim()
}

if ([string]::IsNullOrWhiteSpace($PlayerName)) {
    $PlayerName = Read-Host "Your player name"
}

if ([string]::IsNullOrWhiteSpace($RoomCode)) {
    $RoomCode = "duo-test"
}

$localUrl = "http://localhost:$Port/api/state"
& (Join-Path $PSScriptRoot "configure_mod_connection.ps1") `
    -ServerUrl $localUrl `
    -PlayerName $PlayerName `
    -RoomCode $RoomCode

$tailscaleIp = Get-TailscaleIp
$lanIp = Get-FirstLanIp
$friendIp = if (-not [string]::IsNullOrWhiteSpace($tailscaleIp)) { $tailscaleIp } else { $lanIp }

Write-Host ""
Write-Host "============================================================"
Write-Host "Syx Duo Realm Host"
Write-Host "============================================================"
Write-Host "Host player: $PlayerName"
Write-Host "Room code:   $RoomCode"
Write-Host "Local URL:   $localUrl"
Write-Host ""

if (-not [string]::IsNullOrWhiteSpace($tailscaleIp)) {
    Write-Host "Tailscale URL:"
    Write-Host "http://$tailscaleIp`:$Port/api/state"
}

if (-not [string]::IsNullOrWhiteSpace($lanIp)) {
    Write-Host "LAN URL:"
    Write-Host "http://$lanIp`:$Port/api/state"
}

if ([string]::IsNullOrWhiteSpace($friendIp)) {
    Write-Host ""
    Write-Host "Could not auto-detect a friend-reachable IP."
    Write-Host "Install/sign in to Tailscale, or find your LAN IPv4 manually."
    Write-Host "Your friend still needs a URL like:"
    Write-Host "SYXDUO|http://HOST_IP:$Port/api/state|$RoomCode"
} else {
    $joinCode = "SYXDUO|http://$friendIp`:$Port/api/state|$RoomCode"
    Write-Host ""
    Write-Host "Send this join code to your friend:"
    Write-Host $joinCode
}

Write-Host ""
Write-Host "Keep this window open while both players are in game."
Write-Host "If Windows Firewall asks, allow Node.js on private networks."
Write-Host "============================================================"
Write-Host ""

& (Join-Path $PSScriptRoot "start_server_host.ps1") -HostAddress "0.0.0.0" -Port $Port -DataDir $DataDir
