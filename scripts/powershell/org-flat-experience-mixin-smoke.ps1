param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('1.21.1', '1.21.3', '1.21.4', '1.21.5', '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2', '26.3')]
    [string] $MinecraftVersion,
    [Parameter(Mandatory = $true)]
    [string] $OrgJar,
    [string] $Java25Home = 'C:\Program Files\Java\jdk-25.0.3'
)

# Runs a dedicated server in a fresh build/ directory and tests only the optional ORG Mixin.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$orgPath = (Resolve-Path -LiteralPath $OrgJar).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDir = Join-Path $root "build\org-flat-experience-smoke-$($MinecraftVersion.Replace('.', '_'))-$stamp"
$reportDir = Join-Path $root "scripts\logs\org-flat-experience-smoke-$($MinecraftVersion.Replace('.', '_'))-$stamp"
$stage = Join-Path $runDir 'probe-staging'
$package = Join-Path $stage 'carpet\fga\smoke'
$consoleLog = Join-Path $reportDir 'gradle-console.log'
$probeClasses = Join-Path $root "versions\$MinecraftVersion\build\classes\java\test\carpet\fga\smoke"
$oldJava = $env:JAVA_HOME
$oldRun = $env:FGA_XP_SMOKE_RUN_DIR
$oldProject = $env:FGA_XP_SMOKE_PROJECT
$passed = $false
try {
    New-Item -ItemType Directory -Path $package, $reportDir, (Join-Path $runDir 'mods') | Out-Null
    Get-ChildItem -LiteralPath $probeClasses -Filter 'OrgExperienceMixinProbe*.class' -File |
        Copy-Item -Destination $package
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\org-experience-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $Java25Home 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-org-experience-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
    Copy-Item -LiteralPath $orgPath -Destination (Join-Path $runDir 'mods')
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=2', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful',
        'max-tick-time=15000'
    )
    $env:JAVA_HOME = $Java25Home
    $env:FGA_XP_SMOKE_RUN_DIR = $runDir
    $env:FGA_XP_SMOKE_PROJECT = ":$MinecraftVersion"
    & (Join-Path $root 'gradlew.bat') -I (Join-Path $root 'scripts\gradle\flat-experience-smoke-isolated.init.gradle') `
        ('-Dorg.gradle.java.installations.paths=' + (Join-Path $root 'build\toolchains\jdk-21.0.12.1+1')) `
        ":${MinecraftVersion}:runServer" --no-daemon --configure-on-demand --max-workers=1 '--args=--port 0 --nogui' *> $consoleLog
    $exitCode = $LASTEXITCODE
    $log = Get-Content -LiteralPath $consoleLog -Raw
    $passed = $exitCode -eq 0 -and $log -match 'FGA_ORG_XP_PASS:' `
        -and $log -notmatch 'FGA_ORG_XP_FAIL:|MixinApplyError|InjectionError'
    if (-not $passed) { throw "ORG Mixin smoke failed; inspect $consoleLog" }
    Select-String -LiteralPath $consoleLog -Pattern 'FGA_ORG_XP_PASS:' | ForEach-Object Line
} finally {
    $env:JAVA_HOME = $oldJava
    $env:FGA_XP_SMOKE_RUN_DIR = $oldRun
    $env:FGA_XP_SMOKE_PROJECT = $oldProject
    @("version=$MinecraftVersion", "orgJar=$orgPath", "passed=$passed", "runDir=$runDir", "consoleLog=$consoleLog") |
        Set-Content -LiteralPath (Join-Path $reportDir 'summary.txt') -Encoding utf8
    Write-Output "Report: $reportDir"
}
