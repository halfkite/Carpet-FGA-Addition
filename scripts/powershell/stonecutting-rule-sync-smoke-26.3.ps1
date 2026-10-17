param(
    [Parameter(Mandatory = $true)][string] $Artifact,
    [Parameter(Mandatory = $true)][string] $Java21Home,
    [string] $Java25Home = $env:JAVA_HOME,
    [switch] $Client
)

# Uses only a newly created test world and the explicitly selected installable JAR.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$artifactPath = (Resolve-Path -LiteralPath $Artifact).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$runDir = Join-Path $root "build\issue26-smoke-$stamp"
$stage = Join-Path $runDir 'probe-staging'
$consoleLog = Join-Path $runDir 'gradle-console.log'
$probeClasses = Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke'
$oldJava = $env:JAVA_HOME
$oldRun = $env:FGA_ISSUE26_RUN_DIR
$passed = $false

foreach ($javaHome in @($Java21Home, $Java25Home)) {
    if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\java.exe'))) {
        throw "Missing Java runtime: $javaHome"
    }
}
try {
    $package = Join-Path $stage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Path $package, (Join-Path $runDir 'mods') | Out-Null
    Get-ChildItem -LiteralPath $probeClasses -Filter 'StonecuttingRule*Probe*.class' -File |
        Copy-Item -Destination $package
    @{
        schemaVersion = 1; id = 'fga-issue26-probe'; version = '1.0.0'; environment = '*'
        entrypoints = @{ server = @('carpet.fga.smoke.StonecuttingRuleProbe'); client = @('carpet.fga.smoke.StonecuttingRuleClientProbe') }
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $stage 'fabric.mod.json') -Encoding utf8
    & (Join-Path $Java25Home 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-issue26-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Probe packaging failed' }
    Copy-Item -LiteralPath $artifactPath -Destination (Join-Path $runDir 'mods')
    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false', 'server-port=0', 'max-players=2', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful',
        'max-tick-time=30000'
    )
    $initPath = Join-Path $runDir 'isolated.init.gradle'
    @'
String runDirectory = System.getenv('FGA_ISSUE26_RUN_DIR')
gradle.afterProject { project ->
    if (project.path == ':26.3') {
        project.loom.runs.named('server') { runDir runDirectory }
        project.loom.runs.named('client') { runDir runDirectory }
    }
}
gradle.projectsEvaluated {
    def project = gradle.rootProject.project(':26.3')
    ['runServer', 'runClient'].each { taskName -> project.tasks.named(taskName).configure {
        executable = new File(System.getenv('JAVA_HOME'), 'bin/java.exe').absolutePath
        def mainOutput = project.sourceSets.main.output.files
        classpath = classpath.filter { !mainOutput.contains(it) }
        if (taskName == 'runClient') jvmArgs "-XX:ErrorFile=${runDirectory}/hs_err_pid%p.log"
        doFirst {
            systemProperties.remove('fabric.classPathGroups')
            jvmArgs = jvmArgs.findAll { !it.startsWith('-Dfabric.classPathGroups=') }
        }
    } }
}
'@ | Set-Content -LiteralPath $initPath -Encoding utf8
    $env:JAVA_HOME = $Java25Home
    $env:FGA_ISSUE26_RUN_DIR = $runDir
    & (Join-Path $root 'gradlew.bat') -I $initPath :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 `
        "-Dorg.gradle.java.installations.paths=$Java21Home" --offline '--args=--port 0 --nogui' *> $consoleLog
    $exitCode = $LASTEXITCODE
    $log = Get-Content -LiteralPath $consoleLog -Raw
    $passed = $exitCode -eq 0 -and $log -match 'FGA_ISSUE26_PASS:' -and $log -notmatch 'FGA_ISSUE26_FAIL:|MixinApplyError|InjectionError'
    Select-String -LiteralPath $consoleLog -Pattern 'FGA_ISSUE26_(CASE|PASS|FAIL):' | ForEach-Object Line
    if (-not $passed) { throw "Smoke failed; inspect $consoleLog" }
    if ($Client) {
        $passed = $false
        $saveRoot = Join-Path $runDir 'saves'
        New-Item -ItemType Directory -Path $saveRoot | Out-Null
        Copy-Item -LiteralPath (Join-Path $runDir 'world') -Destination (Join-Path $saveRoot 'issue26-test') -Recurse
        Set-Content -LiteralPath (Join-Path $runDir 'options.txt') -Encoding ascii -Value @(
            'pauseOnLostFocus:false', 'renderDistance:2', 'simulationDistance:5',
            'soundCategory_master:0.0', 'maxFps:30', 'onboardAccessibility:false'
        )
        $clientLog = Join-Path $runDir 'client-console.log'
        & (Join-Path $root 'gradlew.bat') -I $initPath :26.3:runClient --no-daemon --configure-on-demand --max-workers=1 `
            "-Dorg.gradle.java.installations.paths=$Java21Home" --offline '--args=--username FgaIssue26 --quickPlaySingleplayer issue26-test' *> $clientLog
        $clientExit = $LASTEXITCODE
        $clientText = Get-Content -LiteralPath $clientLog -Raw
        $passed = $clientExit -eq 0 -and $clientText -match 'FGA_ISSUE26_CLIENT_PASS:' -and $clientText -notmatch 'FGA_ISSUE26_CLIENT_FAIL:'
        Select-String -LiteralPath $clientLog -Pattern 'FGA_ISSUE26_CLIENT_(CASE|PASS|FAIL):' | ForEach-Object Line
        if (-not $passed) { throw "Client smoke failed; inspect $clientLog" }
    }
} finally {
    $env:JAVA_HOME = $oldJava
    $env:FGA_ISSUE26_RUN_DIR = $oldRun
    @{ version = '26.3'; artifact = $artifactPath; passed = $passed; clientRequested = [bool]$Client; consoleLog = $consoleLog } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDir 'summary.json') -Encoding utf8
    Write-Output "Report: $runDir"
}
