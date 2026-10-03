param(
    [Parameter(Mandatory = $true)][string] $Java25Home,
    [Parameter(Mandatory = $true)][string] $FgaJar
)

# Only the selected published JAR and test probe run in a newly created disposable world.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDir = Join-Path $root "build\dialog-warning-routing-smoke-26.3-$stamp"
$reportDir = Join-Path $root "scripts\logs\dialog-warning-routing-smoke-26.3-$stamp"
$stage = Join-Path $runDir 'probe-staging'
$package = Join-Path $stage 'carpet\fga\smoke'
$consoleLog = Join-Path $reportDir 'gradle-console.log'
$oldJava = $env:JAVA_HOME
$oldRun = $env:FGA_PUBLISHED_JAR_SMOKE_RUN_DIR
$passed = $false
try {
    $jdk = (Resolve-Path -LiteralPath $Java25Home).Path
    $artifact = (Resolve-Path -LiteralPath $FgaJar).Path
    New-Item -ItemType Directory -Path $package, $reportDir, (Join-Path $runDir 'mods') | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') `
        -Filter 'DialogWarningRoutingProbe*.class' -File | Copy-Item -Destination $package
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\dialog-warning-routing-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $jdk 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-dialog-warning-routing-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $runDir 'mods')
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=2', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful',
        'max-tick-time=15000'
    )
    $env:JAVA_HOME = $jdk
    $env:FGA_PUBLISHED_JAR_SMOKE_RUN_DIR = $runDir
    & (Join-Path $root 'gradlew.bat') -I (Join-Path $root 'scripts\gradle\published-fga-jar-smoke.init.gradle') `
        ('-Dorg.gradle.java.installations.paths=' + (Join-Path $root 'build\toolchains\jdk-21.0.12.1+1')) `
        :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 --offline '--args=--port 0 --nogui' *> $consoleLog
    $exitCode = $LASTEXITCODE
    $log = Get-Content -LiteralPath $consoleLog -Raw
    $passed = $exitCode -eq 0 -and $log -match 'FGA_DIALOG_ROUTING_PASS:' `
        -and $log -notmatch 'FGA_DIALOG_ROUTING_FAIL:|MixinApplyError|InjectionError'
    if (-not $passed) { throw "Routing smoke failed; inspect $consoleLog" }
    Select-String -LiteralPath $consoleLog -Pattern 'FGA_DIALOG_ROUTING_PASS:' | ForEach-Object Line
} finally {
    $env:JAVA_HOME = $oldJava
    $env:FGA_PUBLISHED_JAR_SMOKE_RUN_DIR = $oldRun
    @('version=26.3', "passed=$passed", "runDir=$runDir", "consoleLog=$consoleLog", "artifact=$artifact") |
        Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt') -Encoding utf8
    Write-Output "Report: $reportDir"
}
