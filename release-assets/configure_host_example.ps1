$ErrorActionPreference = "Stop"

& (Join-Path $PSScriptRoot "configure_mod_connection.ps1") `
    -ServerUrl "http://localhost:8787/api/state" `
    -PlayerName $env:USERNAME `
    -RoomCode "duo-test"
