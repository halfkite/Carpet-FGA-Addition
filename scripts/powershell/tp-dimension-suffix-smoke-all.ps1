param(
    [string] $VersionList = '',
    [string] $GradleJdk21 = '',
    [switch] $Offline
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\tp-dimension-suffix-smoke-all-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$toolchainArgument = ''
$offlineArgument = if ($Offline) { ' --offline' } else { '' }
if (-not [string]::IsNullOrWhiteSpace($GradleJdk21)) {
    $jdk21 = (Resolve-Path -LiteralPath $GradleJdk21).Path
    if (-not (Test-Path -LiteralPath (Join-Path $jdk21 'bin\javac.exe'))) {
        throw "Not a JDK path: $jdk21"
    }
    $toolchainArgument = ' "-Dorg.gradle.java.installations.paths=' + $jdk21 + '"'
}
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
    if ($allVersions -notcontains $version) { throw "Unsupported version requested: $version" }
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

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -eq $process) { return $true }
    $process.Refresh()
    if ($process.HasExited) { return $true }
    try { & taskkill.exe /PID $process.Id /T /F 2>&1 | Out-Null } catch { }
    try { return $process.WaitForExit(10000) } catch { return $false }
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

$results = @()

foreach ($version in $versions) {
    $startedAt = Get-Date
    $safeVersion = $version.Replace('.', '_')
    $runDir = Join-Path $root "versions\$version\run"
    $worldName = "tp-dimension-suffix-$($version.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $worldName
    $serverLog = Join-Path $reportDir "$version-server.log"
    $eulaPath = Join-Path $runDir 'eula.txt'
    $eulaBackup = "$eulaPath.before-tp-dimension-suffix-$stamp-$safeVersion"
    $hadEula = Test-Path -LiteralPath $eulaPath
    $propertiesPath = Join-Path $runDir 'server.properties'
    $propertiesBackup = "$propertiesPath.before-tp-dimension-suffix-$stamp-$safeVersion"
    $hadProperties = Test-Path -LiteralPath $propertiesPath
    $server = $null
    $serverReady = $false
    $joined = $false
    $netherConfirmed = $false
    $overworldConfirmed = $false
    $relativeXConfirmed = $false
    $cleanStop = $false
    $mixinFailure = $false
    $serverStopped = $true
    $reason = ''

    Write-ProgressLine "START $version"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    try {
        if ($hadEula) { Copy-Item -LiteralPath $eulaPath -Destination $eulaBackup }
        Set-Content -LiteralPath $eulaPath -Value 'eula=true' -Encoding ascii
        if ($hadProperties) { Copy-Item -LiteralPath $propertiesPath -Destination $propertiesBackup }
        $properties = if ($hadProperties) { [IO.File]::ReadAllText($propertiesPath) } else { '' }
        if ($properties -match '(?m)^generate-structures=') {
            $properties = [regex]::Replace($properties, '(?m)^generate-structures=.*$', 'generate-structures=false')
        } else { $properties += "`ngenerate-structures=false" }
        if ($properties -match '(?m)^level-name=') {
            $properties = [regex]::Replace($properties, '(?m)^level-name=.*$', "level-name=$worldName")
        } else { $properties += "`nlevel-name=$worldName" }
        if ($properties -match '(?m)^level-type=') {
            $properties = [regex]::Replace($properties, '(?m)^level-type=.*$', 'level-type=minecraft\:flat')
        } else { $properties += "`nlevel-type=minecraft\:flat" }
        [IO.File]::WriteAllText($propertiesPath, $properties, [Text.UTF8Encoding]::new($false))

        $env:JAVA_HOME = if ($version -like '26.*') { $jdk25 } else { $jdk21 }
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        $startInfo = [Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = 'cmd.exe'
        $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runServer --no-daemon --configure-on-demand --max-workers=1' +
            $toolchainArgument + $offlineArgument + ' --args="--port 0 --world ' + $worldName +
            '" > "' + $serverLog + '" 2>&1"'
        $startInfo.WorkingDirectory = $root
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardInput = $true
        $startInfo.CreateNoWindow = $true
        $server = [Diagnostics.Process]::new()
        $server.StartInfo = $startInfo
        [void] $server.Start()

        $readyDeadline = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $readyDeadline -and -not $server.HasExited) {
            if ((Read-Log $serverLog) -match 'Done \([0-9.]+s\)!') { $serverReady = $true; break }
            Start-Sleep -Seconds 2
        }
        if (-not $serverReady) { throw 'server did not become ready' }

        Send-Command $server 'gamerule doMobSpawning false'
        Send-Command $server 'scoreboard objectives add fga_dim_suffix dummy'
        Send-Command $server 'player FGADimSuffix spawn at 0.5 100 0.5'
        $joined = Wait-LogPattern $serverLog 'fgadimsuffix joined the game' 30
        if (-not $joined) { throw 'fake player did not join' }

        Send-Command $server 'scoreboard players set FGA_NETHER fga_dim_suffix 0'
        Send-Command $server 'execute as fgadimsuffix at @s run tp ~1 ~ ~1 minecraft:the_nether'
        Send-Command $server 'execute as fgadimsuffix at @s if dimension minecraft:the_nether run scoreboard players set FGA_NETHER fga_dim_suffix 1'
        Send-Command $server 'execute as fgadimsuffix run execute store result score FGA_RELATIVE_X fga_dim_suffix run data get entity @s Pos[0] 100'
        Send-Command $server 'scoreboard players get FGA_NETHER fga_dim_suffix'
        Send-Command $server 'scoreboard players get FGA_RELATIVE_X fga_dim_suffix'

        if (-not (Wait-LogPattern $serverLog 'FGA_NETHER has \d+ \[fga_dim_suffix\]' 30)) {
            throw 'the Nether dimension probe did not finish within 30 seconds'
        }
        if (-not (Wait-LogPattern $serverLog 'FGA_RELATIVE_X has -?\d+ \[fga_dim_suffix\]' 30)) {
            throw 'the relative-coordinate probe did not finish within 30 seconds'
        }
        $serverText = Read-Log $serverLog
        $netherConfirmed = $serverText -match 'FGA_NETHER has 1 \[fga_dim_suffix\]'
        $relativeXConfirmed = $serverText -match 'FGA_RELATIVE_X has 150 \[fga_dim_suffix\]'
        if (-not $netherConfirmed) { throw 'the /tp trailing-dimension command did not move the executor into the Nether' }
        if (-not $relativeXConfirmed) { throw 'relative coordinates did not resolve from the executor original position' }

        Send-Command $server 'scoreboard players set FGA_OVERWORLD fga_dim_suffix 0'
        Send-Command $server 'execute as fgadimsuffix at @s run teleport 20 100 20 minecraft:overworld'
        Send-Command $server 'execute as fgadimsuffix at @s if dimension minecraft:overworld run scoreboard players set FGA_OVERWORLD fga_dim_suffix 1'
        Send-Command $server 'scoreboard players get FGA_OVERWORLD fga_dim_suffix'
        if (-not (Wait-LogPattern $serverLog 'FGA_OVERWORLD has \d+ \[fga_dim_suffix\]' 30)) {
            throw 'the Overworld dimension probe did not finish within 30 seconds'
        }
        $serverText = Read-Log $serverLog
        $overworldConfirmed = $serverText -match 'FGA_OVERWORLD has 1 \[fga_dim_suffix\]'
        if (-not $overworldConfirmed) { throw 'the /teleport trailing-dimension command did not return the executor to the Overworld' }

        Send-Command $server 'stop' 100
        if (-not $server.WaitForExit(90000)) { throw 'server did not stop within 90 seconds' }
        $reason = 'both aliases changed the executor dimension; /tp relative coordinates resolved from its original position'
    } catch {
        $reason = $_.Exception.Message
    } finally {
        $serverText = Read-Log $serverLog
        if ($null -ne $server) {
            $server.Refresh()
            if (-not $server.HasExited) { $serverStopped = Stop-ProcessTree $server }
        }
        if ($serverStopped) {
            Remove-TestWorld $runDir $worldPath
            if ($hadEula -and (Test-Path -LiteralPath $eulaBackup)) {
                Move-Item -LiteralPath $eulaBackup -Destination $eulaPath -Force
            } elseif (-not $hadEula -and (Test-Path -LiteralPath $eulaPath)) {
                Remove-Item -LiteralPath $eulaPath -Force
            }
            if ($hadProperties -and (Test-Path -LiteralPath $propertiesBackup)) {
                Move-Item -LiteralPath $propertiesBackup -Destination $propertiesPath -Force
            } elseif (-not $hadProperties -and (Test-Path -LiteralPath $propertiesPath)) {
                Remove-Item -LiteralPath $propertiesPath -Force
            }
        } else {
            $reason = 'test server did not stop; its world and temporary run configuration were preserved for safety'
        }
        $mixinFailure = $serverText -match 'Mixin apply failed|InvalidMixinException|InjectionError|Critical injection failure'
        $cleanStop = $serverText -match 'Stopping server'
        $status = if ($serverStopped -and $serverReady -and $joined -and $netherConfirmed -and
            $overworldConfirmed -and $relativeXConfirmed -and $cleanStop -and -not $mixinFailure) { 'passed' } else { 'failed' }
        $results += [ordered]@{
            version = $version
            status = $status
            serverReady = $serverReady
            fakePlayerJoined = $joined
            netherConfirmed = $netherConfirmed
            overworldConfirmed = $overworldConfirmed
            relativeXConfirmed = $relativeXConfirmed
            cleanStop = $cleanStop
            mixinFailure = $mixinFailure
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status nether=$netherConfirmed overworld=$overworldConfirmed relative=$relativeXConfirmed mixinFailure=$mixinFailure reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
Get-Content -LiteralPath $summaryPath
if ($passed -ne $results.Count) { exit 1 }
