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

$first = $null
$second = $null
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
    $first.StandardInput.WriteLine('setblock 0 100 0 minecraft:diamond_block')
    $first.StandardInput.WriteLine('regenerateTerrain clear box 0 0 0 0')
    $first.StandardInput.Flush()
    $config = Join-Path $world 'config\carpetfgaaddition\terrain-regeneration.json'
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline -and -not [IO.File]::Exists($config)) { Start-Sleep -Milliseconds 250 }
    if (-not [IO.File]::Exists($config)) { throw 'Task config was not created' }
    $draft = (Get-Content -LiteralPath $config -Raw | ConvertFrom-Json).tasks |
        Where-Object status -eq 'draft' | Select-Object -First 1
    if (-not $draft) { throw 'Draft task was not created' }
    $first.StandardInput.WriteLine("regenerateTerrain confirm $($draft.id)")
    $first.StandardInput.WriteLine('save-all flush')
    $first.StandardInput.Flush()
    Start-Sleep -Seconds 2
    Stop-TestServer $first
    $first = $null
    $queued = (Get-Content -LiteralPath $config -Raw | ConvertFrom-Json).tasks |
        Where-Object id -eq $draft.id
    if ($queued.status -ne 'confirmed') { throw "Unexpected queue status: $($queued.status)" }

    $log2 = Join-Path $report 'second.log'
    $second = Start-TestServer $log2
    $second.StandardInput.WriteLine('regenerateTerrain list')
    $second.StandardInput.Flush()
    Start-Sleep -Seconds 2
    Stop-TestServer $second
    $second = $null
    $after = (Get-Content -LiteralPath $config -Raw | ConvertFrom-Json).tasks |
        Where-Object id -eq $draft.id
    $logText = [IO.File]::ReadAllText($log2)
    $backupRoot = Join-Path $world 'config\carpetfgaaddition\terrain-regeneration-backups'
    $backupFiles = if ([IO.Directory]::Exists($backupRoot)) {
        @(Get-ChildItem -LiteralPath $backupRoot -Recurse -File)
    } else { @() }
    $result = [ordered]@{
        passed = $after.status -eq 'complete' -and $backupFiles.Count -gt 0
        task = $draft.id
        status = $after.status
        taskCompleted = $after.status -eq 'complete'
        backupFiles = $backupFiles.Count
        report = $report
    }
    $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $report 'result.json') -Encoding UTF8
    $result | ConvertTo-Json
    if (-not $result.passed) { exit 1 }
} finally {
    foreach ($process in @($first, $second)) {
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
