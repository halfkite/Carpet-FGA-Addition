param(
    [string] $CompatibilityModsDirectory = 'D:\我的世界\服务器\服务端\26.3空岛\mods',
    [string[]] $AdditionalCompatibilityModPatterns = @()
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\spectator-free-teleport-smoke-26.3-$stamp"
$runDir = Join-Path $root "build\spectator-free-teleport-run-26.3-$stamp"
$gradleLog = Join-Path $reportDir 'gradle-console.log'
$serverLog = Join-Path $runDir 'logs\latest.log'
$summaryPath = Join-Path $reportDir 'summary.txt'
$initScript = Join-Path $root 'scripts\gradle\spectator-smoke-isolated.init.gradle'
$jdk = $null
$server = $null
$serverReady = $false
$falseProbePassed = $false
$probePassed = $false
$fullProbePassed = $false
$cleanStop = $false
$failure = ''

function Wait-SpectatorSmokeLogPattern {
    param(
        [string] $Pattern,
        [string] $ServerLogPath,
        [string] $GradleLogPath,
        [System.Diagnostics.Process] $ServerProcess,
        [int] $TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $serverOutput = if (Test-Path -LiteralPath $ServerLogPath) {
            Get-Content -LiteralPath $ServerLogPath -Raw
        } else {
            ''
        }
        $gradleOutput = if (Test-Path -LiteralPath $GradleLogPath) {
            Get-Content -LiteralPath $GradleLogPath -Raw
        } else {
            ''
        }
        $combinedOutput = "$serverOutput`n$gradleOutput"
        if ($combinedOutput -match 'FGA_(TELEPORT_FALSE|SPECTATOR|FULL)_TELEPORT_PROBE_FAIL[^\r\n]*') {
            throw "The in-server spectator probe failed: $($Matches[0])"
        }
        if ($serverOutput -match $Pattern) {
            return
        }
        if ($null -ne $ServerProcess -and $ServerProcess.HasExited) {
            throw "The isolated server exited before logging '$Pattern' (exit code $($ServerProcess.ExitCode))"
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for server log pattern: $Pattern"
}

function Send-SpectatorSmokeCommand {
    param(
        [System.Diagnostics.Process] $ServerProcess,
        [string] $Command,
        [string] $ExpectedPattern,
        [string] $ServerLogPath,
        [string] $GradleLogPath
    )

    $ServerProcess.StandardInput.WriteLine($Command)
    $ServerProcess.StandardInput.Flush()
    Wait-SpectatorSmokeLogPattern -Pattern $ExpectedPattern `
        -ServerLogPath $ServerLogPath -GradleLogPath $GradleLogPath `
        -ServerProcess $ServerProcess
}

$jdkCandidates = [System.Collections.Generic.List[string]]::new()
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
    $jdkCandidates.Add($env:JAVA_HOME)
}
foreach ($installationRoot in @(
    (Join-Path $env:ProgramFiles 'Java'),
    (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
    (Join-Path $env:ProgramFiles 'Microsoft'),
    (Join-Path $env:ProgramFiles 'Amazon Corretto'),
    (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'),
    (Join-Path $env:USERPROFILE '.jdks')
)) {
    if (Test-Path -LiteralPath $installationRoot) {
        Get-ChildItem -LiteralPath $installationRoot -Directory -ErrorAction SilentlyContinue |
            ForEach-Object { $jdkCandidates.Add($_.FullName) }
    }
}
foreach ($candidate in ($jdkCandidates | Select-Object -Unique)) {
    $javaExecutable = Join-Path $candidate 'bin\java.exe'
    $javacExecutable = Join-Path $candidate 'bin\javac.exe'
    if ((Test-Path -LiteralPath $javaExecutable) -and (Test-Path -LiteralPath $javacExecutable)) {
        $versionOutput = (& $javaExecutable -version 2>&1 | Out-String)
        if ($versionOutput -match 'version "25(?:\.|"|\+)') {
            $jdk = $candidate
            break
        }
    }
}
if ($null -eq $jdk) {
    throw 'A JDK 25 installation is required for Minecraft 26.3; set JAVA_HOME or install JDK 25 in a standard location'
}
if (-not (Test-Path -LiteralPath $CompatibilityModsDirectory)) {
    throw "Compatibility mods directory not found: $CompatibilityModsDirectory"
}

$toolchain21 = Join-Path $root 'build\toolchains\jdk-21.0.12.1+1'
$gradleToolchainArgument = @()
if (Test-Path -LiteralPath (Join-Path $toolchain21 'bin\javac.exe')) {
    $gradleToolchainArgument = @("-Dorg.gradle.java.installations.paths=$toolchain21")
}

New-Item -ItemType Directory -Force -Path $reportDir, $runDir, (Join-Path $runDir 'mods') | Out-Null
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
$env:FGA_SPECTATOR_SMOKE_RUN_DIR = $runDir

foreach ($pattern in @(
    'carpet-ams-addition*mc26.3*.jar',
    'carpet-tis-addition*mc26.3*.jar',
    'carpet-org-addition*mc26.3*.jar'
) + $AdditionalCompatibilityModPatterns) {
    $compatibilityJar = Get-ChildItem -LiteralPath $CompatibilityModsDirectory -File -Filter $pattern |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $compatibilityJar) {
        throw "Missing required compatibility mod matching $pattern"
    }
    Copy-Item -LiteralPath $compatibilityJar.FullName -Destination (Join-Path $runDir 'mods')
}

try {
    & (Join-Path $root 'gradlew.bat') :26.3:testClasses @gradleToolchainArgument --no-daemon --configure-on-demand --max-workers=1 *> $gradleLog
    if ($LASTEXITCODE -ne 0) {
        throw "26.3 testClasses failed; see $gradleLog"
    }

    $classSource = Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke'
    $probeClasses = @(Get-ChildItem -LiteralPath $classSource -File -Filter 'SpectatorTeleportProbe*.class')
    if ($probeClasses.Count -eq 0) {
        throw "The 26.3 smoke probe class was not compiled: $classSource"
    }
    $stage = Join-Path $runDir 'probe-staging'
    $probePackage = Join-Path $stage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $probePackage | Out-Null
    $probeClasses | Copy-Item -Destination $probePackage
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\spectator-teleport-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    $probeJar = Join-Path $runDir 'mods\fga-spectator-teleport-probe.jar'
    & (Join-Path $jdk 'bin\jar.exe') cf $probeJar -C $stage .
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not package the test-only spectator probe mod'
    }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false'
        'server-port=0'
        'max-players=8'
        'spawn-protection=0'
        'view-distance=2'
        'simulation-distance=2'
        'level-name=world'
        'gamemode=survival'
        'difficulty=peaceful'
    )

    $serverInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $serverInfo.FileName = 'cmd.exe'
    $toolchainSwitch = if ($gradleToolchainArgument.Count -gt 0) {
        ' ' + $gradleToolchainArgument[0]
    } else {
        ''
    }
    $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $initScript +
        '"' + $toolchainSwitch + ' :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0" > "' +
        $gradleLog + '" 2>&1"'
    $serverInfo.WorkingDirectory = $root
    $serverInfo.UseShellExecute = $false
    $serverInfo.RedirectStandardInput = $true
    $serverInfo.CreateNoWindow = $true
    $server = [System.Diagnostics.Process]::new()
    $server.StartInfo = $serverInfo
    [void] $server.Start()

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Test-Path -LiteralPath $serverLog) -and
            ((Get-Content -LiteralPath $serverLog -Raw) -match 'Done \([0-9.]+s\)!')) {
            $serverReady = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $serverReady) {
        throw "Isolated 26.3 server did not reach Done; see $gradleLog and $serverLog"
    }

    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'carpet opPlayerNoCheat true' `
        -ExpectedPattern 'opPlayerNoCheat: true' -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'carpet preventAdministratorCheat true' `
        -ExpectedPattern 'preventAdministratorCheat: true' -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'player FgaSpecProbe spawn at 0.5 80 0.5' `
        -ExpectedPattern 'FgaSpecProbe\[local\] logged in with entity id' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'player FgaSpecTarget spawn at 10.5 80 0.5' `
        -ExpectedPattern 'FgaSpecTarget\[local\] logged in with entity id' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'fgaTeleportFalseProbe FgaSpecProbe' `
        -ExpectedPattern 'FGA_TELEPORT_FALSE_PROBE_PASS: non-OP #1' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    $falseProbePassed = $true
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'carpet spectatorFreeTeleport true' `
        -ExpectedPattern 'spectatorFreeTeleport: true' -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'gamemode spectator FgaSpecProbe' `
        -ExpectedPattern "Set FgaSpecProbe's game mode to Spectator Mode" `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'fgaSpectatorProbe FgaSpecProbe FgaSpecTarget' `
        -ExpectedPattern 'FGA_SPECTATOR_TELEPORT_PROBE_PASS: non-OP #1' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'op FgaSpecProbe' `
        -ExpectedPattern 'Made FgaSpecProbe a server operator' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'fgaSpectatorProbe FgaSpecProbe FgaSpecTarget' `
        -ExpectedPattern 'FGA_SPECTATOR_TELEPORT_PROBE_PASS: OP #2' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'deop FgaSpecProbe' `
        -ExpectedPattern 'Made FgaSpecProbe no longer a server operator' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'fgaSpectatorProbe FgaSpecProbe FgaSpecTarget' `
        -ExpectedPattern 'FGA_SPECTATOR_TELEPORT_PROBE_PASS: non-OP #3' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    $probePassed = $true

    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'carpet spectatorFreeTeleport full' `
        -ExpectedPattern 'spectatorFreeTeleport: full' -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'fgaSpectatorFullProbe FgaSpecProbe FgaSpecTarget' `
        -ExpectedPattern 'FGA_FULL_TELEPORT_PROBE_PASS: non-OP #4' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'op FgaSpecProbe' `
        -ExpectedPattern 'Made FgaSpecProbe a server operator' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    Send-SpectatorSmokeCommand -ServerProcess $server -Command 'fgaSpectatorFullProbe FgaSpecProbe FgaSpecTarget' `
        -ExpectedPattern 'FGA_FULL_TELEPORT_PROBE_PASS: OP #5' `
        -ServerLogPath $serverLog -GradleLogPath $gradleLog
    $fullProbePassed = $true

    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    $cleanStop = $server.WaitForExit(90000)
    if (-not $cleanStop) {
        throw 'Isolated spectator smoke server did not stop cleanly'
    }
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        try {
            $server.StandardInput.WriteLine('stop')
            $server.StandardInput.Flush()
            $cleanStop = $server.WaitForExit(30000)
        } catch {
        }
    }
    if ($null -ne $server -and -not $server.HasExited) {
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    @(
        "version=26.3"
        "serverReady=$serverReady"
        "probePassed=$probePassed"
        "fullProbePassed=$fullProbePassed"
        "cleanStop=$cleanStop"
        "status=$(if ($serverReady -and $falseProbePassed -and $probePassed -and $fullProbePassed -and $cleanStop -and -not $failure) { 'passed' } else { 'failed' })"
        "falseProbePassed=$falseProbePassed"
        "reason=$failure"
        "compatibilityModsDirectory=$CompatibilityModsDirectory"
        "additionalCompatibilityModPatterns=$($AdditionalCompatibilityModPatterns -join ',')"
        "serverLog=$serverLog"
        "gradleLog=$gradleLog"
    ) | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Remove-Item Env:\FGA_SPECTATOR_SMOKE_RUN_DIR -ErrorAction SilentlyContinue
}

Get-Content -LiteralPath $summaryPath
if (-not ($serverReady -and $falseProbePassed -and $probePassed -and $fullProbePassed -and $cleanStop -and -not $failure)) {
    exit 1
}
