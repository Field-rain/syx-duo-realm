param(
    [string] $JoinCode = "",
    [string] $PlayerName = $env:USERNAME,
    [int] $SyncIntervalSeconds = 30
)

$ErrorActionPreference = "Stop"

function Parse-JoinCode {
    param([string] $Value)

    $text = $Value.Trim()
    if ([string]::IsNullOrWhiteSpace($text)) {
        throw "Join code is required."
    }

    if ($text.StartsWith("SYXDUO|")) {
        $parts = $text.Split("|")
        if ($parts.Length -eq 3 -and $parts[1].StartsWith("http")) {
            return @{
                ServerUrl = $parts[1].Trim()
                RoomCode = $parts[2].Trim()
            }
        }

        $server = $null
        $room = $null
        foreach ($part in $parts) {
            if ($part.StartsWith("server=")) {
                $server = $part.Substring("server=".Length).Trim()
            } elseif ($part.StartsWith("room=")) {
                $room = $part.Substring("room=".Length).Trim()
            }
        }

        if ($server -and $room) {
            return @{
                ServerUrl = $server
                RoomCode = $room
            }
        }
    }

    if ($text.StartsWith("http")) {
        return @{
            ServerUrl = $text
            RoomCode = "duo-test"
        }
    }

    throw "Could not parse join code. Expected SYXDUO|http://host:8787/api/state|room-code"
}

if ([string]::IsNullOrWhiteSpace($JoinCode)) {
    $JoinCode = Read-Host "Paste host join code"
}

if ([string]::IsNullOrWhiteSpace($PlayerName)) {
    $PlayerName = Read-Host "Your player name"
}

$parsed = Parse-JoinCode $JoinCode
$serverUrl = $parsed.ServerUrl
$roomCode = $parsed.RoomCode

& (Join-Path $PSScriptRoot "configure_mod_connection.ps1") `
    -ServerUrl $serverUrl `
    -PlayerName $PlayerName `
    -RoomCode $roomCode `
    -SyncIntervalSeconds $SyncIntervalSeconds

Write-Host ""
Write-Host "Testing server connection..."
try {
    & (Join-Path $PSScriptRoot "verify_server.ps1") -ServerUrl $serverUrl
    Write-Host ""
    Write-Host "Join setup is complete."
    Write-Host "Start Songs of Syx V71, enable Syx Duo Realm, load a test save, then wait for R OK."
} catch {
    Write-Host ""
    Write-Host "Config was written, but the server test failed:"
    Write-Host $_.Exception.Message
    Write-Host ""
    Write-Host "Check that the host server window is still open, Tailscale/LAN is connected, and firewall allows TCP 8787."
    exit 1
}
