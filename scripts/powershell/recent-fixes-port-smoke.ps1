param(
    [Parameter(Mandatory=$true)]
    [ValidateSet('1.21.1','1.21.3','1.21.4','1.21.5','1.21.8','1.21.10','1.21.11','26.1.2','26.2','26.3')]
    [string]$MinecraftVersion,
    [Parameter(Mandatory=$true)][ValidateSet('server','client')][string]$Mode,
    [string]$ServerRunDirectory,
    [string]$VmpJar,
    [string]$LoaderVersion,
    [string]$Java21Home=$env:JAVA21_HOME,
    [string]$Java25Home=$env:JAVA25_HOME
)

$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $Java21Home) {$Java21Home=Join-Path $root 'build\toolchains\jdk-21.0.12.1+1'}
if (-not $Java25Home) {$Java25Home=$env:JAVA_HOME}
$run=Join-Path $root ('build\recent-fixes-' + $MinecraftVersion + '-' + $Mode + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$jdk=if ($MinecraftVersion.StartsWith('26.')) {$Java25Home} else {$Java21Home}
$jdk=(Resolve-Path -LiteralPath $jdk).Path
$oldJava=$env:JAVA_HOME
$oldVersion=$env:FGA_RECENT_PORT_VERSION
$oldRun=$env:FGA_RECENT_PORT_RUN_DIR
$oldMode=$env:FGA_RECENT_PORT_MODE
$oldVmp=$env:FGA_RECENT_PORT_VMP_JAR
$oldVmpLibs=$env:FGA_RECENT_PORT_VMP_LIBS
$process=$null
$passed=$false
$savedStats=$false
New-Item -ItemType Directory -Path $run,"$run\mods","$run\probe-staging" | Out-Null
$env:JAVA_HOME=$jdk
$env:FGA_RECENT_PORT_VERSION=$MinecraftVersion
$env:FGA_RECENT_PORT_RUN_DIR=$run
$env:FGA_RECENT_PORT_MODE=$Mode
$env:FGA_RECENT_PORT_VMP_JAR=if ($VmpJar) {(Resolve-Path -LiteralPath $VmpJar).Path} else {$null}
$env:FGA_RECENT_PORT_VMP_LIBS=$null
try {
    if ($VmpJar) {
        New-Item -ItemType Directory -Path "$run\vmp-libs" | Out-Null
        $archive=[IO.Compression.ZipFile]::OpenRead($env:FGA_RECENT_PORT_VMP_JAR)
        try {
            foreach ($entry in $archive.Entries) {
                if ($entry.FullName -like 'META-INF/jars/*.jar') {
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path "$run\vmp-libs" $entry.Name))
                }
            }
        } finally {$archive.Dispose()}
        $env:FGA_RECENT_PORT_VMP_LIBS="$run\vmp-libs"
    }
    $classes=Join-Path $root "versions\$MinecraftVersion\build\classes\java\test\carpet\fga\smoke"
    $dest=Join-Path $run 'probe-staging\carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    $probeClasses=@(Get-ChildItem -LiteralPath $classes -Filter 'Recent*.class' -File)
    if ($probeClasses.Count -lt 2) {throw 'Build this version and its test classes before smoke testing'}
    $probeClasses | Copy-Item -Destination $dest
    [ordered]@{schemaVersion=1;id='fga_recent_fixes_probe';version='1';environment='*';
      entrypoints=@{main=@('carpet.fga.smoke.RecentFixesProbe');client=@('carpet.fga.smoke.RecentPossessionClientProbe')};
      depends=@{minecraft=$MinecraftVersion;'carpet-fga-addition'='*'}
    } | ConvertTo-Json -Depth 5 | Set-Content "$run\probe-staging\fabric.mod.json" -Encoding utf8
    & "$jdk\bin\jar.exe" --create --file "$run\mods\recent-fixes-probe.jar" -C "$run\probe-staging" .
    if ($LASTEXITCODE -ne 0) {throw 'Probe packaging failed'}
    if ($Mode -eq 'server') {
        Set-Content "$run\eula.txt" 'eula=true' -Encoding ascii
        Set-Content "$run\server.properties" -Encoding ascii -Value @('online-mode=false','server-ip=127.0.0.1','server-port=0','white-list=false','view-distance=2','simulation-distance=2','level-name=world','level-type=minecraft:flat','generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1}]}','max-tick-time=120000','pause-when-empty-seconds=0','spawn-protection=0')
        $gameArguments='nogui --port 0'
    } else {
        $serverRun=(Resolve-Path -LiteralPath $ServerRunDirectory).Path
        $buildRoot=[IO.Path]::GetFullPath((Join-Path $root 'build'))+[IO.Path]::DirectorySeparatorChar
        if (-not $serverRun.StartsWith($buildRoot,[StringComparison]::OrdinalIgnoreCase)) {throw 'Client tests require a disposable server world under build/'}
        if (-not (Test-Path -LiteralPath "$serverRun\summary.json")) {throw 'Server fixture has no completed test summary'}
        $serverSummary=Get-Content "$serverRun\summary.json" -Raw | ConvertFrom-Json
        if (-not $serverSummary.passed -or $serverSummary.version -ne $MinecraftVersion) {throw 'Client requires a passed server fixture for the same version'}
        New-Item -ItemType Directory -Path "$run\saves" | Out-Null
        Copy-Item -LiteralPath "$serverRun\world" -Destination "$run\saves\world" -Recurse
        Set-Content "$run\options.txt" -Encoding ascii -Value @('lang:en_us','pauseOnLostFocus:false','renderDistance:2','simulationDistance:5','soundCategory_master:0.0','maxFps:30','onboardAccessibility:false','guiScale:2','fullscreen:false')
        $gameArguments='--quickPlaySingleplayer world'
    }
    $console=Join-Path $run 'console.log'
    $start=[Diagnostics.ProcessStartInfo]::new()
    $start.FileName='cmd.exe'
    $runTask=if ($Mode -eq 'client') {'runClient'} else {'runServer'}
    if ($LoaderVersion -and $LoaderVersion -notmatch '^\d+\.\d+\.\d+$') {throw 'LoaderVersion must be an explicit numeric Fabric Loader version'}
    $loaderArgument=if ($LoaderVersion) {' -Ploader_version='+$LoaderVersion} else {''}
    $start.Arguments='/d /s /c ""'+(Join-Path $root 'gradlew.bat')+'" -I "'+(Join-Path $root 'scripts\gradle\recent-fixes-port-smoke.init.gradle')+'" :'+$MinecraftVersion+':'+$runTask+' --offline --no-daemon --configure-on-demand --max-workers=1 "-Dorg.gradle.java.installations.paths='+$Java21Home+'"'+$loaderArgument+' --args="'+$gameArguments+'" > "'+$console+'" 2>&1"'
    $start.WorkingDirectory=$root
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardInput=$true
    $process=[Diagnostics.Process]::new()
    $process.StartInfo=$start
    [void]$process.Start()
    Write-Output "RECENT_RUN=$run PID=$($process.Id) VERSION=$MinecraftVersion MODE=$Mode"
    $deadline=(Get-Date).AddMinutes(12)
    while (-not $process.HasExited -and (Get-Date) -lt $deadline) {Start-Sleep -Seconds 2}
    if (-not $process.HasExited) {throw 'Isolated recent-fixes smoke timed out'}
    $log=Get-Content $console -Raw
    if ($Mode -eq 'server') {
        $passed=$process.ExitCode -eq 0 -and $log.Contains('RECENT_FIXES_PASS') -and -not $log.Contains('RECENT_FIXES_FAIL')
        if ($VmpJar -and $log -notmatch '(?m)^\s*-\s+vmp\s+') {
            $passed=$false
            throw 'Requested VMP was not present in the loaded-mod list'
        }
        if ($passed) {
            foreach ($expected in @(@{name='StatsActor';jump=1000;fish=50;deaths=0},@{name='StatsTarget';jump=13;fish=0;deaths=7})) {
                $md5=[Security.Cryptography.MD5]::Create()
                try {$bytes=$md5.ComputeHash([Text.Encoding]::UTF8.GetBytes('OfflinePlayer:'+$expected.name))} finally {$md5.Dispose()}
                $bytes[6]=($bytes[6] -band 15) -bor 48
                $bytes[8]=($bytes[8] -band 63) -bor 128
                $hex=([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant()
                $id=$hex.Substring(0,8)+'-'+$hex.Substring(8,4)+'-'+$hex.Substring(12,4)+'-'+$hex.Substring(16,4)+'-'+$hex.Substring(20)
                $statsFolder=if ($MinecraftVersion.StartsWith('26.')) {'world\players\stats'} else {'world\stats'}
                $saved=Get-Content (Join-Path $run "$statsFolder\$id.json") -Raw | ConvertFrom-Json
                foreach ($pair in @(@('jump','jump'),@('fish','fish_caught'),@('deaths','deaths'))) {
                    $actual=$saved.stats.'minecraft:custom'.('minecraft:'+$pair[1])
                    if ($null -eq $actual) {$actual=0}
                    if ($actual -ne $expected[$pair[0]]) {throw ('Saved statistic mismatch: '+$expected.name+' '+$pair[1]+'='+$actual)}
                }
            }
            $savedStats=$true
        }
    } else {$passed=$process.ExitCode -eq 0 -and $log -match 'SOLO_PERMISSION_RESULT checks=\d+ failures=0'}
    Select-String $console -Pattern 'RECENT_FIXES_(PASS|FAIL|STATS_PASS|SPAWN_PASS)|SOLO_PERMISSION_(SCENARIO|RESULT)|SOLO_PERMISSION_CHECK FAIL' | ForEach-Object Line
    if (-not $passed) {throw "Recent-fixes smoke failed; see $console"}
} finally {
    if ($null -ne $process -and -not $process.HasExited) {
        try {$process.StandardInput.WriteLine('stop');$process.StandardInput.Flush()} catch {}
        if (-not $process.WaitForExit(5000)) {& taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null}
    }
    @{version=$MinecraftVersion;mode=$Mode;passed=($passed -and ($Mode -eq 'client' -or $savedStats));savedStats=$savedStats;vmpJar=$VmpJar;run=$run;log="$run\console.log"} | ConvertTo-Json | Set-Content "$run\summary.json" -Encoding utf8
    $env:JAVA_HOME=$oldJava
    $env:FGA_RECENT_PORT_VERSION=$oldVersion
    $env:FGA_RECENT_PORT_RUN_DIR=$oldRun
    $env:FGA_RECENT_PORT_MODE=$oldMode
    $env:FGA_RECENT_PORT_VMP_JAR=$oldVmp
    $env:FGA_RECENT_PORT_VMP_LIBS=$oldVmpLibs
}
