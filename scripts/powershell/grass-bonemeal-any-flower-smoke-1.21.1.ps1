$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "grass-flower-smoke-$stamp"
$log = Join-Path $run "$worldName.log"

[IO.File]::WriteAllText((Join-Path $run 'eula.txt'), 'eula=true', [Text.Encoding]::ASCII)

$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = 'cmd.exe'
$startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
    '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0 --world ' +
    $worldName + '" > "' + $log + '" 2>&1"'
$startInfo.WorkingDirectory = $root
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.CreateNoWindow = $true
$server = [Diagnostics.Process]::new()
$server.StartInfo = $startInfo
[void] $server.Start()

function Send-Command([string] $command, [int] $delay = 300) {
    $server.StandardInput.WriteLine($command)
    $server.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

# A dispenser at (0,98,1) faces north onto the grass block at (0,98,0).
# Toggling a redstone block at (0,98,2) pulses the dispenser once per rising edge,
# which applies one bone meal to the grass block (vanilla dispenser behavior).
function Send-BonemealPulses([int] $count) {
    for ($i = 0; $i -lt $count; $i++) {
        Send-Command 'setblock 0 98 2 minecraft:redstone_block' 300
        Send-Command 'setblock 0 98 2 minecraft:air' 300
    }
}

try {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        Start-Sleep -Seconds 1
        if ((Test-Path -LiteralPath $log) -and
            ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!')) {
            break
        }
    }
    if ($server.HasExited -or !(Test-Path -LiteralPath $log) -or
        ((Get-Content -LiteralPath $log -Raw) -notmatch 'Done \([0-9.]+s\)!')) {
        throw "Temporary server did not become ready: $log"
    }

    Send-Command 'gamerule doMobSpawning false' 300
    Send-Command 'gamerule randomTickSpeed 0' 300
    Send-Command 'gamerule doDaylightCycle false' 300
    Send-Command 'time set noon' 300
    Send-Command 'scoreboard objectives add fgaFlower dummy' 400
    Send-Command 'forceload add -8 -8 8 8' 500

    # Grass platform at y=98 in open sky, dispenser aimed at its center block.
    Send-Command 'fill -8 99 -8 8 130 8 minecraft:air' 800
    Send-Command 'fill -8 98 -8 8 98 8 minecraft:grass_block' 800
    Send-Command 'setblock 0 98 1 minecraft:dispenser[facing=north]' 600
    Send-Command 'item replace block 0 98 1 container.0 with minecraft:bone_meal 64' 600
    Send-Command 'execute if block 0 98 0 minecraft:grass_block run say FGA_PLATFORM_OK' 400
    Send-Command 'setblock 5 99 5 minecraft:oxeye_daisy' 600
    Send-Command 'execute if block 5 99 5 minecraft:oxeye_daisy run say FGA_FLOWER_SURVIVES' 400

    # S1: rule true -> small flowers appear, tall flowers never
    Send-Command 'carpet grassBonemealAnyFlower true' 500
    Send-Command 'say FGA_S1_START' 500
    Send-BonemealPulses 10
    Send-Command 'execute store result score fgaSmall fgaFlower run fill -7 99 -7 7 99 7 minecraft:air replace #minecraft:small_flowers' 700
    Send-Command 'execute if score fgaSmall fgaFlower matches 1.. run say FGA_S1_SMALL_FOUND' 400
    Send-Command 'execute store result score fgaTall fgaFlower run fill -7 99 -7 7 99 7 minecraft:air replace #minecraft:tall_flowers' 700
    Send-Command 'execute unless score fgaTall fgaFlower matches 1.. run say FGA_S1_TALL_ABSENT' 400
    Send-Command 'say FGA_S1_END' 400

    # S2: rule all -> tall flowers appear too
    Send-Command 'fill -7 99 -7 7 99 7 minecraft:air' 700
    Send-Command 'carpet grassBonemealAnyFlower all' 500
    Send-Command 'say FGA_S2_START' 500
    Send-BonemealPulses 10
    Send-Command 'execute store result score fgaSmall2 fgaFlower run fill -7 99 -7 7 99 7 minecraft:air replace #minecraft:small_flowers' 700
    Send-Command 'execute if score fgaSmall2 fgaFlower matches 1.. run say FGA_S2_SMALL_FOUND' 400
    Send-Command 'execute store result score fgaTall2 fgaFlower run fill -7 99 -7 7 99 7 minecraft:air replace #minecraft:tall_flowers' 700
    Send-Command 'execute if score fgaTall2 fgaFlower matches 1.. run say FGA_S2_TALL_FOUND' 400
    Send-Command 'say FGA_S2_END' 400

    # S3: invalid value rejected
    Send-Command 'say FGA_S3_START' 400
    Send-Command 'carpet grassBonemealAnyFlower banana' 600

    Send-Command 'carpet grassBonemealAnyFlower false' 400
    Send-Command 'kill @e[type=minecraft:item]' 400
    Send-Command 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop' }
} finally {
    if (-not $server.HasExited) { $server.Kill($true) }
}

$raw = Get-Content -LiteralPath $log -Raw
function Get-Window([string] $text, [string] $from, [string] $to) {
    $m = [regex]::Match($text, [regex]::Escape($from) + '[\s\S]*?' + [regex]::Escape($to))
    if ($m.Success) { $m.Value } else { '' }
}
$s1 = Get-Window $raw 'FGA_S1_START' 'FGA_S1_END'
$s2 = Get-Window $raw 'FGA_S2_START' 'FGA_S2_END'

$checks = [ordered]@{
    ServerReady      = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError     = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError'
    PlatformReady    = $raw -match 'FGA_PLATFORM_OK'
    FlowerSurvives   = $raw -match 'FGA_FLOWER_SURVIVES'
    RuleRegistered   = $raw -match 'grassBonemealAnyFlower'
    S1_SmallFound    = $s1 -match 'FGA_S1_SMALL_FOUND'
    S1_TallAbsent    = $s1 -match 'FGA_S1_TALL_ABSENT'
    S2_SmallFound    = $s2 -match 'FGA_S2_SMALL_FOUND'
    S2_TallFound     = $s2 -match 'FGA_S2_TALL_FOUND'
    InvalidRejected  = $raw -match 'grassBonemealAnyFlower must be false, true, or all'
    CleanStop        = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$log"

if ($checks.Values -contains $false) { exit 1 }
