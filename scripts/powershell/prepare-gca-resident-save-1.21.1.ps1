param()

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$version = '1.21.1'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $root "versions\$version\run"
$worldName = "gca-resident-prep-$stamp"
$worldPath = Join-Path $runDir $worldName
$reportDir = Join-Path $root "scripts\logs\gca-resident-prep-$stamp"
$logPath = Join-Path $reportDir 'server.log'
$eulaPath = Join-Path $runDir 'eula.txt'
$propertiesPath = Join-Path $runDir 'server.properties'
$eulaBackup = $null
$propertiesBackup = $null
$server = $null

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

try {
    if (Test-Path -LiteralPath $eulaPath) {
        $eulaBackup = "$eulaPath.before-gca-resident-prep-$stamp"
        Move-Item -LiteralPath $eulaPath -Destination $eulaBackup
    }
    if (Test-Path -LiteralPath $propertiesPath) {
        $propertiesBackup = "$propertiesPath.before-gca-resident-prep-$stamp"
        Move-Item -LiteralPath $propertiesPath -Destination $propertiesBackup
    }

    Set-Content -LiteralPath $eulaPath -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath $propertiesPath -Encoding ascii -Value @(
        'online-mode=false'
        'server-port=0'
        'view-distance=2'
        'simulation-distance=2'
        "level-name=$worldName"
        'difficulty=peaceful'
    )

    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
    $serverInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $serverInfo.FileName = 'cmd.exe'
    $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :1.21.1:runServer --no-daemon --configure-on-demand --max-workers=1 > "' +
        $logPath + '" 2>&1"'
    $serverInfo.WorkingDirectory = $root
    $serverInfo.UseShellExecute = $false
    $serverInfo.RedirectStandardInput = $true
    $serverInfo.CreateNoWindow = $true
    $server = [System.Diagnostics.Process]::new()
    $server.StartInfo = $serverInfo
    [void] $server.Start()

    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
        if ((Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue) -match 'Done \([0-9.]+s\)!') {
            break
        }
        Start-Sleep -Seconds 2
    }
    $raw = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
    if ($server.HasExited -or $raw -notmatch 'Done \([0-9.]+s\)!') {
        throw 'prep server did not become ready'
    }

    foreach ($command in @(
        'carpet setDefault fakePlayerResident true'
        'carpet unlimitedMultiplayerPlayers false'
        'bot group generated FGARes 20 true'
        'save-all'
    )) {
        $server.StandardInput.WriteLine($command)
        $server.StandardInput.Flush()
        Start-Sleep -Milliseconds 800
    }
    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    if (-not $server.WaitForExit(60000)) {
        throw 'prep server did not stop'
    }

    if (-not (Test-Path -LiteralPath $worldPath)) {
        throw "prep world was not created: $worldPath"
    }
    Copy-Item -LiteralPath $worldPath -Destination (Join-Path $reportDir 'world') -Recurse -Force
    [ordered]@{
        status = 'passed'
        worldName = $worldName
        worldPath = $worldPath
        reportDir = $reportDir
        residentConfig = Join-Path $worldPath 'serverconfig\guglecarpetaddition\residents.json'
        botConfig = Join-Path $worldPath 'serverconfig\guglecarpetaddition\bot.json'
        botGroupConfig = Join-Path $worldPath 'serverconfig\guglecarpetaddition\bot_group.json'
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $reportDir 'summary.json') -Encoding utf8
    Write-Output "PREP_OK REPORT=$reportDir WORLD=$worldPath"
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    if (Test-Path -LiteralPath $eulaPath) {
        Remove-Item -LiteralPath $eulaPath -Force
    }
    if ($null -ne $eulaBackup -and (Test-Path -LiteralPath $eulaBackup)) {
        Move-Item -LiteralPath $eulaBackup -Destination $eulaPath
    }
    if (Test-Path -LiteralPath $propertiesPath) {
        Remove-Item -LiteralPath $propertiesPath -Force
    }
    if ($null -ne $propertiesBackup -and (Test-Path -LiteralPath $propertiesBackup)) {
        Move-Item -LiteralPath $propertiesBackup -Destination $propertiesPath
    }
}
