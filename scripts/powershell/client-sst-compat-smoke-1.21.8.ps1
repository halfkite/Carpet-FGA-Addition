param(
    [Parameter(Mandatory = $true)]
    [string] $StackSizeTweaksJar,
    [Parameter(Mandatory = $true)]
    [string] $YaclJar
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$runDir = Join-Path $root 'versions\1.21.8\run'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\sst-compat-smoke-$stamp"
$latestLog = Join-Path $runDir 'logs\latest.log'
$commandLog = Join-Path $reportDir 'gradle-client.log'
$savesDir = Join-Path $runDir 'saves'
$modsDir = Join-Path $runDir 'mods'
$optionsPath = Join-Path $runDir 'options.txt'
$savesBackup = $null
$modsBackup = $null
$optionsBackup = $null
$commandProcess = $null

foreach ($jar in @($StackSizeTweaksJar, $YaclJar)) {
    if (-not (Test-Path -LiteralPath $jar)) {
        throw "Missing compatibility test dependency: $jar"
    }
}

New-Item -ItemType Directory -Force -Path $reportDir, $runDir | Out-Null
$stagedStackSizeTweaksJar = Join-Path $reportDir (Split-Path -Leaf $StackSizeTweaksJar)
$stagedYaclJar = Join-Path $reportDir (Split-Path -Leaf $YaclJar)
Copy-Item -LiteralPath $StackSizeTweaksJar -Destination $stagedStackSizeTweaksJar
Copy-Item -LiteralPath $YaclJar -Destination $stagedYaclJar

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaSstSmokeInput {
    [StructLayout(LayoutKind.Sequential)] public struct Rect { public int Left, Top, Right, Bottom; }
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr window, out Rect rect);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);
    public static void Click(IntPtr window, int x, int y) {
        Rect rect;
        if (GetClientRect(window, out rect)) {
            x = x * Math.Max(1, rect.Right - rect.Left) / 854;
            y = y * Math.Max(1, rect.Bottom - rect.Top) / 480;
        }
        IntPtr position = (IntPtr)((y << 16) | (x & 0xffff));
        PostMessage(window, 0x0200, IntPtr.Zero, position);
        PostMessage(window, 0x0201, (IntPtr)1, position);
        PostMessage(window, 0x0202, IntPtr.Zero, position);
    }
}
'@

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

try {
    if (Test-Path -LiteralPath $savesDir) {
        $savesBackup = "$savesDir.before-sst-compat-$stamp"
        Move-Item -LiteralPath $savesDir -Destination $savesBackup
    }
    New-Item -ItemType Directory -Path $savesDir | Out-Null

    if (Test-Path -LiteralPath $modsDir) {
        $modsBackup = "$modsDir.before-sst-compat-$stamp"
        Move-Item -LiteralPath $modsDir -Destination $modsBackup
    }
    New-Item -ItemType Directory -Path $modsDir | Out-Null
    Copy-Item -LiteralPath $stagedStackSizeTweaksJar, $stagedYaclJar -Destination $modsDir

    if (Test-Path -LiteralPath $optionsPath) {
        $optionsBackup = "$optionsPath.before-sst-compat-$stamp"
        Move-Item -LiteralPath $optionsPath -Destination $optionsBackup
    }
    Set-Content -LiteralPath $optionsPath -Encoding ascii -Value @(
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
        'showSubtitles:false'
    )

    $existingJavaIds = @(Get-Process -Name java -ErrorAction SilentlyContinue | ForEach-Object Id)
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
    $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" :1.21.8:runClient --no-daemon --configure-on-demand --max-workers=1 --args="--username FGACompat" > "' + $commandLog + '" 2>&1"'
    $commandProcess = Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru

    $window = $null
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $commandProcess.HasExited) {
        $window = Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
            $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -like 'Minecraft*' -and
            $existingJavaIds -notcontains $_.Id
        } | Sort-Object StartTime -Descending | Select-Object -First 1
        if ($null -ne $window) { break }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $window) { throw 'Minecraft window did not appear' }

    $deadline = (Get-Date).AddMinutes(2)
    while ((Get-Date) -lt $deadline) {
        $raw = Read-Text $latestLog
        if ($raw -match 'Created: .*textures/atlas|Sound engine started') { break }
        if ($raw -match 'Critical injection failure|Mixin transformation .* failed') {
            throw 'Mixin failed during client bootstrap'
        }
        Start-Sleep -Seconds 2
    }

    Start-Sleep -Seconds 3
    [FgaSstSmokeInput]::Click($window.MainWindowHandle, 427, 235)
    Start-Sleep -Seconds 3
    [FgaSstSmokeInput]::Click($window.MainWindowHandle, 268, 445)

    $joined = $false
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        $raw = Read-Text $latestLog
        if ($raw -match 'FGACompat joined the game') {
            $joined = $true
            break
        }
        if ($raw -match 'Critical injection failure|Mixin transformation .* failed|Mixin apply for mod carpet-fga-addition failed') {
            throw 'Mixin failed while entering the test world'
        }
        if ($commandProcess.HasExited) { break }
    }

    $raw = Read-Text $latestLog
    $worldCreated = @(Get-ChildItem -LiteralPath $savesDir -Recurse -Filter level.dat -File -ErrorAction SilentlyContinue).Count -gt 0
    $stackSizeTweaksLoaded = $raw -match 'stacksizetweaks 0\.3\.4\+1\.21\.8'
    $compatibilityLogged = $raw -match 'Stack Size Tweaks detected; using the compatible ItemEntity merge injection'
    $oldConflictPresent = $raw -match '@ModifyConstant conflict.*stacksizetweaks|increaseMergeStackLimit\(I\)I.*failed injection check'

    if (-not ($joined -and $worldCreated -and $stackSizeTweaksLoaded -and $compatibilityLogged -and -not $oldConflictPresent)) {
        throw "Smoke assertions failed: joined=$joined world=$worldCreated sst=$stackSizeTweaksLoaded compatLog=$compatibilityLogged oldConflict=$oldConflictPresent"
    }

    Copy-Item -LiteralPath $latestLog -Destination (Join-Path $reportDir 'latest.log')
    [ordered]@{
        status = 'passed'
        joined = $joined
        worldCreated = $worldCreated
        stackSizeTweaksLoaded = $stackSizeTweaksLoaded
        compatibilityLogged = $compatibilityLogged
        oldConflictPresent = $oldConflictPresent
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportDir 'summary.json') -Encoding utf8
    Write-Output "SST_COMPAT_SMOKE_PASS report=$reportDir"
} finally {
    Stop-Tree $commandProcess
    Start-Sleep -Seconds 2

    if (Test-Path -LiteralPath $optionsPath) { Remove-Item -LiteralPath $optionsPath -Force }
    if ($null -ne $optionsBackup -and (Test-Path -LiteralPath $optionsBackup)) {
        Move-Item -LiteralPath $optionsBackup -Destination $optionsPath
    }

    if (Test-Path -LiteralPath $modsDir) { Remove-Item -LiteralPath $modsDir -Recurse -Force }
    if ($null -ne $modsBackup -and (Test-Path -LiteralPath $modsBackup)) {
        Move-Item -LiteralPath $modsBackup -Destination $modsDir
    }

    if (Test-Path -LiteralPath $savesDir) {
        Move-Item -LiteralPath $savesDir -Destination "$savesDir.sst-compat-$stamp"
    }
    if ($null -ne $savesBackup -and (Test-Path -LiteralPath $savesBackup)) {
        Move-Item -LiteralPath $savesBackup -Destination $savesDir
    }
}
