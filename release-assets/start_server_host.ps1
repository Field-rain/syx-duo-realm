param(
    [string] $HostAddress = "0.0.0.0",
    [int] $Port = 8787,
    [string] $DataDir = (Join-Path $PSScriptRoot "server-data")
)

$ErrorActionPreference = "Stop"

$server = Join-Path $PSScriptRoot "server\server.js"
if (-not (Test-Path -LiteralPath $server)) {
    throw "Cannot find server.js: $server"
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Write-Host "Node.js was not found."
    Write-Host "Install Node.js LTS, then run this script again."
    exit 1
}

New-Item -ItemType Directory -Path $DataDir -Force | Out-Null

$env:HOST = $HostAddress
$env:PORT = [string] $Port
$env:DATA_DIR = (Resolve-Path $DataDir).Path

Write-Host "Starting Syx Duo Realm test server..."
Write-Host "Server URL for local host: http://127.0.0.1:$Port/api/state"
Write-Host "For remote friend, use the host Tailscale IP: http://<host-tailscale-ip>:$Port/api/state"
Write-Host "Data directory: $env:DATA_DIR"
Write-Host ""

node $server
