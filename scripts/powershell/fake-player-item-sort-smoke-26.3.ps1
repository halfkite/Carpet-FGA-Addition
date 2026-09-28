param(
    [string] $CompatibilityModsDirectory = 'D:\我的世界\服务器\服务端\26.3空岛\mods'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\fake-player-item-sort-smoke-26.3-$stamp"
$runDir = Join-Path $root "build\fake-player-item-sort-run-26.3-$stamp"
$gradleLog = Join-Path $reportDir 'gradle-console.log'
$serverLog = Join-Path $runDir 'logs\latest.log'
$summary = Join-Path $reportDir 'summary.txt'
$initScript = Join-Path $root 'scripts\gradle\fake-player-item-sort-smoke-isolated.init.gradle'
$jdk = Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'Java') -Directory |
    Where-Object { $_.Name -match '^jdk-25' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe')) } |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $jdk) { throw 'JDK 25 is required for the 26.3 smoke server' }
$toolchain21 = Join-Path $root 'build\toolchains\jdk-21.0.12.1+1'
$toolchain = "-Dorg.gradle.java.installations.paths=$toolchain21"
$server = $null
$ready = $false
$quickopen = $false
$summon = $false
$stopped = $false
$failure = ''

function Wait-SortLog {
    param([string] $Pattern, [int] $TimeoutSeconds = 90)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $content = if (Test-Path -LiteralPath $serverLog) { Get-Content -LiteralPath $serverLog -Raw } else { '' }
        if ($content -match 'FGA_SORT_PROBE_FAIL:[^\r\n]*') { throw $Matches[0] }
        if ($content -match $Pattern) { return }
        if ($server.HasExited) { throw "Isolated server exited before $Pattern (code $($server.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Pattern"
}

function Send-SortCommand {
    param([string] $Command, [string] $Pattern, [int] $TimeoutSeconds = 90)
    $server.StandardInput.WriteLine($Command)
    $server.StandardInput.Flush()
    Wait-SortLog -Pattern $Pattern -TimeoutSeconds $TimeoutSeconds
}

New-Item -ItemType Directory -Force -Path $reportDir, $runDir, (Join-Path $runDir 'mods') | Out-Null
$env:JAVA_HOME = $jdk.FullName
$env:Path = (Join-Path $jdk.FullName 'bin') + ';' + $env:Path
$env:FGA_SORT_SMOKE_RUN_DIR = $runDir

try {
    & (Join-Path $root 'gradlew.bat') :26.3:testClasses $toolchain --no-daemon --configure-on-demand --max-workers=1 *> $gradleLog
    if ($LASTEXITCODE -ne 0) { throw "26.3 testClasses failed; see $gradleLog" }

    $classes = Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke'
    $probeClasses = @(Get-ChildItem -LiteralPath $classes -File -Filter 'FakePlayerItemSortProbe*.class')
    if ($probeClasses.Count -eq 0) { throw 'Sort probe classes were not compiled' }
    $stage = Join-Path $runDir 'probe-staging'
    $package = Join-Path $stage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $package | Out-Null
    $probeClasses | Copy-Item -Destination $package
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\fake-player-item-sort-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $jdk.FullName 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-sort-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package test-only sort probe' }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=8', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful'
    )

    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'cmd.exe'
    $start.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $initScript +
        '" ' + $toolchain + ' :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0" > "' +
        $gradleLog + '" 2>&1"'
    $start.WorkingDirectory = $root
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.CreateNoWindow = $true
    $server = [System.Diagnostics.Process]::new()
    $server.StartInfo = $start
    [void] $server.Start()

    Wait-SortLog -Pattern 'Done \([0-9.]+s\)!' -TimeoutSeconds 300
    $ready = $true
    Send-SortCommand -Command 'player FgaSortSource spawn at 0.5 80 0.5' `
        -Pattern 'FgaSortSource\[local\] logged in with entity id' -TimeoutSeconds 120
    Send-SortCommand -Command 'fgaSortProbe quickopen FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=quickopen' -TimeoutSeconds 120
    $quickopen = $true
    Send-SortCommand -Command 'fgaSortProbe summon FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=summon' -TimeoutSeconds 120
    $summon = $true
    $smokeText = Get-Content -LiteralPath $serverLog -Raw
    $targetLoginCount = [regex]::Matches($smokeText, 'bulk_cobblestone\[local\] logged in with entity id').Count
    if ($targetLoginCount -ne 1) {
        throw "Summon mode should log in exactly one target fake player, got $targetLoginCount"
    }
    if ($smokeText -match 'bulk_cobblestone_[0-9]+\[local\] logged in with entity id') {
        throw 'Summon mode spawned an unexpected numbered target fake player'
    }

    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    $stopped = $server.WaitForExit(90000)
    if (-not $stopped) { throw 'Isolated server did not stop cleanly' }
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        try {
            $server.StandardInput.WriteLine('stop')
            $server.StandardInput.Flush()
            $stopped = $server.WaitForExit(30000)
        } catch { }
    }
    if ($null -ne $server -and -not $server.HasExited) {
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    @(
        'version=26.3', "serverReady=$ready", "quickopenPassed=$quickopen",
        "summonPassed=$summon", "cleanStop=$stopped",
        "status=$(if ($ready -and $quickopen -and $summon -and $stopped -and -not $failure) { 'passed' } else { 'failed' })",
        "reason=$failure", "serverLog=$serverLog", "gradleLog=$gradleLog"
    ) | Set-Content -LiteralPath $summary -Encoding utf8
    Remove-Item Env:\FGA_SORT_SMOKE_RUN_DIR -ErrorAction SilentlyContinue
}

Get-Content -LiteralPath $summary
if (-not ($ready -and $quickopen -and $summon -and $stopped -and -not $failure)) { exit 1 }
