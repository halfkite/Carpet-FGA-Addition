$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$logs = @(
    (Join-Path $run 'vehicle-stop-smoke-phase1.log')
    (Join-Path $run 'vehicle-stop-smoke-phase2.log')
    (Join-Path $run 'vehicle-stop-smoke-phase3.log')
)
$logs | ForEach-Object { if (Test-Path -LiteralPath $_) { Remove-Item -LiteralPath $_ -Force } }
$configPath = Join-Path $run 'world\config\carpetfgaaddition\vehicle-stop.json'
$originalExists = Test-Path -LiteralPath $configPath
$originalConfig = if ($originalExists) { Get-Content -LiteralPath $configPath -Raw } else { $null }

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

function Send-Command([System.Diagnostics.Process] $process, [string] $command, [int] $delay = 450) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

function Stop-SmokeServer([System.Diagnostics.Process] $process) {
    Send-Command $process 'stop' 100
    if (-not $process.WaitForExit(60000)) { throw 'temporary server did not stop' }
}

function Link-Carts([System.Diagnostics.Process] $process, [double] $firstX, [double] $secondX) {
    Send-Command $process 'tp FGAVehicleDriver 2.5 100 2.5'
    Send-Command $process "player FGAVehicleDriver look at $firstX 100.3 0.5"
    Send-Command $process 'player FGAVehicleDriver use once'
    Send-Command $process "player FGAVehicleDriver look at $secondX 100.3 0.5"
    Send-Command $process 'player FGAVehicleDriver use once' 800
}

function Assert-Motion([System.Diagnostics.Process] $process, [string] $tag,
                       [int] $x, [int] $y, [int] $z, [string] $marker) {
    Send-Command $process "say $marker" 120
    Send-Command $process 'vehicleStop player FGAVehicleDriver status'
}

$phase1 = Start-SmokeServer $logs[0]
$phase1Ready = $false
try {
    $phase1Ready = Wait-ServerReady $phase1 $logs[0]
    if (-not $phase1Ready) { throw 'phase 1 server did not become ready' }
    Send-Command $phase1 'kill @e[type=minecraft:minecart]'
    Send-Command $phase1 'kill @e[type=minecraft:boat]'
    Send-Command $phase1 'kill @e[type=minecraft:chest_boat]'
    Send-Command $phase1 'fill -10 99 -10 40 99 10 minecraft:stone'
    Send-Command $phase1 'player FGAVehicleDriver spawn at 0 100 3' 3000
    Send-Command $phase1 'player FGAVehPassenger spawn at 0 100 5' 3000
    Send-Command $phase1 'vehicleStop player FGAVehicleDriver reset'
    Send-Command $phase1 'vehicleStop player FGAVehPassenger reset'
    Send-Command $phase1 'tick freeze'

    Send-Command $phase1 'summon minecraft:minecart 0.5 100 0.5 {Tags:["fga_mode_cart"]}'
    Send-Command $phase1 'carpet vehicleStopOnDismount false'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_mode_cart,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_mode_cart,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_mode_cart' 1000 250 500 'FGA_FALSE_MINECART_PRESERVED'

    Send-Command $phase1 'carpet vehicleStopOnDismount boat'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_mode_cart,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_mode_cart,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_mode_cart' 1000 250 500 'FGA_BOAT_MODE_MINECART_PRESERVED'

    Send-Command $phase1 'carpet vehicleStopOnDismount minecart'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_mode_cart,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_mode_cart,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_mode_cart' 0 250 0 'FGA_MINECART_STOPPED_HORIZONTAL'

    Send-Command $phase1 'summon minecraft:boat 5.5 100 0.5 {Type:"oak",Tags:["fga_boat"]}'
    Send-Command $phase1 'carpet vehicleStopOnDismount all'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_boat,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_boat,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_boat' 0 250 0 'FGA_ALL_BOAT_STOPPED'

    Send-Command $phase1 'summon minecraft:chest_boat 8.5 100 0.5 {Type:"oak",Tags:["fga_chest_boat"]}'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_chest_boat,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_chest_boat,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_chest_boat' 0 250 0 'FGA_CHEST_BOAT_STOPPED'

    Send-Command $phase1 'summon minecraft:boat 11.5 100 0.5 {Type:"bamboo",Tags:["fga_raft"]}'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_raft,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_raft,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_raft' 0 250 0 'FGA_RAFT_STOPPED'

    Send-Command $phase1 'carpet vehicleStopOnDismount custom'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_mode_cart,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_mode_cart,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_mode_cart' 1000 250 500 'FGA_CUSTOM_DEFAULT_PRESERVED'
    Send-Command $phase1 'execute as FGAVehicleDriver run vehicleStop set minecart true'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_mode_cart,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_mode_cart,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_mode_cart' 0 250 0 'FGA_CUSTOM_MINECART_STOPPED'
    Send-Command $phase1 'vehicleStop player FGAVehicleDriver set all true'
    Send-Command $phase1 'vehicleStop player FGAVehPassenger set all true'
    Send-Command $phase1 'fga vehicleStop player FGAVehicleDriver status'

    Send-Command $phase1 'summon minecraft:boat 14.5 100 0.5 {Type:"oak",Tags:["fga_two_player_boat"]}'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_two_player_boat,limit=1]'
    Send-Command $phase1 'ride FGAVehPassenger mount @e[tag=fga_two_player_boat,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_two_player_boat,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'ride FGAVehPassenger dismount'
    Assert-Motion $phase1 'fga_two_player_boat' 1000 250 500 'FGA_PASSENGER_DISMOUNT_PRESERVED'
    Send-Command $phase1 'ride FGAVehPassenger mount @e[tag=fga_two_player_boat,limit=1]'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_two_player_boat' 1000 250 500 'FGA_DRIVER_WITH_PASSENGER_PRESERVED'
    Send-Command $phase1 'ride FGAVehPassenger dismount'
    Assert-Motion $phase1 'fga_two_player_boat' 0 250 0 'FGA_LAST_BOAT_DRIVER_STOPPED'

    Send-Command $phase1 'tick unfreeze'
    Send-Command $phase1 'kill @e[type=minecraft:minecart]'
    Send-Command $phase1 'carpet chainMinecartBinding true'
    Send-Command $phase1 'carpet minecartFeatureCommandPermission ops'
    Send-Command $phase1 'minecart chain set 8.0'
    Send-Command $phase1 'summon minecraft:minecart 1.5 100 0.5 {Tags:["fga_train_a"]}'
    Send-Command $phase1 'summon minecraft:minecart 3.5 100 0.5 {Tags:["fga_train_b"]}'
    Send-Command $phase1 'item replace entity FGAVehicleDriver weapon.mainhand with minecraft:chain 4'
    Link-Carts $phase1 1.5 3.5
    Send-Command $phase1 'minecart status'
    Send-Command $phase1 'tick freeze'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_train_a,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_train_a,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'data merge entity @e[tag=fga_train_b,limit=1] {Motion:[-1.0d,-0.25d,-0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_train_a' 0 250 0 'FGA_EMPTY_TRAIN_A_STOPPED'
    Assert-Motion $phase1 'fga_train_b' 0 -250 0 'FGA_EMPTY_TRAIN_B_STOPPED'

    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_train_a,limit=1]'
    Send-Command $phase1 'ride FGAVehPassenger mount @e[tag=fga_train_b,limit=1]'
    Send-Command $phase1 'data merge entity @e[tag=fga_train_a,limit=1] {Motion:[1.0d,0.25d,0.5d]}'
    Send-Command $phase1 'data merge entity @e[tag=fga_train_b,limit=1] {Motion:[-1.0d,-0.25d,-0.5d]}'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Assert-Motion $phase1 'fga_train_a' 1000 250 500 'FGA_OCCUPIED_TRAIN_A_PRESERVED'
    Assert-Motion $phase1 'fga_train_b' -1000 -250 -500 'FGA_OCCUPIED_TRAIN_B_PRESERVED'
    Send-Command $phase1 'ride FGAVehPassenger dismount'
    Assert-Motion $phase1 'fga_train_a' 0 250 0 'FGA_LAST_TRAIN_A_STOPPED'
    Assert-Motion $phase1 'fga_train_b' 0 -250 0 'FGA_LAST_TRAIN_B_STOPPED'
    Send-Command $phase1 'tick unfreeze'

    Send-Command $phase1 'kill @e[type=minecraft:minecart]'
    Send-Command $phase1 'summon minecraft:minecart 0.5 100 0.5 {Tags:["fga_boost_stop"]}'
    Send-Command $phase1 'carpet fireworkMinecartBoost true'
    Send-Command $phase1 'carpet vehicleStopOnDismount minecart'
    Send-Command $phase1 'ride FGAVehicleDriver mount @e[tag=fga_boost_stop,limit=1]'
    Send-Command $phase1 'item replace entity FGAVehicleDriver weapon.mainhand with minecraft:firework_rocket[minecraft:fireworks={flight_duration:3,explosions:[]}] 1'
    Send-Command $phase1 'player FGAVehicleDriver look east'
    Send-Command $phase1 'player FGAVehicleDriver use once' 600
    Send-Command $phase1 'say FGA_BOOST_BEFORE_DISMOUNT'
    Send-Command $phase1 'minecart status'
    Send-Command $phase1 'ride FGAVehicleDriver dismount'
    Send-Command $phase1 'say FGA_BOOST_AFTER_DISMOUNT'
    Send-Command $phase1 'minecart status'

    Send-Command $phase1 'save-all flush' 1000
    Stop-SmokeServer $phase1
} finally {
    if (-not $phase1.HasExited) { $phase1.Kill($true) }
}

$configWritten = (Test-Path -LiteralPath $configPath) -and
    ((Get-Content -LiteralPath $configPath -Raw) -match '"minecart"\s*:\s*true') -and
    ((Get-Content -LiteralPath $configPath -Raw) -match '"boat"\s*:\s*true')

$phase2 = Start-SmokeServer $logs[1]
$phase2Ready = $false
try {
    $phase2Ready = Wait-ServerReady $phase2 $logs[1]
    if (-not $phase2Ready) { throw 'phase 2 server did not become ready' }
    Send-Command $phase2 'player FGAVehicleDriver spawn at 0 100 3' 1600
    Send-Command $phase2 'carpet vehicleStopOnDismount custom'
    Send-Command $phase2 'say FGA_PERSISTED_CONFIG_STATUS'
    Send-Command $phase2 'vehicleStop player FGAVehicleDriver status'
    Stop-SmokeServer $phase2
} finally {
    if (-not $phase2.HasExited) { $phase2.Kill($true) }
}

$validConfig = Get-Content -LiteralPath $configPath -Raw
$invalidConfig = '{"players":'
[System.IO.File]::WriteAllText($configPath, $invalidConfig, [System.Text.UTF8Encoding]::new($false))
$phase3 = Start-SmokeServer $logs[2]
$phase3Ready = $false
try {
    $phase3Ready = Wait-ServerReady $phase3 $logs[2]
    if (-not $phase3Ready) { throw 'phase 3 server did not become ready' }
    Send-Command $phase3 'player FGAVehicleDriver spawn at 0 100 3' 1600
    Send-Command $phase3 'vehicleStop player FGAVehicleDriver set all true'
    Send-Command $phase3 'say FGA_INVALID_CONFIG_STATUS'
    Send-Command $phase3 'vehicleStop player FGAVehicleDriver status'
    Stop-SmokeServer $phase3
} finally {
    if (-not $phase3.HasExited) { $phase3.Kill($true) }
    $invalidPreserved = (Get-Content -LiteralPath $configPath -Raw) -eq $invalidConfig
    if ($originalExists) {
        [System.IO.File]::WriteAllText($configPath, $originalConfig, [System.Text.UTF8Encoding]::new($false))
    } elseif (Test-Path -LiteralPath $configPath) {
        Remove-Item -LiteralPath $configPath -Force
    }
}

$raw = ($logs | ForEach-Object { Get-Content -LiteralPath $_ -Raw }) -join "`n"
$markers = @(
    'FGA_FALSE_MINECART_PRESERVED', 'FGA_BOAT_MODE_MINECART_PRESERVED',
    'FGA_MINECART_STOPPED_HORIZONTAL', 'FGA_ALL_BOAT_STOPPED', 'FGA_CHEST_BOAT_STOPPED',
    'FGA_RAFT_STOPPED', 'FGA_CUSTOM_DEFAULT_PRESERVED', 'FGA_CUSTOM_MINECART_STOPPED',
    'FGA_PASSENGER_DISMOUNT_PRESERVED', 'FGA_DRIVER_WITH_PASSENGER_PRESERVED',
    'FGA_LAST_BOAT_DRIVER_STOPPED', 'FGA_EMPTY_TRAIN_A_STOPPED', 'FGA_EMPTY_TRAIN_B_STOPPED',
    'FGA_OCCUPIED_TRAIN_A_PRESERVED', 'FGA_OCCUPIED_TRAIN_B_PRESERVED',
    'FGA_LAST_TRAIN_A_STOPPED', 'FGA_LAST_TRAIN_B_STOPPED'
)
$missing = @($markers | Where-Object { $raw -notmatch $_ })
$disabledChecks = ($raw -match 'FGA_FALSE_MINECART_PRESERVED[\s\S]{0,1800}lastAction=minecart:disabled') -and
    ($raw -match 'FGA_BOAT_MODE_MINECART_PRESERVED[\s\S]{0,1800}lastAction=minecart:disabled') -and
    ($raw -match 'FGA_CUSTOM_DEFAULT_PRESERVED[\s\S]{0,1800}lastAction=minecart:disabled')
$minecartStopChecks = ($raw -match 'FGA_MINECART_STOPPED_HORIZONTAL[\s\S]{0,1800}lastAction=minecart:stopped:vehicles=1:hadHorizontalMotion=true:horizontalStopped=true:verticalPreserved=true') -and
    ($raw -match 'FGA_CUSTOM_MINECART_STOPPED[\s\S]{0,1800}lastAction=minecart:stopped:vehicles=1:hadHorizontalMotion=true:horizontalStopped=true:verticalPreserved=true')
$boatStopChecks = ($raw -match 'FGA_ALL_BOAT_STOPPED[\s\S]{0,1800}lastAction=boat:stopped:vehicles=1:hadHorizontalMotion=true:horizontalStopped=true:verticalPreserved=true') -and
    ($raw -match 'FGA_CHEST_BOAT_STOPPED[\s\S]{0,1800}lastAction=boat:stopped:vehicles=1:hadHorizontalMotion=true:horizontalStopped=true:verticalPreserved=true') -and
    ($raw -match 'FGA_RAFT_STOPPED[\s\S]{0,1800}lastAction=boat:stopped:vehicles=1:hadHorizontalMotion=true:horizontalStopped=true:verticalPreserved=true')
$passengerChecks = ($raw -match 'FGA_PASSENGER_DISMOUNT_PRESERVED[\s\S]{0,1800}lastAction=minecart:stopped') -and
    ($raw -match 'FGA_DRIVER_WITH_PASSENGER_PRESERVED[\s\S]{0,1800}lastAction=boat:occupied:motionPreserved=true') -and
    ($raw -match 'FGA_LAST_BOAT_DRIVER_STOPPED[\s\S]{0,1800}lastAction=boat:stopped:vehicles=1')
$trainChecks = ($raw -match 'FGA_EMPTY_TRAIN_A_STOPPED[\s\S]{0,1800}lastAction=minecart:stopped:vehicles=2') -and
    ($raw -match 'FGA_EMPTY_TRAIN_B_STOPPED[\s\S]{0,1800}lastAction=minecart:stopped:vehicles=2') -and
    ($raw -match 'FGA_OCCUPIED_TRAIN_A_PRESERVED[\s\S]{0,1800}lastAction=minecart:occupied:vehicles=2:motionPreserved=true') -and
    ($raw -match 'FGA_OCCUPIED_TRAIN_B_PRESERVED[\s\S]{0,1800}lastAction=minecart:occupied:vehicles=2:motionPreserved=true') -and
    ($raw -match 'FGA_LAST_TRAIN_A_STOPPED[\s\S]{0,1800}lastAction=minecart:stopped:vehicles=2') -and
    ($raw -match 'FGA_LAST_TRAIN_B_STOPPED[\s\S]{0,1800}lastAction=minecart:stopped:vehicles=2')
$linkCreated = $raw -match 'chain=true maxDistance=8; links=1 paidLinks=[01]'
$boostStarted = $raw -match 'FGA_BOOST_BEFORE_DISMOUNT[\s\S]{0,2000}activeBoosts=1'
$boostStopped = $raw -match 'FGA_BOOST_AFTER_DISMOUNT[\s\S]{0,2000}activeBoosts=0'
$persisted = $raw -match 'FGA_PERSISTED_CONFIG_STATUS[\s\S]{0,2400}personal minecart=true boat=true[\s\S]{0,600}effective minecart=true boat=true'
$invalidRejected = $raw -match 'FGA_INVALID_CONFIG_STATUS[\s\S]{0,2400}configuration invalid'
$fgaErrors = [regex]::Matches($raw, '(?im)^.*(?:Mixin apply for mod carpet-fga-addition|InvalidMixinException|VehicleStop.*(?:ERROR|Exception)).*$')
$parseErrors = [regex]::Matches($raw, '(?im)^.*(?:Unknown or incomplete command|Expected whitespace|Incorrect argument).*$')

Write-Output "PHASE1_READY=$phase1Ready"
Write-Output "PHASE2_READY=$phase2Ready"
Write-Output "PHASE3_READY=$phase3Ready"
Write-Output "PHASE1_EXIT=$($phase1.ExitCode)"
Write-Output "PHASE2_EXIT=$($phase2.ExitCode)"
Write-Output "PHASE3_EXIT=$($phase3.ExitCode)"
Write-Output "CONFIG_WRITTEN=$configWritten"
Write-Output "MISSING_MARKERS=$($missing.Count)"
Write-Output "DISABLED_CHECKS=$disabledChecks"
Write-Output "MINECART_STOP_CHECKS=$minecartStopChecks"
Write-Output "BOAT_STOP_CHECKS=$boatStopChecks"
Write-Output "PASSENGER_CHECKS=$passengerChecks"
Write-Output "TRAIN_CHECKS=$trainChecks"
Write-Output "LINK_CREATED=$linkCreated"
Write-Output "BOOST_STARTED=$boostStarted"
Write-Output "BOOST_STOPPED=$boostStopped"
Write-Output "PERSISTED=$persisted"
Write-Output "INVALID_REJECTED=$invalidRejected"
Write-Output "INVALID_PRESERVED=$invalidPreserved"
Write-Output "FGA_ERRORS=$($fgaErrors.Count)"
Write-Output "PARSE_ERRORS=$($parseErrors.Count)"
Select-String -LiteralPath $logs -Pattern 'FGA_|vehicleStop|activeBoosts=|InvalidMixin|Unknown or incomplete|Stopping server' |
    Select-Object -Last 140

if (-not $phase1Ready -or -not $phase2Ready -or -not $phase3Ready -or
    $phase1.ExitCode -ne 0 -or $phase2.ExitCode -ne 0 -or $phase3.ExitCode -ne 0 -or
    -not $configWritten -or $missing.Count -ne 0 -or -not $disabledChecks -or
    -not $minecartStopChecks -or -not $boatStopChecks -or -not $passengerChecks -or
    -not $trainChecks -or -not $linkCreated -or -not $boostStarted -or -not $boostStopped -or
    -not $persisted -or -not $invalidRejected -or -not $invalidPreserved -or
    $fgaErrors.Count -ne 0 -or $parseErrors.Count -ne 0) {
    exit 1
}
