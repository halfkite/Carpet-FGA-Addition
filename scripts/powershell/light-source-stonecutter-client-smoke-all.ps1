param(
    [string] $VersionList = '',
    [int] $PortBase = 25590,
    [switch] $PauseAfterOpen
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\light-source-stonecutter-client-smoke-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$allVersions = @(
    '1.21.1', '1.21.3', '1.21.4', '1.21.5',
    '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2', '26.3'
)
if ([string]::IsNullOrWhiteSpace($VersionList)) {
    $versions = [string[]]$allVersions
} else {
    $versions = [string[]]@($VersionList.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}
foreach ($version in $versions) {
    if ($allVersions -notcontains $version) {
        throw "Unsupported version requested: $version"
    }
}

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaLightStonecutterInput {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct Point { public int X, Y; }
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr window, ref Point point);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr window, int command);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr window);
    [DllImport("user32.dll")] public static extern bool SetActiveWindow(IntPtr window);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] public static extern void SwitchToThisWindow(IntPtr window, bool altTab);
    [DllImport("user32.dll", SetLastError = true)] public static extern bool SetWindowPos(
        IntPtr window, IntPtr insertAfter, int x, int y, int width, int height, uint flags);

    private static readonly IntPtr TopMost = new IntPtr(-1);
    private static readonly IntPtr NotTopMost = new IntPtr(-2);
    private const uint NoMove = 0x0002u;
    private const uint NoSize = 0x0001u;
    private const uint Show = 0x0040u;
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
    [DllImport("user32.dll")] public static extern void keybd_event(byte key, byte scan, uint flags, UIntPtr extraInfo);

    private static Point Scale(IntPtr window, int x, int y) {
        Rect rect;
        if (GetClientRect(window, out rect)) {
            x = x * Math.Max(1, rect.Right - rect.Left) / 854;
            y = y * Math.Max(1, rect.Bottom - rect.Top) / 480;
        }
        Point point = new Point { X = x, Y = y };
        ClientToScreen(window, ref point);
        return point;
    }

    public static void Click(IntPtr window, int x, int y) {
        Point point = Scale(window, x, y);
        SetCursorPos(point.X, point.Y);
        mouse_event(0x0002u, 0, 0, 0, UIntPtr.Zero);
        mouse_event(0x0004u, 0, 0, 0, UIntPtr.Zero);
    }

    public static void RightClick(IntPtr window, int x, int y) {
        Point point = Scale(window, x, y);
        SetCursorPos(point.X, point.Y);
        mouse_event(0x0008u, 0, 0, 0, UIntPtr.Zero);
        mouse_event(0x0010u, 0, 0, 0, UIntPtr.Zero);
    }

    public static void Key(byte key, bool down) {
        keybd_event(key, 0, down ? 0u : 0x0002u, UIntPtr.Zero);
    }

}
'@

Add-Type -AssemblyName Microsoft.VisualBasic
Add-Type -AssemblyName System.Windows.Forms
[void][FgaLightStonecutterInput]::SetProcessDPIAware()

$script:gameWindow = [IntPtr]::Zero
$script:gameProcessId = 0

function Write-ProgressLine([string] $message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -LiteralPath $progressPath -Value $line -Encoding utf8
    Write-Host $line
}

function Read-Text([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Stop-Tree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}

function Focus-Game {
    if ($script:gameProcessId -ne 0) {
        [Microsoft.VisualBasic.Interaction]::AppActivate($script:gameProcessId)
    }
    if ($script:gameWindow -ne [IntPtr]::Zero) {
        [void][FgaLightStonecutterInput]::ShowWindowAsync($script:gameWindow, 9)
        [void][FgaLightStonecutterInput]::SetWindowPos($script:gameWindow, [IntPtr]::new(-1), 0, 0, 0, 0, 0x0043)
        [void][FgaLightStonecutterInput]::SetWindowPos($script:gameWindow, [IntPtr]::new(-2), 0, 0, 0, 0, 0x0043)
        [void][FgaLightStonecutterInput]::BringWindowToTop($script:gameWindow)
        [void][FgaLightStonecutterInput]::SetActiveWindow($script:gameWindow)
        [FgaLightStonecutterInput]::SwitchToThisWindow($script:gameWindow, $true)
        [void][FgaLightStonecutterInput]::SetForegroundWindow($script:gameWindow)
    }
    Start-Sleep -Milliseconds 150
}

function Click([int] $x, [int] $y) {
    Focus-Game
    [FgaLightStonecutterInput]::Click($script:gameWindow, $x, $y)
    Start-Sleep -Milliseconds 450
}

function Right-Click([int] $x, [int] $y) {
    Focus-Game
    [FgaLightStonecutterInput]::RightClick($script:gameWindow, $x, $y)
    Start-Sleep -Seconds 2
}

function Press-Key([byte] $key) {
    Focus-Game
    [FgaLightStonecutterInput]::Key($key, $true)
    Start-Sleep -Milliseconds 120
    [FgaLightStonecutterInput]::Key($key, $false)
    Start-Sleep -Milliseconds 700
}

function Send-ServerCommand([System.Diagnostics.Process] $server, [string] $command,
                            [int] $delay = 500) {
    $server.StandardInput.WriteLine($command)
    $server.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

function Wait-ServerReady([string] $serverLog, [System.Diagnostics.Process] $server) {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Read-Text $serverLog) -match 'Done \([0-9.]+s\)!') {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-LogMatch([string] $path, [string] $pattern, [int] $seconds = 30) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Text $path) -match $pattern) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Get-NewMinecraftWindow([int[]] $existingProcessIds,
                                [System.Diagnostics.Process] $commandProcess,
                                [datetime] $deadline) {
    while ((Get-Date) -lt $deadline -and -not $commandProcess.HasExited) {
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
        Start-Sleep -Seconds 2
    }
    return $null
}

function Open-Stonecutter([System.Diagnostics.Process] $server, [string] $username) {
    Send-ServerCommand $server "tp $username 0.5 100 0.5 0 25" 250
    Send-ServerCommand $server 'setblock 0 100 2 minecraft:stonecutter' 500
    Start-Sleep -Seconds 2
    Right-Click 427 240
}

function Prepare-Player([System.Diagnostics.Process] $server, [string] $username) {
    Send-ServerCommand $server "clear $username" 250
    Send-ServerCommand $server "give $username minecraft:glowstone 1" 250
    Send-ServerCommand $server "gamemode survival $username" 250
}

function Assert-Marker([string] $serverLog, [string] $marker) {
    if (-not (Wait-LogMatch $serverLog ([regex]::Escape($marker)) 20)) {
        throw "server marker not observed: $marker"
    }
}

function Run-RecipeCase([System.Diagnostics.Process] $server, [string] $serverLog,
                        [string] $username, [string] $objective, [bool] $enabled) {
    $mode = if ($enabled) { 'ENABLED' } else { 'DISABLED' }
    Send-ServerCommand $server ("carpet lightSourceStonecuttingRecipes " + $enabled.ToString().ToLowerInvariant()) 700
    Prepare-Player $server $username
    Open-Stonecutter $server $username
    if ($PauseAfterOpen) {
        Write-ProgressLine "PAUSE_AFTER_OPEN $username"
        Start-Sleep -Seconds 30
    }

    # Select the first hotbar slot, insert glowstone, select the first recipe, and take output
    Click 356 308
    Click 367 198
    Start-Sleep -Milliseconds 1000
    Click 399 180
    Start-Sleep -Milliseconds 700
    Click 490 198
    Press-Key 0x45

    Send-ServerCommand $server "execute store result score #FGA_LIGHT_SOURCE $objective run clear $username minecraft:glowstone 0" 250
    Send-ServerCommand $server "execute store result score #FGA_LIGHT_OUTPUT $objective run clear $username minecraft:light 0" 250
    if ($enabled) {
        Send-ServerCommand $server "execute if score #FGA_LIGHT_SOURCE $objective matches 0 run say FGA_LIGHT_ENABLED_SOURCE_PASS" 250
        Send-ServerCommand $server "execute if score #FGA_LIGHT_OUTPUT $objective matches 4 run say FGA_LIGHT_ENABLED_OUTPUT_PASS" 250
        Assert-Marker $serverLog 'FGA_LIGHT_ENABLED_SOURCE_PASS'
        Assert-Marker $serverLog 'FGA_LIGHT_ENABLED_OUTPUT_PASS'
    } else {
        Send-ServerCommand $server "execute if score #FGA_LIGHT_SOURCE $objective matches 1 run say FGA_LIGHT_DISABLED_SOURCE_PASS" 250
        Send-ServerCommand $server "execute if score #FGA_LIGHT_OUTPUT $objective matches 0 run say FGA_LIGHT_DISABLED_OUTPUT_PASS" 250
        Assert-Marker $serverLog 'FGA_LIGHT_DISABLED_SOURCE_PASS'
        Assert-Marker $serverLog 'FGA_LIGHT_DISABLED_OUTPUT_PASS'
    }
    return $true
}

$results = @()

for ($index = 0; $index -lt $versions.Count; $index++) {
    $version = $versions[$index]
    $startedAt = Get-Date
    $safeVersion = $version.Replace('.', '_')
    $username = "FGALight$($version.Replace('.', ''))"
    $objective = 'fga_light'
    $port = $PortBase + $index
    $runDir = Join-Path $root "versions\$version\run"
    $serverLog = Join-Path $reportDir "$version-server.log"
    $clientLog = Join-Path $reportDir "$version-client.log"
    $clientRunDir = Join-Path $reportDir "$version-client-run"
    $clientLatestLog = Join-Path $clientRunDir 'logs\latest.log'
    $levelName = "light-source-stonecutter-$($version.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $levelName
    $savedFiles = @('eula.txt', 'server.properties', 'ops.json', 'whitelist.json')
    $backups = @{}
    $server = $null
    $client = $null
    $status = 'failed'
    $reason = ''
    $windowAppeared = $false
    $joined = $false
    $disabledCase = $false
    $enabledCase = $false
    $mixinFailure = $false

    Write-ProgressLine "START $version port=$port"
    New-Item -ItemType Directory -Force -Path $runDir, $clientRunDir | Out-Null

    foreach ($name in $savedFiles) {
        $path = Join-Path $runDir $name
        if (Test-Path -LiteralPath $path) {
            $backup = "$path.before-light-source-stonecutter-$stamp-$safeVersion"
            Move-Item -LiteralPath $path -Destination $backup
            $backups[$name] = $backup
        }
    }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false'
        'spawn-protection=0'
        'max-players=2'
        "server-port=$port"
        'view-distance=2'
        'simulation-distance=2'
        "level-name=$levelName"
        'gamemode=survival'
        'difficulty=peaceful'
    )
    Set-Content -LiteralPath (Join-Path $clientRunDir 'options.txt') -Encoding ascii -Value @(
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
        'showSubtitles:false'
        'fullscreen:false'
        'guiScale:2'
    )

    try {
        $env:JAVA_HOME = if ($version -like '26.*') { $jdk25 } else { $jdk21 }
        $serverInfo = [Diagnostics.ProcessStartInfo]::new()
        $serverInfo.FileName = 'cmd.exe'
        $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runServer --no-daemon --max-workers=1 > "' + $serverLog + '" 2>&1"'
        $serverInfo.WorkingDirectory = $root
        $serverInfo.UseShellExecute = $false
        $serverInfo.RedirectStandardInput = $true
        $serverInfo.CreateNoWindow = $true
        $server = [Diagnostics.Process]::new()
        $server.StartInfo = $serverInfo
        [void] $server.Start()

        if (-not (Wait-ServerReady $serverLog $server)) {
            throw 'server did not become ready'
        }
        # Enable the rule before login so the client receives the complete recipe set
        # during the initial recipe synchronization.
        Send-ServerCommand $server 'carpet lightSourceStonecuttingRecipes true' 1200

        $previousJava = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
        $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runClient --no-daemon --max-workers=1 -PclientRunDir="' + $clientRunDir +
            '" --args="--username ' + $username + ' --quickPlayMultiplayer 127.0.0.1:' + $port +
            ' --width 854 --height 480" > "' + $clientLog + '" 2>&1"'
        $client = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments `
            -WorkingDirectory $root -WindowStyle Hidden -PassThru

        $window = Get-NewMinecraftWindow $previousJava $client ((Get-Date).AddMinutes(7))
        if ($null -eq $window) {
            throw 'Minecraft client window did not appear'
        }
        $windowAppeared = $true
        $script:gameWindow = $window.MainWindowHandle
        $script:gameProcessId = $window.Id
        $clientRect = [FgaLightStonecutterInput+Rect]::new()
        [void][FgaLightStonecutterInput]::GetClientRect($script:gameWindow, [ref]$clientRect)
        Write-ProgressLine "CLIENT_RECT $version width=$($clientRect.Right - $clientRect.Left) height=$($clientRect.Bottom - $clientRect.Top)"

        if (-not (Wait-LogMatch $clientLog 'Sound engine started' 180)) {
            throw 'client did not reach sound-engine startup'
        }
        Start-Sleep -Seconds 4

        if (-not (Wait-LogMatch $serverLog ([regex]::Escape("$username joined the game")) 120)) {
            throw 'client did not join the dedicated server'
        }
        $joined = $true
        Send-ServerCommand $server "op $username" 250
        Send-ServerCommand $server "scoreboard objectives add $objective dummy" 500
        Send-ServerCommand $server "gamemode survival $username" 250

        $enabledCase = Run-RecipeCase $server $serverLog $username $objective $true

        if (-not $enabledCase) {
            throw 'light-source stonecutter case failed'
        }
        Send-ServerCommand $server 'say FGA_LIGHT_STONECUTTER_DONE' 500
        $status = 'passed'
        $reason = 'client opened the stonecutter with the rule enabled and produced four light blocks'
    } catch {
        $reason = $_.Exception.Message
    } finally {
        $serverText = Read-Text $serverLog
        $clientText = Read-Text $clientLog
        $mixinPattern = 'Mixin apply for mod carpet-fga-addition failed|Mixin prepare for mod carpet-fga-addition failed|InvalidInjectionException|InjectionError|Critical injection failure'
        $mixinFailure = ($serverText + "`n" + $clientText) -match $mixinPattern
        if ($mixinFailure -and $status -ne 'passed') {
            $reason = 'FGA mixin or injection failure detected'
        }
        if ($null -ne $server -and -not $server.HasExited) {
            try { Send-ServerCommand $server 'stop' 100 } catch {}
            if (-not $server.WaitForExit(60000)) { Stop-Tree $server }
        }
        Stop-Tree $client
        Start-Sleep -Seconds 1

        if (Test-Path -LiteralPath $worldPath) {
            Remove-Item -LiteralPath $worldPath -Recurse -Force
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
        if (Test-Path -LiteralPath $clientLatestLog) {
            Copy-Item -LiteralPath $clientLatestLog -Destination (Join-Path $reportDir "$version-latest.log") -Force
        }

        $results += [ordered]@{
            version = $version
            status = $status
            port = $port
            windowAppeared = $windowAppeared
            joined = $joined
            disabledCase = $disabledCase
            enabledCase = $enabledCase
            mixinFailure = $mixinFailure
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
            clientLog = [IO.Path]::GetFileName($clientLog)
            clientLatestLog = "$version-latest.log"
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status joined=$joined disabled=$disabledCase enabled=$enabledCase mixinFailure=$mixinFailure reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
if ($passed -ne $results.Count) {
    exit 1
}
