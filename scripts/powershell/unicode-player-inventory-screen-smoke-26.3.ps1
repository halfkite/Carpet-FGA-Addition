param(
    [string] $Java25Home,
    [string] $Java21Home
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\unicode-player-inventory-screen-smoke-26.3-$stamp"
$runDir = Join-Path $root "build\unicode-player-inventory-screen-smoke-26.3-$stamp"
$gradleLog = Join-Path $reportDir 'gradle-console.log'
$serverLog = Join-Path $runDir 'logs\latest.log'
$initScript = Join-Path $root 'scripts\gradle\unicode-open-screen-smoke-isolated.init.gradle'
$classSource = Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke'
$probePassed = $false
$cleanStop = $false
$failure = ''
$server = $null
$originalJavaHome = $env:JAVA_HOME

New-Item -ItemType Directory -Force -Path $reportDir, (Join-Path $runDir 'mods') | Out-Null
$jdkCandidates = [System.Collections.Generic.List[string]]::new()
if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) { $jdkCandidates.Add($env:JAVA_HOME) }
if (-not [string]::IsNullOrWhiteSpace($Java25Home)) { $jdkCandidates.Add($Java25Home) }
if (-not [string]::IsNullOrWhiteSpace($Java21Home)) { $jdkCandidates.Add($Java21Home) }
foreach ($installationRoot in @(
    (Join-Path $env:ProgramFiles 'Java'),
    (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
    (Join-Path $env:ProgramFiles 'Microsoft'),
    (Join-Path $env:ProgramFiles 'Amazon Corretto'),
    (Join-Path $env:LOCALAPPDATA 'Programs\Eclipse Adoptium'),
    (Join-Path $env:USERPROFILE '.jdks'),
    (Join-Path $env:USERPROFILE '.gradle\jdks'),
    (Join-Path 'C:\' 'Java'),
    (Join-Path 'D:\' 'Java')
)) {
    if (Test-Path -LiteralPath $installationRoot) {
        $firstLevel = @(Get-ChildItem -LiteralPath $installationRoot -Directory -ErrorAction SilentlyContinue)
        foreach ($directory in $firstLevel) {
            $jdkCandidates.Add($directory.FullName)
            Get-ChildItem -LiteralPath $directory.FullName -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { $jdkCandidates.Add($_.FullName) }
        }
    }
}
$jdk = $null
$jdk21 = $null
foreach ($candidate in ($jdkCandidates | Select-Object -Unique)) {
    $javaExecutable = Join-Path $candidate 'bin\java.exe'
    $javacExecutable = Join-Path $candidate 'bin\javac.exe'
    if ((Test-Path -LiteralPath $javaExecutable) -and (Test-Path -LiteralPath $javacExecutable)) {
        $versionOutput = (& $javaExecutable -version 2>&1 | Out-String)
        if ($null -eq $jdk -and $versionOutput -match 'version "25(?:\.|"|\+)') { $jdk = $candidate }
        if ($null -eq $jdk21 -and $versionOutput -match 'version "21(?:\.|"|\+)') { $jdk21 = $candidate }
        if ($null -ne $jdk -and $null -ne $jdk21) { break }
    }
}
if ($null -eq $jdk) { throw 'A Java 25 JDK is required for the isolated 26.3 smoke test' }
if ($null -eq $jdk21) { throw 'A Java 21 JDK is required by the 26.3 preprocessing chain' }
$env:JAVA_HOME = $jdk
$gradleToolchainArguments = @('-Dorg.gradle.java.installations.paths=' + $jdk21)

function Wait-SmokePattern([string] $Pattern, [int] $TimeoutSeconds = 45) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $output = if (Test-Path -LiteralPath $serverLog) { Get-Content -LiteralPath $serverLog -Raw } else { '' }
        if ($output -match 'FGA_UNICODE_OPEN_SCREEN_PROBE_FAIL[^\r\n]*') { throw $Matches[0] }
        if ($output -match $Pattern) { return }
        if ($null -ne $server -and $server.HasExited) { throw "Server exited early ($($server.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for '$Pattern'; see $serverLog"
}

try {
    $env:FGA_UNICODE_OPEN_SCREEN_SMOKE_RUN_DIR = $runDir
    & (Join-Path $root 'gradlew.bat') :26.3:testClasses @gradleToolchainArguments --no-daemon --configure-on-demand --max-workers=1 *> $gradleLog
    if ($LASTEXITCODE -ne 0) { throw "26.3 testClasses failed; see $gradleLog" }

    $probeClasses = @(Get-ChildItem -LiteralPath $classSource -File -Filter 'UnicodePlayerInventoryScreenProbe*.class')
    if ($probeClasses.Count -eq 0) { throw "Smoke probe classes were not compiled: $classSource" }
    $stage = Join-Path $runDir 'probe-staging'
    $probePackage = Join-Path $stage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $probePackage | Out-Null
    $probeClasses | Copy-Item -Destination $probePackage
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\unicode-player-inventory-screen-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    $probeJar = Join-Path $runDir 'mods\fga-unicode-open-screen-probe.jar'
    & (Join-Path $jdk 'bin\jar.exe') cf $probeJar -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package the test-only Unicode screen probe' }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false'
        'server-port=0'
        'max-players=4'
        'spawn-protection=0'
        'view-distance=2'
        'simulation-distance=2'
        'level-name=world'
        'gamemode=survival'
        'difficulty=peaceful'
    )

    $serverInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $serverInfo.FileName = 'cmd.exe'
    $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $initScript + '"' +
        ' "' + $gradleToolchainArguments[0] + '"' +
        ' :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0" > "' +
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
            ((Get-Content -LiteralPath $serverLog -Raw) -match 'Done \([0-9.]+s\)!')) { break }
        Start-Sleep -Seconds 2
    }
    if (-not (Test-Path -LiteralPath $serverLog) -or
        (Get-Content -LiteralPath $serverLog -Raw) -notmatch 'Done \([0-9.]+s\)!') {
        throw "Isolated 26.3 server did not reach Done; see $gradleLog and $serverLog"
    }

    $server.StandardInput.WriteLine('carpet fgaUnicodeArgumentsSupport true')
    $server.StandardInput.Flush()
    Wait-SmokePattern 'fgaUnicodeArgumentsSupport: true'
    $server.StandardInput.WriteLine('fgaUnicodeScreenProbe')
    $server.StandardInput.Flush()
    Wait-SmokePattern 'FGA_UNICODE_OPEN_SCREEN_PROBE_PASS: Chinese title preserved and packet round-tripped'
    $probePassed = $true
    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    $cleanStop = $server.WaitForExit(90000)
    if (-not $cleanStop) { throw 'Isolated Unicode screen smoke server did not stop cleanly' }
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        try {
            $server.StandardInput.WriteLine('stop')
            $server.StandardInput.Flush()
            $cleanStop = $server.WaitForExit(30000)
        } catch { }
    }
    if ($null -ne $server -and -not $server.HasExited) {
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    @(
        'version=26.3'
        "probePassed=$probePassed"
        "cleanStop=$cleanStop"
        "status=$(if ($probePassed -and $cleanStop -and -not $failure) { 'passed' } else { 'failed' })"
        "reason=$failure"
        "runDirectory=$runDir"
        "serverLog=$serverLog"
        "gradleLog=$gradleLog"
    ) | Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt')
    Remove-Item Env:\FGA_UNICODE_OPEN_SCREEN_SMOKE_RUN_DIR -ErrorAction SilentlyContinue
    $env:JAVA_HOME = $originalJavaHome
}

Get-Content -LiteralPath (Join-Path $reportDir 'summary.txt')
if (-not ($probePassed -and $cleanStop -and -not $failure)) { exit 1 }
