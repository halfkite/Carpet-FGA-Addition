$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "shulker-bedrock-dup-smoke-$stamp"
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

    # Fake player keeps the test area entity-ticking; bullets despawn in peaceful, so force normal.
    Send-Command 'player FGAShulkerSmoke spawn at 0 100 12' 1500
    Send-Command 'fill -8 99 -8 18 99 8 minecraft:stone' 900
    Send-Command 'setblock 8 102 0 minecraft:stone'
    Send-Command 'difficulty normal'
    Send-Command 'carpet shulkerBedrockDuplication false'

    Send-Command 'say FGA_OFF_SETUP'
    Send-Command 'summon minecraft:shulker 0 100 0 {Tags:["fga_sh_off"],Health:1f,Color:14b,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet -1.5 100.5 0.5 {Motion:[1.0d,0.1d,0.0d]}' 2500
    Send-Command 'say FGA_OFF_RESULTS'
    Send-Command 'execute if entity @e[tag=fga_sh_off] run say FGA_OFF_ORIGINAL_ALIVE'
    Send-Command 'execute unless entity @e[type=minecraft:shulker,x=-2,y=99,z=-2,dx=5,dy=3,dz=4] run say FGA_OFF_NO_RESPAWN'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]' 1300

    Send-Command 'carpet shulkerBedrockDuplication true'
    Send-Command 'say FGA_ON_SETUP'
    Send-Command 'summon minecraft:shulker 0 100 0 {Tags:["fga_sh_on"],Health:1f,Color:14b,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet -1.5 100.5 0.5 {Motion:[1.0d,0.1d,0.0d]}' 2500
    Send-Command 'say FGA_ON_RESULTS'
    Send-Command 'execute if entity @e[tag=fga_sh_on] run say FGA_ON_ORIGINAL_ALIVE'
    Send-Command 'execute if entity @e[type=minecraft:shulker,x=-2,y=99,z=-2,dx=5,dy=3,dz=4,tag=!fga_sh_on] run say FGA_ON_RESPAWNED'
    Send-Command 'data get entity @e[type=minecraft:shulker,x=-2,y=99,z=-2,dx=5,dy=3,dz=4,limit=1] Color'
    Send-Command 'data get entity @e[type=minecraft:shulker,x=-2,y=99,z=-2,dx=5,dy=3,dz=4,limit=1] Pos'
    Send-Command 'carpet shulkerBedrockDuplication false'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]' 1300

    Send-Command 'carpet shulkerBedrockDuplication true'
    Send-Command 'say FGA_MELEE_SETUP'
    Send-Command 'summon minecraft:shulker 14 100 4 {Tags:["fga_sh_melee"],Health:1f,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'damage @e[tag=fga_sh_melee,limit=1] 999 minecraft:mob_attack' 2500
    Send-Command 'say FGA_MELEE_RESULTS'
    Send-Command 'execute unless entity @e[type=minecraft:shulker,x=12,y=99,z=2,dx=5,dy=3,dz=4] run say FGA_MELEE_NO_RESPAWN'
    Send-Command 'kill @e[type=minecraft:shulker]' 1300

    Send-Command 'say FGA_NONLETHAL_SETUP'
    Send-Command 'summon minecraft:shulker 0 100 4 {Tags:["fga_sh_nonlethal"],Health:10f,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet -6 120 -6 {Dir:1}'
    Send-Command 'damage @e[tag=fga_sh_nonlethal,limit=1] 4 minecraft:mob_projectile by @e[type=minecraft:shulker_bullet,limit=1]' 2000
    Send-Command 'say FGA_NONLETHAL_RESULTS'
    Send-Command 'execute if entity @e[tag=fga_sh_nonlethal] run say FGA_NONLETHAL_SURVIVED'
    Send-Command 'execute if entity @e[type=minecraft:shulker,tag=!fga_sh_nonlethal] run say FGA_NONLETHAL_EXTRA_SHULKER'
    Send-Command 'data get entity @e[tag=fga_sh_nonlethal,limit=1] Health'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]' 1300

    Send-Command 'say FGA_DMG_SETUP'
    Send-Command 'summon minecraft:shulker 0 100 -4 {Tags:["fga_sh_dmg"],Health:1f,Color:14b,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet -6 120 -6 {Dir:1}'
    Send-Command 'damage @e[tag=fga_sh_dmg,limit=1] 999 minecraft:mob_projectile by @e[type=minecraft:shulker_bullet,limit=1]' 2500
    Send-Command 'say FGA_DMG_RESULTS'
    Send-Command 'execute if entity @e[tag=fga_sh_dmg] run say FGA_DMG_ORIGINAL_ALIVE'
    Send-Command 'execute if entity @e[type=minecraft:shulker,x=-2,y=99,z=-6,dx=5,dy=3,dz=4,tag=!fga_sh_dmg] run say FGA_DMG_RESPAWNED'
    Send-Command 'data get entity @e[type=minecraft:shulker,x=-2,y=99,z=-6,dx=5,dy=3,dz=4,limit=1] Color'
    Send-Command 'data get entity @e[type=minecraft:shulker,x=-2,y=99,z=-6,dx=5,dy=3,dz=4,limit=1] Pos'
    Send-Command 'carpet shulkerBedrockDuplication false'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]' 1300

    Send-Command 'carpet shulkerBedrockDuplication true'
    Send-Command 'say FGA_CEIL_SETUP'
    Send-Command 'summon minecraft:shulker 8 101 0 {Tags:["fga_sh_ceil"],Health:1f,AttachFace:1b,NoAI:1b,PersistenceRequired:1b}' 600
    Send-Command 'summon minecraft:shulker_bullet 14 120 6 {Dir:1}'
    Send-Command 'damage @e[tag=fga_sh_ceil,limit=1] 999 minecraft:mob_projectile by @e[type=minecraft:shulker_bullet,limit=1]' 2500
    Send-Command 'say FGA_CEIL_RESULTS'
    Send-Command 'execute if entity @e[type=minecraft:shulker,x=6,y=100,z=-2,dx=5,dy=3,dz=4,tag=!fga_sh_ceil] run say FGA_CEIL_RESPAWNED'
    Send-Command 'data get entity @e[type=minecraft:shulker,x=6,y=100,z=-2,dx=5,dy=3,dz=4,limit=1] AttachFace'
    Send-Command 'data get entity @e[type=minecraft:shulker,x=6,y=100,z=-2,dx=5,dy=3,dz=4,limit=1] Pos'

    Send-Command 'carpet shulkerBedrockDuplication false'
    Send-Command 'kill @e[type=minecraft:shulker_bullet]'
    Send-Command 'kill @e[type=minecraft:shulker]'
    Send-Command 'player FGAShulkerSmoke kill'
    Send-Command 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop' }
} finally {
    if (-not $server.HasExited) { $server.Kill($true) }
}

$raw = Get-Content -LiteralPath $log -Raw
$checks = [ordered]@{
    ServerReady = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError'
    RuleRegistered = $raw -match 'shulkerBedrockDuplication: false'
    RuleTrueEcho = $raw -match 'shulkerBedrockDuplication: true'
    OffOriginalDead = $raw -notmatch 'FGA_OFF_ORIGINAL_ALIVE'
    OffNoRespawn = $raw -match 'FGA_OFF_NO_RESPAWN'
    OnOriginalDead = $raw -notmatch 'FGA_ON_ORIGINAL_ALIVE'
    OnRespawned = $raw -match 'FGA_ON_RESPAWNED'
    OnColorCopied = $raw -match 'FGA_ON_RESULTS[\s\S]{0,1500}entity data: 14b'
    OnPositionAtSpot = $raw -match 'FGA_ON_RESULTS[\s\S]{0,3000}\[0\.5d, 100\.0d, 0\.5d\]'
    MeleeNoRespawn = $raw -match 'FGA_MELEE_NO_RESPAWN'
    NonlethalSurvived = $raw -match 'FGA_NONLETHAL_SURVIVED'
    NonlethalNoExtra = $raw -notmatch 'FGA_NONLETHAL_EXTRA_SHULKER'
    NonlethalHealth = $raw -match 'FGA_NONLETHAL_RESULTS[\s\S]{0,1500}entity data: 6\.0f'
    DmgOriginalDead = $raw -notmatch 'FGA_DMG_ORIGINAL_ALIVE'
    DmgRespawned = $raw -match 'FGA_DMG_RESPAWNED'
    DmgColorCopied = $raw -match 'FGA_DMG_RESULTS[\s\S]{0,1500}entity data: 14b'
    DmgPositionAtSpot = $raw -match 'FGA_DMG_RESULTS[\s\S]{0,3000}\[0\.5d, 100\.0d, -3\.5d\]'
    CeilRespawned = $raw -match 'FGA_CEIL_RESPAWNED'
    CeilAttachFaceCopied = $raw -match 'FGA_CEIL_RESULTS[\s\S]{0,1500}entity data: 1b'
    CeilPositionAtSpot = $raw -match 'FGA_CEIL_RESULTS[\s\S]{0,3000}\[8\.5d, 101\.0d, 0\.5d\]'
    CleanStop = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$log"
Select-String -LiteralPath $log -Pattern 'shulkerBedrockDuplication|FGA_|entity data|Applied|Mixin apply|InjectionError|Stopping server' |
    Select-Object -Last 140

if ($checks.Values -contains $false) { exit 1 }
