$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\named-ender-pearl-teleport-smoke-$stamp"
$serverLog = Join-Path $reportDir 'server.log'
$summaryPath = Join-Path $reportDir 'summary.json'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$runDir = Join-Path $root 'versions\26.2\run'
$worldName = "named-ender-pearl-teleport-$stamp"
$worldPath = Join-Path $runDir $worldName
$eulaPath = Join-Path $runDir 'eula.txt'
$eulaBackup = "$eulaPath.before-named-ender-pearl-$stamp"
$server = $null
$serverReady = $false
$targetMoved = $false
$targetWentOffline = $false
$offlineThrowerDidNotMove = $false
$reason = ''

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Send-Command([System.Diagnostics.Process] $process, [string] $command,
                      [int] $delay = 500) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}

function Wait-LogPattern([string] $path, [string] $pattern,
                         [int] $timeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Log $path) -match $pattern) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Remove-TestWorld([string] $runRoot, [string] $testWorld) {
    if (-not (Test-Path -LiteralPath $testWorld)) { return }
    $resolvedRoot = [IO.Path]::GetFullPath($runRoot).TrimEnd('\') + '\'
    $resolvedWorld = [IO.Path]::GetFullPath($testWorld)
    if (-not $resolvedWorld.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove temporary world outside test run directory: $resolvedWorld"
    }
    Remove-Item -LiteralPath $resolvedWorld -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
if (Test-Path -LiteralPath $eulaPath) {
    Copy-Item -LiteralPath $eulaPath -Destination $eulaBackup
}
Set-Content -LiteralPath $eulaPath -Value 'eula=true' -Encoding ascii

try {
    $env:JAVA_HOME = $jdk25
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :26.2:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0 --world ' +
        $worldName + '" > "' + $serverLog + '" 2>&1"'
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.CreateNoWindow = $true
    $server = [Diagnostics.Process]::new()
    $server.StartInfo = $startInfo
    [void] $server.Start()

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Read-Log $serverLog) -match 'Done \([0-9.]+s\)!') {
            $serverReady = $true
            break
        }
        Start-Sleep -Seconds 2
    }
    if (-not $serverReady) { throw 'server did not become ready' }

    Send-Command $server 'carpet namedEnderPearlTeleport true'
    Send-Command $server 'scoreboard objectives add fga_named_pearl dummy'
    Send-Command $server 'player 2 spawn at 0.5 100 0.5'
    Send-Command $server 'player 1 spawn at 20.5 100 0.5'
    if (-not (Wait-LogPattern $serverLog '1 joined the game')) { throw 'target fake player 1 did not join' }
    if (-not (Wait-LogPattern $serverLog '2 joined the game')) { throw 'thrower fake player 2 did not join' }
    Send-Command $server 'fill 3 50 0 3 105 0 minecraft:stone'
    Send-Command $server 'execute if block 3 101 0 minecraft:stone run say FGA_WALL_EXISTS'
    Send-Command $server 'player 2 look east'
    Send-Command $server 'item replace entity 2 weapon.mainhand with minecraft:ender_pearl[minecraft:custom_name={text:"1"}]'
    Send-Command $server 'data get entity 2 SelectedItem'
    Send-Command $server 'execute store result score FGA_TARGET_1_X fga_named_pearl run data get entity 1 Pos[0] 100'
    Send-Command $server 'scoreboard players get FGA_TARGET_1_X fga_named_pearl'
    Send-Command $server 'player 2 use once' 200
    Send-Command $server 'execute as @e[type=minecraft:ender_pearl] run data get entity @s'
    Start-Sleep -Seconds 5
    Send-Command $server 'execute if entity @e[type=minecraft:ender_pearl] run say FGA_MATCH_PEARL_EXISTS'
    Send-Command $server 'execute store result score FGA_TARGET_1_X fga_named_pearl run data get entity 1 Pos[0] 100'
    Send-Command $server 'scoreboard players get FGA_TARGET_1_X fga_named_pearl'

    $serverText = Read-Log $serverLog
    $scores = [regex]::Matches($serverText, 'FGA_TARGET_1_X has (-?\d+)') |
        ForEach-Object { [int]$_.Groups[1].Value }
    if ($scores.Count -lt 2) { throw 'could not read both target 1 X scoreboard values' }
    $initialX = $scores[$scores.Count - 2]
    $finalX = $scores[$scores.Count - 1]
    $targetMoved = [math]::Abs($finalX - $initialX) -ge 100
    if (-not $targetMoved) { throw "target 1 did not move: initial=$initialX final=$finalX" }

    Send-Command $server 'player 1 kill'
    if (-not (Wait-LogPattern $serverLog '1 left the game')) { throw 'target fake player 1 did not go offline' }
    $targetWentOffline = $true
    Send-Command $server 'tp 2 0.5 100 0.5'
    Send-Command $server 'item replace entity 2 weapon.mainhand with minecraft:ender_pearl[minecraft:custom_name={text:"1"}]'
    Send-Command $server 'execute store result score FGA_OFFLINE_THROWER_2_X fga_named_pearl run data get entity 2 Pos[0] 100'
    Send-Command $server 'scoreboard players get FGA_OFFLINE_THROWER_2_X fga_named_pearl'
    Send-Command $server 'player 2 use once' 200
    Start-Sleep -Seconds 5
    Send-Command $server 'execute store result score FGA_OFFLINE_THROWER_2_X fga_named_pearl run data get entity 2 Pos[0] 100'
    Send-Command $server 'scoreboard players get FGA_OFFLINE_THROWER_2_X fga_named_pearl'
    $serverText = Read-Log $serverLog
    $offlineScores = [regex]::Matches($serverText, 'FGA_OFFLINE_THROWER_2_X has (-?\d+)') |
        ForEach-Object { [int]$_.Groups[1].Value }
    if ($offlineScores.Count -lt 2) { throw 'could not read both offline thrower 2 X scoreboard values' }
    $offlineInitialX = $offlineScores[$offlineScores.Count - 2]
    $offlineFinalX = $offlineScores[$offlineScores.Count - 1]
    $offlineThrowerDidNotMove = [math]::Abs($offlineFinalX - $offlineInitialX) -lt 100
    if (-not $offlineThrowerDidNotMove) {
        throw "offline target named pearl teleported thrower 2: initial=$offlineInitialX final=$offlineFinalX"
    }
    Send-Command $server 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'server did not stop within 90 seconds' }
    $reason = "target 1 moved $initialX->$finalX; target 1 went offline; thrower 2 stayed $offlineInitialX->$offlineFinalX"
} catch {
    $reason = $_.Exception.Message
} finally {
    $serverText = Read-Log $serverLog
    if ($null -ne $server -and -not $server.HasExited) {
        Stop-ProcessTree $server
    }
    Remove-TestWorld $runDir $worldPath
    if (Test-Path -LiteralPath $eulaPath) { Remove-Item -LiteralPath $eulaPath -Force }
    if (Test-Path -LiteralPath $eulaBackup) {
        Move-Item -LiteralPath $eulaBackup -Destination $eulaPath
    }

    $mixinFailure = $serverText -match 'Mixin apply failed|InvalidMixinException|InjectionError|Critical injection failure'
    $cleanStop = $serverText -match 'Stopping server'
    $result = [ordered]@{
        version = '26.2'
        status = if ($serverReady -and $targetMoved -and $targetWentOffline -and $offlineThrowerDidNotMove -and $cleanStop -and -not $mixinFailure) { 'passed' } else { 'failed' }
        serverReady = $serverReady
        targetMoved = $targetMoved
        targetWentOffline = $targetWentOffline
        offlineThrowerDidNotMove = $offlineThrowerDidNotMove
        cleanStop = $cleanStop
        mixinFailure = $mixinFailure
        reason = $reason
        serverLog = 'server.log'
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Get-Content -LiteralPath $summaryPath
    if ($result.status -ne 'passed') { exit 1 }
}
