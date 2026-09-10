param(
    [int] $Port = 25581
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$version = '1.21.1'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\full-shulker-stonecutter-gui-$stamp"
$runDir = Join-Path $root "versions\$version\run"
$serverLog = Join-Path $reportDir 'server.log'
$clientLog = Join-Path $reportDir 'client.log'
$clientRunDir = Join-Path $reportDir 'client-run'
$clientLatestLog = Join-Path $clientRunDir 'logs\latest.log'
$username = 'FGAStonecutter'
$levelName = "full-shulker-stonecutter-gui-$stamp"
$objective = 'fga_sc'

New-Item -ItemType Directory -Force -Path $reportDir, $runDir, $clientRunDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaStonecutterInput {
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

    public static void Click(IntPtr window, int x, int y) {
        Point point = Scale(window, x, y);
        SetCursorPos(point.X, point.Y);
        mouse_event(0x0002u, 0, 0, 0, UIntPtr.Zero);
        mouse_event(0x0004u, 0, 0, 0, UIntPtr.Zero);
    }

    public static void Move(IntPtr window, int x, int y) {
        Point point = Scale(window, x, y);
        SetCursorPos(point.X, point.Y);
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

function Send-ServerCommand([string] $command, [int] $delay = 450) {
    $server.StandardInput.WriteLine($command)
    $server.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

function Focus-Game {
    if ($script:gameProcessId -ne 0) {
        [Microsoft.VisualBasic.Interaction]::AppActivate($script:gameProcessId)
    }
    if ($script:gameWindow -ne [IntPtr]::Zero) {
        [void][FgaStonecutterInput]::SetForegroundWindow($script:gameWindow)
    }
    Start-Sleep -Milliseconds 150
}

function Click([int] $x, [int] $y) {
    Focus-Game
    [FgaStonecutterInput]::Click($script:gameWindow, $x, $y)
    Start-Sleep -Milliseconds 450
}

function Press-Key([byte] $key) {
    Focus-Game
    [FgaStonecutterInput]::Key($key, $true)
    Start-Sleep -Milliseconds 120
    [FgaStonecutterInput]::Key($key, $false)
    Start-Sleep -Milliseconds 600
}

function Shift-Click([int] $x, [int] $y) {
    Focus-Game
    [FgaStonecutterInput]::Key(0x10, $true)
    [FgaStonecutterInput]::Click($script:gameWindow, $x, $y)
    [FgaStonecutterInput]::Key(0x10, $false)
    Start-Sleep -Milliseconds 750
}

function Connect-Direct([string] $address) {
    Click 427 284
    Start-Sleep -Seconds 2
    Click 266 400
    Start-Sleep -Seconds 2
    Click 427 252
    Add-Type -AssemblyName System.Windows.Forms
    [System.Windows.Forms.SendKeys]::SendWait('^a')
    [System.Windows.Forms.SendKeys]::SendWait($address)
    Click 427 356
}

function Full-Box([string] $itemId) {
    return Box-With-Count $itemId 1728
}

function Box-With-Count([string] $itemId, [int] $count) {
    if ($count -lt 1 -or $count -gt 1728) { throw "invalid box item count: $count" }
    $items = [Collections.Generic.List[string]]::new()
    $slot = 0
    while ($count -gt 0) {
        $stack = [Math]::Min(64, $count)
        $items.Add('{slot:' + $slot + ',item:{id:"' + $itemId + '",count:' + $stack + '}}')
        $slot++
        $count -= $stack
    }
    return 'minecraft:shulker_box[minecraft:container=[' + ($items -join ',') + ']]'
}

function Assert-Score([string] $holder, [int] $value, [string] $marker) {
    Send-ServerCommand "execute if score $holder $objective matches $value run say $marker"
    Send-ServerCommand "execute unless score $holder $objective matches $value run say ${marker}_FAIL"
}

function Measure-Inventory([string] $prefix, [string] $sourceBox, [string] $outputBox,
                           [int] $sources, [int] $outputs, [int] $totalBoxes) {
    Send-ServerCommand "execute store result score #${prefix}_source $objective run clear $username $sourceBox 0"
    Send-ServerCommand "execute store result score #${prefix}_output $objective run clear $username $outputBox 0"
    Send-ServerCommand "execute store result score #${prefix}_boxes $objective run clear $username minecraft:shulker_box 0"
    Assert-Score "#${prefix}_source" $sources "FGA_${prefix}_SOURCE_PASS"
    Assert-Score "#${prefix}_output" $outputs "FGA_${prefix}_OUTPUT_PASS"
    Assert-Score "#${prefix}_boxes" $totalBoxes "FGA_${prefix}_BOXES_PASS"
}

function Prepare-Player([string] $sourceBox, [int] $emptyBoxes) {
    Send-ServerCommand "clear $username"
    Send-ServerCommand "kill @e[type=minecraft:item,distance=..16]"
    Send-ServerCommand "give $username $sourceBox 1"
    if ($emptyBoxes -gt 0) {
        Send-ServerCommand "give $username minecraft:shulker_box $emptyBoxes"
    }
}

function Open-Stonecutter {
    Send-ServerCommand "tp $username 0.5 100 0.5 0 25"
    Send-ServerCommand 'setblock 0 100 2 minecraft:stonecutter'
    Focus-Game
    [FgaStonecutterInput]::Click($script:gameWindow, 427, 240)
    Start-Sleep -Seconds 2
}

function Load-Source-And-SelectRecipe {
    Click 356 308       # hotbar 0
    Click 367 198       # stonecutter input
    Start-Sleep -Seconds 1
    Click 399 180       # the only recipe supplied for this ingredient
    Start-Sleep -Seconds 1
}

function Close-Menu {
    Press-Key 0x45
    Start-Sleep -Seconds 1
}

$savedFiles = @('eula.txt', 'server.properties', 'ops.json', 'whitelist.json')
$backups = @{}
foreach ($name in $savedFiles) {
    $path = Join-Path $runDir $name
    if (Test-Path -LiteralPath $path) {
        $backup = "$path.before-stonecutter-gui-$stamp"
        Move-Item -LiteralPath $path -Destination $backup
        $backups[$name] = $backup
    }
}

$worldDir = Join-Path $runDir $levelName
$dataPack = Join-Path $worldDir 'datapacks\fga-stonecutter-smoke'
$recipeDir = Join-Path $dataPack 'data\fga_stonecutter_smoke\recipe'
New-Item -ItemType Directory -Force -Path $recipeDir | Out-Null
Set-Content -LiteralPath (Join-Path $dataPack 'pack.mcmeta') -Encoding utf8 -Value @'
{"pack":{"pack_format":48,"description":"FGA full shulker stonecutter smoke"}}
'@
Set-Content -LiteralPath (Join-Path $recipeDir 'coal_to_stone_bricks.json') -Encoding utf8 -Value @'
{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:coal"},"result":{"id":"minecraft:stone_bricks","count":1}}
'@
Set-Content -LiteralPath (Join-Path $recipeDir 'charcoal_to_stone_slabs.json') -Encoding utf8 -Value @'
{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:charcoal"},"result":{"id":"minecraft:stone_slab","count":2}}
'@
Set-Content -LiteralPath (Join-Path $recipeDir '00_diamond_to_gold.json') -Encoding utf8 -Value @'
{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:diamond"},"result":{"id":"minecraft:gold_block","count":1}}
'@
Set-Content -LiteralPath (Join-Path $recipeDir '01_diamond_to_iron.json') -Encoding utf8 -Value @'
{"type":"minecraft:stonecutting","ingredient":{"item":"minecraft:diamond"},"result":{"id":"minecraft:iron_block","count":1}}
'@

Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
    'online-mode=false'
    'spawn-protection=0'
    'max-players=2'
    "server-port=$Port"
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
    'fullscreen:false'
    'guiScale:2'
)

$server = $null
$client = $null
$result = [ordered]@{
    status = 'failed'
    rule_disabled = $false
    pickup_1_to_1 = $false
    missing_empty_box = $false
    supplied_empty_box = $false
    partial_input = $false
    partial_final_box = $false
    recipe_switch = $false
    cleared_result = $false
    quick_move = $false
    throw = $false
    report_directory = $reportDir
}

try {
    $serverInfo = [Diagnostics.ProcessStartInfo]::new()
    $serverInfo.FileName = 'cmd.exe'
    $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :1.21.1:runServer --no-daemon --max-workers=1 > "' + $serverLog + '" 2>&1"'
    $serverInfo.WorkingDirectory = $root
    $serverInfo.UseShellExecute = $false
    $serverInfo.RedirectStandardInput = $true
    $serverInfo.CreateNoWindow = $true
    $server = [Diagnostics.Process]::new()
    $server.StartInfo = $serverInfo
    [void] $server.Start()

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Read-Text $serverLog) -match 'Done \([0-9.]+s\)!') { break }
        Start-Sleep -Seconds 2
    }
    if ((Read-Text $serverLog) -notmatch 'Done \([0-9.]+s\)!') { throw 'server did not become ready' }

    $previousJava = @(Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object Id)
    $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :1.21.1:runClient --no-daemon --max-workers=1 -PclientRunDir="' + $clientRunDir +
            '" --args="--username ' + $username + ' --width 854 --height 480" > "' + $clientLog + '" 2>&1"'
    $client = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments -WorkingDirectory $root -WindowStyle Hidden -PassThru

    $window = $null
    $deadline = (Get-Date).AddMinutes(6)
    while ((Get-Date) -lt $deadline -and -not $client.HasExited) {
        $window = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
            $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' -and
                    $previousJava -notcontains $_.Id
        } | Sort-Object StartTime -Descending | Select-Object -First 1
        if ($null -ne $window) { break }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $window) { throw 'Minecraft client window did not appear' }
    $script:gameWindow = $window.MainWindowHandle
    $script:gameProcessId = $window.Id
    Start-Sleep -Seconds 5
    Connect-Direct "127.0.0.1:$Port"

    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline -and (Read-Text $serverLog) -notmatch "$username joined the game") {
        Start-Sleep -Seconds 1
    }
    if ((Read-Text $serverLog) -notmatch "$username joined the game") { throw 'client did not join' }

    Send-ServerCommand "op $username"
    Send-ServerCommand "scoreboard objectives add $objective dummy"
    Send-ServerCommand "gamemode survival $username"

    $coalBox = Full-Box 'minecraft:coal'
    $brickBox = Full-Box 'minecraft:stone_bricks'
    $charcoalBox = Full-Box 'minecraft:charcoal'
    $slabBox = Full-Box 'minecraft:stone_slab'
    $diamondBox = Full-Box 'minecraft:diamond'
    $ironBox = Full-Box 'minecraft:iron_block'

    Send-ServerCommand 'carpet fullShulkerBoxCrafting false'
    Prepare-Player $coalBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 490 198
    Close-Menu
    Measure-Inventory 'DISABLED' $coalBox $brickBox 1 0 1

    Send-ServerCommand 'carpet fullShulkerBoxCrafting only64'

    Prepare-Player $diamondBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 420 180       # switch from recipe 00 (gold) to recipe 01 (iron)
    Click 490 198
    Close-Menu
    Measure-Inventory 'SWITCH' $diamondBox $ironBox 0 1 1

    Prepare-Player $coalBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 490 198
    Close-Menu
    Measure-Inventory 'PICKUP' $coalBox $brickBox 0 1 1

    Prepare-Player $charcoalBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 490 198
    Close-Menu
    Measure-Inventory 'MISSING' $charcoalBox $slabBox 1 0 1

    Prepare-Player $charcoalBox 1
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 490 198
    Close-Menu
    Measure-Inventory 'SUPPLIED' $charcoalBox $slabBox 0 2 2

    Send-ServerCommand 'carpet fullShulkerBoxCrafting any'
    $partialStoneBox = Box-With-Count 'minecraft:coal' 65
    $partialBrickBox = Box-With-Count 'minecraft:stone_bricks' 65
    Prepare-Player $partialStoneBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 490 198
    Close-Menu
    Measure-Inventory 'PARTIAL' $partialStoneBox $partialBrickBox 0 1 1

    $partialCharcoalBox = Box-With-Count 'minecraft:charcoal' 1000
    $partialSlabBox = Box-With-Count 'minecraft:stone_slab' 272
    Prepare-Player $partialCharcoalBox 1
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 490 198
    Close-Menu
    Send-ServerCommand "execute store result score #FINAL_source $objective run clear $username $partialCharcoalBox 0"
    Send-ServerCommand "execute store result score #FINAL_full $objective run clear $username $slabBox 0"
    Send-ServerCommand "execute store result score #FINAL_partial $objective run clear $username $partialSlabBox 0"
    Send-ServerCommand "execute store result score #FINAL_boxes $objective run clear $username minecraft:shulker_box 0"
    Assert-Score '#FINAL_source' 0 'FGA_FINAL_SOURCE_PASS'
    Assert-Score '#FINAL_full' 1 'FGA_FINAL_FULL_PASS'
    Assert-Score '#FINAL_partial' 1 'FGA_FINAL_PARTIAL_PASS'
    Assert-Score '#FINAL_boxes' 2 'FGA_FINAL_BOXES_PASS'

    Send-ServerCommand 'carpet fullShulkerBoxCrafting only64'

    Prepare-Player $coalBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Click 367 198       # remove input; stale recipeUsed must not keep the result takeable
    Click 490 198
    Close-Menu
    Measure-Inventory 'CLEARED' $coalBox $brickBox 1 0 1

    Prepare-Player $coalBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Shift-Click 490 198
    Close-Menu
    Measure-Inventory 'SHIFT' $coalBox $brickBox 0 1 1

    Prepare-Player $coalBox 0
    Open-Stonecutter
    Load-Source-And-SelectRecipe
    Focus-Game
    [FgaStonecutterInput]::Move($script:gameWindow, 490, 198)
    Press-Key 0x51
    Close-Menu
    Send-ServerCommand "execute store result score #THROW_source $objective run clear $username $coalBox 0"
    Send-ServerCommand "execute store result score #THROW_inventory $objective run clear $username minecraft:shulker_box 0"
    Send-ServerCommand "execute store result score #THROW_drops $objective run kill @e[type=minecraft:item,distance=..16]"
    Assert-Score '#THROW_source' 0 'FGA_THROW_SOURCE_PASS'
    Assert-Score '#THROW_inventory' 0 'FGA_THROW_INVENTORY_PASS'
    Assert-Score '#THROW_drops' 1 'FGA_THROW_DROPS_PASS'

    Send-ServerCommand 'say FGA_STONECUTTER_GUI_DONE'
    $raw = Read-Text $serverLog
    $result.rule_disabled = $raw -match 'FGA_DISABLED_SOURCE_PASS' -and
            $raw -match 'FGA_DISABLED_OUTPUT_PASS' -and $raw -match 'FGA_DISABLED_BOXES_PASS'
    $result.recipe_switch = $raw -match 'FGA_SWITCH_SOURCE_PASS' -and
            $raw -match 'FGA_SWITCH_OUTPUT_PASS' -and $raw -match 'FGA_SWITCH_BOXES_PASS'
    $result.pickup_1_to_1 = $raw -match 'FGA_PICKUP_SOURCE_PASS' -and
            $raw -match 'FGA_PICKUP_OUTPUT_PASS' -and $raw -match 'FGA_PICKUP_BOXES_PASS'
    $result.missing_empty_box = $raw -match 'FGA_MISSING_SOURCE_PASS' -and
            $raw -match 'FGA_MISSING_OUTPUT_PASS' -and $raw -match 'FGA_MISSING_BOXES_PASS'
    $result.supplied_empty_box = $raw -match 'FGA_SUPPLIED_SOURCE_PASS' -and
            $raw -match 'FGA_SUPPLIED_OUTPUT_PASS' -and $raw -match 'FGA_SUPPLIED_BOXES_PASS'
    $result.partial_input = $raw -match 'FGA_PARTIAL_SOURCE_PASS' -and
            $raw -match 'FGA_PARTIAL_OUTPUT_PASS' -and $raw -match 'FGA_PARTIAL_BOXES_PASS'
    $result.partial_final_box = $raw -match 'FGA_FINAL_SOURCE_PASS' -and
            $raw -match 'FGA_FINAL_FULL_PASS' -and $raw -match 'FGA_FINAL_PARTIAL_PASS' -and
            $raw -match 'FGA_FINAL_BOXES_PASS'
    $result.cleared_result = $raw -match 'FGA_CLEARED_SOURCE_PASS' -and
            $raw -match 'FGA_CLEARED_OUTPUT_PASS' -and $raw -match 'FGA_CLEARED_BOXES_PASS'
    $result.quick_move = $raw -match 'FGA_SHIFT_SOURCE_PASS' -and
            $raw -match 'FGA_SHIFT_OUTPUT_PASS' -and $raw -match 'FGA_SHIFT_BOXES_PASS'
    $result.throw = $raw -match 'FGA_THROW_SOURCE_PASS' -and
            $raw -match 'FGA_THROW_INVENTORY_PASS' -and $raw -match 'FGA_THROW_DROPS_PASS'
    if ($raw -match 'FGA_[A-Z_]+_PASS_FAIL|Mixin apply failed|InvalidMixinException|InjectionError') {
        throw 'one or more stonecutter assertions failed'
    }
    if (-not $result.rule_disabled -or -not $result.recipe_switch -or
            -not $result.pickup_1_to_1 -or
            -not $result.missing_empty_box -or -not $result.supplied_empty_box -or
            -not $result.partial_input -or -not $result.partial_final_box -or
            -not $result.cleared_result -or -not $result.quick_move -or -not $result.throw) {
        throw 'one or more stonecutter markers were missing'
    }
    $result.status = 'passed'
} catch {
    $result.error = $_.Exception.Message
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        try { Send-ServerCommand 'stop' 100 } catch {}
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
    $result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $reportDir 'summary.json') -Encoding utf8
    $result | ConvertTo-Json -Depth 4
}

if ($result.status -ne 'passed') { exit 1 }
