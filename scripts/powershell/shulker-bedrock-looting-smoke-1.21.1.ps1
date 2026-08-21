$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "shulker-bedrock-looting-smoke-$stamp"
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

function Invoke-Kills([int] $count) {
    for ($i = 0; $i -lt $count; $i++) {
        Send-Command 'summon minecraft:shulker 3 100 0 {Tags:["fga_loot"],Health:1f,NoAI:1b,PersistenceRequired:1b}' 400
        Send-Command 'damage @e[tag=fga_loot,limit=1] 999 minecraft:player_attack by FGALoot' 1400
        Send-Command 'data get entity FGALoot Inventory' 300
    }
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

    Send-Command 'player FGALoot spawn at 3 100 1' 3500
    # The dev world persists across runs; clear leftovers before testing.
    Send-Command 'kill @e[type=minecraft:shulker]' 400
    Send-Command 'kill @e[type=minecraft:item]' 400
    Send-Command 'clear FGALoot' 400
    Send-Command 'fill -3 99 -3 8 99 5 minecraft:stone' 900
    Send-Command 'fill -3 99 -3 8 99 5 minecraft:stone' 900
    Send-Command 'execute if block 3 99 0 minecraft:stone run say FGA_PLATFORM_OK' 300
    Send-Command 'difficulty normal'
    Send-Command 'gamerule doMobSpawning false'
    Send-Command 'gamerule doMobLoot true' 400
    Send-Command 'give FGALoot minecraft:netherite_sword[minecraft:enchantments={levels:{"minecraft:looting":3}}]' 500
    Send-Command 'execute if entity FGALoot[nbt={Inventory:[{id:"minecraft:netherite_sword"}]}] run say FGA_SWORD_OK' 300
    # Move close to the drop spot so dropped shells are attracted and picked up.
    Send-Command 'tp FGALoot 3.5 100 1.1' 400

    # S1: rule OFF + Looting III -> vanilla: max 1 shell per kill
    Send-Command 'carpet shulkerBedrockLooting false' 400
    Send-Command 'say FGA_L1_START' 300
    Invoke-Kills 15
    Send-Command 'say FGA_L1_RESULTS' 300

    # S2: rule ON + Looting III -> Bedrock: uniform 1-4 per dropped kill, some kills must give >=2
    Send-Command 'carpet shulkerBedrockLooting true' 400
    Send-Command 'say FGA_L2_START' 300
    Invoke-Kills 15
    Send-Command 'say FGA_L2_RESULTS' 300

    # S3: rule ON + no Looting -> Bedrock equals vanilla shape: max 1 per kill
    Send-Command 'clear FGALoot' 400
    Send-Command 'give FGALoot minecraft:iron_sword' 500
    Send-Command 'tp FGALoot 3.5 100 1.1' 400
    Send-Command 'say FGA_L3_START' 300
    Invoke-Kills 10
    Send-Command 'say FGA_L3_RESULTS' 300

    # S4: rule ON + doMobLoot false -> no drops at all
    Send-Command 'gamerule doMobLoot false' 400
    Send-Command 'say FGA_L4_START' 300
    Invoke-Kills 3
    Send-Command 'say FGA_L4_RESULTS' 300
    Send-Command 'gamerule doMobLoot true' 400

    Send-Command 'player FGALoot kill'
    Send-Command 'kill @e[type=minecraft:item]'
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
function Get-ShellTotals([string] $window) {
    # Item stacks serialize as {count: N, Slot: Xb, components: {...}, id: "..."} - count precedes id.
    $totals = @()
    foreach ($line in ($window -split "`n")) {
        if ($line -match 'has the following entity data') {
            $sum = 0
            foreach ($m in [regex]::Matches($line, '\{[^{}]*id: "minecraft:shulker_shell"[^{}]*\}')) {
                if ($m.Value -match 'count:\s*(\d+)') {
                    $sum += [int]$Matches[1]
                }
            }
            $totals += $sum
        }
    }
    return $totals
}
function Get-Increments([int[]] $totals) {
    $inc = @()
    for ($i = 1; $i -lt $totals.Count; $i++) { $inc += ($totals[$i] - $totals[$i - 1]) }
    return $inc
}

$t1 = Get-ShellTotals (Get-Window $raw 'FGA_L1_START' 'FGA_L1_RESULTS')
$t2 = Get-ShellTotals (Get-Window $raw 'FGA_L2_START' 'FGA_L2_RESULTS')
$t3 = Get-ShellTotals (Get-Window $raw 'FGA_L3_START' 'FGA_L3_RESULTS')
$t4 = Get-ShellTotals (Get-Window $raw 'FGA_L4_START' 'FGA_L4_RESULTS')
$i1 = Get-Increments $t1
$i2 = Get-Increments $t2
$i3 = Get-Increments $t3
$i4 = Get-Increments $t4

Write-Output ("S1_totals=[{0}]" -f ($t1 -join ','))
Write-Output ("S2_totals=[{0}]" -f ($t2 -join ','))
Write-Output ("S3_totals=[{0}]" -f ($t3 -join ','))
Write-Output ("S4_totals=[{0}]" -f ($t4 -join ','))

$checks = [ordered]@{
    ServerReady     = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError    = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError'
    PlatformReady   = $raw -match 'FGA_PLATFORM_OK'
    SwordGiven      = $raw -match 'FGA_SWORD_OK'
    RuleRegistered  = $raw -match 'shulkerBedrockLooting'
    S1_EnoughKills  = $i1.Count -ge 13
    S1_VanillaMaxOne = ($i1 | Where-Object { $_ -gt 1 }).Count -eq 0
    S2_EnoughKills  = $i2.Count -ge 13
    S2_BedrockMultiDrop = ($i2 | Where-Object { $_ -ge 2 }).Count -ge 1
    S2_MaxFour      = ($i2 | Where-Object { $_ -gt 4 }).Count -eq 0
    S3_EnoughKills  = $i3.Count -ge 8
    S3_NoLootMaxOne = ($i3 | Where-Object { $_ -gt 1 }).Count -eq 0
    S4_NoDrops      = ($i4 | Where-Object { $_ -ne 0 }).Count -eq 0
    CleanStop       = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$log"
Select-String -LiteralPath $log -Pattern 'shulkerBedrockLooting|FGA_L[0-9]|Mixin apply|InjectionError|Stopping server' |
    Select-Object -Last 40

if ($checks.Values -contains $false) { exit 1 }
