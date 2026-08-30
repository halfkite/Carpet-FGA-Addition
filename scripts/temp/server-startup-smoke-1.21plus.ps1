$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$versions = @('1.21','1.21.1','1.21.4','1.21.5','1.21.8','1.21.10','1.21.11','26.1.2','26.2')
$results = @()

foreach ($version in $versions) {
    $env:JAVA_HOME = if ($version.StartsWith('26.')) { 'C:\Program Files\Java\jdk-25.0.3' } else { 'C:\Program Files\Java\jdk-21.0.11' }
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    $run = Join-Path $root "versions\$version\run"
    New-Item -ItemType Directory -Force -Path $run | Out-Null
    [IO.File]::WriteAllText((Join-Path $run 'eula.txt'), 'eula=true', [Text.Encoding]::ASCII)
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $worldName = "fga-smoke-$($version.Replace('.', '_'))-$stamp"
    $log = Join-Path $run "$worldName.log"

    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        ' :' + $version + ':runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0 --world ' +
        $worldName + '" > "' + $log + '" 2>&1"'
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.CreateNoWindow = $true
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void] $process.Start()

    $ready = $false
    try {
        $deadline = (Get-Date).AddMinutes(4)
        while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
            Start-Sleep -Seconds 2
            if ((Test-Path -LiteralPath $log) -and
                ((Get-Content -LiteralPath $log -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!')) {
                $ready = $true
                break
            }
        }
        if ($ready -and -not $process.HasExited) {
            $process.StandardInput.WriteLine('stop')
            $process.StandardInput.Flush()
        }
        if (-not $process.WaitForExit(90000)) { $process.Kill($true) }
    } finally {
        if (-not $process.HasExited) { $process.Kill($true) }
    }

    $raw = if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Raw } else { '' }
    $fatal = [regex]::Matches($raw, '(?im)^.*(?:InvalidMixinException|Mixin apply for mod carpet-fga-addition failed|Exception in server tick loop|Could not initialize game|ERROR).*?$').Count
    $stopped = $raw -match 'Stopping server|All dimensions are saved'
    $results += [pscustomobject]@{
        version = $version
        ready = $ready
        exitCode = $process.ExitCode
        stopped = $stopped
        fatal = $fatal
        log = $log
    }
    Write-Output "$version`: ready=$ready exit=$($process.ExitCode) stopped=$stopped fatal=$fatal"
    if (-not $ready -or $fatal -gt 0 -or -not $stopped) {
        if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log -Tail 60 }
        throw "server startup smoke failed for $version"
    }
}

$results | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $root 'scripts\temp\fga-1.21plus-server-smoke-results.json') -Encoding utf8
