param()

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$version = '1.21.1'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\unlimited-multiplayer-players-network-smoke-$stamp"
$runDir = Join-Path $root "versions\$version\run"
$clientRunDir = Join-Path $runDir "client-game-$stamp"
$clientModsDir = Join-Path $clientRunDir 'mods'
$clientOptionsPath = Join-Path $clientRunDir 'options.txt'
$savesDir = Join-Path $runDir 'saves'
$modsDir = Join-Path $runDir 'mods'
$optionsPath = Join-Path $runDir 'options.txt'
$eulaPath = Join-Path $runDir 'eula.txt'
$propertiesPath = Join-Path $runDir 'server.properties'
$opsPath = Join-Path $runDir 'ops.json'
$carpetDefaultPath = Join-Path $runDir 'config\carpet\default_carpet.conf'
$gcaArtifact = Join-Path $root 'build\actual-full-test-1.21.1-2102\mods\gugle-carpet-addition-v2.12.4+build.88.jar'
$serverPort = 25575
$serverWorldName = "unlimited-players-network-$stamp"
$serverWorldPath = Join-Path $runDir $serverWorldName
$username = 'FGAFakeResident'
$botGroupName = 'FGARes'
$fakeNames = 0..19 | ForEach-Object { 'bot_{0}_{1}' -f $botGroupName, $_ }
$serverProcess = $null
$clientProcess = $null
$clientWindow = $null
$backupSaves = $null
$backupMods = $null
$backupOptions = $null
$backupEula = $null
$backupProperties = $null
$backupOps = $null
$backupCarpetDefault = $null
$status = 'failed'
$reason = ''
$firstFakePlayers = 0
$reloadFakePlayers = 0
$realPlayerFirst = $false
$realPlayerReload = $false

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

Add-Type @'
using System;
using System.Runtime.InteropServices;
public static class FgaUnlimitedPlayersNetworkSmoke {
    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr window);
}
'@

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName Microsoft.VisualBasic

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Write-ProgressLine([string] $message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -LiteralPath (Join-Path $reportDir 'progress.log') -Value $line -Encoding utf8
    Write-Host $line
}

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}

function Start-Server([string] $logPath) {
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
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $serverInfo
    [void] $process.Start()
    return $process
}

function Wait-ServerReady([System.Diagnostics.Process] $process, [string] $logPath) {
    $deadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $deadline -and -not $process.HasExited) {
        if ((Read-Log $logPath) -match 'Done \([0-9.]+s\)!') {
            return $true
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Send-ServerCommand([System.Diagnostics.Process] $process, [string] $command) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds 800
}

function Get-NewMinecraftWindow([int[]] $existingProcessIds, [System.Diagnostics.Process] $commandProcess,
                                [datetime] $deadline) {
    while ((Get-Date) -lt $deadline) {
        $window = Get-Process -Name java,javaw -ErrorAction SilentlyContinue |
            Where-Object {
                $_.MainWindowHandle -ne 0 -and
                $_.MainWindowTitle -like 'Minecraft*' -and
                $existingProcessIds -notcontains $_.Id
            } |
            Sort-Object StartTime -Descending |
            Select-Object -First 1
        if ($null -ne $window) {
            return $window
        }
        if ($commandProcess.HasExited) {
            return $null
        }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

function Start-Client([string] $logPath) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
    $arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
        '" :1.21.1:runClient --no-daemon --configure-on-demand --max-workers=1' +
        ' --args="--gameDir ' + $clientRunDir + ' --username ' + $username + ' --quickPlayMultiplayer localhost:' + $serverPort +
        '" > "' + $logPath + '" 2>&1"'
    return Start-Process -FilePath 'cmd.exe' -ArgumentList $arguments `
        -WorkingDirectory $root -WindowStyle Hidden -PassThru
}

function Send-ChatCommand([System.Diagnostics.Process] $window, [string] $command) {
    [void][FgaUnlimitedPlayersNetworkSmoke]::SetForegroundWindow($window.MainWindowHandle)
    [Microsoft.VisualBasic.Interaction]::AppActivate($window.Id)
    [System.Windows.Forms.SendKeys]::SendWait('/')
    Start-Sleep -Milliseconds 250
    Set-Clipboard -Value $command.TrimStart('/')
    [System.Windows.Forms.SendKeys]::SendWait('^v')
    [System.Windows.Forms.SendKeys]::SendWait('{ENTER}')
    Start-Sleep -Milliseconds 1200
}

function Count-FakePlayers([string] $text) {
    return @($fakeNames | Where-Object {
            $text -match ([regex]::Escape($_) + '.+logged in with entity id')
        }).Count
}

try {
    if (-not (Test-Path -LiteralPath $gcaArtifact)) {
        throw "GCA test artifact not found: $gcaArtifact"
    }
    if (Test-Path -LiteralPath $savesDir) {
        $backupSaves = Join-Path $runDir "saves.before-unlimited-players-network-smoke-$stamp"
        Move-Item -LiteralPath $savesDir -Destination $backupSaves
    }
    New-Item -ItemType Directory -Force -Path $savesDir | Out-Null

    if (Test-Path -LiteralPath $modsDir) {
        $backupMods = Join-Path $runDir "mods.before-unlimited-players-network-smoke-$stamp"
        Move-Item -LiteralPath $modsDir -Destination $backupMods
    }
    New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
    Copy-Item -LiteralPath $gcaArtifact -Destination (Join-Path $modsDir (Split-Path $gcaArtifact -Leaf))
    New-Item -ItemType Directory -Force -Path $clientModsDir | Out-Null
    Copy-Item -LiteralPath $gcaArtifact -Destination (Join-Path $clientModsDir (Split-Path $gcaArtifact -Leaf))
    Set-Content -LiteralPath $clientOptionsPath -Encoding ascii -Value @(
        'lang:en_us'
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
        'showSubtitles:false'
    )

    if (Test-Path -LiteralPath $optionsPath) {
        $backupOptions = Join-Path $runDir "options.before-unlimited-players-network-smoke-$stamp.txt"
        Move-Item -LiteralPath $optionsPath -Destination $backupOptions
    }
    Set-Content -LiteralPath $optionsPath -Encoding ascii -Value @(
        'lang:en_us'
        'soundCategory_master:0.0'
        'soundCategory_music:0.0'
        'narrator:0'
        'showSubtitles:false'
    )

    foreach ($item in @(
        @{ Path = $eulaPath; Name = 'eula' }
        @{ Path = $propertiesPath; Name = 'server-properties' }
        @{ Path = $opsPath; Name = 'ops' }
        @{ Path = $carpetDefaultPath; Name = 'carpet-default' }
    )) {
        if (Test-Path -LiteralPath $item.Path) {
            $backup = "$($item.Path).before-unlimited-players-network-smoke-$stamp"
            Move-Item -LiteralPath $item.Path -Destination $backup
            switch ($item.Name) {
                'eula' { $backupEula = $backup }
                'server-properties' { $backupProperties = $backup }
                'ops' { $backupOps = $backup }
                'carpet-default' { $backupCarpetDefault = $backup }
            }
        }
    }
    Set-Content -LiteralPath $eulaPath -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath $propertiesPath -Encoding ascii -Value @(
        'online-mode=false'
        "server-port=$serverPort"
        'max-players=8'
        'view-distance=4'
        'simulation-distance=4'
        "level-name=$serverWorldName"
        'difficulty=peaceful'
    )

    $firstServerLog = Join-Path $reportDir 'server-first.log'
    $firstClientLog = Join-Path $reportDir 'client-first.log'
    Write-ProgressLine 'START first server'
    $serverProcess = Start-Server $firstServerLog
    if (-not (Wait-ServerReady $serverProcess $firstServerLog)) {
        throw 'first dedicated server did not become ready'
    }
    Send-ServerCommand $serverProcess "op $username"
    $opDeadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $opDeadline -and (Read-Log $firstServerLog) -notmatch [regex]::Escape($username)) {
        Start-Sleep -Seconds 1
    }

    $existingJavaIds = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
    $clientProcess = Start-Client $firstClientLog
    $clientWindow = Get-NewMinecraftWindow $existingJavaIds $clientProcess ((Get-Date).AddMinutes(6))
    if ($null -eq $clientWindow) { throw 'first network client window did not appear' }
    $joinDeadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $joinDeadline) {
        $firstServerRaw = Read-Log $firstServerLog
        if ($firstServerRaw -match ([regex]::Escape($username) + '.+logged in with entity id')) {
            $realPlayerFirst = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $realPlayerFirst) { throw 'first network client did not join the dedicated server' }

    Send-ChatCommand $clientWindow 'carpet setDefault unlimitedMultiplayerPlayers false'
    Send-ChatCommand $clientWindow 'carpet setDefault fakePlayerResident true'
    Send-ChatCommand $clientWindow "bot group generated $botGroupName 20 true"
    $fakeDeadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $fakeDeadline) {
        $firstFakePlayers = Count-FakePlayers (Read-Log $firstServerLog)
        if ($firstFakePlayers -eq 20) { break }
        Start-Sleep -Seconds 1
    }
    $firstServerRaw = Read-Log $firstServerLog
    $firstFakePlayers = Count-FakePlayers $firstServerRaw
    if ($firstFakePlayers -ne 20) {
        throw "expected 20 fake players on first server, observed $firstFakePlayers"
    }

    [void] $clientWindow.CloseMainWindow()
    if (-not $clientProcess.WaitForExit(60000)) { Stop-ProcessTree $clientProcess }
    $clientProcess = $null
    Send-ServerCommand $serverProcess 'save-all'
    Send-ServerCommand $serverProcess 'stop'
    if (-not $serverProcess.WaitForExit(60000)) { throw 'first dedicated server did not stop' }
    $serverProcess = $null

    $secondServerLog = Join-Path $reportDir 'server-reload.log'
    $secondClientLog = Join-Path $reportDir 'client-reload.log'
    Write-ProgressLine 'START reload server'
    $serverProcess = Start-Server $secondServerLog
    if (-not (Wait-ServerReady $serverProcess $secondServerLog)) {
        throw 'reload dedicated server did not become ready'
    }
    $existingJavaIds = @(Get-Process -Name java,javaw -ErrorAction SilentlyContinue | ForEach-Object Id)
    $clientProcess = Start-Client $secondClientLog
    $clientWindow = Get-NewMinecraftWindow $existingJavaIds $clientProcess ((Get-Date).AddMinutes(6))
    if ($null -eq $clientWindow) { throw 'reload network client window did not appear' }
    $reloadDeadline = (Get-Date).AddMinutes(5)
    while ((Get-Date) -lt $reloadDeadline) {
        $reloadRaw = Read-Log $secondServerLog
        if ($reloadRaw -match ([regex]::Escape($username) + '.+logged in with entity id')) {
            $realPlayerReload = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $realPlayerReload) { throw 'reload network client did not join the dedicated server' }
    $reloadFakeDeadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $reloadFakeDeadline) {
        $reloadFakePlayers = Count-FakePlayers (Read-Log $secondServerLog)
        if ($reloadFakePlayers -eq 20) { break }
        Start-Sleep -Seconds 1
    }
    $reloadRaw = Read-Log $secondServerLog
    $reloadFakePlayers = Count-FakePlayers $reloadRaw
    if ($reloadFakePlayers -ne 20) {
        throw "expected 20 resident fake players after reload, observed $reloadFakePlayers"
    }

    $status = 'passed'
    $reason = '20 GCA resident fake players joined with FGA unlimited players disabled, then all 20 were present after server and client reload'
} catch {
    $reason = $_.Exception.Message
} finally {
    if ($null -ne $clientProcess -and -not $clientProcess.HasExited) {
        Stop-ProcessTree $clientProcess
    }
    if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
        Send-ServerCommand $serverProcess 'stop'
        if (-not $serverProcess.WaitForExit(60000)) { Stop-ProcessTree $serverProcess }
    }
    if (Test-Path -LiteralPath $serverWorldPath) {
        Move-Item -LiteralPath $serverWorldPath -Destination (Join-Path $reportDir 'world') -Force
    }
    if (Test-Path -LiteralPath $clientRunDir) {
        Move-Item -LiteralPath $clientRunDir -Destination (Join-Path $reportDir 'client-game') -Force
    }
    if (Test-Path -LiteralPath $modsDir) { Remove-Item -LiteralPath $modsDir -Recurse -Force }
    if ($null -ne $backupMods -and (Test-Path -LiteralPath $backupMods)) { Move-Item $backupMods $modsDir }
    if (Test-Path -LiteralPath $optionsPath) { Remove-Item $optionsPath -Force }
    if ($null -ne $backupOptions -and (Test-Path -LiteralPath $backupOptions)) { Move-Item $backupOptions $optionsPath }
    if (Test-Path -LiteralPath $savesDir) { Remove-Item $savesDir -Recurse -Force }
    if ($null -ne $backupSaves -and (Test-Path -LiteralPath $backupSaves)) { Move-Item $backupSaves $savesDir }

    foreach ($item in @(
        @{ Path = $eulaPath; Backup = $backupEula }
        @{ Path = $propertiesPath; Backup = $backupProperties }
        @{ Path = $opsPath; Backup = $backupOps }
        @{ Path = $carpetDefaultPath; Backup = $backupCarpetDefault }
    )) {
        if (Test-Path -LiteralPath $item.Path) { Remove-Item -LiteralPath $item.Path -Force }
        if ($null -ne $item.Backup -and (Test-Path -LiteralPath $item.Backup)) {
            New-Item -ItemType Directory -Force -Path (Split-Path $item.Path) | Out-Null
            Move-Item -LiteralPath $item.Backup -Destination $item.Path
        }
    }
    $result = [ordered]@{
        version = $version
        status = $status
        realPlayerFirst = $realPlayerFirst
        firstFakePlayers = $firstFakePlayers
        realPlayerReload = $realPlayerReload
        reloadFakePlayers = $reloadFakePlayers
        serverPort = $serverPort
        gcaArtifact = $gcaArtifact
        reportDir = $reportDir
        reason = $reason
    }
    $result | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $reportDir 'summary.json') -Encoding utf8
    Write-ProgressLine "RESULT $version $status realFirst=$realPlayerFirst fakeFirst=$firstFakePlayers realReload=$realPlayerReload fakeReload=$reloadFakePlayers reason=$reason"
}

if ($status -ne 'passed') { exit 1 }
