$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jdk = Join-Path $root 'build\toolchains\jdk-21.0.12.1+1'
if (-not (Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe'))) { throw 'JDK 21 fixture toolchain missing' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $root "build\resilient-plants-run-1.21.1-$stamp"
$reportDir = Join-Path $root "scripts\logs\resilient-plants-1.21.1-$stamp"
$log = Join-Path $reportDir 'gradle-console.log'
$serverLog = Join-Path $runDir 'logs\latest.log'
$server = $null
$passed = $false
$stopped = $false
$failure = ''
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
$env:FGA_PLANTS_SMOKE_RUN_DIR = $runDir

function Wait-PlantsLog([string] $pattern, [int] $seconds = 120) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $raw = if (Test-Path -LiteralPath $serverLog) { Get-Content -LiteralPath $serverLog -Raw -Encoding UTF8 } else { '' }
        if ($raw -match 'FGA_PLANTS_(?:PROBE|TERRAIN)_FAIL:[^\r\n]*') { throw $Matches[0] }
        if ($raw -match 'Player name: [^\r\n]+ is too long') { throw $Matches[0] }
        if ($raw -match $pattern) { return }
        if ($server.HasExited) { throw "Test server exited before $pattern" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $pattern"
}

# Fresh disposable world only: never reuse, delete or modify production saves
if ((Test-Path -LiteralPath $runDir) -or (Test-Path -LiteralPath $reportDir)) { throw 'Fixture directory already exists' }
New-Item -ItemType Directory -Path $runDir, $reportDir, (Join-Path $runDir 'mods') | Out-Null
try {
    & (Join-Path $root 'gradlew.bat') :1.21.1:testClasses --no-daemon --configure-on-demand --max-workers=1
    if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
    $stageDir = Join-Path $runDir 'probe-staging'
    $packageDir = Join-Path $stageDir 'carpet\fga\smoke'
    New-Item -ItemType Directory -Path $packageDir | Out-Null
    $classes = Join-Path $root 'versions\1.21.1\build\classes\java\test\carpet\fga\smoke'
    Get-ChildItem -LiteralPath $classes -Filter 'ResilientPlantsProbe*.class' | Copy-Item -Destination $packageDir
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\resilient-plants-probe\fabric.mod.json') -Destination $stageDir
    & (Join-Path $jdk 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-plants-probe.jar') -C $stageDir .
    if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-ip=127.0.0.1', 'server-port=0', 'view-distance=2',
        'simulation-distance=2', 'level-name=world', 'difficulty=peaceful', 'level-seed=428611'
    )
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'cmd.exe'
    $init = Join-Path $root 'scripts\gradle\resilient-plants-smoke-isolated.init.gradle'
    $start.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $init +
        '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0" > "' + $log + '" 2>&1"'
    $start.WorkingDirectory = $root
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.CreateNoWindow = $true
    $server = [Diagnostics.Process]::new()
    $server.StartInfo = $start
    [void]$server.Start()
    Wait-PlantsLog 'Done \([0-9.]+s\)!' 240
    $server.StandardInput.WriteLine('fgaPlantsProbe')
    $server.StandardInput.Flush()
    Wait-PlantsLog 'FGA_PLANTS_PROBE_PASS: checks=52 worldgen=true runtime=true updates=true' 90
    $server.StandardInput.WriteLine('player FgaPlantProbe spawn at 0 100 0')
    $server.StandardInput.Flush()
    Wait-PlantsLog 'FgaPlantProbe\[local\] logged in' 120
    $server.StandardInput.WriteLine('fgaPlantsTerrainProbe FgaPlantProbe 12')
    $server.StandardInput.Flush()
    Wait-PlantsLog 'FGA_PLANTS_TERRAIN_PASS: chunks=12 bushes=[1-9][0-9]* unsupported=0 rule=true fake=FgaPlantProbe' 300
    $passed = $true
    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    $stopped = $server.WaitForExit(60000)
    if (-not $stopped) { throw 'Test server did not stop normally' }
    if ($server.ExitCode -ne 0) { throw "Test server exit code $($server.ExitCode)" }
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        # Process and children belong to this invocation, not an existing game
        try {
            & taskkill.exe /PID $server.Id /T /F 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { $failure = "$failure; test-process cleanup returned $LASTEXITCODE" }
        } catch {
            $failure = "$failure; test-process cleanup failed: $($_.Exception.Message)"
        }
    }
    $result = @('version=1.21.1', 'developmentRuntime=True', "probePassed=$passed", "cleanStop=$stopped",
        "status=$(if ($passed -and $stopped -and -not $failure) { 'passed' } else { 'failed' })",
        "reason=$failure", "serverLog=$serverLog", "gradleLog=$log")
    $result | Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt') -Encoding UTF8
    $result | Write-Output
}
if (-not ($passed -and $stopped -and -not $failure)) { exit 1 }
