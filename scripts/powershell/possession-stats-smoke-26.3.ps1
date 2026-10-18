param(
    [Parameter(Mandatory = $true)][string] $ServerFixture,
    [Parameter(Mandatory = $true)][string] $FgaJar,
    [Parameter(Mandatory = $true)][string] $CarpetJar,
    [Parameter(Mandatory = $true)][string] $RankBoardJar,
    [Parameter(Mandatory = $true)][string] $JavaHome,
    [switch] $VerifyOfflineRanking
)

# Reuses only server binaries and libraries; creates a fresh disposable world for every run.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixture = (Resolve-Path -LiteralPath $ServerFixture).Path
$jdk = Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin'
$run = Join-Path $root ('build\possession-stats-26.3-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
$fga = (Resolve-Path -LiteralPath $FgaJar).Path
$carpet = (Resolve-Path -LiteralPath $CarpetJar).Path
$rankboard = (Resolve-Path -LiteralPath $RankBoardJar).Path
New-Item -ItemType Directory -Path $run, "$run\mods", "$run\deps", "$run\classes", "$run\config\rankboard" | Out-Null

$api = [IO.Compression.ZipFile]::OpenRead("$fixture\mods\fabric-api.jar")
try {
    foreach ($entry in $api.Entries) {
        if ($entry.FullName -like 'META-INF/jars/*.jar') {
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path "$run\deps" $entry.Name))
        }
    }
} finally { $api.Dispose() }
$jars = @(Get-ChildItem "$fixture\libraries" -Recurse -Filter '*.jar' | ForEach-Object FullName)
$jars += @(Get-ChildItem "$run\deps" -Filter '*.jar' | ForEach-Object FullName)
$jars += @("$fixture\versions\26.3\server-26.3.jar", $fga, $carpet, $rankboard)
& "$jdk\javac.exe" -encoding UTF-8 -cp ($jars -join ';') -d "$run\classes" `
    "$root\scripts\tests\possession-stats\java\cn\bamgdam\rankboard\PossessionStatsTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Test addon compilation failed' }
@{schemaVersion = 1; id = 'fga_possession_stats_test'; version = '1'; environment = 'server';
    entrypoints = @{main = @('cn.bamgdam.rankboard.PossessionStatsTest')};
    depends = @{fabricloader = '>=0.19.5'; minecraft = '26.3'; rankboard = '*'; 'carpet-fga-addition' = '*'}
} | ConvertTo-Json -Depth 5 | Set-Content "$run\classes\fabric.mod.json" -Encoding utf8
& "$jdk\jar.exe" --create --file "$run\mods\possession-stats-test.jar" -C "$run\classes" .
if ($LASTEXITCODE -ne 0) { throw 'Test addon packaging failed' }
foreach ($file in @('server.jar', 'fabric-server-launch.jar')) {
    Copy-Item "$fixture\$file" "$run\$file"
}
foreach ($directory in @('libraries', 'versions')) {
    New-Item -ItemType Junction -Path "$run\$directory" -Target "$fixture\$directory" | Out-Null
}
Copy-Item "$fixture\mods\fabric-api.jar" "$run\mods\fabric-api.jar"
Copy-Item $fga "$run\mods\fga.jar"
Copy-Item $carpet "$run\mods\carpet.jar"
Copy-Item $rankboard "$run\mods\rankboard.jar"
Set-Content "$run\eula.txt" 'eula=true' -Encoding ascii
Set-Content "$run\server.properties" -Encoding ascii -Value @(
    'online-mode=false', 'server-ip=127.0.0.1', 'server-port=0', 'view-distance=2',
    'simulation-distance=2', 'level-name=world', 'level-type=minecraft:flat',
    'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1}]}',
    'max-tick-time=30000', 'spawn-protection=0'
)
Set-Content "$run\config\rankboard\rankboard-web.properties" -Value @('host=127.0.0.1', 'port=0') -Encoding ascii
Write-Host "Test directory: $run"
$serverArguments = @('-Xms512M', '-Xmx1G')
if ($VerifyOfflineRanking) { $serverArguments += '-Dfga.test.verifyOfflineRanking=true' }
$serverArguments += @('-jar', 'fabric-server-launch.jar', 'nogui')
$process = Start-Process -FilePath "$jdk\java.exe" -ArgumentList $serverArguments `
    -WorkingDirectory $run -WindowStyle Hidden -PassThru -RedirectStandardOutput "$run\stdout.log" -RedirectStandardError "$run\stderr.log"
try {
    $deadline = (Get-Date).AddSeconds(180)
    while (-not $process.HasExited -and (Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
    if (-not $process.HasExited) { throw 'Test server timeout' }
    Get-Content "$run\stdout.log" | Select-String 'POSSESSION_STATS_|Loading Minecraft|carpet-fga-addition|rankboard 1'
    if ($process.ExitCode -ne 0 -or (Select-String "$run\stdout.log" -Pattern 'POSSESSION_STATS_FAIL') `
            -or -not (Select-String "$run\stdout.log" -Pattern 'POSSESSION_STATS_PASS checks=')) {
        throw "Possession stats regression failed; see $run\stdout.log"
    }
    $expected = @{
        '52706e57-fa04-395e-bf81-9de1b367f013' = @{jump = 1005; fish_caught = 50; deaths = 4}
        '84340ebf-f876-3ebf-90ac-40f66156bf6f' = @{jump = 13; fish_caught = 2; deaths = 7}
    }
    foreach ($id in $expected.Keys) {
        $saved = Get-Content "$run\world\players\stats\$id.json" -Raw | ConvertFrom-Json
        foreach ($stat in $expected[$id].Keys) {
            $actual = $saved.stats.'minecraft:custom'."minecraft:$stat"
            if ($actual -ne $expected[$id][$stat]) { throw "Saved stats mismatch: $id $stat actual=$actual" }
        }
    }
    @{minecraft = '26.3'; fgaJar = $fga; rankboardJar = $rankboard; serverChecks = 'PASS';
        savedStats = 'PASS'; verifyOfflineRanking = [bool]$VerifyOfflineRanking;
        expectedStatsByUuid = $expected; log = "$run\stdout.log"
    } | ConvertTo-Json -Depth 5 | Set-Content "$run\summary.json" -Encoding utf8
    Write-Host "POSSESSION_STATS_SAVED_PASS $run\summary.json"
} finally {
    if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force }
}
