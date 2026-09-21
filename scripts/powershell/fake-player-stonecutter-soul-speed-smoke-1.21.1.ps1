$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$version = '1.21.1'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $root "versions\$version\run"
$reportDir = Join-Path $root "scripts\logs\fake-player-stonecutter-soul-speed-$stamp"
$worldName = "fake-player-stonecutter-soul-speed-$stamp"
$walkDurationSeconds = 75
$worldPath = Join-Path $runDir $worldName
$serverLog = Join-Path $reportDir 'server.log'
$eulaPath = Join-Path $runDir 'eula.txt'
$eulaBackup = "$eulaPath.before-fake-player-stonecutter-soul-speed-$stamp"

New-Item -ItemType Directory -Force -Path $reportDir, $runDir | Out-Null
$datapackDir = Join-Path $worldPath 'datapacks\fga-smoke'
New-Item -ItemType Directory -Force -Path (Join-Path $datapackDir 'data\fga_smoke\recipe') | Out-Null
Set-Content -LiteralPath (Join-Path $datapackDir 'pack.mcmeta') -Encoding utf8 -Value '{"pack":{"pack_format":48,"description":"FGA fake-player smoke"}}'
Set-Content -LiteralPath (Join-Path $datapackDir 'data\fga_smoke\recipe\simple.json') -Encoding utf8 -Value '{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:beacon"},"result":{"id":"minecraft:stone","count":1}}'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

if (Test-Path -LiteralPath $eulaPath) {
    Copy-Item -LiteralPath $eulaPath -Destination $eulaBackup
}
Set-Content -LiteralPath $eulaPath -Value 'eula=true' -Encoding ascii

$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = 'cmd.exe'
$startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
    '" :' + $version + ':runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0 --world ' +
    $worldName + '" > "' + $serverLog + '" 2>&1"'
$startInfo.WorkingDirectory = $root
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.CreateNoWindow = $true
$server = [Diagnostics.Process]::new()
$server.StartInfo = $startInfo
[void] $server.Start()

function Send-Command([string] $command, [int] $delay = 500) {
    $server.StandardInput.WriteLine($command)
    $server.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

function Read-Log() {
    if (Test-Path -LiteralPath $serverLog) {
        return Get-Content -LiteralPath $serverLog -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Wait-LogPattern([string] $pattern, [int] $timeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Read-Log) -match $pattern) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for server log pattern: $pattern"
}

try {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Read-Log) -match 'Done \([0-9.]+s\)!') { break }
        Start-Sleep -Seconds 2
    }
    if ($server.HasExited -or (Read-Log) -notmatch 'Done \([0-9.]+s\)!') {
        throw 'Temporary server did not become ready'
    }

    Send-Command 'gamerule doMobSpawning false'
    Send-Command 'difficulty normal'
    Send-Command 'carpet soulSpeedNoDurability true'
    Send-Command 'carpet lightSourceStonecuttingRecipes true'
    Send-Command 'reload' 4000
    Send-Command 'scoreboard objectives add fga_smoke dummy'

    # The two fake players share a flat, loaded test lane.  The first opens the stonecutter.
    Send-Command 'forceload add 0 0 120 4' 3000
    Send-Command 'fill 0 99 0 120 99 4 minecraft:soul_sand'
    Send-Command 'fill 0 100 0 120 102 4 minecraft:air'
    Send-Command 'setblock 0 100 0 minecraft:stonecutter'
    Send-Command 'player FGAStonecutter spawn at 0.5 100 2.5'
    Wait-LogPattern 'fgastonecutter joined the game'
    Send-Command 'player FGAStonecutter look north'
    Send-Command 'item replace entity FGAStonecutter weapon.mainhand with minecraft:beacon'
    Send-Command 'player FGAStonecutter use once' 1200
    Send-Command 'execute store result score FGA_VANILLA fga_smoke run recipe give FGAStonecutter minecraft:stonecutter'
    Send-Command 'scoreboard players get FGA_VANILLA fga_smoke'
    Send-Command 'execute store result score FGA_DATAPACK fga_smoke run recipe give FGAStonecutter fga_smoke:simple'
    Send-Command 'scoreboard players get FGA_DATAPACK fga_smoke'
    Send-Command 'execute store result score FGA_RECIPE fga_smoke run recipe give FGAStonecutter carpet-fga-addition:light_level_01_15_from_light_source_stonecutting'
    Send-Command 'scoreboard players get FGA_RECIPE fga_smoke'
    Send-Command 'say FGA_STONECUTTER_FAKE_PLAYER_DONE'

    # The second fake player walks east on soul sand for at least 50 blocks with Soul Speed.
    Send-Command 'player FGAStonecutter kill'
    Send-Command 'player FGASoulSpeed spawn at 0.5 100 2.5'
    Wait-LogPattern 'fgasoulspeed joined the game'
    Send-Command 'item replace entity FGASoulSpeed armor.feet with minecraft:diamond_boots[minecraft:damage=100,minecraft:enchantments={levels:{"minecraft:soul_speed":3}}]'
    Send-Command 'execute if items entity FGASoulSpeed armor.feet minecraft:diamond_boots run say FGA_BOOT_PRESENT'
    Send-Command 'data get entity FGASoulSpeed Inventory'
    Send-Command 'execute store result score FGA_START fga_smoke run data get entity FGASoulSpeed Pos[0] 100'
    Send-Command 'player FGASoulSpeed look east'
    Send-Command 'player FGASoulSpeed move forward'
    Start-Sleep -Seconds $walkDurationSeconds
    Send-Command 'player FGASoulSpeed stop'
    Send-Command 'execute store result score FGA_END fga_smoke run data get entity FGASoulSpeed Pos[0] 100'
    Send-Command 'scoreboard players operation FGA_DISTANCE fga_smoke = FGA_END fga_smoke'
    Send-Command 'scoreboard players operation FGA_DISTANCE fga_smoke -= FGA_START fga_smoke'
    Send-Command 'scoreboard players get FGA_DISTANCE fga_smoke'
    Send-Command 'execute if items entity FGASoulSpeed armor.feet minecraft:diamond_boots[minecraft:damage=100] run say FGA_BOOT_DURABILITY_UNCHANGED'
    Send-Command 'data get entity FGASoulSpeed Inventory'
    Send-Command 'say FGA_SOUL_SPEED_FAKE_PLAYER_DONE'
    Send-Command 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop within 90 seconds' }
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $eulaPath) { Remove-Item -LiteralPath $eulaPath -Force }
    if (Test-Path -LiteralPath $eulaBackup) {
        Move-Item -LiteralPath $eulaBackup -Destination $eulaPath
    }
    if (Test-Path -LiteralPath $worldPath) {
        $resolvedRun = [IO.Path]::GetFullPath($runDir).TrimEnd('\') + '\'
        $resolvedWorld = [IO.Path]::GetFullPath($worldPath)
        if (-not $resolvedWorld.StartsWith($resolvedRun, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to remove world outside test run directory: $resolvedWorld"
        }
        Remove-Item -LiteralPath $worldPath -Recurse -Force
    }
}

$raw = Read-Log
$distance = [regex]::Matches($raw, 'FGA_DISTANCE.*?(-?\d+)') | Select-Object -Last 1
$checks = [ordered]@{
    ServerReady = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError|Critical injection failure'
    StonecutterFakePlayer = $raw -match 'FGA_STONECUTTER_FAKE_PLAYER_DONE'
    RecipeRegistered = $raw -match 'FGA_RECIPE.*\b1\b'
    SoulSpeedFakePlayer = $raw -match 'FGA_SOUL_SPEED_FAKE_PLAYER_DONE'
    BootsPresent = $raw -match 'FGA_BOOT_PRESENT'
    BootsDurabilityUnchanged = $raw -match 'FGA_BOOT_DURABILITY_UNCHANGED'
    DistanceAtLeast50m = $raw -match 'FGA_DISTANCE.*\b(5[0-9]{3}|[6-9][0-9]{3}|[1-9][0-9]{4,})\b'
    CleanStop = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$serverLog"
Select-String -LiteralPath $serverLog -Pattern 'FGA_|soulSpeedNoDurability|lightSourceStonecuttingRecipes|InvalidMixin|Injection|Exception|Stopping server' |
    Select-Object -Last 80

if ($checks.Values -contains $false) { exit 1 }
