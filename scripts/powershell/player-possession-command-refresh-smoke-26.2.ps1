param(
    [string] $Version = '26.2'
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\player-possession-command-refresh-smoke-$Version-$stamp"
$reportPath = Join-Path $reportDir 'summary.json'
$runDir = Join-Path $root "versions\$Version\run"
$savesDir = Join-Path $runDir 'saves'
$modsDir = Join-Path $runDir 'mods'
$optionsPath = Join-Path $runDir 'options.txt'
$carpetConfigPath = Join-Path $runDir 'config\carpet\default_carpet.conf'
$latestLog = Join-Path $runDir 'logs\latest.log'
$commandLog = Join-Path $reportDir "$Version-client.log"
$backupSaves = Join-Path $runDir "saves.before-command-refresh-smoke-$stamp"
$backupMods = Join-Path $runDir "mods.before-command-refresh-smoke-$stamp"
$backupOptions = Join-Path $runDir "options.txt.before-command-refresh-smoke-$stamp"
$backupCarpetConfig = Join-Path $runDir "config\carpet\default_carpet.conf.before-command-refresh-smoke-$stamp"
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$commandProcess = $null
$window = $null
$status = 'failed'
$reason = ''
$joined = $false
$targetSpawned = $false
$possessCommandAccepted = $false
$statusCommandAccepted = $false
$unknownCommand = $false

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaPossessionCommandSmokeInput {
    [StructLayout(LayoutKind.Sequential)]
    public struct Rect { public int Left, Top, Right, Bottom; }

    [DllImport("user32.dll")]
    public static extern bool GetClientRect(IntPtr window, out Rect rect);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr window);

    [DllImport("user32.dll")]
    public static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);

    public static void ClickScaled(IntPtr window, int x, int y) {
        Rect rect;
        if (GetClientRect(window, out rect)) {
            x = x * Math.Max(1, rect.Right - rect.Left) / 854;
            y = y * Math.Max(1, rect.Bottom - rect.Top) / 480;
        }
        IntPtr position = (IntPtr)((y << 16) | (x & 0xffff));
        SetForegroundWindow(window);
        PostMessage(window, 0x0200, IntPtr.Zero, position);
        PostMessage(window, 0x0201, (IntPtr)1, position);
        PostMessage(window, 0x0202, IntPtr.Zero, position);
    }
}
'@

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName Microsoft.VisualBasic

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Send-ChatCommand([IntPtr] $windowHandle, [string] $command) {
    [void][FgaPossessionCommandSmokeInput]::SetForegroundWindow($windowHandle)
    $process = Get-Process | Where-Object { $_.MainWindowHandle -eq $windowHandle } | Select-Object -First 1
    if ($null -eq $process) { throw 'Minecraft client window process was not found' }
    [Microsoft.VisualBasic.Interaction]::AppActivate($process.Id)
    [System.Windows.Forms.SendKeys]::SendWait('/')
    Start-Sleep -Milliseconds 250
    Set-Clipboard -Value ($command.TrimStart('/'))
    [System.Windows.Forms.SendKeys]::SendWait('^v')
    [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
    Start-Sleep -Milliseconds 750
}

function Wait-LogPattern([string] $path, [string] $pattern, [int] $timeoutSeconds = 30) {
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

try {
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $carpetConfigPath) | Out-Null
    if (Test-Path -LiteralPath $carpetConfigPath) { Move-Item -LiteralPath $carpetConfigPath -Destination $backupCarpetConfig }
    Set-Content -LiteralPath $carpetConfigPath -Encoding ascii -Value @(
        'commandPlayer true'
        'playerPossession true'
    )
    if (Test-Path -LiteralPath $savesDir) { Move-Item -LiteralPath $savesDir -Destination $backupSaves }
    New-Item -ItemType Directory -Force -Path $savesDir | Out-Null
    if (Test-Path -LiteralPath $modsDir) { Move-Item -LiteralPath $modsDir -Destination $backupMods }
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
    if (Test-Path -LiteralPath $optionsPath) { Move-Item -LiteralPath $optionsPath -Destination $backupOptions }
    Set-Content -LiteralPath $optionsPath -Encoding ascii -Value @(
        'lang:en_us'
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
    )

    $env:JAVA_HOME = if ($Version -like '26.*') { $jdk25 } else { $jdk21 }
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    $existingJavaIds = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
    $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :' + $Version + ':runClient --no-daemon --configure-on-demand --max-workers=1' +
        ' --args="--username FGARefresh" > "' + $commandLog + '" 2>&1"'
    $commandProcess = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru

    $windowDeadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $windowDeadline) {
        $window = Get-Process -Name java,javaw -ErrorAction SilentlyContinue |
            Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' -and $existingJavaIds -notcontains $_.Id } |
            Sort-Object StartTime -Descending | Select-Object -First 1
        if ($null -ne $window) { break }
        if ($commandProcess.HasExited) { throw 'client process exited before opening a window' }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $window) { throw 'client window did not appear within five minutes' }

    if (-not (Wait-LogPattern $latestLog 'Sound engine started|Created: .*textures/atlas' 120)) {
        throw 'client did not finish loading the menu'
    }
    Start-Sleep -Seconds 5
    [FgaPossessionCommandSmokeInput]::ClickScaled($window.MainWindowHandle, 427, 235)
    Start-Sleep -Seconds 5
    [FgaPossessionCommandSmokeInput]::ClickScaled($window.MainWindowHandle, 268, 445)
    if (-not (Wait-LogPattern $latestLog 'FGARefresh.+logged in with entity id' 120)) {
        throw 'test player did not join the integrated server'
    }
    $joined = $true
    Start-Sleep -Seconds 5

    Send-ChatCommand $window.MainWindowHandle '/player FGARefreshTarget spawn at 1 70 1'
    if (-not (Wait-LogPattern $latestLog 'FGARefreshTarget.*logged in with entity id|FGARefreshTarget joined the game' 30)) {
        throw 'fake possession target did not spawn'
    }
    $targetSpawned = $true

    Send-ChatCommand $window.MainWindowHandle '/player FGARefreshTarget possess'
    Start-Sleep -Seconds 2
    $text = Read-Log $latestLog
    $unknownCommand = $text -match 'Unknown or incomplete command|Unknown command|Expected whitespace to end one argument'
    $possessCommandAccepted = $text -match 'Swapped state with FGARefreshTarget'
    if (-not $possessCommandAccepted) {
        throw 'possess command was not accepted by the client/server command tree'
    }

    Send-ChatCommand $window.MainWindowHandle '/controlPlayer'
    Start-Sleep -Seconds 1
    $text = Read-Log $latestLog
    $statusCommandAccepted = $text -match 'is controlling FGARefreshTarget'
    if (-not $statusCommandAccepted) { throw 'controlPlayer command was not visible after possession started' }

    $status = 'passed'
    $reason = 'playerPossession was enabled before the initial join; /player possess and /controlPlayer both executed without reconnect'
} catch {
    $reason = $_.Exception.Message
} finally {
    if ($null -ne $commandProcess -and -not $commandProcess.HasExited) {
        Stop-ProcessTree $commandProcess
    }
    Start-Sleep -Seconds 2
    $finalLog = Read-Log $latestLog
    if ($finalLog) { Set-Content -LiteralPath (Join-Path $reportDir "$Version-latest.log") -Value $finalLog -Encoding utf8 }
    if (Test-Path -LiteralPath $savesDir) { Move-Item -LiteralPath $savesDir -Destination (Join-Path $runDir "saves.client-command-refresh-smoke-$stamp") }
    if (Test-Path -LiteralPath $modsDir) { Move-Item -LiteralPath $modsDir -Destination (Join-Path $runDir "mods.client-command-refresh-smoke-$stamp") }
    if (Test-Path -LiteralPath $optionsPath) { Remove-Item -LiteralPath $optionsPath -Force }
    if (Test-Path -LiteralPath $backupSaves) { Move-Item -LiteralPath $backupSaves -Destination $savesDir }
    if (Test-Path -LiteralPath $backupMods) { Move-Item -LiteralPath $backupMods -Destination $modsDir }
    if (Test-Path -LiteralPath $backupOptions) { Move-Item -LiteralPath $backupOptions -Destination $optionsPath }
    if (Test-Path -LiteralPath $carpetConfigPath) {
        Move-Item -LiteralPath $carpetConfigPath -Destination (Join-Path $runDir "config\carpet\default_carpet.conf.client-command-refresh-smoke-$stamp")
    }
    if (Test-Path -LiteralPath $backupCarpetConfig) { Move-Item -LiteralPath $backupCarpetConfig -Destination $carpetConfigPath }

    $summary = [ordered]@{
        version = $Version
        status = $status
        joined = $joined
        targetSpawned = $targetSpawned
        possessCommandAccepted = $possessCommandAccepted
        statusCommandAccepted = $statusCommandAccepted
        unknownCommand = $unknownCommand
        reason = $reason
        reportDir = $reportDir
    }
    $summary | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding utf8
    Get-Content -LiteralPath $reportPath
}

if ($status -ne 'passed') { exit 1 }
