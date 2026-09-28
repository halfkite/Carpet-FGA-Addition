param(
    [Parameter(Mandatory = $true)] [string] $ServerFgaJar,
    [string] $ClientFgaJar,
    [string] $ExpectedProfileName = 'FGA_LongFake_0001',
    [switch] $ExpectLongNameFailure,
    [switch] $SkipCompile,
    [string] $Java25Home,
    [string] $Java21Home
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runRoot = Join-Path $root "build\mixed-version-long-name-smoke-26.3-$stamp"
$reportDir = Join-Path $root "scripts\logs\mixed-version-long-name-smoke-26.3-$stamp"
$serverDir = Join-Path $runRoot 'server'
$clientDir = Join-Path $runRoot 'client'
$serverLog = Join-Path $reportDir 'server-gradle.log'
$clientLog = Join-Path $reportDir 'client-gradle.log'
$initScript = Join-Path $root 'scripts\gradle\published-fga-jar-smoke.init.gradle'
$originalJavaHome = $env:JAVA_HOME
$serverProcess = $null
$clientProcess = $null
$passed = $false
$failure = ''
$cleanStop = $false
$joined = $false
$shortNamePassed = $false
$longNamePassed = $false

if ([string]::IsNullOrWhiteSpace($Java25Home)) {
    $settings = (& java -XshowSettings:properties -version 2>&1 | Out-String)
    if ($settings -match 'java.home\s*=\s*([^\r\n]+)') { $Java25Home = $Matches[1].Trim() }
}
if ([string]::IsNullOrWhiteSpace($Java21Home)) {
    foreach ($candidate in @(Get-ChildItem -LiteralPath (Join-Path $root 'build\toolchains') -Directory -ErrorAction SilentlyContinue)) {
        $java = Join-Path $candidate.FullName 'bin\java.exe'
        if ((Test-Path -LiteralPath $java) -and ((& $java -version 2>&1 | Out-String) -match 'version "21(?:\.|"|\+)')) {
            $Java21Home = $candidate.FullName
            break
        }
    }
}
if ([string]::IsNullOrWhiteSpace($Java25Home) -or [string]::IsNullOrWhiteSpace($Java21Home)) {
    throw 'Java 25 and Java 21 JDKs are required'
}
$toolchainOption = '-Dorg.gradle.java.installations.paths=' + $Java21Home

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) { return Get-Content -LiteralPath $path -Raw }
    return ''
}

function Wait-Pattern([string] $path, [string] $pattern, [System.Diagnostics.Process] $process, [int] $seconds = 90) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Log $path) -match $pattern) { return }
        if ($null -ne $process -and $process.HasExited) { throw "Process exited before '$pattern'; see $path" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for '$pattern'; see $path"
}

function Start-SmokeProcess([string] $directory, [string] $task, [string] $arguments, [string] $log, [bool] $stdin) {
    $info = [System.Diagnostics.ProcessStartInfo]::new()
    $info.FileName = 'cmd.exe'
    $info.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $initScript +
        '" "' + $toolchainOption + '" ' + $task + ' --offline --no-daemon --configure-on-demand --max-workers=1 ' +
        '--args="' + $arguments + '" > "' + $log + '" 2>&1"'
    $info.WorkingDirectory = $root
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $stdin
    $info.CreateNoWindow = $true
    $info.Environment['FGA_PUBLISHED_JAR_SMOKE_RUN_DIR'] = $directory
    if ($task -eq ':26.3:runClient') {
        $info.Environment['FGA_MIXED_VERSION_SMOKE_ENDPOINT'] = '127.0.0.1:' + $script:port
        $info.Environment['FGA_MIXED_VERSION_SMOKE_EXPECTED_NAME'] = $ExpectedProfileName
    }
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $info
    [void] $process.Start()
    return $process
}

function Send-ServerCommand([string] $command) {
    $serverProcess.StandardInput.WriteLine($command)
    $serverProcess.StandardInput.Flush()
}

try {
    if (-not (Test-Path -LiteralPath $ServerFgaJar -PathType Leaf)) { throw 'Server FGA JAR does not exist' }
    if ($ClientFgaJar -and -not (Test-Path -LiteralPath $ClientFgaJar -PathType Leaf)) { throw 'Client FGA JAR does not exist' }
    New-Item -ItemType Directory -Force -Path $reportDir, (Join-Path $serverDir 'mods'), (Join-Path $clientDir 'mods') | Out-Null
    $env:JAVA_HOME = $Java25Home
    if (-not $SkipCompile) {
        & (Join-Path $root 'gradlew.bat') :26.3:testClasses $toolchainOption --offline --no-daemon --configure-on-demand --max-workers=1 *> (Join-Path $reportDir 'compile.log')
        if ($LASTEXITCODE -ne 0) { throw 'Test probe compilation failed' }
    }
    $stage = Join-Path $clientDir 'probe-staging'
    $probePackage = Join-Path $stage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $probePackage | Out-Null
    $probeClasses = @(Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') -Filter 'MixedVersionLongNameClientProbe*.class' -File)
    if ($probeClasses.Count -eq 0) { throw 'Compiled mixed-version client probe is missing' }
    $probeClasses | Copy-Item -Destination $probePackage
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\mixed-version-long-name-client-probe\fabric.mod.json') -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $Java25Home 'bin\jar.exe') cf (Join-Path $clientDir 'mods\fga-mixed-version-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package client probe' }
    $serverStage = Join-Path $serverDir 'probe-staging'
    $serverPackage = Join-Path $serverStage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $serverPackage | Out-Null
    $serverClasses = @(Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') -Filter 'MixedVersionLongNameServerProbe*.class' -File)
    if ($serverClasses.Count -eq 0) { throw 'Compiled mixed-version server probe is missing' }
    $serverClasses | Copy-Item -Destination $serverPackage
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\mixed-version-long-name-server-probe\fabric.mod.json') -Destination (Join-Path $serverStage 'fabric.mod.json')
    & (Join-Path $Java25Home 'bin\jar.exe') cf (Join-Path $serverDir 'mods\fga-mixed-version-server-probe.jar') -C $serverStage .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package server probe' }
    Copy-Item -LiteralPath $ServerFgaJar -Destination (Join-Path $serverDir 'mods\server-fga-under-test.jar')
    if ($ClientFgaJar) { Copy-Item -LiteralPath $ClientFgaJar -Destination (Join-Path $clientDir 'mods\client-fga-under-test.jar') }
    $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $script:port = $listener.LocalEndpoint.Port
    $listener.Stop()
    'eula=true' | Set-Content -LiteralPath (Join-Path $serverDir 'eula.txt') -Encoding ascii
    @(
        'server-ip=127.0.0.1', "server-port=$port", 'online-mode=false', 'enforce-secure-profile=false',
        'level-name=world', 'level-type=minecraft:flat', 'max-players=8', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'gamemode=creative', 'difficulty=peaceful', 'allow-flight=true',
        'white-list=false', 'enforce-whitelist=false'
    ) | Set-Content -LiteralPath (Join-Path $serverDir 'server.properties') -Encoding ascii
    $serverProcess = Start-SmokeProcess $serverDir ':26.3:runServer' '--nogui' $serverLog $true
    Wait-Pattern $serverLog 'Done \([0-9.]+s\)!' $serverProcess 180
    Send-ServerCommand 'carpet fakePlayerNameLength 32'
    Wait-Pattern $serverLog 'fakePlayerNameLength: 32' $serverProcess
    $clientProcess = Start-SmokeProcess $clientDir ':26.3:runClient' '--username FGAMixedSmoke --width 854 --height 480' $clientLog $false
    Wait-Pattern $clientLog 'FGA_MIXED_VERSION_JOIN_PASS:' $clientProcess 180
    $joined = $true
    Send-ServerCommand 'player FGAMixedShort spawn at 0 80 0'
    Wait-Pattern $clientLog 'FGA_MIXED_VERSION_SHORT_NAME_PASS:' $clientProcess
    $shortNamePassed = $true
    Send-ServerCommand 'fgaMixedVersionClientState'
    Wait-Pattern $serverLog "FGA_MIXED_VERSION_HANDSHAKE: modded=$(if ($ClientFgaJar) { 'true' } else { 'false' })" $serverProcess
    Send-ServerCommand 'player FGA_LongFake_0001 spawn at 0 80 1'
    if ($ExpectLongNameFailure) {
        Wait-Pattern $clientLog 'FGA_MIXED_VERSION_DISCONNECTED:' $clientProcess
    } else {
        Wait-Pattern $clientLog 'FGA_MIXED_VERSION_LONG_NAME_PASS:' $clientProcess
        $longNamePassed = $true
    }
    if (-not $clientProcess.WaitForExit(30000)) { throw 'Client did not shut down after its test' }
    $clientOutput = Read-Log $clientLog
    if ($ExpectLongNameFailure) {
        if ($clientOutput -notmatch 'DecoderException' -or $clientOutput -notmatch '16') {
            throw 'Expected legacy long-name decoder failure was not present'
        }
    }
    Send-ServerCommand 'stop'
    $cleanStop = $serverProcess.WaitForExit(30000)
    if (-not $cleanStop) { throw 'Server did not stop cleanly' }
    $passed = $true
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        try { Send-ServerCommand 'stop'; $cleanStop = $serverProcess.WaitForExit(30000) } catch { }
    }
    foreach ($process in @($clientProcess, $serverProcess)) {
        if ($null -ne $process -and -not $process.HasExited) { & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null }
    }
    $env:JAVA_HOME = $originalJavaHome
    if (Test-Path -LiteralPath $reportDir) {
        @(
            'minecraftVersion=26.3', "serverFgaJar=$ServerFgaJar", "clientFgaJar=$ClientFgaJar",
            "joined=$joined", "shortNamePassed=$shortNamePassed", "longNamePassed=$longNamePassed",
            "expectedLongNameFailure=$ExpectLongNameFailure", "cleanServerStop=$cleanStop",
            "status=$(if ($passed) { 'passed' } else { 'failed' })", "reason=$failure",
            "runDirectory=$runRoot", "serverLog=$serverLog", "clientLog=$clientLog"
        ) | Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt')
        Get-Content -LiteralPath (Join-Path $reportDir 'summary.txt')
    }
}
if (-not $passed) { exit 1 }
