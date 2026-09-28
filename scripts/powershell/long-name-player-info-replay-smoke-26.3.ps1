param(
    [string] $Java25Home,
    [string] $Java21Home,
    [string] $FlashbackJar,
    [switch] $ExpectFailure
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($Java25Home)) {
    $javaSettings = (& java -XshowSettings:properties -version 2>&1 | Out-String)
    if ($javaSettings -match 'java.home\s*=\s*([^\r\n]+)') {
        $Java25Home = $Matches[1].Trim()
    }
}
if ([string]::IsNullOrWhiteSpace($Java21Home)) {
    foreach ($candidate in @(Get-ChildItem -LiteralPath (Join-Path $root 'build\toolchains') -Directory -ErrorAction SilentlyContinue)) {
        $candidateJava = Join-Path $candidate.FullName 'bin\java.exe'
        if ((Test-Path -LiteralPath $candidateJava) -and
            ((& $candidateJava -version 2>&1 | Out-String) -match 'version "21(?:\.|"|\+)')) {
            $Java21Home = $candidate.FullName
            break
        }
    }
}
if ([string]::IsNullOrWhiteSpace($Java25Home) -or [string]::IsNullOrWhiteSpace($Java21Home)) {
    throw 'Java 25 and Java 21 JDKs are required; pass -Java25Home and -Java21Home'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $root "build\long-name-replay-smoke-26.3-$stamp"
$reportDir = Join-Path $root "scripts\logs\long-name-replay-smoke-26.3-$stamp"
$compileLog = Join-Path $reportDir 'compile.log'
$gradleLog = Join-Path $reportDir 'client-gradle.log'
$clientLog = Join-Path $runDir 'logs\latest.log'
$probeStage = Join-Path $runDir 'probe-staging'
$probePackage = Join-Path $probeStage 'carpet\fga\smoke'
$taskJavaHome = $env:JAVA_HOME
$clientProcess = $null
$passed = $false
$failure = ''
$toolchainOption = '-Dorg.gradle.java.installations.paths=' + $Java21Home

try {
    foreach ($jdk in @($Java25Home, $Java21Home)) {
        if (-not (Test-Path -LiteralPath (Join-Path $jdk 'bin\javac.exe'))) {
            throw "JDK not found: $jdk"
        }
    }
    New-Item -ItemType Directory -Force -Path $reportDir, $probePackage, (Join-Path $runDir 'mods') | Out-Null
    $env:JAVA_HOME = $Java25Home
    & (Join-Path $root 'gradlew.bat') :26.3:testClasses $toolchainOption --offline --no-daemon --configure-on-demand --max-workers=1 *> $compileLog
    if ($LASTEXITCODE -ne 0) { throw "Test probe compilation failed; see $compileLog" }
    $probeClasses = @(Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') -Filter 'LongNamePlayerInfoReplayProbe*.class' -File)
    if ($probeClasses.Count -eq 0) { throw 'Compiled replay probe is missing' }
    $probeClasses | Copy-Item -Destination $probePackage
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\long-name-player-info-replay-probe\fabric.mod.json') -Destination (Join-Path $probeStage 'fabric.mod.json')
    & (Join-Path $Java25Home 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-long-name-replay-probe.jar') -C $probeStage .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package the opt-in test probe' }
    if (-not [string]::IsNullOrWhiteSpace($FlashbackJar)) {
        Copy-Item -LiteralPath $FlashbackJar -Destination (Join-Path $runDir 'mods\flashback-under-test.jar')
    }

    # Only this isolated test client is launched; it stops itself after the codec probe.
    $clientInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $clientInfo.FileName = 'cmd.exe'
    $clientInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" "' + $toolchainOption +
        '" "-PclientRunDir=' + $runDir + '" :26.3:runClient --offline --no-daemon --configure-on-demand --max-workers=1 ' +
        '--args="--username FGAReplaySmoke --width 854 --height 480" > "' + $gradleLog + '" 2>&1"'
    $clientInfo.WorkingDirectory = $root
    $clientInfo.UseShellExecute = $false
    $clientInfo.CreateNoWindow = $true
    $clientProcess = [System.Diagnostics.Process]::new()
    $clientProcess.StartInfo = $clientInfo
    [void] $clientProcess.Start()
    $deadline = (Get-Date).AddMinutes(4)
    while (-not $clientProcess.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 500
    }
    if (-not $clientProcess.HasExited) { throw 'Test client did not stop within four minutes' }
    $output = Get-Content -LiteralPath $gradleLog -Raw
    if ($ExpectFailure) {
        $passed = $output -match 'FGA_LONG_NAME_REPLAY_PROBE_FAIL:' -and $output -match 'String too big'
    } else {
        $passed = $clientProcess.ExitCode -eq 0 -and $output -match 'FGA_LONG_NAME_REPLAY_PROBE_PASS:' -and
            $output -notmatch 'FGA_LONG_NAME_REPLAY_PROBE_FAIL:'
        if (-not [string]::IsNullOrWhiteSpace($FlashbackJar)) {
            $passed = $passed -and $output -match 'FGA_FLASHBACK_REPLAY_PROBE_PASS:'
        }
    }
    if (-not $passed) { throw "Unexpected replay probe result; see $gradleLog" }
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $clientProcess -and -not $clientProcess.HasExited) {
        # Terminate only the process tree created by this script after a failed test timeout.
        & taskkill.exe /PID $clientProcess.Id /T /F 2>$null | Out-Null
    }
    $env:JAVA_HOME = $taskJavaHome
    if (Test-Path -LiteralPath $reportDir) {
        $summary = @(
            'version=26.3'
            "expectedFailure=$ExpectFailure"
            "flashbackRequested=$(-not [string]::IsNullOrWhiteSpace($FlashbackJar))"
            "status=$(if ($passed -and -not $failure) { 'passed' } else { 'failed' })"
            "reason=$failure"
            "runDirectory=$runDir"
            "clientLog=$clientLog"
            "gradleLog=$gradleLog"
        )
        $summary | Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt')
        $summary
    }
}
if (-not $passed -or $failure) { exit 1 }
