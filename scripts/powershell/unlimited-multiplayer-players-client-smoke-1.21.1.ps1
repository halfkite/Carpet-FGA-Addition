param()

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$version = '1.21.1'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\unlimited-multiplayer-players-client-smoke-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$runDir = Join-Path $root "versions\$version\run"
$savesDir = Join-Path $runDir 'saves'
$modsDir = Join-Path $runDir 'mods'
$optionsPath = Join-Path $runDir 'options.txt'
$latestLog = Join-Path $runDir 'logs\latest.log'
$initialCommandLog = Join-Path $reportDir 'initial-client.log'
$reloadCommandLog = Join-Path $reportDir 'reload-client.log'
$gcaArtifact = Join-Path $root 'build\actual-full-test-1.21.1-2102\mods\gugle-carpet-addition-v2.12.4+build.88.jar'
$username = 'FGAFakeResident'
$botGroupName = 'FGARes'
$fakeNames = 0..19 | ForEach-Object { 'bot_{0}_{1}' -f $botGroupName, $_ }

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaUnlimitedPlayersSmokeMouse {
    [StructLayout(LayoutKind.Sequential)]
    public struct Rect { public int Left, Top, Right, Bottom; }

    [DllImport("user32.dll")]
    public static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern bool GetClientRect(IntPtr window, out Rect rect);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr window);

    [StructLayout(LayoutKind.Sequential)]
    public struct Point { public int X, Y; }

    [DllImport("user32.dll")]
    public static extern bool ClientToScreen(IntPtr window, ref Point point);

    [DllImport("user32.dll")]
    public static extern bool SetCursorPos(int x, int y);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);

    [DllImport("user32.dll")]
    public static extern bool SetProcessDPIAware();

    public static void ClickScaled(IntPtr window, int x, int y) {
        Rect rect;
        if (GetClientRect(window, out rect)) {
            x = x * Math.Max(1, rect.Right - rect.Left) / 854;
            y = y * Math.Max(1, rect.Bottom - rect.Top) / 480;
        }
        SetForegroundWindow(window);
        Point screenPoint = new Point { X = x, Y = y };
        if (ClientToScreen(window, ref screenPoint)) {
            SetCursorPos(screenPoint.X, screenPoint.Y);
            mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
            mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
        }
        IntPtr position = (IntPtr)((y << 16) | (x & 0xffff));
        PostMessage(window, 0x0200, IntPtr.Zero, position);
        PostMessage(window, 0x0201, (IntPtr)1, position);
        PostMessage(window, 0x0202, IntPtr.Zero, position);
    }
}
'@

[void][FgaUnlimitedPlayersSmokeMouse]::SetProcessDPIAware()

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName Microsoft.VisualBasic

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
            return $window
        }
        if ($commandProcess.HasExited) {
            return $null
        }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

function Send-ChatCommand([System.Diagnostics.Process] $window, [string] $command) {
    [void][FgaUnlimitedPlayersSmokeMouse]::SetForegroundWindow($window.MainWindowHandle)
    [Microsoft.VisualBasic.Interaction]::AppActivate($window.Id)
    [System.Windows.Forms.SendKeys]::SendWait('/')
    Start-Sleep -Milliseconds 250
    Set-Clipboard -Value $command.TrimStart('/')
    [System.Windows.Forms.SendKeys]::SendWait('^v')
    [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
    Start-Sleep -Milliseconds 700
}

function Start-Client([string] $logPath, [string] $extraArguments) {
    $env:JAVA_HOME = $jdk21
    $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :' + $version + ':runClient --no-daemon --configure-on-demand --max-workers=1' +
        ' --args="--username ' + $username + ' ' + $extraArguments + '" > "' + $logPath + '" 2>&1"'
    return Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru
}

function Wait-ForLog([string] $path, [string] $pattern, [int] $seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Log $path) -match $pattern) {
            return $true
        }
        Start-Sleep -Seconds 1
    }
    return $false
}

function Count-JoinedFakePlayers([string] $text) {
    return @($fakeNames | Where-Object {
            $escapedName = [regex]::Escape($_)
            $text -match "$escapedName.+logged in with entity id"
        }).Count
}

$commandProcess = $null
$window = $null
$backupSaves = $null
$backupMods = $null
$backupOptions = $null
$isolatedSaves = $null
$status = 'failed'
$reason = ''
$worldFolder = $null
$worldCreated = $false
$realPlayerJoined = $false
$initialFakePlayers = 0
$reloadFakePlayers = 0
$reloadPresence = 0
$mixinFailure = $false

try {
    if (-not (Test-Path -LiteralPath $gcaArtifact)) {
        throw "GCA test artifact not found: $gcaArtifact"
    }

    New-Item -ItemType Directory -Force -Path $runDir | Out-Null

    if (Test-Path -LiteralPath $savesDir) {
        $backupSaves = Join-Path $runDir "saves.before-unlimited-players-smoke-$stamp"
        Move-Item -LiteralPath $savesDir -Destination $backupSaves
    }
    New-Item -ItemType Directory -Path $savesDir | Out-Null

    if (Test-Path -LiteralPath $modsDir) {
        $backupMods = Join-Path $runDir "mods.before-unlimited-players-smoke-$stamp"
        Move-Item -LiteralPath $modsDir -Destination $backupMods
    }
    New-Item -ItemType Directory -Path $modsDir | Out-Null
    Copy-Item -LiteralPath $gcaArtifact -Destination (Join-Path $modsDir (Split-Path $gcaArtifact -Leaf))

    if (Test-Path -LiteralPath $optionsPath) {
        $backupOptions = Join-Path $runDir "options.before-unlimited-players-smoke-$stamp.txt"
        Move-Item -LiteralPath $optionsPath -Destination $backupOptions
    }
    Set-Content -LiteralPath $optionsPath -Encoding ascii -Value @(
        'lang:zh_cn'
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
        'showSubtitles:false'
    )

    $existingJavaIds = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
    $commandProcess = Start-Client $initialCommandLog ''
    $window = Get-NewMinecraftWindow $existingJavaIds $commandProcess ((Get-Date).AddMinutes(6))
    if ($null -eq $window) { throw 'initial Minecraft client window did not appear' }

    if (-not (Wait-ForLog $latestLog 'Sound engine started|Created: .*textures/atlas' 180)) {
        throw 'initial Minecraft client did not reach the main menu'
    }
    Start-Sleep -Seconds 2
    [FgaUnlimitedPlayersSmokeMouse]::ClickScaled($window.MainWindowHandle, 427, 235)
    Start-Sleep -Seconds 3
    [FgaUnlimitedPlayersSmokeMouse]::ClickScaled($window.MainWindowHandle, 268, 445)
    Start-Sleep -Seconds 3
    [FgaUnlimitedPlayersSmokeMouse]::ClickScaled($window.MainWindowHandle, 268, 445)

    $realPlayerJoined = Wait-ForLog $latestLog ([regex]::Escape($username) + '.+logged in with entity id') 300
    if (-not $realPlayerJoined) { throw 'real player did not join the new integrated-server world' }

    Send-ChatCommand $window 'reload'
    Start-Sleep -Seconds 5
    Send-ChatCommand $window 'carpet unlimitedMultiplayerPlayers false'
    Send-ChatCommand $window 'carpet fakePlayerResident true'
    Send-ChatCommand $window "bot group generated $botGroupName 20 true"
    Send-ChatCommand $window "execute if entity @e[type=player,name=$($fakeNames[0])] run say FGA_RESIDENT_INITIAL_PRESENT"

    $initialDeadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $initialDeadline) {
        $initialFakePlayers = Count-JoinedFakePlayers (Read-Log $latestLog)
        if ($initialFakePlayers -eq 20 -and (Read-Log $latestLog) -match 'FGA_RESIDENT_INITIAL_PRESENT') {
            break
        }
        Start-Sleep -Seconds 1
    }
    $initialRaw = Read-Log $latestLog
    $initialFakePlayers = Count-JoinedFakePlayers $initialRaw
    if ($initialFakePlayers -ne 20) {
        throw "expected 20 initial fake players, observed $initialFakePlayers"
    }
    if ($initialRaw -notmatch 'fakePlayerResident:\s*true') {
        throw 'GCA fakePlayerResident was not confirmed as enabled'
    }
    if ($initialRaw -notmatch 'unlimitedMultiplayerPlayers:\s*false') {
        throw 'FGA unlimitedMultiplayerPlayers was not confirmed as manually disabled'
    }

    $worldFolder = Get-ChildItem -LiteralPath $savesDir -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'level.dat') } |
        Select-Object -First 1
    if ($null -eq $worldFolder) { throw 'created world folder could not be identified' }
    $worldCreated = $true

    [void] $window.CloseMainWindow()
    if (-not $commandProcess.WaitForExit(60000)) {
        Stop-ProcessTree $commandProcess.Id
    }
    $commandProcess = $null
    Start-Sleep -Seconds 3

    $existingJavaIds = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
    $commandProcess = Start-Client $reloadCommandLog ("--quickPlaySingleplayer '" + $worldFolder.Name + "'")
    $window = Get-NewMinecraftWindow $existingJavaIds $commandProcess ((Get-Date).AddMinutes(6))
    if ($null -eq $window) { throw 'reload Minecraft client window did not appear' }

    if (-not (Wait-ForLog $latestLog ([regex]::Escape($username) + '.+logged in with entity id') 300)) {
        throw 'real player could not re-enter the saved world'
    }
    $reloadRaw = Read-Log $latestLog
    $reloadFakePlayers = Count-JoinedFakePlayers $reloadRaw
    foreach ($fakeName in $fakeNames) {
        Send-ChatCommand $window "execute if entity @e[type=player,name=$fakeName] run say FGA_RESIDENT_RELOAD_$fakeName"
    }
    $reloadDeadline = (Get-Date).AddSeconds(45)
    while ((Get-Date) -lt $reloadDeadline) {
        $reloadRaw = Read-Log $latestLog
        $reloadPresence = @($fakeNames | Where-Object {
                $reloadRaw -match "FGA_RESIDENT_RELOAD_$($_)"
            }).Count
        if ($reloadPresence -eq 20) { break }
        Start-Sleep -Seconds 1
    }
    $reloadRaw = Read-Log $latestLog
    $reloadFakePlayers = Count-JoinedFakePlayers $reloadRaw
    $reloadPresence = @($fakeNames | Where-Object {
            $reloadRaw -match "FGA_RESIDENT_RELOAD_$($_)"
        }).Count

    if ($reloadPresence -ne 20) {
        throw "expected 20 resident fake players after reload, observed $reloadPresence"
    }

    $status = 'passed'
    $reason = '20 GCA resident fake players were summoned with the FGA rule false, the client exited, and the real player re-entered the saved world with all 20 fake players present'
} catch {
    $reason = $_.Exception.Message
} finally {
    if ($null -ne $commandProcess -and -not $commandProcess.HasExited) {
        Stop-ProcessTree $commandProcess.Id
    }
    Start-Sleep -Seconds 2

    $combinedLog = (Read-Log $latestLog) + "`n" + (Read-Log $initialCommandLog) + "`n" + (Read-Log $reloadCommandLog)
    if ($combinedLog -match 'Mixin apply for mod carpet-fga-addition failed|Mixin prepare for mod carpet-fga-addition failed|Invalid player data|Couldn''t place player in world') {
        $mixinFailure = $true
        if ($status -ne 'passed') { $reason = 'FGA mixin or player placement failure detected' }
    }
    if (Test-Path -LiteralPath $latestLog) {
        Copy-Item -LiteralPath $latestLog -Destination (Join-Path $reportDir 'latest.log') -Force
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

    if (Test-Path -LiteralPath $savesDir) {
        $isolatedSaves = Join-Path $runDir "saves.unlimited-players-smoke-$stamp"
        Move-Item -LiteralPath $savesDir -Destination $isolatedSaves
    }
    if ($null -ne $backupSaves -and (Test-Path -LiteralPath $backupSaves)) {
        Move-Item -LiteralPath $backupSaves -Destination $savesDir
    }

    $result = [ordered]@{
        version = $version
        status = $status
        worldCreated = $worldCreated
        realPlayerJoined = $realPlayerJoined
        initialFakePlayers = $initialFakePlayers
        reloadFakePlayersFromLog = $reloadFakePlayers
        reloadFakePlayersPresent = $reloadPresence
        mixinFailure = $mixinFailure
        gcaArtifact = $gcaArtifact
        isolatedSaves = $isolatedSaves
        reason = $reason
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Write-ProgressLine "RESULT $version $status world=$worldCreated real=$realPlayerJoined initialFake=$initialFakePlayers reloadFake=$reloadPresence mixinFailure=$mixinFailure reason=$reason"
}

if ($status -ne 'passed') {
    exit 1
}
