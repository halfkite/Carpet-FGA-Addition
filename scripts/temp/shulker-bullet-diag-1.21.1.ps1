$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "shulker-bullet-diag-$stamp"
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

    Send-Command 'fill -8 99 -8 16 99 8 minecraft:stone' 900
    Send-Command 'setblock 4 102 0 minecraft:stone'
    Send-Command 'setblock 10 102 0 minecraft:stone'
    Send-Command 'difficulty normal'

    Send-Command 'say FGA_DIAG_A_SETUP'
    Send-Command 'summon minecraft:shulker 4 103 0 {Tags:["fga_diag_a"],NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet 4.5 103.5 0.5 {Dir:5}'
    Send-Command 'say FGA_DIAG_A_T1'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_DIAG_A_BULLET_ALIVE_T1'
    Send-Command 'data get entity @e[type=minecraft:shulker_bullet,limit=1] Pos'
    Send-Command 'data get entity @e[tag=fga_diag_a,limit=1] Health' 700
    Send-Command 'say FGA_DIAG_A_T2'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_DIAG_A_BULLET_ALIVE_T2'
    Send-Command 'data get entity @e[type=minecraft:shulker_bullet,limit=1] Pos'
    Send-Command 'data get entity @e[tag=fga_diag_a,limit=1] Health' 700
    Send-Command 'say FGA_DIAG_A_T3'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_DIAG_A_BULLET_ALIVE_T3'
    Send-Command 'data get entity @e[tag=fga_diag_a,limit=1] Health'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'

    Send-Command 'say FGA_DIAG_B_SETUP'
    Send-Command 'summon minecraft:shulker 10 103 0 {Tags:["fga_diag_b"],NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet 8.5 103.5 0.5 {Dir:5,Motion:[1.0d,0.0d,0.0d]}'
    Send-Command 'say FGA_DIAG_B_T1'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_DIAG_B_BULLET_ALIVE_T1'
    Send-Command 'data get entity @e[tag=fga_diag_b,limit=1] Health' 700
    Send-Command 'say FGA_DIAG_B_T2'
    Send-Command 'execute if entity @e[type=minecraft:shulker_bullet] run say FGA_DIAG_B_BULLET_ALIVE_T2'
    Send-Command 'data get entity @e[tag=fga_diag_b,limit=1] Health'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]'
    Send-Command 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop' }
} finally {
    if (-not $server.HasExited) { $server.Kill($true) }
}

Write-Output "LOG=$log"
Select-String -LiteralPath $log -Pattern 'FGA_DIAG|entity data|Summoned|No entity|Killed' | Select-Object -Last 60
