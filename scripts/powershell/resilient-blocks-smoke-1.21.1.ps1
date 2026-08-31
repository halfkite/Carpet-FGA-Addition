$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$run = Join-Path $root 'versions\1.21.1\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$worldName = "resilient-blocks-smoke-$stamp"
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

    # The dev world persists across runs; clear leftovers before setting up.
    Send-Command 'kill @e[type=minecraft:item]' 400
    Send-Command 'kill @e[type=minecraft:falling_block]' 400
    Send-Command 'forceload add -8 -8 12 14' 500
    Send-Command 'fill -8 99 -8 12 99 14 minecraft:stone' 900
    Send-Command 'fill -8 99 -8 12 99 14 minecraft:stone' 900
    Send-Command 'execute if block 2 99 2 minecraft:stone run say FGA_PLATFORM_OK' 300
    Send-Command 'gamerule doMobSpawning false' 300
    Send-Command 'gamerule randomTickSpeed 0' 300

    # S1: rule off (vanilla) -> floating sand falls, floating cactus pops on neighbor update
    Send-Command 'carpet resilientBlocks false' 400
    Send-Command 'setblock 5 105 8 minecraft:sand' 600
    Send-Command 'say FGA_S1_START' 3500
    Send-Command 'execute if block 5 105 8 minecraft:air run say FGA_S1_SAND_FELL' 400
    Send-Command 'setblock 7 103 10 minecraft:cactus' 600
    Send-Command 'setblock 6 103 10 minecraft:stone' 600
    Send-Command 'setblock 6 103 10 minecraft:air' 600
    Send-Command 'say FGA_S1_RESULTS' 800
    Send-Command 'execute if block 7 103 10 minecraft:air run say FGA_S1_CACTUS_GONE' 400
    Send-Command 'say FGA_S1_END' 400

    # S2: rule [sand,cactus] -> floating sand stays, floating cactus survives neighbor update
    Send-Command 'carpet resilientBlocks [sand,cactus]' 600
    Send-Command 'setblock 5 105 8 minecraft:sand' 600
    Send-Command 'say FGA_S2_START' 3500
    Send-Command 'execute if block 5 105 8 minecraft:sand run say FGA_S2_SAND_FLOATS' 400
    Send-Command 'setblock 7 103 10 minecraft:cactus' 600
    Send-Command 'setblock 6 103 10 minecraft:stone' 600
    Send-Command 'setblock 6 103 10 minecraft:air' 600
    Send-Command 'say FGA_S2_RESULTS' 800
    Send-Command 'execute if block 7 103 10 minecraft:cactus run say FGA_S2_CACTUS_STAYS' 400
    Send-Command 'say FGA_S2_END' 400

    # S3: unlisted blocks keep vanilla behavior
    Send-Command 'carpet resilientBlocks [cactus]' 600
    Send-Command 'setblock 5 105 8 minecraft:sand' 600
    Send-Command 'say FGA_S3_START' 3500
    Send-Command 'execute if block 5 105 8 minecraft:air run say FGA_S3_SAND_FELL' 400
    Send-Command 'say FGA_S3_END' 400

    # S4: invalid value and unknown block rejected
    Send-Command 'carpet resilientBlocks banana' 600
    Send-Command 'carpet resilientBlocks [nosuchblock]' 600

    Send-Command 'carpet resilientBlocks false' 400
    Send-Command 'kill @e[type=minecraft:item]' 400
    Send-Command 'kill @e[type=minecraft:falling_block]' 400
    Send-Command 'forceload remove -8 -8 12 14' 500
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
$s1 = Get-Window $raw 'FGA_S1_START' 'FGA_S1_END'
$s2 = Get-Window $raw 'FGA_S2_START' 'FGA_S2_END'
$s3 = Get-Window $raw 'FGA_S3_START' 'FGA_S3_END'

$checks = [ordered]@{
    ServerReady        = $raw -match 'Done \([0-9.]+s\)!'
    NoMixinError       = $raw -notmatch 'Mixin apply failed|InvalidMixinException|InjectionError'
    PlatformReady      = $raw -match 'FGA_PLATFORM_OK'
    RuleRegistered     = $raw -match 'resilientBlocks'
    S1_SandFell        = $s1 -match 'FGA_S1_SAND_FELL'
    S1_CactusPopped    = $s1 -match 'FGA_S1_CACTUS_GONE'
    S2_SandFloats      = $s2 -match 'FGA_S2_SAND_FLOATS'
    S2_CactusStays     = $s2 -match 'FGA_S2_CACTUS_STAYS'
    S3_UnlistedSandFalls = $s3 -match 'FGA_S3_SAND_FELL'
    InvalidRejected    = $raw -match 'resilientBlocks must be false or a block list'
    UnknownRejected    = $raw -match 'unknown block id: nosuchblock'
    ListNormalized     = $raw -match 'resilientBlocks: \[minecraft:cactus,minecraft:sand\]'
    CleanStop          = $raw -match 'Stopping server'
}

$checks.GetEnumerator() | ForEach-Object { Write-Output ("{0}={1}" -f $_.Key, $_.Value) }
Write-Output "LOG=$log"

if ($checks.Values -contains $false) { exit 1 }
