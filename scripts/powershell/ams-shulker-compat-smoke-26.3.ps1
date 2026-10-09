param(
    [Parameter(Mandatory)][string] $FgaJar,
    [Parameter(Mandatory)][string] $LegacyAmsJar,
    [Parameter(Mandatory)][string] $BetaAmsJar,
    [Parameter(Mandatory)][string] $Jdk25,
    [Parameter(Mandatory)][string] $Jdk21
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runRoot = Join-Path $root "build\ams-shulker-compat-run-26.3-$stamp"
$report = Join-Path $root "scripts\logs\ams-shulker-compat-26.3-$stamp"
$init = Join-Path $root 'scripts\gradle\published-fga-jar-smoke.init.gradle'
foreach ($path in @($FgaJar, $LegacyAmsJar, $BetaAmsJar, "$Jdk25\bin\jar.exe", "$Jdk21\bin\javac.exe")) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Missing prerequisite: $path" }
}
New-Item -ItemType Directory -Path $report, $runRoot | Out-Null
$stage = Join-Path $runRoot 'probe-staging'
$package = Join-Path $stage 'carpet\fga\smoke'
New-Item -ItemType Directory -Path $package -Force | Out-Null
$classes = @(Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') -Filter 'AmsShulkerCompatProbe*.class')
if ($classes.Count -eq 0) { throw 'Compile :26.3:testClasses before running this test' }
$classes | Copy-Item -Destination $package
Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\ams-shulker-compat-probe\fabric.mod.json') -Destination (Join-Path $stage 'fabric.mod.json')
$probeJar = Join-Path $runRoot 'fga-ams-shulker-probe.jar'
& "$Jdk25\bin\jar.exe" cf $probeJar -C $stage .
if ($LASTEXITCODE -ne 0) { throw 'Could not package test probe' }
$results = @()
foreach ($scenario in @('none', 'legacy-off', 'legacy-on', 'beta')) {
    $run = Join-Path $runRoot $scenario
    $mods = Join-Path $run 'mods'
    $world = Join-Path $run 'world'
    New-Item -ItemType Directory -Path $mods, $world -Force | Out-Null
    Copy-Item -LiteralPath $FgaJar, $probeJar -Destination $mods
    if ($scenario.StartsWith('legacy')) { Copy-Item -LiteralPath $LegacyAmsJar -Destination $mods }
    if ($scenario -eq 'beta') { Copy-Item -LiteralPath $BetaAmsJar -Destination $mods }
    Set-Content -LiteralPath (Join-Path $run 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $run 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-ip=127.0.0.1', 'server-port=0', 'view-distance=2',
        'simulation-distance=2', 'level-name=world', 'gamemode=survival', 'difficulty=peaceful'
    )
    if ($scenario -ne 'none') {
        $initial = if ($scenario -eq 'legacy-on') { 'true' } else { 'false' }
        Set-Content -LiteralPath (Join-Path $world 'carpet.conf') -Encoding ascii -Value "largeShulkerBox $initial"
    }
    $log = Join-Path $report "$scenario.log"
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = 'cmd.exe'
    $info.WorkingDirectory = $root
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardInput = $true
    $info.Environment['JAVA_HOME'] = $Jdk25
    $info.Environment['FGA_PUBLISHED_JAR_SMOKE_RUN_DIR'] = $run
    $info.Environment['FGA_AMS_SMOKE_SCENARIO'] = $scenario
    $info.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" "-Dorg.gradle.java.installations.paths=' + $Jdk21 + ',' + $Jdk25 + '" -I "' + $init + '" :26.3:runServer --offline --no-daemon --configure-on-demand --max-workers=1 > "' + $log + '" 2>&1"'
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    Write-Output "Starting isolated scenario: $scenario"
    try {
        [void] $process.Start()
        $deadline = (Get-Date).AddMinutes(5)
        while (-not $process.WaitForExit(500)) {
            if ((Get-Date) -gt $deadline) { throw "Scenario timed out: $scenario; see $log" }
        }
        $output = Get-Content -LiteralPath $log -Raw
        if ($process.ExitCode -ne 0 -or $output -match 'FGA_AMS_SHULKER_FAIL' -or
            $output -notmatch "FGA_AMS_SHULKER_PASS scenario=$scenario checks=(\d+)" -or
            $output -notmatch 'Stopping server') {
            throw "Scenario failed: $scenario; see $log"
        }
        $count = [regex]::Match($output, "FGA_AMS_SHULKER_PASS scenario=$scenario checks=(\d+)").Groups[1].Value
        $results += [pscustomobject]@{ scenario = $scenario; checks = [int]$count; passed = $true; log = $log }
        $results | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $report 'summary.json') -Encoding utf8
        Write-Output "Passed: $scenario ($count checks)"
    } finally {
        if (-not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
        $process.Dispose()
    }
}
Write-Output "Report: $report"
