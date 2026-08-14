$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$run = Join-Path $root 'versions\1.21.1\run'
$world = Join-Path $run 'terrain-fluid-smoke-20260812-191148'
$config = Join-Path $world 'config\carpetfgaaddition\terrain-regeneration.json'
$report = Join-Path $root 'scripts\logs\terrain-fluid-smoke-20260812-191148'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'

function Read-Shared([string] $path) {
    if (-not (Test-Path -LiteralPath $path)) { return '' }
    $stream = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    try {
        $reader = [IO.StreamReader]::new($stream)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
}

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
        if ((Read-Shared $log) -match 'Done \([0-9.]+s\)!') { return $process }
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
try {
    $first = Start-TestServer (Join-Path $report 'buffer-queue.log')
    $first.StandardInput.WriteLine('fill 47 100 0 56 100 0 minecraft:air')
    $first.StandardInput.WriteLine('setblock 56 100 0 minecraft:water')
    $first.StandardInput.WriteLine('regenerateTerrain clear dimension minecraft:overworld box 32 0 47 15')
    $first.StandardInput.Flush()

    $deadline = (Get-Date).AddSeconds(20)
    $draft = $null
    do {
        Start-Sleep -Milliseconds 250
        $draft = @((Get-Content -Raw -LiteralPath $config | ConvertFrom-Json).tasks) |
            Where-Object status -eq 'draft' | Select-Object -Last 1
    } while (-not $draft -and (Get-Date) -lt $deadline)
    if (-not $draft) { throw 'Draft task was not created' }

    $first.StandardInput.WriteLine("regenerateTerrain confirm $($draft.id)")
    $first.StandardInput.WriteLine('save-all flush')
    $first.StandardInput.Flush()
    Start-Sleep -Seconds 2
    Stop-TestServer $first
    $first = $null

    $second = Start-TestServer (Join-Path $report 'buffer-execute.log')
    Start-Sleep -Seconds 15
    $second.StandardInput.WriteLine('save-all flush')
    $second.StandardInput.Flush()
    Start-Sleep -Seconds 2
    Stop-TestServer $second
    $second = $null

    $task = @((Get-Content -Raw -LiteralPath $config | ConvertFrom-Json).tasks) |
        Where-Object id -eq $draft.id
    [ordered]@{ task = $draft.id; status = $task.status; world = $world } | ConvertTo-Json
    if ($task.status -ne 'complete') { exit 1 }
} finally {
    foreach ($process in @($first, $second)) {
        if ($null -ne $process -and -not $process.HasExited) {
            & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        }
    }
}
