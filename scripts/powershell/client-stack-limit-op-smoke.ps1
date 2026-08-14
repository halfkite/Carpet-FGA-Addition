param(
    [string] $Version = '1.16.5',
    [int] $Port = 25578,
    [Parameter(Mandatory = $true)]
    [string] $TweakermoreJar,
    [Parameter(Mandatory = $true)]
    [string] $MalilibJar
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\client-stack-limit-op-smoke-$stamp"
$runDir = Join-Path $root "versions\$Version\run"
$serverLog = Join-Path $reportDir 'server.log'
$clientLog = Join-Path $reportDir 'client.log'
$username = "FGAOp$($Version.Replace('.', ''))"
$jdk = if ($Version -like '26.*') { 'C:\Program Files\Java\jdk-25.0.3' } else { 'C:\Program Files\Java\jdk-21.0.11' }

New-Item -ItemType Directory -Force -Path $reportDir, $runDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaOpSmokeInput {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct Point { public int X, Y; }
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr window, ref Point point);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr window);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
    public static void Click(IntPtr window, int x, int y) {
        Rect rect;
        if (GetClientRect(window, out rect)) {
            x = x * Math.Max(1, rect.Right - rect.Left) / 854;
            y = y * Math.Max(1, rect.Bottom - rect.Top) / 480;
        }
        Point point = new Point { X = x, Y = y };
        ClientToScreen(window, ref point);
        SetCursorPos(point.X, point.Y);
        mouse_event(0x0002, 0, 0, 0, UIntPtr.Zero);
        mouse_event(0x0004, 0, 0, 0, UIntPtr.Zero);
    }
}
'@

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName Microsoft.VisualBasic

function Read-Text([string] $path) {
    if (Test-Path -LiteralPath $path) { return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue }
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
    Start-Sleep -Milliseconds 600
}

function Send-ClientCommand([IntPtr] $window, [string] $command) {
    [void][FgaOpSmokeInput]::SetForegroundWindow($window)
    [Microsoft.VisualBasic.Interaction]::AppActivate((Get-Process | Where-Object { $_.MainWindowHandle -eq $window }).Id)
    [System.Windows.Forms.SendKeys]::SendWait('/')
    Start-Sleep -Milliseconds 200
    Set-Clipboard -Value ($command.TrimStart('/'))
    [System.Windows.Forms.SendKeys]::SendWait('^v')
    [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
    Start-Sleep -Milliseconds 500
}

function Connect-Direct([IntPtr] $window, [string] $address) {
    [void][FgaOpSmokeInput]::SetForegroundWindow($window)
    [Microsoft.VisualBasic.Interaction]::AppActivate((Get-Process | Where-Object { $_.MainWindowHandle -eq $window }).Id)
    Start-Sleep -Seconds 3
    [FgaOpSmokeInput]::Click($window, 427, 284)
    Start-Sleep -Seconds 3
    [FgaOpSmokeInput]::Click($window, 266, 400)
    Start-Sleep -Seconds 2
    [FgaOpSmokeInput]::Click($window, 427, 396)
    Start-Sleep -Seconds 2
    [FgaOpSmokeInput]::Click($window, 427, 252)
    [System.Windows.Forms.SendKeys]::SendWait('^a')
    [System.Windows.Forms.SendKeys]::SendWait($address)
    [FgaOpSmokeInput]::Click($window, 427, 356)
}

$savedFiles = @('eula.txt', 'server.properties', 'ops.json', 'whitelist.json')
$backups = @{}
foreach ($name in $savedFiles) {
    $path = Join-Path $runDir $name
    if (Test-Path -LiteralPath $path) {
        $backup = "$path.before-op-smoke-$stamp"
        Move-Item -LiteralPath $path -Destination $backup
        $backups[$name] = $backup
    }
}

if ($Version -ne '1.16.5') { throw 'This TweakerMore-based OP client smoke currently supports 1.16.5 only' }
if (-not (Test-Path -LiteralPath $TweakermoreJar) -or -not (Test-Path -LiteralPath $MalilibJar)) {
    throw 'TweakerMore and MaLiLib JAR paths are required for offline multiplayer testing'
}
$modsDir = Join-Path $runDir 'mods'
$modsBackup = $null
if (Test-Path -LiteralPath $modsDir) {
    $modsBackup = "$modsDir.before-op-smoke-$stamp"
    Move-Item -LiteralPath $modsDir -Destination $modsBackup
}
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
Copy-Item -LiteralPath $TweakermoreJar -Destination $modsDir
Copy-Item -LiteralPath $MalilibJar -Destination $modsDir
$tweakermoreConfig = Join-Path $runDir 'config\tweakermore.json'
$tweakermoreConfigBackup = $null
if (Test-Path -LiteralPath $tweakermoreConfig) {
    $tweakermoreConfigBackup = "$tweakermoreConfig.before-op-smoke-$stamp"
    Move-Item -LiteralPath $tweakermoreConfig -Destination $tweakermoreConfigBackup
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $tweakermoreConfig) | Out-Null
Set-Content -LiteralPath $tweakermoreConfig -Encoding utf8 -Value '{"Generic":{"multiplayerForcedEnabled":true}}'

Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
    'online-mode=false'
    'spawn-protection=0'
    'max-players=4'
    "server-port=$Port"
    'view-distance=2'
    'simulation-distance=2'
    'motd=FGA op stack smoke'
    "level-name=op-smoke-$Version-$stamp"
    'gamemode=survival'
    'difficulty=peaceful'
)

$server = $null
$client = $null
try {
    $env:JAVA_HOME = $jdk
    $serverInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $serverInfo.FileName = 'cmd.exe'
    $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" :' + $Version + ':runServer --no-daemon --configure-on-demand --max-workers=1 > "' + $serverLog + '" 2>&1"'
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
    if ((Read-Text $serverLog) -notmatch 'Done \([0-9.]+s\)!') { throw 'temporary dedicated server did not become ready' }

    $previousJava = @(Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object Id)
    $clientArguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" :' + $Version + ':runClient --no-daemon --configure-on-demand --max-workers=1 --args="--username ' + $username + '" > "' + $clientLog + '" 2>&1"'
    $client = Start-Process -FilePath 'cmd.exe' -ArgumentList $clientArguments -WorkingDirectory $root -WindowStyle Hidden -PassThru

    $window = $null
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $client.HasExited) {
        $window = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
            $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' -and $previousJava -notcontains $_.Id
        } | Sort-Object StartTime -Descending | Select-Object -First 1
        if ($null -ne $window) { break }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $window) { throw 'test client window did not appear' }
    Connect-Direct $window.MainWindowHandle "127.0.0.1:$Port"

    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Text $serverLog) -match [regex]::Escape("$username joined the game")) { break }
        Start-Sleep -Seconds 1
    }
    if ((Read-Text $serverLog) -notmatch [regex]::Escape("$username joined the game")) { throw 'test client did not join dedicated server' }

    Send-ServerCommand "op $username"
    Send-ClientCommand $window.MainWindowHandle '/execute if entity @s run say FGA_OPERATOR_COMMAND_PASS'
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline -and (Read-Text $serverLog) -notmatch 'FGA_OPERATOR_COMMAND_PASS') { Start-Sleep -Seconds 1 }
    if ((Read-Text $serverLog) -notmatch 'FGA_OPERATOR_COMMAND_PASS') { throw 'test player did not receive operator permission' }

    foreach ($command in @(
        '/carpet droppedItemStackLimit true',
        '/droppedItemStackLimit mode inventory 1000',
        '/clear @s',
        '/give @s minecraft:stone 200',
        '/scoreboard objectives add fga_stack dummy',
        '/execute store result score #slot fga_stack run data get entity @s Inventory[{Slot:0b}].count',
        '/execute if score #slot fga_stack matches 200 run say FGA_INVENTORY_STACK_PASS',
        '/execute unless score #slot fga_stack matches 200 run say FGA_INVENTORY_STACK_FAIL'
    )) {
        Send-ClientCommand $window.MainWindowHandle $command
    }
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline -and (Read-Text $serverLog) -notmatch 'FGA_INVENTORY_STACK_PASS|FGA_INVENTORY_STACK_FAIL') { Start-Sleep -Seconds 1 }
    $raw = Read-Text $serverLog
    if ($raw -notmatch 'FGA_INVENTORY_STACK_PASS' -or $raw -match 'FGA_INVENTORY_STACK_FAIL') {
        throw 'player inventory stack did not reach 200 items'
    }

    Write-Output "OP_SMOKE_PASS version=$Version player=$username"
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        try { Send-ServerCommand 'stop' } catch {}
        if (-not $server.WaitForExit(30000)) { Stop-Tree $server }
    }
    Stop-Tree $client
    foreach ($name in $savedFiles) {
        $path = Join-Path $runDir $name
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
        if ($backups.ContainsKey($name) -and (Test-Path -LiteralPath $backups[$name])) {
            Move-Item -LiteralPath $backups[$name] -Destination $path
        }
    }
    if (Test-Path -LiteralPath $tweakermoreConfig) { Remove-Item -LiteralPath $tweakermoreConfig -Force }
    if ($null -ne $tweakermoreConfigBackup -and (Test-Path -LiteralPath $tweakermoreConfigBackup)) {
        Move-Item -LiteralPath $tweakermoreConfigBackup -Destination $tweakermoreConfig
    }
    if (Test-Path -LiteralPath $modsDir) { Remove-Item -LiteralPath $modsDir -Recurse -Force }
    if ($null -ne $modsBackup -and (Test-Path -LiteralPath $modsBackup)) {
        Move-Item -LiteralPath $modsBackup -Destination $modsDir
    }
}
