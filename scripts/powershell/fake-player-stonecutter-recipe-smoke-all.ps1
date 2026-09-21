param(
    [string] $VersionList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\fake-player-stonecutter-recipe-smoke-$stamp"
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
foreach ($version in $versions) {
    if ($allVersions -notcontains $version) {
        throw "Unsupported version requested: $version"
    }
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

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}

function Remove-TestWorld([string] $runDir, [string] $worldPath) {
    if (-not (Test-Path -LiteralPath $worldPath)) { return }
    $resolvedRun = [IO.Path]::GetFullPath($runDir).TrimEnd('\') + '\'
    $resolvedWorld = [IO.Path]::GetFullPath($worldPath)
    if (-not $resolvedWorld.StartsWith($resolvedRun, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove temporary world outside test run directory: $resolvedWorld"
    }
    Remove-Item -LiteralPath $resolvedWorld -Recurse -Force
}

function Send-Command([System.Diagnostics.Process] $server, [string] $command,
                      [int] $delay = 500) {
    $server.StandardInput.WriteLine($command)
    $server.StandardInput.Flush()
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

$results = @()

foreach ($version in $versions) {
    $startedAt = Get-Date
    $safeVersion = $version.Replace('.', '_')
    $runDir = Join-Path $root "versions\$version\run"
    $worldName = "fake-player-stonecutter-recipe-$($version.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $worldName
    $serverLog = Join-Path $reportDir "$version-server.log"
    $eulaPath = Join-Path $runDir 'eula.txt'
    $eulaBackup = "$eulaPath.before-fake-player-stonecutter-recipe-$stamp-$safeVersion"
    $server = $null
    $serverReady = $false
    $fakePlayerJoined = $false
    $status = 'failed'
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

        $readyDeadline = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $readyDeadline -and -not $server.HasExited) {
            if ((Read-Log $serverLog) -match 'Done \([0-9.]+s\)!') {
                $serverReady = $true
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $serverReady) { throw 'server did not become ready' }

        Send-Command $server 'gamerule doMobSpawning false'
        Send-Command $server 'carpet lightSourceStonecuttingRecipes true'
        Send-Command $server 'reload' 4000
        Send-Command $server 'scoreboard objectives add fga_smoke dummy'
        Send-Command $server 'setblock 0 100 0 minecraft:stonecutter'
        Send-Command $server 'player FGAStonecutter spawn at 0.5 100 2.5'
        $fakePlayerJoined = Wait-LogPattern $serverLog 'fgastonecutter joined the game' 30
        if (-not $fakePlayerJoined) { throw 'fake player did not join' }

        Send-Command $server 'player FGAStonecutter look north'
        Send-Command $server 'item replace entity FGAStonecutter weapon.mainhand with minecraft:beacon'
        Send-Command $server 'player FGAStonecutter use once' 1200
        Send-Command $server 'execute store result score FGA_RECIPE fga_smoke run recipe give FGAStonecutter carpet-fga-addition:light_level_01_15_from_light_source_stonecutting'
        Send-Command $server 'scoreboard players get FGA_RECIPE fga_smoke'
        Send-Command $server 'say FGA_STONECUTTER_RECIPE_DONE'

        Send-Command $server 'stop' 100
        if (-not $server.WaitForExit(90000)) { throw 'server did not stop within 90 seconds' }
        $status = 'passed'
        $reason = 'fake player joined and the FGA light-source stonecutting recipe was registered'
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
        $recipeRegistered = $serverText -match 'FGA_RECIPE.*\b1\b'
        $cleanStop = $serverText -match 'Stopping server'
        if ($mixinFailure -and $status -ne 'passed') { $reason = 'mixin or injection failure detected' }
        if ($status -eq 'passed' -and (-not $recipeRegistered -or -not $cleanStop)) {
            $status = 'failed'
            $reason = 'recipe marker or clean-stop marker missing'
        }

        $results += [ordered]@{
            version = $version
            status = $status
            serverReady = $serverReady
            fakePlayerJoined = $fakePlayerJoined
            recipeRegistered = $recipeRegistered
            cleanStop = $cleanStop
            mixinFailure = $mixinFailure
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status ready=$serverReady joined=$fakePlayerJoined recipe=$recipeRegistered mixinFailure=$mixinFailure reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
if ($passed -ne $results.Count) { exit 1 }
