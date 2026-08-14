$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$testRoot = Join-Path $root "scripts\fixtures\player-load-distance-$stamp"
$world = Join-Path $testRoot 'world'
$log = Join-Path $testRoot 'server.log'
$server = $null

New-Item -ItemType Directory -Force -Path $testRoot | Out-Null
[IO.File]::WriteAllText((Join-Path $testRoot 'eula.txt'), 'eula=true', [Text.Encoding]::ASCII)
[IO.File]::WriteAllLines((Join-Path $testRoot 'server.properties'), @(
    'online-mode=false',
    'server-port=0',
    'view-distance=2',
    'simulation-distance=2',
    'level-name=world',
    'difficulty=peaceful'
), [Text.Encoding]::ASCII)

try {
    $startInfo = [Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'cmd.exe'
    $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--universe ' +
        $testRoot + ' --world world" > "' + $log + '" 2>&1"'
    $startInfo.WorkingDirectory = $root
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardInput = $true
    $startInfo.CreateNoWindow = $true
    $server = [Diagnostics.Process]::new()
    $server.StartInfo = $startInfo
    [void] $server.Start()

    $deadline = (Get-Date).AddMinutes(4)
    $ready = $false
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        Start-Sleep -Seconds 2
        if (Test-Path -LiteralPath $log) {
            $raw = Get-Content -LiteralPath $log -Raw
            if ($raw -match 'Done \([0-9.]+s\)!') { $ready = $true; break }
            if ($raw -match 'Mixin apply failed|InvalidMixinException|InjectionError|Failed to start') { break }
        }
    }
    if (-not $ready) { throw "Temporary server did not become ready: $log" }

    $server.StandardInput.WriteLine('carpet playerLoadDistance true')
    $server.StandardInput.WriteLine('playerLoadDistance help')
    $server.StandardInput.WriteLine('fga playerLoadDistance help')
    $server.StandardInput.WriteLine('carpet deepslateStonecuttingRecipes true')
    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    if (-not $server.WaitForExit(90000)) { throw 'Temporary server did not stop' }

    $raw = Get-Content -LiteralPath $log -Raw
    $checks = [ordered]@{
        ServerReady = $raw -match 'Done \([0-9.]+s\)!'
        NoMixinError = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError'
        PlayerRuleRegistered = $raw -match 'playerLoadDistance: true'
        DeepslateRuleRegistered = $raw -match 'deepslateStonecuttingRecipes: true'
        CommandRegistered = $raw -match 'Player Load Distance /'
        CleanStop = $raw -match 'Stopping server'
    }
    $checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
    Write-Output "LOG=$log"
    if ($checks.Values -contains $false) { exit 1 }
} finally {
    if ($null -ne $server -and -not $server.HasExited) { $server.Kill($true) }
    if (Test-Path -LiteralPath $world) { Remove-Item -LiteralPath $world -Recurse -Force }
}
