$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$log = Join-Path $run 'item-frame-blockification-benchmark.log'
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

function Send-Command([string] $command) {
    $process.StandardInput.WriteLine($command)
}

function Stop-ProcessTree([int] $processId) {
    & taskkill.exe /PID $processId /T /F 2>$null | Out-Null
}

function Wait-ForPattern([string] $pattern, [int] $timeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        Start-Sleep -Milliseconds 500
        if ((Test-Path -LiteralPath $log) -and
            ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match $pattern)) {
            return
        }
    }
    throw "timed out waiting for log pattern: $pattern"
}

try {
    Wait-ForPattern 'Done \([0-9.]+s\)!' 240
    Send-Command 'kill @e[type=minecraft:item_frame]'
    Send-Command 'fill 0 40 0 63 71 0 minecraft:stone'
    Send-Command 'fill 28 39 2 36 39 8 minecraft:stone'
    Send-Command 'player FGAFrameBench spawn at 32 40 5'
    Send-Command 'carpet itemFrameBlockification false'
    for ($x = 0; $x -lt 64; $x++) {
        for ($y = 40; $y -lt 72; $y++) {
            Send-Command "summon minecraft:item_frame $($x + 0.5) $($y + 0.5) 1.03125 {Facing:3b}"
        }
    }
    Send-Command 'say FGA_BENCH_READY'
    $process.StandardInput.Flush()
    Wait-ForPattern 'FGA_BENCH_READY' 180
    Start-Sleep -Seconds 6

    Send-Command 'tick sprint 1000'
    $process.StandardInput.Flush()
    Wait-ForPattern 'Sprint completed with .* ms per tick' 120
    $before = Get-Content -LiteralPath $log -Raw
    $vanillaMatches = [regex]::Matches($before, 'Sprint completed with .*?or ([0-9.]+) ms per tick')
    if ($vanillaMatches.Count -eq 0) { throw 'could not parse baseline sprint result' }
    $baselineMspt = [double] $vanillaMatches[$vanillaMatches.Count - 1].Groups[1].Value

    Send-Command 'carpet itemFrameBlockification true'
    Send-Command 'tick sprint 1000'
    $process.StandardInput.Flush()
    $expectedResults = $vanillaMatches.Count + 1
    $deadline = (Get-Date).AddSeconds(120)
    do {
        Start-Sleep -Milliseconds 500
        $current = Get-Content -LiteralPath $log -Raw
        $optimizedMatches = [regex]::Matches($current, 'Sprint completed with .*?or ([0-9.]+) ms per tick')
    } while ($optimizedMatches.Count -lt $expectedResults -and (Get-Date) -lt $deadline -and -not $process.HasExited)
    if ($optimizedMatches.Count -lt $expectedResults) { throw 'optimized sprint did not complete' }
    $optimizedMspt = [double] $optimizedMatches[$optimizedMatches.Count - 1].Groups[1].Value

    Send-Command 'player FGAFrameBench kill'
    Send-Command 'stop'
    $process.StandardInput.Flush()
    if (-not $process.WaitForExit(60000)) { throw 'temporary server did not stop' }

    $speedup = if ($optimizedMspt -gt 0) { $baselineMspt / $optimizedMspt } else { 0 }
    Write-Output 'ITEM_FRAMES=2048'
    Write-Output "BASELINE_MSPT=$baselineMspt"
    Write-Output "BLOCKIFIED_MSPT=$optimizedMspt"
    Write-Output "SPEEDUP=$([math]::Round($speedup, 2))x"
} finally {
    if (-not $process.HasExited) { Stop-ProcessTree $process.Id }
}
