param(
    [string] $OrgJar = 'D:\我的世界\.minecraft\versions\26.3-Fabric 0.19.5\mods\carpet-org-addition-mc26.3.x-v1.46.0-2609161136.jar',
    [string] $Java25Home = 'C:\Program Files\Java\jdk-25.0.3',
    [switch] $WithoutOrg
)

# Creates only a new test world under build; never opens a user's existing world.
# Run after :26.3:build. The test-only server entrypoint stops the server itself.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDir = Join-Path $root "build\flat-experience-smoke-26.3-$stamp"
$reportDir = Join-Path $root "scripts\logs\flat-experience-smoke-26.3-$stamp"
$stage = Join-Path $runDir 'probe-staging'
$package = Join-Path $stage 'carpet\fga\smoke'
$consoleLog = Join-Path $reportDir 'gradle-console.log'
$oldJava = $env:JAVA_HOME
$oldRun = $env:FGA_XP_SMOKE_RUN_DIR
$passed = $false
try {
    New-Item -ItemType Directory -Path $package, $reportDir, (Join-Path $runDir 'mods') | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke') `
        -Filter 'FlatExperienceProbe*.class' -File | Copy-Item -Destination $package
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\flat-experience-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $Java25Home 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-xp-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
    if (-not $WithoutOrg) { Copy-Item -LiteralPath $OrgJar -Destination (Join-Path $runDir 'mods') }
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=4', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful',
        'max-tick-time=15000'
    )
    $env:JAVA_HOME = $Java25Home
    $env:FGA_XP_SMOKE_RUN_DIR = $runDir
    & (Join-Path $root 'gradlew.bat') -I (Join-Path $root 'scripts\gradle\flat-experience-smoke-isolated.init.gradle') `
        ('-Dorg.gradle.java.installations.paths=' + (Join-Path $root 'build\toolchains\jdk-21.0.12.1+1')) `
        :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 '--args=--port 0 --nogui' *> $consoleLog
    $exitCode = $LASTEXITCODE
    $log = Get-Content -LiteralPath $consoleLog -Raw
    $passed = $exitCode -eq 0 -and $log -match 'FGA_XP_PASS:' -and $log -notmatch 'FGA_XP_FAIL:|MixinApplyError|InjectionError'
    if (-not $passed) { throw "Smoke failed; inspect $consoleLog" }
    Select-String -LiteralPath $consoleLog -Pattern 'FGA_XP_PASS:' | ForEach-Object Line
} finally {
    $env:JAVA_HOME = $oldJava
    $env:FGA_XP_SMOKE_RUN_DIR = $oldRun
    @("version=26.3", "org=$(-not $WithoutOrg)", "passed=$passed", "runDir=$runDir", "consoleLog=$consoleLog") |
        Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt') -Encoding utf8
    Write-Output "Report: $reportDir"
}
