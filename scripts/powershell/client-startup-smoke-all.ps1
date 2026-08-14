param(
    [string] $VersionList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\client-startup-smoke-$stamp"
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
public static class FgaClientStartupWindow {
    [DllImport("user32.dll")]
    public static extern bool ShowWindowAsync(IntPtr window, int command);
}
'@

function Write-ProgressLine([string] $message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -LiteralPath $progressPath -Value $line -Encoding utf8
    Write-Host $line
}

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Stop-ProcessTree([int] $processId) {
    & taskkill.exe /PID $processId /T /F 2>$null | Out-Null
}

function Get-NewMinecraftWindow([int[]] $existingProcessIds, [System.Diagnostics.Process] $commandProcess,
                                [datetime] $deadline) {
    while ((Get-Date) -lt $deadline) {
        $window = Get-Process -Name java,javaw -ErrorAction SilentlyContinue |
            Where-Object {
                $_.MainWindowHandle -ne 0 -and
                $_.MainWindowTitle -like 'Minecraft*' -and
                $existingProcessIds -notcontains $_.Id
            } |
            Sort-Object StartTime -Descending |
            Select-Object -First 1
        if ($null -ne $window) {
            [void][FgaClientStartupWindow]::ShowWindowAsync($window.MainWindowHandle, 0)
            return $window
        }
        if ($commandProcess.HasExited) {
            return $null
        }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

$fgaFailurePattern = @(
    'Mixin apply for mod carpet-fga-addition failed',
    'Mixin prepare for mod carpet-fga-addition failed',
    'Mixin transformation of carpet\.fga\.',
    'from mod carpet-fga-addition.*(?:failed injection check|Critical injection failure|InvalidInjectionException|InjectionError)'
) -join '|'
$startupFailurePattern = @(
    $fgaFailurePattern,
    'Could not execute entrypoint stage',
    'net.fabricmc.loader.impl.FormattedException',
    '#@!@# Game crashed!'
) -join '|'
$results = @()

foreach ($version in $versions) {
    if ($allVersions -notcontains $version) {
        throw "Unsupported version requested: $version"
    }

    $startedAt = Get-Date
    $safeVersion = $version.Replace('.', '_')
    $runDir = Join-Path $root "versions\$version\run"
    $modsDir = Join-Path $runDir 'mods'
    $optionsPath = Join-Path $runDir 'options.txt'
    $commandLog = Join-Path $reportDir "$version-client.log"
    $backupMods = $null
    $backupOptions = $null
    $commandProcess = $null
    $windowAppeared = $false
    $mainMenuLoaded = $false
    $mixinFailure = $false
    $status = 'failed'
    $reason = ''

    Write-ProgressLine "START $version"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    if (Test-Path -LiteralPath $modsDir) {
        $backupMods = Join-Path $runDir "mods.before-client-startup-$stamp-$safeVersion"
        Move-Item -LiteralPath $modsDir -Destination $backupMods
    }
    New-Item -ItemType Directory -Path $modsDir | Out-Null

    if (Test-Path -LiteralPath $optionsPath) {
        $backupOptions = Join-Path $runDir "options.before-client-startup-$stamp-$safeVersion.txt"
        Move-Item -LiteralPath $optionsPath -Destination $backupOptions
    }
    Set-Content -LiteralPath $optionsPath -Encoding ascii -Value @(
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
        'showSubtitles:false'
    )

    try {
        $existingJavaIds = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
        $env:JAVA_HOME = if ($version -like '26.*') { $jdk25 } else { $jdk21 }
        $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runClient --no-daemon --configure-on-demand --max-workers=1' +
            ' --args="--username FGAStartup' + $safeVersion + '" > "' + $commandLog + '" 2>&1"'
        $commandProcess = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments `
            -WorkingDirectory $root -WindowStyle Hidden -PassThru

        $window = Get-NewMinecraftWindow $existingJavaIds $commandProcess ((Get-Date).AddMinutes(6))
        if ($null -eq $window) {
            $raw = Read-Log $commandLog
            $mixinFailure = $raw -match $fgaFailurePattern
            throw 'client window did not appear before the launcher exited or timed out'
        }
        $windowAppeared = $true

        $menuDeadline = (Get-Date).AddMinutes(3)
        while ((Get-Date) -lt $menuDeadline) {
            $raw = Read-Log $commandLog
            if ($raw -match $fgaFailurePattern) {
                $mixinFailure = $true
                break
            }
            if ($raw -match $startupFailurePattern) {
                break
            }
            if ($raw -match 'Sound engine started' -and $raw -match 'Created: .*textures/atlas') {
                $mainMenuLoaded = $true
                break
            }
            if ($commandProcess.HasExited) {
                break
            }
            Start-Sleep -Seconds 1
        }

        if ($mainMenuLoaded -and -not $mixinFailure) {
            Start-Sleep -Seconds 2
            $status = 'passed'
            $reason = 'client reached the main menu without creating a world'
        } elseif ($mixinFailure) {
            $reason = 'FGA mixin or injection failure detected'
        } elseif ($commandProcess.HasExited) {
            $reason = 'client exited before reaching the main menu'
        } else {
            $reason = 'main menu startup markers were not observed before timeout'
        }
    } catch {
        if ([string]::IsNullOrWhiteSpace($reason)) {
            $reason = $_.Exception.Message
        }
    } finally {
        if ($null -ne $commandProcess -and -not $commandProcess.HasExited) {
            Stop-ProcessTree $commandProcess.Id
        }
        Start-Sleep -Seconds 1

        $raw = Read-Log $commandLog
        if ($raw -match $fgaFailurePattern) {
            $mixinFailure = $true
            if ($status -ne 'passed') {
                $reason = 'FGA mixin or injection failure detected'
            }
        } elseif ($raw -match $startupFailurePattern -and $status -ne 'passed') {
            $reason = 'client startup failure detected outside FGA mixins'
        }

        if (Test-Path -LiteralPath $modsDir) {
            Remove-Item -LiteralPath $modsDir -Recurse -Force
        }
        if ($null -ne $backupMods -and (Test-Path -LiteralPath $backupMods)) {
            Move-Item -LiteralPath $backupMods -Destination $modsDir
        }

        if (Test-Path -LiteralPath $optionsPath) {
            Remove-Item -LiteralPath $optionsPath -Force
        }
        if ($null -ne $backupOptions -and (Test-Path -LiteralPath $backupOptions)) {
            Move-Item -LiteralPath $backupOptions -Destination $optionsPath
        }

        $results += [ordered]@{
            version = $version
            status = $status
            windowAppeared = $windowAppeared
            mainMenuLoaded = $mainMenuLoaded
            mixinFailure = $mixinFailure
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            commandLog = [System.IO.Path]::GetFileName($commandLog)
            modsRestored = $null -eq $backupMods -or (Test-Path -LiteralPath $modsDir)
            optionsRestored = $null -eq $backupOptions -or (Test-Path -LiteralPath $optionsPath)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status menu=$mainMenuLoaded mixinFailure=$mixinFailure reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
if ($passed -ne $results.Count) {
    exit 1
}
