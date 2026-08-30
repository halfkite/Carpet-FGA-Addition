param(
    [string] $VersionList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\server-fake-player-stack-smoke-$stamp"
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

function Send-ServerCommand([System.Diagnostics.Process] $process, [string] $command) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds 700
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
    $botName = "FGAStack$($version.Replace('.', ''))"
    $levelName = "server-fake-stack-$($version.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $levelName
    $savedFiles = @('eula.txt', 'server.properties', 'ops.json', 'whitelist.json')
    $backups = @{}
    $server = $null
    $status = 'failed'
    $reason = ''
    $ready = $false
    $spawned = $false
    $operator = $false
    $survivedHandshake = $false
    $stackPassed = $false

    Write-ProgressLine "START $version"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    foreach ($name in $savedFiles) {
        $path = Join-Path $runDir $name
        if (Test-Path -LiteralPath $path) {
            $backup = "$path.before-server-fake-stack-$stamp"
            Move-Item -LiteralPath $path -Destination $backup
            $backups[$name] = $backup
        }
    }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false'
        'spawn-protection=0'
        'max-players=4'
        'server-port=0'
        'view-distance=2'
        'simulation-distance=2'
        "level-name=$levelName"
        'gamemode=creative'
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

        foreach ($command in @(
            'carpet droppedItemStackLimit true',
            'droppedItemStackLimit mode inventory 1000',
            "player $botName spawn",
            "op $botName",
            "clear $botName",
            "give $botName minecraft:stone 200",
            "execute if entity $botName run say FGA_FAKE_PLAYER_PRESENT"
        )) {
            Send-ServerCommand $server $command
        }

        Start-Sleep -Seconds 4
        Send-ServerCommand $server "execute if entity $botName run say FGA_FAKE_PLAYER_HANDSHAKE_EXEMPT"
        Send-ServerCommand $server "data get entity $botName Inventory"
        Start-Sleep -Seconds 2

        $raw = Read-Log $serverLog
        $spawned = $raw -match 'FGA_FAKE_PLAYER_PRESENT'
        $operator = $raw -match "Made $([regex]::Escape($botName)) a server operator|Made $([regex]::Escape($botName)) a server op"
        $survivedHandshake = $raw -match 'FGA_FAKE_PLAYER_HANDSHAKE_EXEMPT'
        $stackPassed = $raw -match '(?i)(Count\s*:\s*200b|count\s*:\s*200)'

        if ($spawned -and $operator -and $survivedHandshake -and $stackPassed) {
            $status = 'passed'
            $reason = 'server started, OP fake player remained online, and one inventory stack reached 200 items'
        } elseif (-not $spawned) {
            $reason = 'fake player did not spawn'
        } elseif (-not $operator) {
            $reason = 'fake player was not confirmed as OP'
        } elseif (-not $survivedHandshake) {
            $reason = 'fake player did not survive the client handshake enforcement interval'
        } else {
            $reason = 'fake-player inventory did not contain a 200-item stack'
        }
    } catch {
        $reason = $_.Exception.Message
    } finally {
        if ($null -ne $server -and -not $server.HasExited) {
            try {
                Send-ServerCommand $server 'stop'
                if (-not $server.WaitForExit(30000)) {
                    Stop-ProcessTree $server
                }
            } catch {
                Stop-ProcessTree $server
            }
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

        $results += [ordered]@{
            version = $version
            status = $status
            serverReady = $ready
            fakePlayerSpawned = $spawned
            fakePlayerOperator = $operator
            survivedHandshake = $survivedHandshake
            inventoryStackPassed = $stackPassed
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status ready=$ready spawn=$spawned op=$operator handshake=$survivedHandshake stack=$stackPassed reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
if ($passed -ne $results.Count) {
    exit 1
}
