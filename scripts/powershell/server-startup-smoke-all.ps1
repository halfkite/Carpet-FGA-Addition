param(
    [string] $VersionList = '',
    [string] $CommandList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\server-startup-smoke-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$allVersions = @(
    '1.21.1', '1.21.3', '1.21.4', '1.21.5',
    '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2'
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

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}

function Remove-TemporaryWorld([string] $runDir, [string] $worldPath) {
    if (-not (Test-Path -LiteralPath $worldPath)) {
        return
    }
    $resolvedRun = [IO.Path]::GetFullPath($runDir).TrimEnd('\') + '\'
    $resolvedWorld = [IO.Path]::GetFullPath($worldPath)
    if (-not $resolvedWorld.StartsWith($resolvedRun, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove temporary world outside run directory: $resolvedWorld"
    }
    Remove-Item -LiteralPath $resolvedWorld -Recurse -Force
}

$results = @()

foreach ($version in $versions) {
    $startedAt = Get-Date
    $runDir = Join-Path $root "versions\$version\run"
    $serverLog = Join-Path $reportDir "$version-server.log"
    $levelName = "server-startup-$($version.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $levelName
    $savedFiles = @('eula.txt', 'server.properties')
    $backups = @{}
    $server = $null
    $ready = $false
    $stoppedCleanly = $false
    $reason = ''

    Write-ProgressLine "START $version"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    foreach ($name in $savedFiles) {
        $path = Join-Path $runDir $name
        if (Test-Path -LiteralPath $path) {
            $backup = "$path.before-server-startup-$stamp"
            Move-Item -LiteralPath $path -Destination $backup
            $backups[$name] = $backup
        }
    }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false'
        'server-port=0'
        'view-distance=2'
        'simulation-distance=2'
        "level-name=$levelName"
        'difficulty=peaceful'
    )

    try {
        $env:JAVA_HOME = if ($version -like '26.*') { $jdk25 } else { $jdk21 }
        $serverInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $serverInfo.FileName = 'cmd.exe'
        $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runServer --no-daemon --configure-on-demand --max-workers=1 > "' +
            $serverLog + '" 2>&1"'
        $serverInfo.WorkingDirectory = $root
        $serverInfo.UseShellExecute = $false
        $serverInfo.RedirectStandardInput = $true
        $serverInfo.CreateNoWindow = $true
        $server = [System.Diagnostics.Process]::new()
        $server.StartInfo = $serverInfo
        [void] $server.Start()

        $deadline = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
            if ((Read-Log $serverLog) -match 'Done \([0-9.]+s\)!') {
                $ready = $true
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $ready) {
            throw 'temporary dedicated server did not become ready'
        }

        if (-not [string]::IsNullOrWhiteSpace($CommandList)) {
            foreach ($command in $CommandList.Split(';;')) {
                $trimmed = $command.Trim()
                if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
                    Write-ProgressLine "COMMAND $version $trimmed"
                    $server.StandardInput.WriteLine($trimmed)
                    $server.StandardInput.Flush()
                    Start-Sleep -Milliseconds 500
                }
            }
        }

        $server.StandardInput.WriteLine('stop')
        $server.StandardInput.Flush()
        $stoppedCleanly = $server.WaitForExit(60000)
        if (-not $stoppedCleanly) {
            throw 'temporary dedicated server did not stop within 60 seconds'
        }
        $reason = 'server reached Done and stopped cleanly'
    } catch {
        $reason = $_.Exception.Message
    } finally {
        if ($null -ne $server -and -not $server.HasExited) {
            Stop-ProcessTree $server
        }

        foreach ($name in $savedFiles) {
            $path = Join-Path $runDir $name
            if (Test-Path -LiteralPath $path) {
                Remove-Item -LiteralPath $path -Force
            }
            if ($backups.ContainsKey($name) -and (Test-Path -LiteralPath $backups[$name])) {
                Move-Item -LiteralPath $backups[$name] -Destination $path
            }
        }
        Remove-TemporaryWorld $runDir $worldPath

        $status = if ($ready -and $stoppedCleanly) { 'passed' } else { 'failed' }
        $results += [ordered]@{
            version = $version
            status = $status
            serverReady = $ready
            stoppedCleanly = $stoppedCleanly
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status ready=$ready stopped=$stoppedCleanly reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
if ($passed -ne $results.Count) {
    exit 1
}
