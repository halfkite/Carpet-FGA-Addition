$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "baby-mob-no-growth-smoke-$stamp"
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

    Send-Command 'player FGABabyTest spawn at 0 100 3' 1800
    Send-Command 'fill -5 99 -5 12 99 5 minecraft:stone'
    Send-Command 'carpet babyMobNoGrowth false'
    Send-Command 'summon minecraft:cow 0 100 0 {Tags:["fga_false_cow"],Age:-5,Invulnerable:1b}'
    Send-Command 'summon minecraft:villager 2 100 0 {Tags:["fga_false_villager"],Age:-5,Invulnerable:1b}'
    Send-Command 'summon minecraft:tadpole 4 100 0 {Tags:["fga_false_tadpole"],Age:23995,Invulnerable:1b}'
    Send-Command 'say FGA_WAIT_FALSE' 2500
    Send-Command 'say FGA_FALSE_RESULTS'
    Send-Command 'data get entity @e[tag=fga_false_cow,limit=1] Age'
    Send-Command 'data get entity @e[tag=fga_false_villager,limit=1] Age'
    Send-Command 'execute unless entity @e[type=minecraft:tadpole,tag=fga_false_tadpole,limit=1] run say FGA_FALSE_TADPOLE_GREW'

    Send-Command 'carpet babyMobNoGrowth true'
    Send-Command 'summon minecraft:cow 0 102 0 {Tags:["fga_true_cow"],Age:-20,Invulnerable:1b}'
    Send-Command 'summon minecraft:villager 2 102 0 {Tags:["fga_true_villager"],Age:-20,Invulnerable:1b}'
    Send-Command 'summon minecraft:tadpole 4 102 0 {Tags:["fga_true_tadpole"],Age:23990,Invulnerable:1b}'
    Send-Command 'say FGA_WAIT_TRUE' 3000
    Send-Command 'say FGA_TRUE_RESULTS'
    Send-Command 'data get entity @e[tag=fga_true_cow,limit=1] Age'
    Send-Command 'data get entity @e[tag=fga_true_villager,limit=1] Age'
    Send-Command 'data get entity @e[tag=fga_true_tadpole,limit=1] Age'

    Send-Command 'carpet babyMobNoGrowth "Forever Young"'
    Send-Command 'summon minecraft:cow 0 104 0 {Tags:["fga_name_match"],Age:-20,Invulnerable:1b,CustomName:''{"text":"Forever Young"}''}'
    Send-Command 'summon minecraft:cow 2 104 0 {Tags:["fga_name_case_mismatch"],Age:-20,Invulnerable:1b,CustomName:''{"text":"forever young"}''}'
    Send-Command 'summon minecraft:cow 4 104 0 {Tags:["fga_name_unnamed"],Age:-20,Invulnerable:1b}'
    Send-Command 'say FGA_WAIT_NAME' 3000
    Send-Command 'say FGA_NAME_RESULTS'
    Send-Command 'data get entity @e[tag=fga_name_match,limit=1] Age'
    Send-Command 'data get entity @e[tag=fga_name_case_mismatch,limit=1] Age'
    Send-Command 'data get entity @e[tag=fga_name_unnamed,limit=1] Age'

    Send-Command 'carpet babyMobNoGrowth mini'
    Send-Command 'summon minecraft:cow 6 104 0 {Tags:["fga_mini_match"],Age:-20,Invulnerable:1b,CustomName:''{"text":"mini"}''}'
    Send-Command 'summon minecraft:cow 8 104 0 {Tags:["fga_mini_case_mismatch"],Age:-20,Invulnerable:1b,CustomName:''{"text":"Mini"}''}'
    Send-Command 'say FGA_WAIT_MINI' 3000
    Send-Command 'say FGA_MINI_RESULTS'
    Send-Command 'data get entity @e[tag=fga_mini_match,limit=1] Age'
    Send-Command 'data get entity @e[tag=fga_mini_case_mismatch,limit=1] Age'

    Send-Command 'carpet babyMobNoGrowth true'
    Send-Command 'summon minecraft:cow 10 104 0 {Tags:["fga_manual_age"],Age:-100,Invulnerable:1b}'
    Send-Command 'data merge entity @e[tag=fga_manual_age,limit=1] {Age:0}'
    Send-Command 'say FGA_MANUAL_RESULT'
    Send-Command 'data get entity @e[tag=fga_manual_age,limit=1] Age'

    Send-Command 'summon minecraft:cow 12 104 0 {Tags:["fga_feed_cow"],Age:-20000,NoAI:1b,Invulnerable:1b}'
    Send-Command 'tp FGABabyTest 12 104 3' 500
    Send-Command 'item replace entity FGABabyTest weapon.mainhand with minecraft:wheat 1'
    Send-Command 'player FGABabyTest look at 12 104 0'
    Send-Command 'player FGABabyTest use once' 700
    Send-Command 'say FGA_FEED_RESULT'
    Send-Command 'data get entity @e[tag=fga_feed_cow,limit=1] Age'
    Send-Command 'player FGABabyTest kill'

    Send-Command 'carpet babyMobNoGrowth "   "'
    Send-Command 'carpet babyMobNoGrowth false'
    Send-Command 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop' }
} finally {
    if (-not $server.HasExited) { $server.Kill($true) }
}

$raw = Get-Content -LiteralPath $log -Raw
$checks = [ordered]@{
    ServerReady = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError'
    RuleRegistered = $raw -match 'babyMobNoGrowth: false'
    FalseCowGrew = $raw -match 'FGA_FALSE_RESULTS[\s\S]{0,1200}Cow has the following entity data: 0'
    FalseVillagerGrew = $raw -match 'FGA_FALSE_RESULTS[\s\S]{0,1800}Villager has the following entity data: 0'
    FalseTadpoleGrew = $raw -match 'FGA_FALSE_TADPOLE_GREW'
    TrueCowFrozen = $raw -match 'FGA_TRUE_RESULTS[\s\S]{0,1200}Cow has the following entity data: -20'
    TrueVillagerFrozen = $raw -match 'FGA_TRUE_RESULTS[\s\S]{0,1800}Villager has the following entity data: -20'
    TrueTadpoleFrozen = $raw -match 'FGA_TRUE_RESULTS[\s\S]{0,3000}has the following entity data: 23990'
    NameMatchFrozen = $raw -match 'FGA_NAME_RESULTS[\s\S]{0,1200}Forever Young has the following entity data: -20'
    NameMismatchGrew = ($raw -match 'FGA_NAME_RESULTS[\s\S]{0,1800}forever young has the following entity data: 0') -and
        ($raw -match 'FGA_NAME_RESULTS[\s\S]{0,2400}Cow has the following entity data: 0')
    MiniPresetMatchFrozen = $raw -match 'FGA_MINI_RESULTS[\s\S]{0,1200}mini has the following entity data: -20'
    MiniPresetCaseMismatchGrew = $raw -match 'FGA_MINI_RESULTS[\s\S]{0,1800}Mini has the following entity data: 0'
    ManualAgeAllowed = $raw -match 'FGA_MANUAL_RESULT[\s\S]{0,1200}has the following entity data: 0'
    FeedingBlocked = $raw -match 'FGA_FEED_RESULT[\s\S]{0,1200}has the following entity data: -20000'
    BlankRejected = $raw -match 'babyMobNoGrowth must be false, true, or a non-blank custom name'
    CleanStop = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$log"
Select-String -LiteralPath $log -Pattern 'babyMobNoGrowth|FGA_|has the following entity data|Mixin apply|InjectionError|Stopping server' |
    Select-Object -Last 100

if ($checks.Values -contains $false) { exit 1 }
