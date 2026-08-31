$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "full-shulker-stonecutter-smoke-$stamp"
$log = Join-Path $run "$worldName.log"

[IO.File]::WriteAllText((Join-Path $run 'eula.txt'), 'eula=true', [Text.Encoding]::ASCII)

$startInfo = [Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = 'cmd.exe'
$startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
    '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0 --world ' +
    $worldName + '" > "' + $log + '" 2>&1"'
$startInfo.WorkingDirectory = $root
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.CreateNoWindow = $true
$server = [Diagnostics.Process]::new()
$server.StartInfo = $startInfo
[void] $server.Start()

function Send-Command([string] $command, [int] $delay = 300) {
    $server.StandardInput.WriteLine($command)
    $server.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

try {
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        Start-Sleep -Seconds 1
        if ((Test-Path -LiteralPath $log) -and
            ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!')) {
            break
        }
    }
    if ($server.HasExited -or !(Test-Path -LiteralPath $log) -or
        ((Get-Content -LiteralPath $log -Raw) -notmatch 'Done \([0-9.]+s\)!')) {
        throw "Temporary server did not become ready: $log"
    }

    # The fake player comes online first so the area around the test site stays loaded.
    Send-Command 'player FGAFullBox spawn at 4 101 6' 2500
    Send-Command 'fill 0 99 0 8 99 8 minecraft:stone' 900
    Send-Command 'fill 0 99 0 8 99 8 minecraft:stone' 900
    Send-Command 'execute if block 4 99 4 minecraft:stone run say FGA_PLATFORM_OK' 300
    Send-Command 'gamerule doMobSpawning false' 300
    Send-Command 'difficulty normal' 300

    # Rule registration, three-mode handling and rejection
    Send-Command 'carpet fullShulkerBoxCrafting banana' 600
    Send-Command 'carpet fullShulkerBoxCrafting true' 600
    Send-Command 'carpet fullShulkerBoxCrafting only64' 400
    Send-Command 'carpet fullShulkerBoxCrafting any' 400
    Send-Command 'say FGA_RULE_RESULTS' 400

    # Stonecutter + menu open: exercises the three new stonecutter mixins on the vanilla path
    # (empty input, rule on). Any injection failure would crash or log a mixin error here.
    # Vanilla stonecutters store no items in the world, so the full-box branch of the flow
    # (input slot content) can only be exercised in-game and is verified manually.
    Send-Command 'setblock 4 100 4 minecraft:stonecutter' 700
    Send-Command 'setblock 4 100 4 minecraft:stonecutter' 700
    Send-Command 'execute if block 4 100 4 minecraft:stonecutter run say FGA_SC_OK' 300
    Send-Command 'carpet woodStonecuttingRecipes true' 400
    Send-Command 'player FGAFullBox look north' 400
    Send-Command 'player FGAFullBox use' 1000
    Send-Command 'say FGA_MENU_OPEN1' 1000
    Send-Command 'carpet fullShulkerBoxCrafting false' 500
    Send-Command 'player FGAFullBox use' 1000
    Send-Command 'say FGA_MENU_OPEN2' 1000
    Send-Command 'carpet fullShulkerBoxCrafting any' 500

    Send-Command 'kill @e[type=minecraft:item]' 400
    Send-Command 'player FGAFullBox kill' 500
    Send-Command 'stop' 100
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop' }
} finally {
    if (-not $server.HasExited) { $server.Kill($true) }
}

$raw = Get-Content -LiteralPath $log -Raw
function Get-Window([string] $text, [string] $from, [string] $to) {
    $m = [regex]::Match($text, [regex]::Escape($from) + '[\s\S]*?' + [regex]::Escape($to))
    if ($m.Success) { $m.Value } else { '' }
}
$menu1 = Get-Window $raw 'FGA_MENU_OPEN1' 'FGA_MENU_OPEN2'
$menu2 = Get-Window $raw 'FGA_MENU_OPEN2' 'kill @e'

$checks = [ordered]@{
    ServerReady      = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError     = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError|Critical injection failure'
    PlatformReady    = $raw -match 'FGA_PLATFORM_OK'
    StonecutterOK    = $raw -match 'FGA_SC_OK'
    RuleRegistered   = $raw -match 'fullShulkerBoxCrafting'
    InvalidRejected  = $raw -match 'fullShulkerBoxCrafting must be false, only64, or any'
    TrueNormalized   = $raw -match 'fullShulkerBoxCrafting: any'
    Only64Accepted   = $raw -match 'fullShulkerBoxCrafting: only64'
    MenuOpen1        = $raw -match 'FGA_MENU_OPEN1'
    MenuOpen2        = $raw -match 'FGA_MENU_OPEN2'
    Menu1Clean       = $menu1 -notmatch 'Exception|Error while|Mixin'
    Menu2Clean       = $menu2 -notmatch 'Exception|Error while|Mixin'
    NoServerCrash    = $raw -notmatch 'Fatal errors were detected|Failed to start server'
    CleanStop        = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$log"
Select-String -LiteralPath $log -Pattern 'fullShulkerBoxCrafting|FGA_|Exception|Mixin' |
    Select-Object -Last 40

if ($checks.Values -contains $false) { exit 1 }
