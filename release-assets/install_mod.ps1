$ErrorActionPreference = "Stop"

$source = Join-Path $PSScriptRoot "mods\Syx Duo Realm"
$modsDir = Join-Path $env:APPDATA "songsofsyx\mods"
$destination = Join-Path $modsDir "Syx Duo Realm"

if (-not (Test-Path -LiteralPath $source)) {
    throw "Cannot find packaged mod folder: $source"
}

New-Item -ItemType Directory -Path $modsDir -Force | Out-Null

if (Test-Path -LiteralPath $destination) {
    $backupName = "Syx Duo Realm.backup-" + (Get-Date -Format "yyyyMMdd-HHmmss")
    $backupPath = Join-Path $modsDir $backupName
    Move-Item -LiteralPath $destination -Destination $backupPath
    Write-Host "Backed up existing mod to: $backupPath"
}

Copy-Item -LiteralPath $source -Destination $destination -Recurse -Force

Write-Host "Installed Syx Duo Realm to:"
Write-Host $destination
Write-Host ""
Write-Host "Start Songs of Syx V71 and enable the mod in the Mod menu."

