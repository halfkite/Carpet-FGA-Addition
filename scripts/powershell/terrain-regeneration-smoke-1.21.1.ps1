$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$level = "terrain-clear-smoke-$stamp"
$world = Join-Path $run $level
$report = Join-Path $root "scripts\logs\terrain-clear-smoke-$stamp"
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
[IO.Directory]::CreateDirectory($report) | Out-Null
$backups = @{}

function Start-TestServer([string] $log) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = 'cmd.exe'
    $info.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 > "' + $log + '" 2>&1"'
    $info.WorkingDirectory = $root
    $info.UseShellExecute = $false
    $info.RedirectStandardInput = $true
    $info.CreateNoWindow = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    [void] $process.Start()
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        if ([IO.File]::Exists($log)) {
            $stream = [IO.File]::Open($log, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
            try {
                $reader = [IO.StreamReader]::new($stream)
                try { $text = $reader.ReadToEnd() } finally { $reader.Dispose() }
            } finally { $stream.Dispose() }
        } else {
            $text = ''
        }
        if ($text -match 'Done \([0-9.]+s\)!') {
            return $process
        }
        Start-Sleep -Milliseconds 500
    }
    throw "Server did not reach Done: $log"
}

function Stop-TestServer([Diagnostics.Process] $process) {
    $process.StandardInput.WriteLine('stop')
    $process.StandardInput.Flush()
    if (-not $process.WaitForExit(60000)) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        throw 'Server stop timed out'
    }
}

function Read-ServerLog([string] $path) {
    if (-not [IO.File]::Exists($path)) { return '' }
    $stream = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $reader = [IO.StreamReader]::new($stream)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
}

$first = $null
foreach ($name in @('eula.txt', 'server.properties')) {
    $path = Join-Path $run $name
    if ([IO.File]::Exists($path)) {
        $backup = "$path.before-terrain-$stamp"
        [IO.File]::Move($path, $backup)
        $backups[$name] = $backup
    }
}
[IO.File]::WriteAllText((Join-Path $run 'eula.txt'), 'eula=true', [Text.Encoding]::ASCII)
[IO.File]::WriteAllLines((Join-Path $run 'server.properties'), @(
    'online-mode=false', 'server-port=0', 'view-distance=2', 'simulation-distance=2',
    "level-name=$level", 'difficulty=peaceful'
), [Text.Encoding]::ASCII)

try {
    $log1 = Join-Path $report 'first.log'
    $first = Start-TestServer $log1
    $first.StandardInput.WriteLine('forceload add 0 0')
    $first.StandardInput.WriteLine('setblock 0 100 0 minecraft:diamond_block')
    $first.StandardInput.WriteLine('regenerateTerrain clear from 0 0 0 0')
    $first.StandardInput.WriteLine('regenerateTerrain list')
    $first.StandardInput.Flush()
    $draftPattern = '(?m)(?<id>[0-9a-f]{8})\s+CLEAR\b.*\bDRAFT\b'
    $draftMatch = $null
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        $logText = Read-ServerLog $log1
        $draftMatch = [regex]::Match($logText, $draftPattern)
        if ($draftMatch.Success) { break }
        Start-Sleep -Milliseconds 250
    }
    if ($null -eq $draftMatch -or -not $draftMatch.Success) { throw 'Draft task was not listed' }
    $taskId = $draftMatch.Groups['id'].Value
    $first.StandardInput.WriteLine("regenerateTerrain confirm $taskId")
    $first.StandardInput.Flush()
    $completePattern = "Terrain task $taskId COMPLETE:"
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $logText = Read-ServerLog $log1
        if ($logText.Contains($completePattern)) { break }
        Start-Sleep -Milliseconds 500
    }
    if (-not $logText.Contains($completePattern)) { throw 'Terrain clear task did not complete' }
    $first.StandardInput.WriteLine('regenerateTerrain list')
    $first.StandardInput.WriteLine('save-all flush')
    $first.StandardInput.Flush()
    Start-Sleep -Seconds 2
    $first.StandardInput.WriteLine('regenerateTerrain clear from 0 0 2047 2047')
    $first.StandardInput.WriteLine('regenerateTerrain clear from 0 0 2063 2047')
    $first.StandardInput.Flush()
    $capDeadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $capDeadline) {
        $logText = Read-ServerLog $log1
        if ($logText.Contains('16384 total') -and $logText.Contains('task exceeds the 16384 chunk limit')) { break }
        Start-Sleep -Milliseconds 250
    }
    $capAccepted = $logText.Contains('16384 total')
    $overLimitRejected = $logText.Contains('task exceeds the 16384 chunk limit')
    $backupRoot = Join-Path $world 'config\carpetfgaaddition\terrain-regeneration-backups'
    $backupFiles = if ([IO.Directory]::Exists($backupRoot)) {
        @(Get-ChildItem -LiteralPath $backupRoot -Recurse -File)
    } else { @() }
    $result = [ordered]@{
        passed = $logText.Contains($completePattern) -and $backupFiles.Count -gt 0 -and $capAccepted -and $overLimitRejected
        task = $taskId
        status = if ($logText.Contains($completePattern)) { 'complete' } else { 'unknown' }
        taskCompleted = $logText.Contains($completePattern)
        backupFiles = $backupFiles.Count
        capAccepted = $capAccepted
        overLimitRejected = $overLimitRejected
        report = $report
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $report 'result.json') -Encoding UTF8
    $result | ConvertTo-Json
    if (-not $result.passed) { exit 1 }
} finally {
    foreach ($process in @($first)) {
        if ($null -ne $process -and -not $process.HasExited) {
            & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        }
    }
    if ([IO.Directory]::Exists($world)) { [IO.Directory]::Delete($world, $true) }
    foreach ($name in @('eula.txt', 'server.properties')) {
        $path = Join-Path $run $name
        if ([IO.File]::Exists($path)) { [IO.File]::Delete($path) }
        if ($backups.ContainsKey($name) -and [IO.File]::Exists($backups[$name])) {
            [IO.File]::Move($backups[$name], $path)
        }
    }
}
