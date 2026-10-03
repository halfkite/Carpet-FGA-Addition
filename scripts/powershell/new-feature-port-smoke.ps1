param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('1.21.1', '1.21.3', '1.21.4', '1.21.5', '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2', '26.3')]
    [string] $MinecraftVersion,
    [string] $Java21Home = 'D:\ai\carpet-fga\build\toolchains\jdk-21.0.12.1+1',
    [string] $Java25Home = 'C:\Program Files\Java\jdk-25.0.3',
    [switch] $Offline
)

# Starts a development server in a new build/ directory with no user saves or run-directory mods.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDir = Join-Path $root "build\new-feature-port-smoke-$($MinecraftVersion.Replace('.', '_'))-$stamp"
$reportDir = Join-Path $root "scripts\logs\new-feature-port-smoke-$($MinecraftVersion.Replace('.', '_'))-$stamp"
$consoleLog = Join-Path $reportDir 'server.log'
$oldJava = $env:JAVA_HOME
$oldRun = $env:FGA_NEW_FEATURE_PORT_SMOKE_RUN_DIR
$oldProject = $env:FGA_NEW_FEATURE_PORT_SMOKE_PROJECT
$process = $null
$ready = $false
$stopped = $false

try {
    $jdk = if ($MinecraftVersion.StartsWith('26.')) {
        (Resolve-Path -LiteralPath $Java25Home).Path
    } else {
        (Resolve-Path -LiteralPath $Java21Home).Path
    }
    New-Item -ItemType Directory -Path $runDir, $reportDir | Out-Null
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=2', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful',
        'max-tick-time=15000'
    )
    $env:JAVA_HOME = $jdk
    $env:FGA_NEW_FEATURE_PORT_SMOKE_RUN_DIR = $runDir
    $env:FGA_NEW_FEATURE_PORT_SMOKE_PROJECT = ":$MinecraftVersion"
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'cmd.exe'
    $quotedArguments = @(
        '"' + (Join-Path $root 'gradlew.bat') + '"',
        '-I', '"' + (Join-Path $root 'scripts\gradle\new-feature-port-smoke.init.gradle') + '"',
        '"-Dorg.gradle.java.installations.paths=' + (Join-Path $root 'build\toolchains\jdk-21.0.12.1+1') + '"',
        ":$MinecraftVersion`:runServer", '--no-daemon', '--configure-on-demand', '--max-workers=1'
    )
    if ($Offline) { $quotedArguments += '--offline' }
    $start.Arguments = '/d /s /c "' + ($quotedArguments -join ' ') + ' > "' + $consoleLog + '" 2>&1"'
    $start.WorkingDirectory = $root
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true
    $start.RedirectStandardOutput = $false
    $start.RedirectStandardError = $false
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    [void] $process.Start()

    $deadline = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        if (Test-Path -LiteralPath (Join-Path $runDir 'logs\latest.log')) {
            $log = Get-Content -LiteralPath (Join-Path $runDir 'logs\latest.log') -Raw -ErrorAction SilentlyContinue
            if ($log -match 'Done \([0-9.]+s\)!') { $ready = $true; break }
            if ($log -match 'MixinApplyError|InjectionError|InvalidInjectionException|MixinApplicationError|FAILED to load|Failed to load mod') {
                throw 'Isolated server reported a startup or Mixin failure'
            }
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Isolated development server did not become ready' }
    $commands = @(
        'carpet abnormalDisconnectNotice console', 'carpet vehicleJump true',
        'carpet doubleBarrelCapacity true', 'carpet playerVehicleCapacity 4',
        'carpet vehicleNoCramming true', 'carpet iceFormationChances 30,10',
        'carpet fastEating true', 'carpet noSnowAccumulation true', 'carpet flatBedrock true'
    )
    foreach ($command in $commands) {
        $process.StandardInput.WriteLine($command)
        $process.StandardInput.Flush()
        Start-Sleep -Milliseconds 500
    }
    Start-Sleep -Seconds 2
    $process.StandardInput.WriteLine('stop')
    $process.StandardInput.Flush()
    $stopped = $process.WaitForExit(90000)
    if (-not $stopped) { throw 'Isolated development server did not stop cleanly' }
    $log = Get-Content -LiteralPath (Join-Path $runDir 'logs\latest.log') -Raw -ErrorAction SilentlyContinue
    if ($log -match 'MixinApplyError|InjectionError|InvalidInjectionException|MixinApplicationError|Unknown rule|Unknown or incomplete command') {
        throw 'Isolated server rejected a required Mixin or rule command'
    }
    if ($process.ExitCode -ne 0) { throw "Gradle runServer exited with $($process.ExitCode)" }
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        try { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() } catch { }
        if (-not $process.WaitForExit(5000)) { & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null }
    }
    $env:JAVA_HOME = $oldJava
    $env:FGA_NEW_FEATURE_PORT_SMOKE_RUN_DIR = $oldRun
    $env:FGA_NEW_FEATURE_PORT_SMOKE_PROJECT = $oldProject
    @("version=$MinecraftVersion", "ready=$ready", "stoppedCleanly=$stopped", "runDir=$runDir", "consoleLog=$consoleLog") |
        Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt') -Encoding utf8
    if (Test-Path -LiteralPath (Join-Path $runDir 'logs\latest.log')) {
        Copy-Item -LiteralPath (Join-Path $runDir 'logs\latest.log') -Destination $consoleLog -Force
    }
    Write-Output "Run directory: $runDir"
    Write-Output "Report: $reportDir"
}
