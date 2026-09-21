param(
    [string] $VersionList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\player-possession-command-refresh-smoke-all-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$scriptPath = Join-Path $PSScriptRoot 'player-possession-command-refresh-smoke-26.2.ps1'
$allVersions = @(
    '1.21.1', '1.21.3', '1.21.4', '1.21.5',
    '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2', '26.3'
)
$versions = if ([string]::IsNullOrWhiteSpace($VersionList)) {
    $allVersions
} else {
    @($VersionList.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$results = @()

foreach ($version in $versions) {
    Write-Host "START $version"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $scriptPath -Version $version
    $exitCode = $LASTEXITCODE
    $latestReport = Get-ChildItem -LiteralPath (Join-Path $root 'scripts\logs') -Directory -Filter "player-possession-command-refresh-smoke-$version-*" |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $childSummary = $null
    if ($null -ne $latestReport) {
        $childSummaryPath = Join-Path $latestReport.FullName 'summary.json'
        if (Test-Path -LiteralPath $childSummaryPath) {
            $childSummary = Get-Content -LiteralPath $childSummaryPath -Raw | ConvertFrom-Json
        }
    }
    $status = if ($exitCode -eq 0 -and $null -ne $childSummary -and $childSummary.status -eq 'passed') {
        'passed'
    } else {
        'failed'
    }
    $results += [ordered]@{
        version = $version
        status = $status
        exitCode = $exitCode
        childReportDir = if ($null -ne $latestReport) { $latestReport.FullName } else { '' }
        childSummary = $childSummary
    }
    $results | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Write-Host "RESULT $version $status"
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-Host "COMPLETE passed=$passed total=$($results.Count)"
Get-Content -LiteralPath $summaryPath
if ($passed -ne $results.Count) { exit 1 }
