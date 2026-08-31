$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "shulker-armorstand-smoke-$stamp"
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
            ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!')) {
            break
        }
    }
    if ($server.HasExited -or !(Test-Path -LiteralPath $log) -or
        ((Get-Content -LiteralPath $log -Raw) -notmatch 'Done \([0-9.]+s\)!')) {
        throw "Temporary server did not become ready: $log"
    }

    Send-Command 'player FGAShulkerAS spawn at 0 100 24' 3500
    # The dev world persists across runs; clear leftovers from previous runs before setting up.
    Send-Command 'kill @e[type=minecraft:shulker]' 400
    Send-Command 'kill @e[type=minecraft:armor_stand]' 400
    Send-Command 'kill @e[type=minecraft:shulker_bullet]' 400
    Send-Command 'fill -6 99 -6 8 99 26 minecraft:stone' 900
    Send-Command 'fill -6 99 -6 8 99 26 minecraft:stone' 900
    Send-Command 'execute if block 3 99 2 minecraft:stone run say FGA_PLATFORM_OK' 300
    Send-Command 'difficulty normal'
    Send-Command 'gamerule doMobSpawning false'
    Send-Command 'summon minecraft:shulker 0 100 0 {Tags:["fga_as_sh"],PersistenceRequired:1b}' 800
    Send-Command 'execute if entity @e[tag=fga_as_sh] run say FGA_SHULKER_OK' 300

    # Scenario A: rule false + plain armor stand -> no attack
    Send-Command 'carpet shulkerAttackArmorStand false' 400
    Send-Command 'summon minecraft:armor_stand 3 100 0 {Tags:["fga_as_plain"]}' 600
    Send-Command 'kill @e[type=minecraft:shulker_bullet]' 400
    Send-Command 'say FGA_A_START' 5000
    1..5 | ForEach-Object {
        Send-Command 'execute if entity @e[type=minecraft:shulker_bullet,x=-10,y=90,z=-10,dx=25,dy=25,dz=25] run say FGA_A_BULLET' 1500
    }
    Send-Command 'data get entity @e[tag=fga_as_sh,limit=1] Peek' 400
    Send-Command 'say FGA_A_RESULTS' 400

    # Scenario B1: pumpkin + plain armor stand -> no attack. Set via legacy alias to verify normalization.
    Send-Command 'kill @e[type=minecraft:shulker_bullet]' 400
    Send-Command 'carpet shulkerAttackArmorStand onlyWithPumpkinHead' 400
    Send-Command 'say FGA_B1_START' 5000
    1..5 | ForEach-Object {
        Send-Command 'execute if entity @e[type=minecraft:shulker_bullet,x=-10,y=90,z=-10,dx=25,dy=25,dz=25] run say FGA_B1_BULLET' 1500
    }
    Send-Command 'data get entity @e[tag=fga_as_sh,limit=1] Peek' 400
    Send-Command 'say FGA_B1_RESULTS' 400

    # Scenario B2: onlyWithPumpkinHead + armor stand wearing carved pumpkin on head -> attack
    Send-Command 'kill @e[type=minecraft:armor_stand,tag=fga_as_plain]' 400
    Send-Command 'summon minecraft:armor_stand 3 100 2 {Tags:["fga_as_pumpkin"]}' 600
    Send-Command 'item replace entity @e[tag=fga_as_pumpkin,limit=1] armor.head with minecraft:carved_pumpkin 1' 500
    Send-Command 'kill @e[type=minecraft:shulker_bullet]' 400
    Send-Command 'say FGA_B2_START' 5000
    1..5 | ForEach-Object {
        Send-Command 'execute if entity @e[type=minecraft:shulker_bullet,x=-10,y=90,z=-10,dx=25,dy=25,dz=25] run say FGA_B2_BULLET' 1500
        Send-Command 'execute if entity @e[type=minecraft:shulker_bullet,x=-10,y=90,z=-10,dx=25,dy=25,dz=25] run data get entity @e[type=minecraft:shulker_bullet,limit=1] Target' 400
    }
    Send-Command 'data get entity @e[tag=fga_as_sh,limit=1] Peek' 400
    Send-Command 'say FGA_B2_RESULTS' 400

    # Scenario C: true + plain armor stand -> attack
    Send-Command 'kill @e[type=minecraft:armor_stand,tag=fga_as_pumpkin]' 400
    Send-Command 'kill @e[type=minecraft:shulker_bullet]' 400
    Send-Command 'carpet shulkerAttackArmorStand true' 400
    Send-Command 'summon minecraft:armor_stand 3 100 2 {Tags:["fga_as_plain2"]}' 600
    Send-Command 'say FGA_C_START' 5000
    1..5 | ForEach-Object {
        Send-Command 'execute if entity @e[type=minecraft:shulker_bullet,x=-10,y=90,z=-10,dx=25,dy=25,dz=25] run say FGA_C_BULLET' 1500
    }
    Send-Command 'data get entity @e[tag=fga_as_sh,limit=1] Peek' 400
    Send-Command 'say FGA_C_RESULTS' 400

    # Scenario E: switch true -> false mid-attack -> target released
    Send-Command 'carpet shulkerAttackArmorStand false' 400
    Send-Command 'kill @e[type=minecraft:shulker_bullet]' 400
    Send-Command 'say FGA_E_START' 4000
    1..3 | ForEach-Object {
        Send-Command 'execute if entity @e[type=minecraft:shulker_bullet,x=-10,y=90,z=-10,dx=25,dy=25,dz=25] run say FGA_E_BULLET' 1500
    }
    Send-Command 'data get entity @e[tag=fga_as_sh,limit=1] Peek' 400
    Send-Command 'say FGA_E_RESULTS' 400

    # Scenario W: wall-attached shulker + armor stand 8 blocks out from the wall plane -> attack
    # (attach-face search box only reaches 4 blocks along the wall axis, so the old geometry failed here)
    Send-Command 'kill @e[type=minecraft:armor_stand]' 400
    Send-Command 'kill @e[type=minecraft:shulker_bullet]' 400
    Send-Command 'carpet shulkerAttackArmorStand true' 400
    Send-Command 'fill -5 100 -6 -4 101 -6 minecraft:stone' 700
    Send-Command 'summon minecraft:shulker -5 100 -5 {Tags:["fga_as_wall"],AttachFace:2b,PersistenceRequired:1b}' 800
    Send-Command 'execute if entity @e[tag=fga_as_wall] run say FGA_WALL_SHULKER_OK' 300
    Send-Command 'summon minecraft:armor_stand -5 100 3 {Tags:["fga_as_wall_target"]}' 600
    Send-Command 'say FGA_W_START' 5000
    1..5 | ForEach-Object {
        Send-Command 'execute if entity @e[type=minecraft:shulker_bullet,x=-10,y=90,z=-10,dx=25,dy=25,dz=25] run say FGA_W_BULLET' 1500
    }
    Send-Command 'data get entity @e[tag=fga_as_wall,limit=1] Peek' 400
    Send-Command 'say FGA_W_RESULTS' 400

    # Invalid value rejected
    Send-Command 'carpet shulkerAttackArmorStand banana' 600

    Send-Command 'player FGAShulkerAS kill'
    Send-Command 'kill @e[type=minecraft:armor_stand]'
    Send-Command 'kill @e[type=minecraft:shulker]'
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
$a  = Get-Window $raw 'FGA_A_START'  'FGA_A_RESULTS'
$b1 = Get-Window $raw 'FGA_B1_START' 'FGA_B1_RESULTS'
$b2 = Get-Window $raw 'FGA_B2_START' 'FGA_B2_RESULTS'
$c  = Get-Window $raw 'FGA_C_START'  'FGA_C_RESULTS'
$e  = Get-Window $raw 'FGA_E_START'  'FGA_E_RESULTS'
$w  = Get-Window $raw 'FGA_W_START'  'FGA_W_RESULTS'

$checks = [ordered]@{
    ServerReady        = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError       = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError'
    PlatformReady      = $raw -match 'FGA_PLATFORM_OK'
    ShulkerPresent     = $raw -match 'FGA_SHULKER_OK'
    RuleRegistered     = $raw -match 'shulkerAttackArmorStand'
    A_NoBullet         = $a -notmatch 'FGA_A_BULLET' -and $a -match 'has the following entity data: (0|30)b'
    B1_NoBullet        = $b1 -notmatch 'FGA_B1_BULLET' -and $b1 -match 'has the following entity data: (0|30)b'
    B2_BulletFired     = $b2 -match 'FGA_B2_BULLET'
    B2_PeekAttacking   = $b2 -match 'has the following entity data: 100b'
    B2_BulletHasTarget = $b2 -match 'has the following entity data: \[I;'
    C_BulletFired      = $c -match 'FGA_C_BULLET'
    C_PeekAttacking    = $c -match 'has the following entity data: 100b'
    E_NoNewBullet      = $e -notmatch 'FGA_E_BULLET'
    E_PeekReleased     = $e -match 'has the following entity data: (0|30)b'
    WallShulkerPresent = $raw -match 'FGA_WALL_SHULKER_OK'
    W_BulletFired      = $w -match 'FGA_W_BULLET'
    W_PeekAttacking    = $w -match 'has the following entity data: 100b'
    InvalidRejected    = $raw -match 'shulkerAttackArmorStand must be false, true, or pumpkin'
    AliasNormalized    = $raw -match 'shulkerAttackArmorStand: pumpkin'
    CleanStop          = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$log"
Select-String -LiteralPath $log -Pattern 'shulkerAttackArmorStand|FGA_|has the following entity data|Mixin apply|InjectionError|Stopping server' |
    Select-Object -Last 120

if ($checks.Values -contains $false) { exit 1 }
