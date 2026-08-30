$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "shulker-diag4-$stamp"
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

try {
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        Start-Sleep -Seconds 1
        if ((Test-Path -LiteralPath $log) -and
            ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!')) { break }
    }
    if ($server.HasExited -or !(Test-Path -LiteralPath $log) -or
        ((Get-Content -LiteralPath $log -Raw) -notmatch 'Done \([0-9.]+s\)!')) {
        throw "Temporary server did not become ready: $log"
    }

    Send-Command 'player FGADiag spawn at 0 100 10' 1500
    Send-Command 'fill -4 99 -4 8 99 6 minecraft:stone' 900
    Send-Command 'difficulty normal'
    Send-Command 'carpet shulkerBedrockDuplication true'
    Send-Command 'say FGA_D4_SETUP'
    Send-Command 'summon minecraft:shulker 0 100 0 {Tags:["fga_d4"],Health:30f,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet 0.5 100.5 0.5 {Dir:5}'
    Send-Command 'say FGA_D4_T1'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_D4_BULLET_ALIVE_T1'
    Send-Command 'data get entity @e[type=minecraft:shulker_bullet,limit=1] Pos'
    Send-Command 'data get entity @e[tag=fga_d4,limit=1] Health' 900
    Send-Command 'say FGA_D4_T2'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_D4_BULLET_ALIVE_T2'
    Send-Command 'data get entity @e[type=minecraft:shulker_bullet,limit=1] Pos'
    Send-Command 'data get entity @e[tag=fga_d4,limit=1] Health'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]' 600
    Send-Command 'say FGA_D4_SETUP2'
    Send-Command 'summon minecraft:shulker 4 100 0 {Tags:["fga_d4b"],Health:30f,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet 3.5 100.5 0.5 {Dir:5,Motion:[1.0d,0.1d,0.0d]}'
    Send-Command 'say FGA_D4_T3'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_D4B_BULLET_ALIVE_T3'
    Send-Command 'data get entity @e[type=minecraft:shulker_bullet,limit=1] Pos'
    Send-Command 'data get entity @e[tag=fga_d4b,limit=1] Health' 900
    Send-Command 'say FGA_D4_T4'
    Send-Command 'data get entity @e[tag=fga_d4b,limit=1] Health'
    Send-Command 'carpet shulkerBedrockDuplication false'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]'
    Send-Command 'player FGADiag kill'
    Send-Command 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop' }
} finally {
    if (-not $server.HasExited) { $server.Kill($true) }
}

Write-Output "LOG=$log"
Select-String -LiteralPath $log -Pattern 'FGA_D3|FGA-DEBUG|entity data|Applied|Mixin|ERROR' | Select-Object -Last 40
