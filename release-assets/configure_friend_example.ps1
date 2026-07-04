param(
    [Parameter(Mandatory = $true)]
    [string] $HostTailscaleIp,

    [string] $PlayerName = $env:USERNAME,

    [string] $RoomCode = "duo-test"
)

$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "configure_mod_connection.ps1") `
    -ServerUrl "http://$HostTailscaleIp`:8787/api/state" `
    -PlayerName $PlayerName `
    -RoomCode $RoomCode
