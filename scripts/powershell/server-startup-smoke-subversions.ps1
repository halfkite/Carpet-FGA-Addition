param(
    [string] $VersionList = '',
    [string] $CommandList = 'carpet namedEnderPearlTeleport true;;carpet boneMealMaxEfficiency true;;carpet fireAspectOnTools true;;carpet soulSpeedNoDurability true;;carpet thornsNoDurability true;;carpet lightSourceStonecuttingRecipes true;;carpet lightBlockBreakable true;;carpet mapLoadCommandPermission ops'
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\server-startup-smoke-subversions-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'

$variants = @(
    [pscustomobject]@{ Name = '1.21.1'; Node = '1.21.1'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '1.21.2'; Node = '1.21.3'; Minecraft = '1.21.2'; Loader = '0.16.9'; Carpet = '1.21.2-1.4.158+v241022'; FabricApi = '0.106.1+1.21.2' }
    [pscustomobject]@{ Name = '1.21.3'; Node = '1.21.3'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '1.21.4'; Node = '1.21.4'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '1.21.5'; Node = '1.21.5'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '1.21.6'; Node = '1.21.8'; Minecraft = '1.21.6'; Loader = '0.19.3'; Carpet = '1.21.7-1.4.177+v250630'; FabricApi = '0.128.2+1.21.6' }
    [pscustomobject]@{ Name = '1.21.7'; Node = '1.21.8'; Minecraft = '1.21.7'; Loader = '0.19.3'; Carpet = '1.21.7-1.4.177+v250630'; FabricApi = '0.129.0+1.21.7' }
    [pscustomobject]@{ Name = '1.21.8'; Node = '1.21.8'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '1.21.9'; Node = '1.21.10'; Minecraft = '1.21.9'; Loader = '0.17.2'; Carpet = '1.21.9-1.4.185+v250930'; FabricApi = '0.134.1+1.21.9' }
    [pscustomobject]@{ Name = '1.21.10'; Node = '1.21.10'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '1.21.11'; Node = '1.21.11'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '26.1'; Node = '26.1.2'; Minecraft = '26.1'; Loader = '0.18.4'; Carpet = '26.1+v260401'; FabricApi = '0.145.1+26.1' }
    [pscustomobject]@{ Name = '26.1.1'; Node = '26.1.2'; Minecraft = '26.1.1'; Loader = '0.18.4'; Carpet = '26.1+v260401'; FabricApi = '0.145.4+26.1.1' }
    [pscustomobject]@{ Name = '26.1.2'; Node = '26.1.2'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '26.2'; Node = '26.2'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
    [pscustomobject]@{ Name = '26.3'; Node = '26.3'; Minecraft = $null; Loader = $null; Carpet = $null; FabricApi = $null }
)

$selected = if ([string]::IsNullOrWhiteSpace($VersionList)) {
    $variants
} else {
    $wanted = @($VersionList.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    @($variants | Where-Object { $wanted -contains $_.Name })
}
if ($selected.Count -eq 0) { throw "No matching subversions: $VersionList" }

New-Item -ItemType Directory -Force -Path $reportDir | Out-Null

function Read-Log([string] $path) {
    if (Test-Path -LiteralPath $path) {
        return Get-Content -LiteralPath $path -Raw -ErrorAction SilentlyContinue
    }
    return ''
}

function Write-ProgressLine([string] $message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -LiteralPath $progressPath -Value $line -Encoding utf8
    Write-Host $line
}

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
    }
}

function Remove-TemporaryWorld([string] $runDir, [string] $worldPath) {
    if (-not (Test-Path -LiteralPath $worldPath)) { return }
    $resolvedRun = [IO.Path]::GetFullPath($runDir).TrimEnd('\') + '\'
    $resolvedWorld = [IO.Path]::GetFullPath($worldPath)
    if (-not $resolvedWorld.StartsWith($resolvedRun, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove temporary world outside run directory: $resolvedWorld"
    }
    Remove-Item -LiteralPath $resolvedWorld -Recurse -Force
}

function Set-Property([string] $path, [string] $name, [string] $value) {
    $lines = @(Get-Content -LiteralPath $path)
    $prefix = "$name="
    $index = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].StartsWith($prefix, [StringComparison]::Ordinal)) {
            $index = $i
            break
        }
    }
    if ($index -ge 0) {
        $lines[$index] = "$name=$value"
    } else {
        $lines += "$name=$value"
    }
    Set-Content -LiteralPath $path -Value $lines -Encoding utf8
}

$results = @()

foreach ($variant in $selected) {
    $startedAt = Get-Date
    $runDir = Join-Path $root "versions\$($variant.Node)\run"
    $propertiesPath = Join-Path $root "versions\$($variant.Node)\gradle.properties"
    $propertiesBackup = "$propertiesPath.before-subversion-smoke-$stamp"
    $modsPath = Join-Path $runDir 'mods'
    $modsBackup = "$modsPath.before-subversion-smoke-$stamp"
    $hadMods = Test-Path -LiteralPath $modsPath
    $serverLog = Join-Path $reportDir "$($variant.Name)-server.log"
    $levelName = "server-startup-$($variant.Name.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $levelName
    $savedFiles = @('eula.txt', 'server.properties')
    $backups = @{}
    $server = $null
    $ready = $false
    $stoppedCleanly = $false
    $reason = ''

    Write-ProgressLine "START $($variant.Name) node=$($variant.Node)"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    Copy-Item -LiteralPath $propertiesPath -Destination $propertiesBackup
    if ($hadMods) { Move-Item -LiteralPath $modsPath -Destination $modsBackup }

    foreach ($name in $savedFiles) {
        $path = Join-Path $runDir $name
        if (Test-Path -LiteralPath $path) {
            $backup = "$path.before-server-startup-subversion-$stamp"
            Move-Item -LiteralPath $path -Destination $backup
            $backups[$name] = $backup
        }
    }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        'online-mode=false'
        'server-port=0'
        'view-distance=2'
        'simulation-distance=2'
        "level-name=$levelName"
        'difficulty=peaceful'
    )

    try {
        if ($null -ne $variant.Minecraft) { Set-Property $propertiesPath 'minecraft_version' $variant.Minecraft }
        if ($null -ne $variant.Loader) { Set-Property $propertiesPath 'loader_version' $variant.Loader }
        if ($null -ne $variant.Carpet) { Set-Property $propertiesPath 'carpet_core_version' $variant.Carpet }
        if ($null -ne $variant.FabricApi) { Set-Property $propertiesPath 'fabric_api_version' $variant.FabricApi }

        $env:JAVA_HOME = if ($variant.Name -like '26.*') { $jdk25 } else { $jdk21 }
        $serverInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $serverInfo.FileName = 'cmd.exe'
        $serverInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $variant.Node + ':runServer --no-daemon --configure-on-demand --max-workers=1 > "' +
            $serverLog + '" 2>&1"'
        $serverInfo.WorkingDirectory = $root
        $serverInfo.UseShellExecute = $false
        $serverInfo.RedirectStandardInput = $true
        $serverInfo.CreateNoWindow = $true
        $server = [System.Diagnostics.Process]::new()
        $server.StartInfo = $serverInfo
        [void] $server.Start()

        $deadline = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
            if ((Read-Log $serverLog) -match 'Done \([0-9.]+s\)!') {
                $ready = $true
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $ready) { throw 'temporary dedicated server did not become ready' }

        foreach ($command in $CommandList.Split(';;')) {
            $trimmed = $command.Trim()
            if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
                Write-ProgressLine "COMMAND $($variant.Name) $trimmed"
                $server.StandardInput.WriteLine($trimmed)
                $server.StandardInput.Flush()
                Start-Sleep -Milliseconds 500
            }
        }

        $server.StandardInput.WriteLine('stop')
        $server.StandardInput.Flush()
        $stoppedCleanly = $server.WaitForExit(60000)
        if (-not $stoppedCleanly) { throw 'temporary dedicated server did not stop within 60 seconds' }
        $reason = 'server reached Done and stopped cleanly'
    } catch {
        $reason = $_.Exception.Message
    } finally {
        if ($null -ne $server -and -not $server.HasExited) { Stop-ProcessTree $server }

        foreach ($name in $savedFiles) {
            $path = Join-Path $runDir $name
            if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
            if ($backups.ContainsKey($name) -and (Test-Path -LiteralPath $backups[$name])) {
                Move-Item -LiteralPath $backups[$name] -Destination $path
            }
        }
        Remove-TemporaryWorld $runDir $worldPath
        if (Test-Path -LiteralPath $modsPath) { Remove-Item -LiteralPath $modsPath -Recurse -Force }
        if ($hadMods -and (Test-Path -LiteralPath $modsBackup)) {
            Move-Item -LiteralPath $modsBackup -Destination $modsPath
        }
        if (Test-Path -LiteralPath $propertiesPath) { Remove-Item -LiteralPath $propertiesPath -Force }
        Move-Item -LiteralPath $propertiesBackup -Destination $propertiesPath

        $status = if ($ready -and $stoppedCleanly) { 'passed' } else { 'failed' }
        $results += [ordered]@{
            version = $variant.Name
            buildNode = $variant.Node
            status = $status
            serverReady = $ready
            stoppedCleanly = $stoppedCleanly
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $($variant.Name) $status ready=$ready stopped=$stoppedCleanly reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
if ($passed -ne $results.Count) { exit 1 }
