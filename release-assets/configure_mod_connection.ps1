param(
    [string] $ServerUrl = "http://localhost:8787/api/state",
    [string] $PlayerName = $env:USERNAME,
    [string] $RoomCode = "duo-test",
    [int] $SyncIntervalSeconds = 30,
    [string] $TradeOfferResourceKey = "FISH",
    [int] $TradeOfferAmount = 25,
    [string] $TradeOfferToPlayer = "",
    [bool] $MirrorNativeDiplomacy = $true,
    [ValidateSet("TRADE", "NEUTRAL", "PACT", "ALLY")]
    [string] $NativePeaceStance = "TRADE"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($PlayerName)) {
    throw "PlayerName is required."
}

$dir = Join-Path $env:APPDATA "songsofsyx\saves\profile\Syx Duo Realm"
$path = Join-Path $dir "syx_duo_realm.json"
New-Item -ItemType Directory -Path $dir -Force | Out-Null

$config = [ordered]@{
    serverUrl = $ServerUrl
    playerName = $PlayerName
    roomCode = $RoomCode
    syncIntervalSeconds = $SyncIntervalSeconds
    tradeOfferResourceKey = $TradeOfferResourceKey
    tradeOfferAmount = $TradeOfferAmount
    tradeOfferToPlayer = $TradeOfferToPlayer
    mirrorNativeDiplomacy = $MirrorNativeDiplomacy
    nativePeaceStance = $NativePeaceStance
}

$config | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $path -Encoding UTF8

Write-Host "Wrote Syx Duo Realm config:"
Write-Host $path
Write-Host ""
Get-Content -LiteralPath $path
