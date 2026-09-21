param(
    [string] $VersionList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\named-ender-pearl-teleport-smoke-all-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$allVersions = @(
    '1.21.1', '1.21.3', '1.21.4', '1.21.5',
    '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2', '26.3'
)
$versions = if ([string]::IsNullOrWhiteSpace($VersionList)) {
    $allVersions
} else {
    @($VersionList.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Write-ProgressLine([string] $message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -LiteralPath $progressPath -Value $line -Encoding utf8
    Write-Host $line
}

function Send-Command([System.Diagnostics.Process] $process, [string] $command,
                      [int] $delay = 500) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
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

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
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

function PearlComponent([string] $version) {
    if ($version -in @('1.21.1', '1.21.3', '1.21.4')) {
        return 'minecraft:ender_pearl[minecraft:custom_name=''{"text":"1"}'']'
    }
    return 'minecraft:ender_pearl[minecraft:custom_name={text:"1"}]'
}

$results = @()

foreach ($version in $versions) {
    $startedAt = Get-Date
    $runDir = Join-Path $root "versions\$version\run"
    $worldName = "named-ender-pearl-teleport-$($version.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $worldName
    $eulaPath = Join-Path $runDir 'eula.txt'
    $eulaBackup = "$eulaPath.before-named-ender-pearl-$stamp"
    $serverLog = Join-Path $reportDir "$version-server.log"
    $server = $null
    $serverReady = $false
    $targetMoved = $false
    $targetWentOffline = $false
    $offlineThrowerDidNotMove = $false
    $cleanStop = $false
    $mixinFailure = $false
    $reason = ''

    Write-ProgressLine "START $version"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    if (Test-Path -LiteralPath $eulaPath) {
        Copy-Item -LiteralPath $eulaPath -Destination $eulaBackup
    }
    Set-Content -LiteralPath $eulaPath -Value 'eula=true' -Encoding ascii

    try {
        $env:JAVA_HOME = if ($version -like '26.*') { $jdk25 } else { $jdk21 }
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        $startInfo = [Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = 'cmd.exe'
        $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0 --world ' +
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

        $pearl = PearlComponent $version
        Send-Command $server 'carpet namedEnderPearlTeleport true'
        Send-Command $server 'scoreboard objectives add fga_named_pearl dummy'
        Send-Command $server 'player 2 spawn at 0.5 100 0.5'
        Send-Command $server 'player 1 spawn at 20.5 100 0.5'
        if (-not (Wait-LogPattern $serverLog '1 joined the game')) { throw 'target fake player 1 did not join' }
        if (-not (Wait-LogPattern $serverLog '2 joined the game')) { throw 'thrower fake player 2 did not join' }
        Send-Command $server 'fill 3 50 0 3 105 0 minecraft:stone'
        Send-Command $server 'player 2 look east'
        Send-Command $server "item replace entity 2 weapon.mainhand with $pearl"
        Send-Command $server 'execute store result score FGA_TARGET_1_X fga_named_pearl run data get entity 1 Pos[0] 100'
        Send-Command $server 'scoreboard players get FGA_TARGET_1_X fga_named_pearl'
        Send-Command $server 'player 2 use once' 200
        Start-Sleep -Seconds 5
        Send-Command $server 'execute store result score FGA_TARGET_1_X fga_named_pearl run data get entity 1 Pos[0] 100'
        Send-Command $server 'scoreboard players get FGA_TARGET_1_X fga_named_pearl'

        $serverText = Read-Log $serverLog
        $targetScores = [regex]::Matches($serverText, 'FGA_TARGET_1_X has (-?\d+)') |
            ForEach-Object { [int]$_.Groups[1].Value }
        if ($targetScores.Count -lt 2) { throw 'could not read both target 1 X scoreboard values' }
        $targetInitialX = $targetScores[$targetScores.Count - 2]
        $targetFinalX = $targetScores[$targetScores.Count - 1]
        $targetMoved = [math]::Abs($targetFinalX - $targetInitialX) -ge 100
        if (-not $targetMoved) { throw "target 1 did not move: initial=$targetInitialX final=$targetFinalX" }

        Send-Command $server 'player 1 kill'
        if (-not (Wait-LogPattern $serverLog '1 left the game')) { throw 'target fake player 1 did not go offline' }
        $targetWentOffline = $true
        Send-Command $server 'tp 2 0.5 100 0.5'
        Send-Command $server "item replace entity 2 weapon.mainhand with $pearl"
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
        $reason = "target 1 moved $targetInitialX->$targetFinalX; target 1 went offline; thrower 2 stayed $offlineInitialX->$offlineFinalX"
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
        $status = if ($serverReady -and $targetMoved -and $targetWentOffline -and $offlineThrowerDidNotMove -and $cleanStop -and -not $mixinFailure) { 'passed' } else { 'failed' }
        $results += [ordered]@{
            version = $version
            status = $status
            serverReady = $serverReady
            targetMoved = $targetMoved
            targetWentOffline = $targetWentOffline
            offlineThrowerDidNotMove = $offlineThrowerDidNotMove
            cleanStop = $cleanStop
            mixinFailure = $mixinFailure
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
Get-Content -LiteralPath $summaryPath
if ($passed -ne $results.Count) { exit 1 }
