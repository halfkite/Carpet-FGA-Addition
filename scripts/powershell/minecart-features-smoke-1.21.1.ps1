$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$logs = @(
    (Join-Path $run 'minecart-features-smoke-phase1.log')
    (Join-Path $run 'minecart-features-smoke-phase2.log')
    (Join-Path $run 'minecart-features-smoke-phase3.log')
)
$logs | ForEach-Object { if (Test-Path -LiteralPath $_) { Remove-Item -LiteralPath $_ -Force } }

function Start-SmokeServer([string] $log) {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 > "' + $log + '" 2>&1"'
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.CreateNoWindow = $true
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void] $process.Start()
    return $process
}

function Wait-ServerReady([System.Diagnostics.Process] $process, [string] $log) {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        Start-Sleep -Seconds 2
        if ((Test-Path -LiteralPath $log) -and
            ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!')) {
            return $true
        }
    }
    return $false
}

function Send-Command([System.Diagnostics.Process] $process, [string] $command, [int] $delay = 500) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

function Stop-SmokeServer([System.Diagnostics.Process] $process) {
    Send-Command $process 'stop' 100
    if (-not $process.WaitForExit(60000)) { throw 'temporary server did not stop' }
}

function Link-Carts([System.Diagnostics.Process] $process, [double] $firstX, [double] $secondX,
                    [double] $playerX) {
    Send-Command $process "tp FGAMinecartTest $playerX 100 3"
    Send-Command $process "player FGAMinecartTest look at $firstX 100.3 0.5"
    Send-Command $process 'player FGAMinecartTest use once'
    Send-Command $process "player FGAMinecartTest look at $secondX 100.3 0.5"
    Send-Command $process 'player FGAMinecartTest use once' 800
}

$phase1 = Start-SmokeServer $logs[0]
$phase1Ready = $false
try {
    $phase1Ready = Wait-ServerReady $phase1 $logs[0]
    if (-not $phase1Ready) { throw 'phase 1 server did not become ready' }

    Send-Command $phase1 'kill @e[type=minecraft:minecart]'
    Send-Command $phase1 'kill @e[type=minecraft:item]'
    Send-Command $phase1 'fill -5 99 -5 50 99 5 minecraft:stone'
    foreach ($permission in @('false', 'true', 'ops', '0', '1', '2', '3', '4')) {
        Send-Command $phase1 "carpet minecartFeatureCommandPermission $permission" 150
    }
    Send-Command $phase1 'carpet minecartFeatureCommandPermission ops'
    Send-Command $phase1 'carpet fireworkMinecartBoost true'
    Send-Command $phase1 'carpet chainMinecartBinding true'
    Send-Command $phase1 'minecart firework set 1.2 10 1.0'
    Send-Command $phase1 'fga minecart chain set 1.0'
    Send-Command $phase1 'player FGAMinecartTest spawn at 0 100 3' 1800
    Send-Command $phase1 'gamemode survival FGAMinecartTest'

    # The status records the duration calculated from each rocket's flight component.
    foreach ($flight in @(1, 2, 3)) {
        Send-Command $phase1 'kill @e[tag=fga_boost_cart]'
        Send-Command $phase1 'summon minecraft:minecart 0.5 100 0.5 {Tags:["fga_boost_cart"]}'
        Send-Command $phase1 'ride FGAMinecartTest mount @e[type=minecraft:minecart,tag=fga_boost_cart,limit=1]'
        Send-Command $phase1 "item replace entity FGAMinecartTest weapon.mainhand with minecraft:firework_rocket[minecraft:fireworks={flight_duration:$flight,explosions:[]}] 1"
        Send-Command $phase1 'player FGAMinecartTest look east'
        Send-Command $phase1 'player FGAMinecartTest use once'
        Send-Command $phase1 "say FGA_FLIGHT_${flight}_TRIGGERED"
        Send-Command $phase1 'minecart status'
        Start-Sleep -Milliseconds ($flight * 500 + 1200)
        Send-Command $phase1 "say FGA_FLIGHT_${flight}_DECEL_DONE"
        Send-Command $phase1 'minecart status'
        Send-Command $phase1 'ride FGAMinecartTest dismount'
    }
    Send-Command $phase1 'execute unless entity @e[type=minecraft:firework_rocket] run say FGA_FIREWORK_NO_ENTITY'

    # Starting a boost and disabling the rule must clear it immediately.
    Send-Command $phase1 'kill @e[tag=fga_boost_cart]'
    Send-Command $phase1 'summon minecraft:minecart 0.5 100 0.5 {Tags:["fga_boost_cart"]}'
    Send-Command $phase1 'ride FGAMinecartTest mount @e[type=minecraft:minecart,tag=fga_boost_cart,limit=1]'
    Send-Command $phase1 'item replace entity FGAMinecartTest weapon.mainhand with minecraft:firework_rocket[minecraft:fireworks={flight_duration:3,explosions:[]}] 1'
    Send-Command $phase1 'player FGAMinecartTest use once' 800
    Send-Command $phase1 'carpet fireworkMinecartBoost false'
    Send-Command $phase1 'say FGA_BOOST_RULE_DISABLED'
    Send-Command $phase1 'minecart status'
    Send-Command $phase1 'carpet fireworkMinecartBoost true'
    Send-Command $phase1 'ride FGAMinecartTest dismount'
    Send-Command $phase1 'kill @e[tag=fga_boost_cart]'

    # Leave one paid link in SavedData and verify it after a full restart.
    Send-Command $phase1 'summon minecraft:minecart 1.5 100 0.5 {Tags:["fga_persist_a"]}'
    Send-Command $phase1 'summon minecraft:minecart 3.5 100 0.5 {Tags:["fga_persist_b"]}'
    Send-Command $phase1 'item replace entity FGAMinecartTest weapon.mainhand with minecraft:chain 16'
    Link-Carts $phase1 1.5 3.5 2.5
    Send-Command $phase1 'say FGA_LINK_BEFORE_RESTART'
    Send-Command $phase1 'minecart status'
    Send-Command $phase1 'save-all flush' 1200
    Stop-SmokeServer $phase1
} finally {
    if (-not $phase1.HasExited) { $phase1.Kill($true) }
}

$configPath = Join-Path $run 'world\config\carpetfgaaddition\minecart-features.json'
$configWritten = (Test-Path -LiteralPath $configPath) -and
    ((Get-Content -LiteralPath $configPath -Raw) -match '"max_speed"\s*:\s*1\.2')

$phase2 = Start-SmokeServer $logs[1]
$phase2Ready = $false
try {
    $phase2Ready = Wait-ServerReady $phase2 $logs[1]
    if (-not $phase2Ready) { throw 'phase 2 server did not become ready' }

    Send-Command $phase2 'carpet minecartFeatureCommandPermission ops'
    Send-Command $phase2 'carpet chainMinecartBinding true'
    Send-Command $phase2 'say FGA_LINK_AFTER_RESTART'
    Send-Command $phase2 'minecart status'
    Send-Command $phase2 'player FGAMinecartTest spawn at 2.5 100 3' 1800
    Send-Command $phase2 'gamemode survival FGAMinecartTest'
    Send-Command $phase2 'item replace entity FGAMinecartTest weapon.mainhand with minecraft:chain 16'

    Send-Command $phase2 'carpet chainMinecartBinding false'
    Send-Command $phase2 'tp @e[type=minecraft:minecart,tag=fga_persist_b,limit=1] 25.5 100 0.5' 1000
    Send-Command $phase2 'say FGA_DISABLED_LINK_PRESERVED'
    Send-Command $phase2 'minecart status'
    Send-Command $phase2 'carpet chainMinecartBinding true' 1000
    Send-Command $phase2 'say FGA_DISTANCE_LINK_BROKEN'
    Send-Command $phase2 'minecart status'
    Send-Command $phase2 'execute as @e[type=minecraft:item] if items entity @s contents minecraft:chain run say FGA_DISTANCE_CHAIN_REFUNDED'

    Send-Command $phase2 'kill @e[type=minecraft:minecart]'
    Send-Command $phase2 'kill @e[type=minecraft:item]'
    Send-Command $phase2 'minecart chain set 8.0'
    Send-Command $phase2 'summon minecraft:minecart 1.5 100 0.5 {Tags:["fga_loop_a"]}'
    Send-Command $phase2 'summon minecraft:minecart 3.5 100 0.5 {Tags:["fga_loop_b"]}'
    Send-Command $phase2 'summon minecraft:minecart 5.5 100 0.5 {Tags:["fga_loop_c"]}'
    Send-Command $phase2 'summon minecraft:minecart 7.5 100 0.5 {Tags:["fga_loop_d"]}'
    Link-Carts $phase2 1.5 3.5 2.5
    Link-Carts $phase2 3.5 5.5 4.5
    Link-Carts $phase2 5.5 1.5 3.5
    Send-Command $phase2 'say FGA_CYCLE_REJECTED'
    Send-Command $phase2 'minecart status'
    Link-Carts $phase2 3.5 7.5 5.5
    Send-Command $phase2 'say FGA_BRANCH_REJECTED'
    Send-Command $phase2 'minecart status'
    Send-Command $phase2 'kill @e[type=minecraft:minecart]'
    Send-Command $phase2 'say FGA_DESTROYED_LINKS_CLEANED'
    Send-Command $phase2 'minecart status'
    Stop-SmokeServer $phase2
} finally {
    if (-not $phase2.HasExited) { $phase2.Kill($true) }
}

$validConfig = Get-Content -LiteralPath $configPath -Raw
$invalidConfig = '{"firework":'
[System.IO.File]::WriteAllText($configPath, $invalidConfig, [System.Text.UTF8Encoding]::new($false))
$phase3 = Start-SmokeServer $logs[2]
$phase3Ready = $false
try {
    $phase3Ready = Wait-ServerReady $phase3 $logs[2]
    if (-not $phase3Ready) { throw 'phase 3 server did not become ready' }
    Send-Command $phase3 'carpet minecartFeatureCommandPermission ops'
    Send-Command $phase3 'minecart chain reset'
    Send-Command $phase3 'say FGA_INVALID_CONFIG_STATUS'
    Send-Command $phase3 'minecart status'
    Stop-SmokeServer $phase3
} finally {
    if (-not $phase3.HasExited) { $phase3.Kill($true) }
    $preservedInvalidConfig = (Get-Content -LiteralPath $configPath -Raw) -eq $invalidConfig
    [System.IO.File]::WriteAllText($configPath, $validConfig, [System.Text.UTF8Encoding]::new($false))
}

$raw = ($logs | ForEach-Object { Get-Content -LiteralPath $_ -Raw }) -join "`n"
$markers = @('FGA_FIREWORK_NO_ENTITY', 'FGA_DISTANCE_CHAIN_REFUNDED')
$missing = @($markers | Where-Object { $raw -notmatch $_ })
$flightChecks = $true
foreach ($flight in @(1, 2, 3)) {
    $flightChecks = $flightChecks -and
        ($raw -match "FGA_FLIGHT_${flight}_TRIGGERED[\s\S]{0,2000}lastBoostDuration=$($flight * 10)gt") -and
        ($raw -match "FGA_FLIGHT_${flight}_DECEL_DONE[\s\S]{0,2000}activeBoosts=0 fullBoostRemaining=0gt")
}
$ruleDisable = $raw -match 'FGA_BOOST_RULE_DISABLED[\s\S]{0,2000}activeBoosts=0 fullBoostRemaining=0gt'
$persisted = $raw -match 'FGA_LINK_AFTER_RESTART[\s\S]{0,2000}links=1 paidLinks=1'
$disabledPreserved = $raw -match 'FGA_DISABLED_LINK_PRESERVED[\s\S]{0,2000}links=1 paidLinks=1'
$distanceBroken = $raw -match 'FGA_DISTANCE_LINK_BROKEN[\s\S]{0,2000}links=0 paidLinks=0'
$cycleRejected = $raw -match 'FGA_CYCLE_REJECTED[\s\S]{0,2000}links=2 paidLinks=2'
$branchRejected = $raw -match 'FGA_BRANCH_REJECTED[\s\S]{0,2000}links=2 paidLinks=2'
$destroyClean = $raw -match 'FGA_DESTROYED_LINKS_CLEANED[\s\S]{0,2000}links=0 paidLinks=0'
$invalidConfigRejected = $raw -match 'FGA_INVALID_CONFIG_STATUS[\s\S]{0,2000}configuration invalid / 配置文件损坏'
$fgaErrors = [regex]::Matches($raw, '(?im)^.*(?:Mixin apply for mod carpet-fga-addition|InvalidMixinException|MinecartFeature.*(?:ERROR|Exception)).*$')
$parseErrors = [regex]::Matches($raw, '(?im)^.*(?:Unknown or incomplete command|Expected whitespace|Incorrect argument).*$')

Write-Output "PHASE1_READY=$phase1Ready"
Write-Output "PHASE2_READY=$phase2Ready"
Write-Output "PHASE3_READY=$phase3Ready"
Write-Output "PHASE1_EXIT=$($phase1.ExitCode)"
Write-Output "PHASE2_EXIT=$($phase2.ExitCode)"
Write-Output "PHASE3_EXIT=$($phase3.ExitCode)"
Write-Output "CONFIG_WRITTEN=$configWritten"
Write-Output "FLIGHT_CHECKS=$flightChecks"
Write-Output "RULE_DISABLE=$ruleDisable"
Write-Output "PERSISTED=$persisted"
Write-Output "DISABLED_PRESERVED=$disabledPreserved"
Write-Output "DISTANCE_BROKEN=$distanceBroken"
Write-Output "CYCLE_REJECTED=$cycleRejected"
Write-Output "BRANCH_REJECTED=$branchRejected"
Write-Output "DESTROY_CLEAN=$destroyClean"
Write-Output "INVALID_CONFIG_REJECTED=$invalidConfigRejected"
Write-Output "INVALID_CONFIG_PRESERVED=$preservedInvalidConfig"
Write-Output "MISSING_MARKERS=$($missing.Count)"
Write-Output "FGA_ERRORS=$($fgaErrors.Count)"
Write-Output "PARSE_ERRORS=$($parseErrors.Count)"
Select-String -LiteralPath $logs -Pattern 'FGA_|activeBoosts=|links=|InvalidMixin|Unknown or incomplete|Stopping server' |
    Select-Object -Last 120

if (-not $phase1Ready -or -not $phase2Ready -or -not $phase3Ready -or
    $phase1.ExitCode -ne 0 -or $phase2.ExitCode -ne 0 -or $phase3.ExitCode -ne 0 -or
    -not $configWritten -or -not $flightChecks -or -not $ruleDisable -or -not $persisted -or
    -not $disabledPreserved -or -not $distanceBroken -or -not $cycleRejected -or
    -not $branchRejected -or -not $destroyClean -or -not $invalidConfigRejected -or
    -not $preservedInvalidConfig -or $missing.Count -ne 0 -or
    $fgaErrors.Count -ne 0 -or $parseErrors.Count -ne 0) {
    exit 1
}
