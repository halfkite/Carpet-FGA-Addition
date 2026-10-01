param(
    [string] $CompatibilityModsDirectory = 'D:\我的世界\服务器\服务端\26.3空岛\mods',
    [string] $Java25Home,
    [string] $Java21Home,
    [switch] $SkipCompile
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $root "build\fake-player-rejoin-smoke-26.3-$stamp"
$reportDir = Join-Path $root "scripts\logs\fake-player-rejoin-smoke-26.3-$stamp"
$serverLog = Join-Path $runDir 'logs\latest.log'
$gradleLog = Join-Path $reportDir 'gradle-console.log'
$summary = Join-Path $reportDir 'summary.txt'
$initScript = Join-Path $root 'scripts\gradle\fake-player-rejoin-smoke-isolated.init.gradle'
$server = $null
$failure = ''
$passed = $false
$originalJavaHome = $env:JAVA_HOME
$jdk25 = $null
$jdk21 = $null
$jdkCandidates = [System.Collections.Generic.List[string]]::new()
foreach ($candidate in @($Java25Home, $Java21Home, $env:JAVA_HOME, $env:JAVA21_HOME,
        (Join-Path $root 'build\toolchains\jdk-21.0.12.1+1'))) {
    if (-not [string]::IsNullOrWhiteSpace($candidate)) { $jdkCandidates.Add($candidate) }
}
foreach ($directory in @((Join-Path $env:ProgramFiles 'Java'), 'D:\Java', 'D:\java')) {
    if (Test-Path -LiteralPath $directory) {
        Get-ChildItem -LiteralPath $directory -Directory | ForEach-Object {
            $jdkCandidates.Add($_.FullName)
            Get-ChildItem -LiteralPath $_.FullName -Directory | ForEach-Object { $jdkCandidates.Add($_.FullName) }
        }
    }
}
foreach ($candidate in ($jdkCandidates | Select-Object -Unique)) {
    $java = Join-Path $candidate 'bin\java.exe'
    if (-not (Test-Path -LiteralPath $java)) { continue }
    $version = (& $java -version 2>&1 | Out-String)
    if ($null -eq $jdk25 -and $version -match 'version "25(?:\.|"|\+)') { $jdk25 = $candidate }
    if ($null -eq $jdk21 -and $version -match 'version "21(?:\.|"|\+)') { $jdk21 = $candidate }
}
if ($null -eq $jdk25 -or $null -eq $jdk21) { throw 'Java 25 and Java 21 JDKs are required; provide -Java25Home and -Java21Home' }
$toolchainArg = '-Dorg.gradle.java.installations.paths=' + ($jdk21 -replace '\\', '/')

function Wait-Log([string] $Pattern, [int] $MinimumCount = 1, [int] $Seconds = 60) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $content = if (Test-Path -LiteralPath $serverLog) { Get-Content -LiteralPath $serverLog -Raw } else { '' }
        $gradleContent = if (Test-Path -LiteralPath $gradleLog) { Get-Content -LiteralPath $gradleLog -Raw } else { '' }
        if ($null -eq $content) { $content = '' }
        if ($null -eq $gradleContent) { $gradleContent = '' }
        if ($content -match 'FGA_REJOIN_PROBE_FAIL:[^\r\n]*') { throw $Matches[0] }
        if ($gradleContent -match 'MixinApplyError|InjectionError') { throw 'Mixin application failed; see Gradle log' }
        if ([regex]::Matches($content, $Pattern).Count -ge $MinimumCount) { return }
        if ($null -ne $server -and $server.HasExited) { throw "Server exited before '$Pattern'" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for '$Pattern' ($MinimumCount occurrences); see $serverLog"
}

function Send-Command([string] $Line, [string] $Pattern, [int] $MinimumCount = 1) {
    $server.StandardInput.WriteLine($Line)
    $server.StandardInput.Flush()
    Wait-Log $Pattern $MinimumCount
}

function Start-IsolatedServer {
    if (Test-Path -LiteralPath $serverLog) {
        Move-Item -LiteralPath $serverLog -Destination (Join-Path $reportDir 'first-server.log')
    }
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $initScript +
        '" "' + $toolchainArg + '"' +
        ' :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0" > "' +
        $gradleLog + '" 2>&1"'
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.CreateNoWindow = $true
    $script:server = [System.Diagnostics.Process]::new()
    $script:server.StartInfo = $startInfo
    [void] $script:server.Start()
    Wait-Log 'Done \([0-9.]+s\)!' 1 300
}

function Stop-IsolatedServer {
    if ($null -ne $server -and -not $server.HasExited) {
        $server.StandardInput.WriteLine('stop')
        $server.StandardInput.Flush()
        if (-not $server.WaitForExit(90000)) { throw 'Isolated server did not stop cleanly' }
    }
}

try {
    if (-not (Test-Path -LiteralPath (Join-Path $CompatibilityModsDirectory 'carpet-tis-addition-v1.82.4-mc26.3.jar'))) {
        throw 'Carpet TIS 26.3 jar is missing from the compatibility mods directory'
    }
    New-Item -ItemType Directory -Force -Path $reportDir, (Join-Path $runDir 'mods') | Out-Null
    Copy-Item -LiteralPath (Join-Path $CompatibilityModsDirectory 'carpet-tis-addition-v1.82.4-mc26.3.jar') `
        -Destination (Join-Path $runDir 'mods')
    $env:JAVA_HOME = $jdk25
    $env:Path = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:Path
    $env:FGA_REJOIN_SMOKE_RUN_DIR = $runDir
    if (-not $SkipCompile) {
        & (Join-Path $root 'gradlew.bat') `
            $toolchainArg `
            :26.3:testClasses --no-daemon --configure-on-demand --max-workers=1 *> $gradleLog
        if ($LASTEXITCODE -ne 0) { throw "26.3 testClasses failed; see $gradleLog" }
    }

    $stage = Join-Path $runDir 'probe-staging'
    $package = Join-Path $stage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $package | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') `
        -Filter 'FakePlayerRejoinProbe*.class' -File | Copy-Item -Destination $package
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\fake-player-rejoin-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $env:JAVA_HOME 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-rejoin-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package rejoin probe' }
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=4', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful'
    )

    Start-IsolatedServer
    Send-Command 'carpet enhancedFakePlayerRejoin true' 'enhancedFakePlayerRejoin: true'
    Send-Command 'forceload add -16 -16 16 16' 'force loaded|Marked chunk'
    Send-Command 'fill -3 79 -3 3 79 3 minecraft:stone' 'Successfully filled'
    Send-Command 'player FgaRejoinBot spawn at 0.5 80 0.5' 'FgaRejoinBot\[local\] logged in' 1
    Send-Command 'summon minecraft:oak_boat 0.5 80 0.5' 'Summoned new' 1
    Send-Command 'summon minecraft:pig 0.5 80 0.5' 'Summoned new' 2
    Send-Command 'fgaRejoinProbe setup FgaRejoinBot' 'FGA_REJOIN_PROBE_PASS: setup'
    Send-Command 'player FgaRejoinBot kill' 'FgaRejoinBot lost connection'
    Send-Command 'fgaRejoinProbe offline FgaRejoinBot' 'FGA_REJOIN_PROBE_PASS: offline'
    Stop-IsolatedServer

    Start-IsolatedServer
    Send-Command 'carpet enhancedFakePlayerRejoin true' 'enhancedFakePlayerRejoin: true'
    Send-Command 'player FgaRejoinBot rejoin' 'FgaRejoinBot\[local\] logged in' 1
    Send-Command 'fgaRejoinProbe restored FgaRejoinBot' 'FGA_REJOIN_PROBE_PASS: restored'
    Send-Command 'player FgaRejoinBot kill' 'FgaRejoinBot lost connection'
    Send-Command 'execute in minecraft:the_nether run forceload add 40 40' 'force loaded|Marked chunk'
    Send-Command 'execute in minecraft:the_nether run fill 38 80 38 42 83 42 minecraft:air' `
        'Successfully filled|No blocks were filled'
    Send-Command 'execute in minecraft:the_nether run fill 38 79 38 42 79 42 minecraft:stone' `
        'Successfully filled|No blocks were filled' 2
    Send-Command 'player FgaRejoinBot rejoin at 40.5 80 40.5 facing 90 0 in minecraft:the_nether' `
        'FgaRejoinBot\[local\] logged in' 2
    Send-Command 'fgaRejoinProbe cross FgaRejoinBot' 'FGA_REJOIN_PROBE_PASS: cross'
    Send-Command 'player FgaRejoinBot kill' 'FgaRejoinBot lost connection' 2
    Send-Command 'player FgaRejoinBot rejoin at 42.5 80 42.5 in minecraft:the_nether' `
        'FgaRejoinBot\[local\] logged in' 3
    Send-Command 'fgaRejoinProbe repeat FgaRejoinBot' 'FGA_REJOIN_PROBE_PASS: repeat'
    Send-Command 'carpet enhancedFakePlayerRejoin false' 'enhancedFakePlayerRejoin: false'
    Send-Command 'player FgaRejoinBot kill' 'FgaRejoinBot lost connection' 3
    Send-Command 'player FgaRejoinBot rejoin' 'FgaRejoinBot\[local\] logged in' 4
    Send-Command 'fgaRejoinProbe disabled FgaRejoinBot' 'FGA_REJOIN_PROBE_PASS: disabled'
    Stop-IsolatedServer
    $passed = $true
} catch {
    $failure = $_.Exception.Message
} finally {
    try { Stop-IsolatedServer } catch { if (-not $failure) { $failure = $_.Exception.Message } }
    if ($null -ne $server -and -not $server.HasExited) {
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    $env:JAVA_HOME = $originalJavaHome
    Remove-Item Env:\FGA_REJOIN_SMOKE_RUN_DIR -ErrorAction SilentlyContinue
    @(
        'version=26.3',
        "status=$(if ($passed -and -not $failure) { 'passed' } else { 'failed' })",
        "reason=$failure",
        "runDir=$runDir",
        "serverLog=$serverLog",
        "gradleLog=$gradleLog"
    ) | Set-Content -LiteralPath $summary -Encoding utf8
}

Get-Content -LiteralPath $summary
if (-not $passed -or $failure) { exit 1 }
