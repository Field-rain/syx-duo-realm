param(
    [Parameter(Mandatory = $true)]
    [string] $SteamUser,

    [Parameter(Mandatory = $true)]
    [string] $PublishedFileId
)

$ErrorActionPreference = "Stop"

$here = $PSScriptRoot
$projectRoot = Resolve-Path (Join-Path $here "..\..")
$contentFolder = Resolve-Path (Join-Path $here "..\SyxDuoRealm-V71-Workshop\content")
$previewFile = Resolve-Path (Join-Path $here "..\SyxDuoRealm-V71-Workshop\preview.png")
$descriptionFile = Resolve-Path (Join-Path $projectRoot "workshop-assets\description_steamcmd_safe.txt")
$vdfPath = Join-Path $here "syx_duo_realm_description_fix.vdf"
$steamCmdDir = Join-Path $here "steamcmd"
$steamCmdExe = Join-Path $steamCmdDir "steamcmd.exe"

function Escape-Vdf([string] $value) {
    return $value.Replace("\", "\\").Replace('"', "'").Replace("`r", " ").Replace("`n", " ")
}

if (-not (Test-Path -LiteralPath $steamCmdExe)) {
    New-Item -ItemType Directory -Path $steamCmdDir -Force | Out-Null
    $zip = Join-Path $steamCmdDir "steamcmd.zip"
    Write-Host "Downloading SteamCMD..."
    Invoke-WebRequest -Uri "https://steamcdn-a.akamaihd.net/client/installer/steamcmd.zip" -OutFile $zip
    Expand-Archive -LiteralPath $zip -DestinationPath $steamCmdDir -Force
}

$description = Get-Content -LiteralPath $descriptionFile -Raw

$vdf = @"
"workshopitem"
{
    "appid"             "1162750"
    "publishedfileid"   "$(Escape-Vdf $PublishedFileId)"
    "contentfolder"     "$(Escape-Vdf $contentFolder.Path)"
    "previewfile"       "$(Escape-Vdf $previewFile.Path)"
    "visibility"        "2"
    "title"             "Syx Duo Realm"
    "description"       "$(Escape-Vdf $description)"
    "changenote"        "Update Workshop description for 0.3.1."
}
"@

Set-Content -LiteralPath $vdfPath -Value $vdf -Encoding UTF8

Write-Host "Generated VDF:"
Write-Host $vdfPath
Write-Host ""
Write-Host "Updating Workshop description for PublishedFileId=$PublishedFileId"
Write-Host ""

& $steamCmdExe +login $SteamUser +workshop_build_item $vdfPath +quit
