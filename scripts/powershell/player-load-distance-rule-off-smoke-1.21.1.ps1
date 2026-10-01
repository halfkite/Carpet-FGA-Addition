$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$jdk = Join-Path $root 'build\toolchains\jdk-21.0.12.1+1'
if (-not (Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe'))) { throw 'JDK 21 fixture toolchain missing' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $root "build\player-load-distance-run-1.21.1-$stamp"
$reportDir = Join-Path $root "scripts\logs\player-load-distance-1.21.1-$stamp"
$log = Join-Path $reportDir 'gradle-console.log'
$serverLog = Join-Path $runDir 'logs\latest.log'
$summary = Join-Path $reportDir 'summary.txt'
$server = $null
$passed = $false
$stopped = $false
$failure = ''
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
$env:FGA_DISTANCE_SMOKE_RUN_DIR = $runDir

function Wait-DistanceLog([string] $pattern, [int] $seconds = 120) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $raw = if (Test-Path -LiteralPath $serverLog) { Get-Content -LiteralPath $serverLog -Raw -Encoding UTF8 } else { '' }
        if ($raw -match 'FGA_DISTANCE_PROBE_FAIL:[^\r\n]*') { throw $Matches[0] }
        if ($raw -match $pattern) { return }
        if ($server.HasExited) { throw "Test server exited before $pattern" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $pattern"
}

New-Item -ItemType Directory -Path $runDir, $reportDir, (Join-Path $runDir 'mods') | Out-Null
try {
    & (Join-Path $root 'gradlew.bat') :1.21.1:testClasses --no-daemon --configure-on-demand --max-workers=1
    if ($LASTEXITCODE -ne 0) { throw 'Probe compilation failed' }
    $stageDir = Join-Path $runDir 'probe-staging'
    $packageDir = Join-Path $stageDir 'carpet\fga\smoke'
    New-Item -ItemType Directory -Path $packageDir | Out-Null
    $classes = Join-Path $root 'versions\1.21.1\build\classes\java\test\carpet\fga\smoke'
    Get-ChildItem -LiteralPath $classes -Filter 'PlayerLoadDistanceProbe*.class' | Copy-Item -Destination $packageDir
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\player-load-distance-probe\fabric.mod.json') -Destination $stageDir
    & (Join-Path $jdk 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-distance-probe.jar') -C $stageDir .
    if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-ip=127.0.0.1', 'server-port=0', 'view-distance=10',
        'simulation-distance=2', 'level-name=world', 'difficulty=peaceful'
    )
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'cmd.exe'
    $init = Join-Path $root 'scripts\gradle\player-load-distance-smoke-isolated.init.gradle'
    $start.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $init +
        '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0" > "' + $log + '" 2>&1"'
    $start.WorkingDirectory = $root
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.CreateNoWindow = $true
    $server = [Diagnostics.Process]::new()
    $server.StartInfo = $start
    [void]$server.Start()
    Wait-DistanceLog 'Done \([0-9.]+s\)!' 240
    $server.StandardInput.WriteLine('player FgaDistanceProbe spawn at 0 100 0')
    $server.StandardInput.Flush()
    Wait-DistanceLog 'FgaDistanceProbe\[local\] logged in' 120
    $server.StandardInput.WriteLine('fgaDistanceProbe FgaDistanceProbe')
    $server.StandardInput.Flush()
    Wait-DistanceLog 'FGA_DISTANCE_PROBE_PASS: disabled=true restore-once=true none-cleanup=true current-baseline=true external-writer=true' 90
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
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    $result = @("version=1.21.1", "developmentRuntime=True", "probePassed=$passed", "cleanStop=$stopped",
        "status=$(if ($passed -and $stopped -and -not $failure) { 'passed' } else { 'failed' })",
        "reason=$failure", "serverLog=$serverLog", "gradleLog=$log")
    $result | Set-Content -LiteralPath $summary -Encoding UTF8
    $result | Write-Output
}
if (-not ($passed -and $stopped -and -not $failure)) { exit 1 }
