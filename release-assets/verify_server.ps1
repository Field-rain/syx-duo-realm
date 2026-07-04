param(
    [string] $ServerUrl = "http://localhost:8787/api/state"
)

$ErrorActionPreference = "Stop"

$base = $ServerUrl.TrimEnd("/")
if ($base.EndsWith("/state")) {
    $base = $base.Substring(0, $base.Length - 6)
}

$healthUrl = "$base/health"
Write-Host "Checking $healthUrl"

$result = Invoke-RestMethod $healthUrl
$result | Format-List

Write-Host ""
Write-Host "Server is reachable."

