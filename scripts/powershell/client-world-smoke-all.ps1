param(
    [string] $VersionList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\client-world-smoke-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$allVersions = @(
    '1.16.5', '1.17.1', '1.18.2', '1.19.2', '1.19.4', '1.20.1',
    '1.20.4', '1.20.6', '1.21', '1.21.1', '1.21.3', '1.21.4',
    '1.21.5', '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2'
)
$versions = if ([string]::IsNullOrWhiteSpace($VersionList)) {
    $allVersions
} else {
    @($VersionList.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaClientSmokeMouse {
    [DllImport("user32.dll")]
    public static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);

    public static void Click(IntPtr window, int x, int y) {
        IntPtr position = (IntPtr)((y << 16) | (x & 0xffff));
        PostMessage(window, 0x0200, IntPtr.Zero, position);
        PostMessage(window, 0x0201, (IntPtr)1, position);
        PostMessage(window, 0x0202, IntPtr.Zero, position);
    }
}
'@

function Write-ProgressLine([string] $message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -LiteralPath $progressPath -Value $line -Encoding utf8
    Write-Host $line
}

function Stop-ProcessTree([int] $processId) {
    & taskkill.exe /PID $processId /T /F 2>$null | Out-Null
}

function Get-NewMinecraftWindow([int[]] $existingProcessIds, [System.Diagnostics.Process] $commandProcess,
                                [datetime] $deadline) {
    while ((Get-Date) -lt $deadline) {
        $window = Get-Process -Name java -ErrorAction SilentlyContinue |
            Where-Object {
                $_.MainWindowHandle -ne 0 -and
                $_.MainWindowTitle -like 'Minecraft*' -and
                $existingProcessIds -notcontains $_.Id
            } |
            Sort-Object StartTime -Descending |
            Select-Object -First 1
        if ($null -ne $window) {
            return $window
        }
        if ($commandProcess.HasExited) {
            return $null
        }
        Start-Sleep -Seconds 2
    }
    return $null
}

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

$results = @()

foreach ($version in $versions) {
    $startedAt = Get-Date
    $safeVersion = $version.Replace('.', '_')
    $username = "FGA$($version.Replace('.', ''))"
    $runDir = Join-Path $root "versions\$version\run"
    $savesDir = Join-Path $runDir 'saves'
    $modsDir = Join-Path $runDir 'mods'
    $latestLog = Join-Path $runDir 'logs\latest.log'
    $commandLog = Join-Path $reportDir "$version-client.log"
    $backupSaves = $null
    $backupMods = $null
    $status = 'failed'
    $reason = ''
    $joined = $false
    $worldCreated = $false
    $mixinFailure = $false
    $commandProcess = $null

    Write-ProgressLine "START $version"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    if (Test-Path -LiteralPath $savesDir) {
        $backupSaves = Join-Path $runDir "saves.before-client-smoke-$stamp-$safeVersion"
        Move-Item -LiteralPath $savesDir -Destination $backupSaves
    }
    New-Item -ItemType Directory -Path $savesDir | Out-Null

    if (Test-Path -LiteralPath $modsDir) {
        $backupMods = Join-Path $runDir "mods.before-client-smoke-$stamp-$safeVersion"
        Move-Item -LiteralPath $modsDir -Destination $backupMods
    }
    New-Item -ItemType Directory -Path $modsDir | Out-Null

    Set-Content -LiteralPath (Join-Path $runDir 'options.txt') -Encoding ascii -Value @(
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
        'showSubtitles:false'
    )

    try {
        $existingJavaIds = @(Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object Id)
        $env:JAVA_HOME = if ($version -like '26.*') { $jdk25 } else { $jdk21 }
        $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runClient --no-daemon --configure-on-demand --max-workers=1' +
            ' --args="--username ' + $username + '" > "' + $commandLog + '" 2>&1"'
        $commandProcess = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments `
            -WorkingDirectory $root -WindowStyle Hidden -PassThru

        $window = Get-NewMinecraftWindow $existingJavaIds $commandProcess ((Get-Date).AddMinutes(5))
        if ($null -eq $window) {
            throw 'client window did not appear within five minutes'
        }

        $menuDeadline = (Get-Date).AddMinutes(2)
        while ((Get-Date) -lt $menuDeadline) {
            if ((Read-Log $latestLog) -match 'Sound engine started|Created: .*textures/atlas') {
                break
            }
            Start-Sleep -Seconds 2
        }
        Start-Sleep -Seconds 3
        [FgaClientSmokeMouse]::Click($window.MainWindowHandle, 427, 235)
        Start-Sleep -Seconds 3
        [FgaClientSmokeMouse]::Click($window.MainWindowHandle, 268, 445)

        $joinDeadline = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $joinDeadline) {
            Start-Sleep -Seconds 3
            $raw = Read-Log $latestLog
            if ($raw -match [regex]::Escape("$username joined the game")) {
                $joined = $true
                break
            }
            if ($raw -match 'Mixin apply for mod carpet-fga-addition failed|Mixin prepare for mod carpet-fga-addition failed|Invalid player data|无效的玩家数据|Couldn''t place player in world') {
                $mixinFailure = $true
                break
            }
            if ($commandProcess.HasExited -and -not $joined) {
                break
            }
        }

        $worldCreated = @(Get-ChildItem -LiteralPath $savesDir -Recurse -Filter 'level.dat' -File -ErrorAction SilentlyContinue).Count -gt 0
        if ($joined -and $worldCreated -and -not $mixinFailure) {
            $status = 'passed'
            $reason = 'new world created and player joined integrated server'
        } elseif ($mixinFailure) {
            $reason = 'FGA mixin or player placement failure detected'
        } elseif (-not $worldCreated) {
            $reason = 'client did not create a world'
        } else {
            $reason = 'player did not join the integrated server before timeout'
        }
    } catch {
        $reason = $_.Exception.Message
    } finally {
        if ($null -ne $commandProcess -and -not $commandProcess.HasExited) {
            Stop-ProcessTree $commandProcess.Id
        }
        Start-Sleep -Seconds 2

        $finalLog = Read-Log $latestLog
        $combinedLog = $finalLog + "`n" + (Read-Log $commandLog)
        if ($combinedLog -match 'Mixin apply for mod carpet-fga-addition failed|Mixin prepare for mod carpet-fga-addition failed|Invalid player data|无效的玩家数据|Couldn''t place player in world') {
            $mixinFailure = $true
            if ($status -ne 'passed') {
                $reason = 'FGA mixin or player placement failure detected'
            }
        }
        if ($finalLog) {
            Set-Content -LiteralPath (Join-Path $reportDir "$version-latest.log") -Value $finalLog -Encoding utf8
        }

        if (Test-Path -LiteralPath $modsDir) {
            $isolatedMods = Join-Path $runDir "mods.client-smoke-$stamp-$safeVersion"
            Move-Item -LiteralPath $modsDir -Destination $isolatedMods
        }
        if ($null -ne $backupMods -and (Test-Path -LiteralPath $backupMods)) {
            Move-Item -LiteralPath $backupMods -Destination $modsDir
        }

        $results += [ordered]@{
            version = $version
            status = $status
            worldCreated = $worldCreated
            joined = $joined
            mixinFailure = $mixinFailure
            reason = $reason
            startedAt = $startedAt.ToString('o')
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            commandLog = [System.IO.Path]::GetFileName($commandLog)
            minecraftLog = "$version-latest.log"
            savesBackup = $backupSaves
            modsBackupRestored = $null -eq $backupMods -or (Test-Path -LiteralPath $modsDir)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status world=$worldCreated joined=$joined mixinFailure=$mixinFailure reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
if ($passed -ne $results.Count) {
    exit 1
}
