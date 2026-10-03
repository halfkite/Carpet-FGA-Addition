param(
    [Parameter(Mandatory = $true)][string] $Java25Home,
    [string] $TisJar = ''
)

# Creates a new test world under build; never opens existing player/world data.
# Run after :26.3:build. Only the test probe JAR is staged in this disposable run.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDir = Join-Path $root "build\player-vehicle-world-smoke-26.3-$stamp"
$reportDir = Join-Path $root "scripts\logs\player-vehicle-world-smoke-26.3-$stamp"
$stage = Join-Path $runDir 'probe-staging'
$package = Join-Path $stage 'carpet\fga\smoke'
$consoleLog = Join-Path $reportDir 'gradle-console.log'
$oldJava = $env:JAVA_HOME
$oldRun = $env:FGA_PLAYER_WORLD_SMOKE_RUN_DIR
$passed = $false
try {
    $jdk = (Resolve-Path -LiteralPath $Java25Home).Path
    New-Item -ItemType Directory -Path $package, $reportDir, (Join-Path $runDir 'mods') | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') `
        -Filter 'PlayerVehicleWorldProbe*.class' -File | Copy-Item -Destination $package
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\player-vehicle-world-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $jdk 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-player-world-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
    if ($TisJar) { Copy-Item -LiteralPath $TisJar -Destination (Join-Path $runDir 'mods') }
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=4', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=normal',
        'max-tick-time=15000'
    )
    $env:JAVA_HOME = $jdk
    $env:FGA_PLAYER_WORLD_SMOKE_RUN_DIR = $runDir
    & (Join-Path $root 'gradlew.bat') -I (Join-Path $root 'scripts\gradle\player-vehicle-world-smoke-isolated.init.gradle') `
        ('-Dorg.gradle.java.installations.paths=' + (Join-Path $root 'build\toolchains\jdk-21.0.12.1+1')) `
        :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 --offline '--args=--port 0 --nogui' *> $consoleLog
    $exitCode = $LASTEXITCODE
    $log = Get-Content -LiteralPath $consoleLog -Raw
    $passed = $exitCode -eq 0 -and $log -match 'FGA_NEW_FEATURES_PASS:' -and $log -notmatch 'FGA_NEW_FEATURES_FAIL:|MixinApplyError|InjectionError'
    if (-not $passed) { throw "Smoke failed; inspect $consoleLog" }
    Select-String -LiteralPath $consoleLog -Pattern 'FGA_NEW_FEATURES_PASS:' | ForEach-Object Line
} finally {
    $env:JAVA_HOME = $oldJava
    $env:FGA_PLAYER_WORLD_SMOKE_RUN_DIR = $oldRun
    @('version=26.3', "tis=$([bool]$TisJar)", "passed=$passed", "runDir=$runDir", "consoleLog=$consoleLog") |
        Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt') -Encoding utf8
    Write-Output "Report: $reportDir"
}
