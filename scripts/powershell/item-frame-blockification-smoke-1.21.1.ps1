$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$log = Join-Path $run 'item-frame-blockification-smoke.log'
if (Test-Path -LiteralPath $log) { Remove-Item -LiteralPath $log -Force }

$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = 'cmd.exe'
$startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
    '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 > "' + $log + '" 2>&1"'
$startInfo.WorkingDirectory = $root
$startInfo.UseShellExecute = $false
$startInfo.RedirectStandardInput = $true
$startInfo.CreateNoWindow = $true
$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $startInfo
[void] $process.Start()

function Send-Command([string] $command, [int] $delay = 700) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

try {
    $deadline = (Get-Date).AddMinutes(4)
    $ready = $false
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        Start-Sleep -Seconds 2
        if ((Test-Path -LiteralPath $log) -and
            ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!')) {
            $ready = $true
            break
        }
    }
    if (-not $ready) { throw 'temporary server did not become ready' }

    Send-Command 'kill @e[type=minecraft:item_frame]'
    Send-Command 'kill @e[type=minecraft:glow_item_frame]'
    Send-Command 'fill -6 99 -6 8 99 8 minecraft:stone'
    Send-Command 'player FGAFrameTick spawn at 0 100 4'
    Send-Command 'carpet itemFrameBlockification true'
    Send-Command 'setblock 0 100 0 minecraft:stone'
    Send-Command 'summon minecraft:item_frame 0.5 100.5 1.03125 {Facing:3b,Tags:["fga_frame_test"]}' 6200
    Send-Command 'execute if entity @e[type=minecraft:item_frame,tag=fga_frame_test] run say FGA_FRAME_SURVIVED_100GT'
    Send-Command 'setblock 0 100 0 minecraft:air' 1000
    Send-Command 'execute unless entity @e[type=minecraft:item_frame,tag=fga_frame_test] run say FGA_FRAME_REMOVED_WITH_SUPPORT'

    Send-Command 'setblock 2 100 0 minecraft:stone'
    Send-Command 'summon minecraft:glow_item_frame 2.5 100.5 1.03125 {Facing:3b,Tags:["fga_glow_frame_test"]}' 6200
    Send-Command 'execute if entity @e[type=minecraft:glow_item_frame,tag=fga_glow_frame_test] run say FGA_GLOW_FRAME_SURVIVED_100GT'
    Send-Command 'setblock 2 100 0 minecraft:air' 1000
    Send-Command 'execute unless entity @e[type=minecraft:glow_item_frame,tag=fga_glow_frame_test] run say FGA_GLOW_FRAME_REMOVED_WITH_SUPPORT'

    Send-Command 'setblock 6 100 0 minecraft:stone'
    Send-Command 'setblock 6 100 1 minecraft:air'
    Send-Command 'summon minecraft:item_frame 6.5 100.5 1.03125 {Facing:3b,Tags:["fga_frame_collision_test"]}'
    Send-Command 'setblock 6 100 1 minecraft:stone' 1000
    Send-Command 'execute unless entity @e[type=minecraft:item_frame,tag=fga_frame_collision_test] run say FGA_FRAME_REMOVED_WITH_COLLISION'

    Send-Command 'setblock 4 100 0 minecraft:stone'
    Send-Command 'summon minecraft:item_frame 4.5 100.5 1.03125 {Facing:3b,Tags:["fga_frame_restore_test"]}'
    Send-Command 'carpet itemFrameBlockification false'
    Send-Command 'setblock 4 100 0 minecraft:air' 6200
    Send-Command 'execute unless entity @e[type=minecraft:item_frame,tag=fga_frame_restore_test] run say FGA_FRAME_VANILLA_TICK_RESTORED'
    Send-Command 'player FGAFrameTick kill'
    Send-Command 'stop' 100
    if (-not $process.WaitForExit(60000)) { throw 'temporary server did not stop' }
} finally {
    if (-not $process.HasExited) { $process.Kill($true) }
}

$raw = Get-Content -LiteralPath $log -Raw
$markers = @(
    'FGA_FRAME_SURVIVED_100GT'
    'FGA_FRAME_REMOVED_WITH_SUPPORT'
    'FGA_GLOW_FRAME_SURVIVED_100GT'
    'FGA_GLOW_FRAME_REMOVED_WITH_SUPPORT'
    'FGA_FRAME_REMOVED_WITH_COLLISION'
    'FGA_FRAME_VANILLA_TICK_RESTORED'
)
$missing = @($markers | Where-Object { $raw -notmatch $_ })
$fgaErrors = [regex]::Matches($raw, '(?im)^.*(?:Mixin apply for mod carpet-fga-addition|InvalidMixinException|ItemFrameBlockification.*(?:ERROR|Exception)).*$')
$parseErrors = [regex]::Matches($raw, '(?im)^.*(?:Unknown or incomplete command|Expected whitespace).*$')

Write-Output "READY=$ready"
Write-Output "EXIT=$($process.ExitCode)"
Write-Output "MISSING_MARKERS=$($missing.Count)"
Write-Output "FGA_ERRORS=$($fgaErrors.Count)"
Write-Output "PARSE_ERRORS=$($parseErrors.Count)"
Select-String -LiteralPath $log -Pattern 'itemFrameBlockification|FGA_FRAME|FGA_GLOW_FRAME|Done \(|Stopping server|InvalidMixin' |
    Select-Object -Last 60

if (-not $ready -or $process.ExitCode -ne 0 -or $missing.Count -ne 0 -or
    $fgaErrors.Count -ne 0 -or $parseErrors.Count -ne 0) {
    exit 1
}
