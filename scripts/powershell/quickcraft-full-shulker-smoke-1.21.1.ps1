param(
    [Parameter(Mandatory = $true)]
    [string] $QuickCraftJar,
    [string] $AmsJar,
    [int] $Port = 25579
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$version = '1.21.1'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\quickcraft-full-shulker-$stamp"
$runDir = Join-Path $root "versions\$version\run"
$serverLog = Join-Path $reportDir 'server.log'
$clientLog = Join-Path $reportDir 'client.log'
$clientRunDir = Join-Path $reportDir 'client-run'
$clientLatestLog = Join-Path $clientRunDir 'logs\latest.log'
$clientFixture = Join-Path $root 'scripts\fixtures\quickcraft-client'
$username = 'FGAQuickCraft'
$jdk = 'C:\Program Files\Java\jdk-21.0.11'
$shulkerSlots = if ([string]::IsNullOrWhiteSpace($AmsJar)) { 27 } else { 54 }
$levelName = "quickcraft-smoke-$stamp"

New-Item -ItemType Directory -Force -Path $reportDir, $runDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaQuickCraftInput {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct Point { public int X, Y; }
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr window, ref Point point);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
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

    public static void Click(IntPtr window, int x, int y, bool right) {
        Point point = Scale(window, x, y);
        SetCursorPos(point.X, point.Y);
        uint down = right ? 0x0008u : 0x0002u;
        uint up = right ? 0x0010u : 0x0004u;
        mouse_event(down, 0, 0, 0, UIntPtr.Zero);
        mouse_event(up, 0, 0, 0, UIntPtr.Zero);
    }

    public static void Key(byte key, bool down) {
        keybd_event(key, 0, down ? 0u : 0x0002u, UIntPtr.Zero);
    }
}
'@

Add-Type -AssemblyName Microsoft.VisualBasic
$script:gameWindow = [IntPtr]::Zero
$script:gameProcessId = 0

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

function Send-ServerCommand([string] $command) {
    $server.StandardInput.WriteLine($command)
    $server.StandardInput.Flush()
    Start-Sleep -Milliseconds 450
}

function Focus-Game {
    if ($script:gameProcessId -ne 0) {
        [Microsoft.VisualBasic.Interaction]::AppActivate($script:gameProcessId)
    }
    if ($script:gameWindow -ne [IntPtr]::Zero) {
        [void][FgaQuickCraftInput]::SetForegroundWindow($script:gameWindow)
    }
    Start-Sleep -Milliseconds 120
}

function Click([IntPtr] $window, [int] $x, [int] $y) {
    Focus-Game
    [FgaQuickCraftInput]::Click($window, $x, $y, $false)
    Start-Sleep -Milliseconds 350
}

function Press-Key([byte] $key) {
    Focus-Game
    [FgaQuickCraftInput]::Key($key, $true)
    Start-Sleep -Milliseconds 120
    [FgaQuickCraftInput]::Key($key, $false)
    Start-Sleep -Milliseconds 500
}

function Hold-Rapid-Craft([int] $milliseconds = 2500) {
    Focus-Game
    [FgaQuickCraftInput]::Key(0x12, $true)
    [FgaQuickCraftInput]::Key(0x43, $true)
    Start-Sleep -Milliseconds $milliseconds
    [FgaQuickCraftInput]::Key(0x43, $false)
    [FgaQuickCraftInput]::Key(0x12, $false)
    Start-Sleep -Seconds 2
}

function Connect-Direct([IntPtr] $window, [string] $address) {
    Focus-Game
    Click $window 427 284
    Start-Sleep -Seconds 2
    Click $window 266 400
    Start-Sleep -Seconds 2
    Click $window 427 252
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.SendKeys]::SendWait('^a')
    [System.Windows.Forms.SendKeys]::SendWait($address)
    Click $window 427 356
}

function Full-Box([string] $itemId) {
    $items = 0..($shulkerSlots - 1) | ForEach-Object {
        '{slot:' + $_ + ',item:{id:"' + $itemId + '",count:64}}'
    }
    return 'minecraft:shulker_box[minecraft:container=[' + ($items -join ',') + ']]'
}

function Prepare-Stone-Boxes([int] $count) {
    Send-ServerCommand "clear $username"
    for ($i = 0; $i -lt $count; $i++) {
        Send-ServerCommand "give $username $stoneBox 1"
    }
    Start-Sleep -Seconds 1
}

function Open-Workbench([IntPtr] $window) {
    Send-ServerCommand "tp $username 0.5 100 0.5 0 30"
    Send-ServerCommand 'setblock 0 100 2 minecraft:crafting_table'
    Focus-Game
    [FgaQuickCraftInput]::Click($window, 427, 240, $true)
    Start-Sleep -Seconds 2
}

function Move-Hotbar-Zero-To-Workbench([IntPtr] $window) {
    Click $window 356 308
    Click $window 378 183
    Start-Sleep -Seconds 2
}

function Move-Hotbar-Zero-To-InventoryGrid([IntPtr] $window) {
    Click $window 356 308
    Click $window 446 184
    Start-Sleep -Seconds 2
}

function Assert-Score([string] $holder, [int] $value, [string] $marker) {
    Send-ServerCommand "execute if score $holder fga_qc matches $value run say $marker"
    Send-ServerCommand "execute unless score $holder fga_qc matches $value run say ${marker}_FAIL"
}

function Measure-Inventory([string] $prefix, [int] $expectedOutputs) {
    Send-ServerCommand "execute store result score #${prefix}_source fga_qc run clear $username $stoneBox 0"
    Send-ServerCommand "execute store result score #${prefix}_output fga_qc run clear $username $buttonBox 0"
    Assert-Score "#${prefix}_source" 0 "FGA_${prefix}_SOURCE_PASS"
    Assert-Score "#${prefix}_output" $expectedOutputs "FGA_${prefix}_OUTPUT_PASS"
}

$savedFiles = @('eula.txt', 'server.properties', 'ops.json', 'whitelist.json')
$backups = @{}
foreach ($name in $savedFiles) {
    $path = Join-Path $runDir $name
    if (Test-Path -LiteralPath $path) {
        $backup = "$path.before-quickcraft-smoke-$stamp"
        Move-Item -LiteralPath $path -Destination $backup
        $backups[$name] = $backup
    }
}

$modsDir = Join-Path $runDir 'mods'
$modsBackup = $null
if (Test-Path -LiteralPath $modsDir) {
    $modsBackup = "$modsDir.before-quickcraft-smoke-$stamp"
    Move-Item -LiteralPath $modsDir -Destination $modsBackup
}
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
if (-not [string]::IsNullOrWhiteSpace($AmsJar)) {
    if (-not (Test-Path -LiteralPath $AmsJar -PathType Leaf)) { throw "AMS jar not found: $AmsJar" }
    Copy-Item -LiteralPath $AmsJar -Destination $modsDir
}

Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
    'online-mode=false'
    'spawn-protection=0'
    'max-players=4'
    "server-port=$Port"
    'view-distance=2'
    'simulation-distance=2'
    'motd=FGA QuickCraft smoke'
    "level-name=$levelName"
    'gamemode=survival'
    'difficulty=peaceful'
)

if (-not [string]::IsNullOrWhiteSpace($AmsJar)) {
    $worldDir = Join-Path $runDir $levelName
    New-Item -ItemType Directory -Force -Path $worldDir | Out-Null
    [System.IO.File]::WriteAllLines(
        (Join-Path $worldDir 'carpet.conf'),
        @('largeShulkerBox true', 'fullShulkerBoxCrafting true'),
        [System.Text.UTF8Encoding]::new($false)
    )
}

New-Item -ItemType Directory -Force -Path $clientRunDir | Out-Null
if (-not [string]::IsNullOrWhiteSpace($AmsJar)) {
    $clientMods = Join-Path $clientRunDir 'mods'
    New-Item -ItemType Directory -Force -Path $clientMods | Out-Null
    Copy-Item -LiteralPath $AmsJar -Destination $clientMods
}
Set-Content -LiteralPath (Join-Path $clientRunDir 'options.txt') -Encoding ascii -Value @(
    'soundCategory_master:0.0'
    'soundCategory_music:0.0'
    'narrator:0'
    'showSubtitles:false'
    'fullscreen:false'
    'guiScale:2'
)

$server = $null
$client = $null
$result = [ordered]@{
    status = 'failed'
    workbench = $false
    inventory_grid = $false
    throw_path = $false
    quickcraft_loaded = $false
    fga_client_absent = $false
    ams_loaded = [string]::IsNullOrWhiteSpace($AmsJar)
    shulker_slots = $shulkerSlots
    report_directory = $reportDir
}

try {
    $env:JAVA_HOME = $jdk
    $serverInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $serverInfo.FileName = 'cmd.exe'
    $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 > "' + $serverLog + '" 2>&1"'
    $serverInfo.WorkingDirectory = $root
    $serverInfo.UseShellExecute = $false
    $serverInfo.RedirectStandardInput = $true
    $serverInfo.CreateNoWindow = $true
    $server = [System.Diagnostics.Process]::new()
    $server.StartInfo = $serverInfo
    [void] $server.Start()

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Read-Text $serverLog) -match 'Done \([0-9.]+s\)!') { break }
        Start-Sleep -Seconds 2
    }
    if ((Read-Text $serverLog) -notmatch 'Done \([0-9.]+s\)!') {
        throw 'temporary FGA server did not become ready'
    }

    $previousJava = @(Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object Id)
    $amsArgument = if ([string]::IsNullOrWhiteSpace($AmsJar)) { '' } else { ' -PamsJar="' + $AmsJar + '"' }
    $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -p "' + $clientFixture + '" runClient --no-daemon --max-workers=1' +
            ' -PquickcraftJar="' + $QuickCraftJar + '" -PrunDir="' + $clientRunDir + '"' +
            $amsArgument +
            ' --args="--username ' + $username + ' --width 854 --height 480" > "' + $clientLog + '" 2>&1"'
    $client = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru

    $window = $null
    $deadline = (Get-Date).AddMinutes(6)
    while ((Get-Date) -lt $deadline -and -not $client.HasExited) {
        $window = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
            $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' -and $previousJava -notcontains $_.Id
        } | Sort-Object StartTime -Descending | Select-Object -First 1
        if ($null -ne $window) { break }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $window) { throw 'QuickCraft test client window did not appear' }
    $script:gameWindow = $window.MainWindowHandle
    $script:gameProcessId = $window.Id

    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Text $clientLatestLog) -match 'Sound engine started') { break }
        if ($client.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    if ((Read-Text $clientLatestLog) -notmatch 'Sound engine started') {
        throw 'QuickCraft test client did not reach the main menu'
    }
    Start-Sleep -Seconds 3

    Connect-Direct $window.MainWindowHandle "127.0.0.1:$Port"
    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Text $serverLog) -match [regex]::Escape("$username joined the game")) { break }
        Start-Sleep -Seconds 1
    }
    if ((Read-Text $serverLog) -notmatch [regex]::Escape("$username joined the game")) {
        throw 'QuickCraft test client did not join the FGA server'
    }

    Send-ServerCommand "op $username"
    Send-ServerCommand 'scoreboard objectives add fga_qc dummy'
    Send-ServerCommand 'carpet fullShulkerBoxCrafting true'
    Send-ServerCommand "gamemode survival $username"

    $stoneBox = Full-Box 'minecraft:stone'
    $buttonBox = Full-Box 'minecraft:stone_button'

    Prepare-Stone-Boxes 3
    Open-Workbench $window.MainWindowHandle
    Move-Hotbar-Zero-To-Workbench $window.MainWindowHandle
    Hold-Rapid-Craft
    Measure-Inventory 'WORKBENCH' 3
    $serverText = Read-Text $serverLog
    $result.workbench = $serverText -match 'FGA_WORKBENCH_SOURCE_PASS' -and
            $serverText -match 'FGA_WORKBENCH_OUTPUT_PASS' -and
            $serverText -notmatch 'FGA_WORKBENCH_(SOURCE|OUTPUT)_PASS_FAIL'

    Press-Key 0x45
    Prepare-Stone-Boxes 3
    Press-Key 0x45
    Move-Hotbar-Zero-To-InventoryGrid $window.MainWindowHandle
    Hold-Rapid-Craft
    Measure-Inventory 'INVENTORY' 3
    $serverText = Read-Text $serverLog
    $result.inventory_grid = $serverText -match 'FGA_INVENTORY_SOURCE_PASS' -and
            $serverText -match 'FGA_INVENTORY_OUTPUT_PASS' -and
            $serverText -notmatch 'FGA_INVENTORY_(SOURCE|OUTPUT)_PASS_FAIL'

    Press-Key 0x45
    Prepare-Stone-Boxes 2
    foreach ($slot in 2..8) {
        Send-ServerCommand "item replace entity $username hotbar.$slot with minecraft:dirt 64"
    }
    foreach ($slot in 0..26) {
        Send-ServerCommand "item replace entity $username inventory.$slot with minecraft:dirt 64"
    }
    Open-Workbench $window.MainWindowHandle
    Move-Hotbar-Zero-To-Workbench $window.MainWindowHandle
    Send-ServerCommand "item replace entity $username hotbar.0 with minecraft:dirt 64"
    Hold-Rapid-Craft
    Send-ServerCommand "execute store result score #THROW_source fga_qc run clear $username $stoneBox 0"
    Send-ServerCommand 'execute store result score #THROW_drops fga_qc run kill @e[type=minecraft:item,distance=..8]'
    Assert-Score '#THROW_source' 0 'FGA_THROW_SOURCE_PASS'
    Assert-Score '#THROW_drops' 2 'FGA_THROW_DROPS_PASS'
    $serverText = Read-Text $serverLog
    $result.throw_path = $serverText -match 'FGA_THROW_SOURCE_PASS' -and
            $serverText -match 'FGA_THROW_DROPS_PASS' -and
            $serverText -notmatch 'FGA_THROW_(SOURCE|DROPS)_PASS_FAIL'

    $latestClient = Read-Text $clientLatestLog
    $result.quickcraft_loaded = $latestClient -match '(?m)^\s*- quickcraft '
    $result.fga_client_absent = $latestClient -notmatch '(?m)^\s*- carpet-fga-addition '
    $result.ams_loaded = [string]::IsNullOrWhiteSpace($AmsJar) -or
            (($latestClient -match '(?m)^\s*- carpet-ams-addition ') -and
             ((Read-Text $serverLog) -match '(?m)^\s*- carpet-ams-addition '))
    if (-not $result.quickcraft_loaded) { throw 'QuickCraft was not present in the client mod list' }
    if (-not $result.fga_client_absent) { throw 'FGA unexpectedly loaded on the test client' }
    if (-not $result.ams_loaded) { throw 'Carpet AMS Addition was not loaded on both sides' }
    if (-not $result.workbench -or -not $result.inventory_grid -or -not $result.throw_path) {
        throw 'one or more QuickCraft full-shulker scenarios failed'
    }

    $result.status = 'passed'
} catch {
    $result.error = $_.Exception.Message
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        try { Send-ServerCommand 'stop' } catch {}
        if (-not $server.WaitForExit(30000)) { Stop-Tree $server }
    }
    Stop-Tree $client
    if (Test-Path -LiteralPath $clientLatestLog) {
        Copy-Item -LiteralPath $clientLatestLog -Destination (Join-Path $reportDir 'client-latest.log')
    }

    foreach ($name in $savedFiles) {
        $path = Join-Path $runDir $name
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
        if ($backups.ContainsKey($name) -and (Test-Path -LiteralPath $backups[$name])) {
            Move-Item -LiteralPath $backups[$name] -Destination $path
        }
    }
    if (Test-Path -LiteralPath $modsDir) { Remove-Item -LiteralPath $modsDir -Recurse -Force }
    if ($null -ne $modsBackup -and (Test-Path -LiteralPath $modsBackup)) {
        Move-Item -LiteralPath $modsBackup -Destination $modsDir
    }

    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $reportDir 'summary.json') -Encoding utf8
    $result | ConvertTo-Json -Depth 4
}
