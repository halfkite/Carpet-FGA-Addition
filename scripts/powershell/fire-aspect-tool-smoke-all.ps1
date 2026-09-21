param(
    [string] $VersionList = ''
)

$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\fire-aspect-tool-smoke-all-$stamp"
$summaryPath = Join-Path $reportDir 'summary.json'
$progressPath = Join-Path $reportDir 'progress.log'
$jdk21 = 'C:\Program Files\Java\jdk-21.0.11'
$jdk25 = 'C:\Program Files\Java\jdk-25.0.3'
$allVersions = @(
    '1.21.1', '1.21.3', '1.21.4', '1.21.5',
    '1.21.8', '1.21.10', '1.21.11', '26.1.2', '26.2', '26.3'
)
$versions = if ([string]::IsNullOrWhiteSpace($VersionList)) {
    $allVersions
} else {
    @($VersionList.Split(',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

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

function Send-Command([System.Diagnostics.Process] $process, [string] $command,
                      [int] $delay = 500) {
    $process.StandardInput.WriteLine($command)
    $process.StandardInput.Flush()
    Start-Sleep -Milliseconds $delay
}

function Wait-LogPattern([string] $path, [string] $pattern,
                         [int] $timeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($timeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Log $path) -match $pattern) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Stop-ProcessTree([System.Diagnostics.Process] $process) {
    if ($null -ne $process -and -not $process.HasExited) {
        try {
            & taskkill.exe /PID $process.Id /T /F 2>$null | Out-Null
        } catch {
            # The command may already have exited while its child is being reaped.
        }
    }
}

function Remove-TestWorld([string] $runRoot, [string] $testWorld) {
    if (-not (Test-Path -LiteralPath $testWorld)) { return }
    $resolvedRoot = [IO.Path]::GetFullPath($runRoot).TrimEnd('\') + '\'
    $resolvedWorld = [IO.Path]::GetFullPath($testWorld)
    if (-not $resolvedWorld.StartsWith($resolvedRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove temporary world outside test run directory: $resolvedWorld"
    }
    Remove-Item -LiteralPath $resolvedWorld -Recurse -Force
}

function Get-PackFormat([string] $version) {
    switch ($version) {
        '1.21.1' { return 48 }
        '1.21.3' { return 57 }
        '1.21.4' { return 61 }
        '1.21.5' { return 71 }
        '1.21.8' { return 81 }
        '1.21.10' { return 89 }
        '1.21.11' { return 94 }
        '26.1.2' { return 101 }
        '26.2' { return 102 }
        '26.3' { return 103 }
        default { throw "No datapack format mapping is recorded for $version" }
    }
}

function New-TestDatapack([string] $worldPath, [string] $version) {
    $packRoot = Join-Path $worldPath 'datapacks\fga-fire-aspect-test'
    $recipeRoot = Join-Path $packRoot 'data\fga_fire_aspect_test\recipe'
    New-Item -ItemType Directory -Force -Path $recipeRoot | Out-Null
    $packFormat = Get-PackFormat $version
    $cookingTimeKey = if ($version -eq '26.3') { 'cookingtime' } else { 'cooking_time' }
    Set-Content -LiteralPath (Join-Path $packRoot 'pack.mcmeta') -Encoding utf8 -Value @"
{
  "pack": {
    "pack_format": $packFormat,
    "min_format": $packFormat,
    "max_format": $packFormat,
    "description": "FGA fire aspect tool smoke test"
  }
}
"@
    $ironIngredient = if ($version -eq '1.21.1') {
        '{"item":"minecraft:iron_ingot"}'
    } else {
        '"minecraft:iron_ingot"'
    }
    Set-Content -LiteralPath (Join-Path $recipeRoot 'iron_to_gold.json') -Encoding utf8 -Value @"
{
  "type": "minecraft:smelting",
  "category": "misc",
  "ingredient": $ironIngredient,
  "result": {
    "id": "minecraft:gold_ingot",
    "count": 1
  },
  "experience": 0.0,
  "$cookingTimeKey": 1
}
"@
}

function Read-Score([string] $text, [string] $scoreName) {
    $matches = [regex]::Matches($text, [regex]::Escape($scoreName) + ' has (-?\d+)')
    if ($matches.Count -eq 0) { throw "scoreboard value was not printed: $scoreName" }
    return [int]$matches[$matches.Count - 1].Groups[1].Value
}

function Read-Section([string] $text, [string] $begin, [string] $end) {
    $match = [regex]::Match(
        $text,
        [regex]::Escape($begin) + '(?<body>[\s\S]*?)' + [regex]::Escape($end))
    if (-not $match.Success) { return '' }
    return $match.Groups['body'].Value
}

$results = @()

foreach ($version in $versions) {
    $startedAt = Get-Date
    $runDir = Join-Path $root "versions\$version\run"
    $worldName = "fire-aspect-tool-$($version.Replace('.', '-'))-$stamp"
    $worldPath = Join-Path $runDir $worldName
    $eulaPath = Join-Path $runDir 'eula.txt'
    $eulaBackup = "$eulaPath.before-fire-aspect-tool-$stamp"
    $serverLog = Join-Path $reportDir "$version-server.log"
    $serverPropertiesPath = Join-Path $runDir 'server.properties'
    $serverPropertiesBackup = "$serverPropertiesPath.before-fire-aspect-tool-$stamp"
    $hadServerProperties = Test-Path -LiteralPath $serverPropertiesPath
    $server = $null
    $serverReady = $false
    $packLoaded = $false
    $fireOnePassed = $false
    $fireTwoPassed = $false
    $fireFortunePassed = $false
    $enchantmentsRecorded = $false
    $cleanStop = $false
    $mixinFailure = $false
    $reason = ''
    $miningWaitSeconds = if ($version -like '26.*') { 5 } else { 4 }

    Write-ProgressLine "START $version"
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    if (Test-Path -LiteralPath $eulaPath) {
        Copy-Item -LiteralPath $eulaPath -Destination $eulaBackup
    }
    Set-Content -LiteralPath $eulaPath -Value 'eula=true' -Encoding ascii
    if ($hadServerProperties) {
        Copy-Item -LiteralPath $serverPropertiesPath -Destination $serverPropertiesBackup
    }
    Set-Content -LiteralPath $serverPropertiesPath -Encoding ascii -Value @(
        'online-mode=false'
        'server-port=0'
        'view-distance=2'
        'simulation-distance=2'
        "level-name=$worldName"
        'difficulty=peaceful'
        'level-type=minecraft:flat'
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}'
        'generate-structures=false'
        'spawn-monsters=false'
        'spawn-animals=false'
    )
    New-Item -ItemType Directory -Force -Path $worldPath | Out-Null
    New-TestDatapack $worldPath $version

    try {
        $env:JAVA_HOME = if ($version -like '26.*') { $jdk25 } else { $jdk21 }
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        $startInfo = [Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = 'cmd.exe'
        $startInfo.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') +
            '" :' + $version + ':runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0 --world ' +
            $worldName + '" > "' + $serverLog + '" 2>&1"'
        $startInfo.WorkingDirectory = $root
        $startInfo.UseShellExecute = $false
        $startInfo.RedirectStandardInput = $true
        $startInfo.CreateNoWindow = $true
        $server = [Diagnostics.Process]::new()
        $server.StartInfo = $startInfo
        [void] $server.Start()

        $deadline = (Get-Date).AddMinutes(5)
        while ((Get-Date) -lt $deadline -and -not $server.HasExited) {
            if ((Read-Log $serverLog) -match 'Done \([0-9.]+s\)!') {
                $serverReady = $true
                break
            }
            Start-Sleep -Seconds 2
        }
        if (-not $serverReady) { throw 'server did not become ready' }

        Send-Command $server 'reload' 1500
        Send-Command $server 'say FGA_FIRE_ASPECT_RELOAD_OK'
        if (-not (Wait-LogPattern $serverLog 'FGA_FIRE_ASPECT_RELOAD_OK' 15)) {
            throw 'test datapack did not reload'
        }
        $packLoaded = $true

        Send-Command $server 'carpet fireAspectOnTools true'
        Send-Command $server 'carpet fakePlayerRangeControl true'
        Send-Command $server 'scoreboard objectives add fga_fire dummy'
        Send-Command $server 'player FGAFireOne spawn at 0.5 100 1.5'
        Send-Command $server 'player FGAFireTwo spawn at 0.5 100 11.5'
        Send-Command $server 'player FGAFireFortune spawn at 0.5 100 21.5'
        if (-not (Wait-LogPattern $serverLog 'FGAFireOne joined the game' 30)) { throw 'FGAFireOne did not join' }
        if (-not (Wait-LogPattern $serverLog 'FGAFireTwo joined the game' 30)) { throw 'FGAFireTwo did not join' }
        if (-not (Wait-LogPattern $serverLog 'FGAFireFortune joined the game' 30)) { throw 'FGAFireFortune did not join' }
        Send-Command $server 'recipe give FGAFireTwo fga_fire_aspect_test:iron_to_gold'
        Send-Command $server 'fill 0 199 0 6 199 24 minecraft:stone'
        Send-Command $server 'fill 0 200 0 6 202 24 minecraft:air'
        Send-Command $server 'tp FGAFireOne 0.5 200 1.5'
        Send-Command $server 'tp FGAFireTwo 0.5 200 11.5'
        Send-Command $server 'tp FGAFireFortune 0.5 200 21.5'
        if ($version -like '26.*') {
            Send-Command $server 'gamerule block_drops true'
            Send-Command $server 'gamerule entity_drops true'
        } else {
            Send-Command $server 'gamerule doTileDrops true'
            Send-Command $server 'gamerule doEntityDrops true'
        }
        Send-Command $server 'gamemode survival FGAFireOne'
        Send-Command $server 'gamemode survival FGAFireTwo'
        Send-Command $server 'gamemode survival FGAFireFortune'
        Start-Sleep -Seconds 1

        Send-Command $server 'player FGAFireOne look east'
        Send-Command $server 'item replace entity FGAFireOne weapon.mainhand with minecraft:diamond_pickaxe'
        Send-Command $server 'enchant FGAFireOne fire_aspect 1'
        Send-Command $server 'data get entity FGAFireOne SelectedItem'
        Send-Command $server 'setblock 1 201 1 minecraft:iron_ore'
        Send-Command $server 'player FGAFireOne attack continuous range 1 201 1 to 1 201 1'
        Start-Sleep -Seconds $miningWaitSeconds
        Send-Command $server 'player FGAFireOne stop'
        Send-Command $server 'data get block 1 201 1'
        Send-Command $server 'execute if block 1 201 1 minecraft:air run say FGA_FIRE_ONE_BLOCK_BROKEN'
        Send-Command $server 'say FGA_FIRE_ONE_DROPS_BEGIN'
        Send-Command $server 'execute as FGAFireOne at @s run execute as @e[type=minecraft:item,distance=..8] run data get entity @s Item'
        Send-Command $server 'say FGA_FIRE_ONE_DROPS_END'
        Send-Command $server 'execute as FGAFireOne at @s run kill @e[type=minecraft:item,distance=..8]'
        Send-Command $server 'execute store result score FGA_FIRE_ONE_INGOT fga_fire run clear FGAFireOne minecraft:iron_ingot 0'
        Send-Command $server 'data get entity FGAFireOne Inventory'
        Send-Command $server 'scoreboard players get FGA_FIRE_ONE_INGOT fga_fire'

        Send-Command $server 'player FGAFireTwo look east'
        Send-Command $server 'item replace entity FGAFireTwo weapon.mainhand with minecraft:diamond_pickaxe'
        Send-Command $server 'enchant FGAFireTwo fire_aspect 2'
        Send-Command $server 'data get entity FGAFireTwo SelectedItem'
        Send-Command $server 'setblock 1 201 11 minecraft:iron_ore'
        Send-Command $server 'player FGAFireTwo attack continuous range 1 201 11 to 1 201 11'
        Start-Sleep -Seconds $miningWaitSeconds
        Send-Command $server 'player FGAFireTwo stop'
        Send-Command $server 'data get block 1 201 11'
        Send-Command $server 'execute if block 1 201 11 minecraft:air run say FGA_FIRE_TWO_BLOCK_BROKEN'
        Send-Command $server 'say FGA_FIRE_TWO_DROPS_BEGIN'
        Send-Command $server 'execute as FGAFireTwo at @s run execute as @e[type=minecraft:item,distance=..8] run data get entity @s Item'
        Send-Command $server 'say FGA_FIRE_TWO_DROPS_END'
        Send-Command $server 'execute as FGAFireTwo at @s run kill @e[type=minecraft:item,distance=..8]'
        Send-Command $server 'execute store result score FGA_FIRE_TWO_GOLD fga_fire run clear FGAFireTwo minecraft:gold_ingot 0'
        Send-Command $server 'data get entity FGAFireTwo Inventory'
        Send-Command $server 'scoreboard players get FGA_FIRE_TWO_GOLD fga_fire'

        Send-Command $server 'player FGAFireFortune look east'
        Send-Command $server 'item replace entity FGAFireFortune weapon.mainhand with minecraft:diamond_pickaxe'
        Send-Command $server 'enchant FGAFireFortune fire_aspect 1'
        Send-Command $server 'enchant FGAFireFortune fortune 3'
        Send-Command $server 'data get entity FGAFireFortune SelectedItem'
        Send-Command $server 'setblock 1 201 21 minecraft:iron_ore'
        Send-Command $server 'player FGAFireFortune attack continuous range 1 201 21 to 1 201 21'
        Start-Sleep -Seconds $miningWaitSeconds
        Send-Command $server 'player FGAFireFortune stop'
        Send-Command $server 'data get block 1 201 21'
        Send-Command $server 'execute if block 1 201 21 minecraft:air run say FGA_FIRE_FORTUNE_BLOCK_BROKEN'
        Send-Command $server 'say FGA_FIRE_FORTUNE_DROPS_BEGIN'
        Send-Command $server 'execute as FGAFireFortune at @s run execute as @e[type=minecraft:item,distance=..8] run data get entity @s Item'
        Send-Command $server 'say FGA_FIRE_FORTUNE_DROPS_END'
        Send-Command $server 'execute as FGAFireFortune at @s run kill @e[type=minecraft:item,distance=..8]'
        Send-Command $server 'execute store result score FGA_FIRE_FORTUNE_INGOT fga_fire run clear FGAFireFortune minecraft:iron_ingot 0'
        Send-Command $server 'data get entity FGAFireFortune Inventory'
        Send-Command $server 'scoreboard players get FGA_FIRE_FORTUNE_INGOT fga_fire'

        $serverText = Read-Log $serverLog
        $oneCount = Read-Score $serverText 'FGA_FIRE_ONE_INGOT'
        $twoCount = Read-Score $serverText 'FGA_FIRE_TWO_GOLD'
        $fortuneCount = Read-Score $serverText 'FGA_FIRE_FORTUNE_INGOT'
        $oneDrops = Read-Section $serverText 'FGA_FIRE_ONE_DROPS_BEGIN' 'FGA_FIRE_ONE_DROPS_END'
        $twoDrops = Read-Section $serverText 'FGA_FIRE_TWO_DROPS_BEGIN' 'FGA_FIRE_TWO_DROPS_END'
        $fortuneDrops = Read-Section $serverText 'FGA_FIRE_FORTUNE_DROPS_BEGIN' 'FGA_FIRE_FORTUNE_DROPS_END'
        $oneDropPresent = $oneDrops -match 'id: "minecraft:iron_ingot"'
        $twoDropPresent = $twoDrops -match 'id: "minecraft:gold_ingot"'
        $fortuneDropPresent = $fortuneDrops -match 'id: "minecraft:iron_ingot"'
        $fortuneDropMatch = [regex]::Match($fortuneDrops, 'count: (\d+)')
        $fortuneDropCount = if ($fortuneDropMatch.Success) { [int]$fortuneDropMatch.Groups[1].Value } else { 0 }
        $fireOnePassed = $oneDropPresent -or $oneCount -ge 1
        $fireTwoPassed = $twoDropPresent -or $twoCount -ge 1
        $fireFortunePassed = ($fortuneDropPresent -and $fortuneDropCount -ge 1) -or $fortuneCount -ge 1
        $enchantmentsRecorded = $serverText -match 'fire_aspect' -and $serverText -match 'fortune'
        if (-not $fireOnePassed) { throw "Fire Aspect I expected an iron ingot drop, inventory=$oneCount" }
        if (-not $fireTwoPassed) { throw "Fire Aspect II expected a gold ingot drop from the chained recipe, inventory=$twoCount" }
        if (-not $fireFortunePassed) { throw "Fire Aspect plus Fortune expected an iron ingot drop, inventory=$fortuneCount" }
        if (-not $enchantmentsRecorded) { throw 'the Fire Aspect plus Fortune pickaxe was not recorded with both enchantments' }

        Send-Command $server 'stop' 100
        if (-not $server.WaitForExit(90000)) { throw 'server did not stop within 90 seconds' }
        $reason = "Fire I ironIngotDrop=$oneDropPresent inventory=$oneCount; Fire II goldIngotDrop=$twoDropPresent inventory=$twoCount; Fire+Fortune ironIngotDropCount=$fortuneDropCount inventory=$fortuneCount"
    } catch {
        $reason = $_.Exception.Message
    } finally {
        $serverText = Read-Log $serverLog
        if ($null -ne $server -and -not $server.HasExited) {
            Stop-ProcessTree $server
        }
        Remove-TestWorld $runDir $worldPath
        if (Test-Path -LiteralPath $eulaPath) { Remove-Item -LiteralPath $eulaPath -Force }
        if (Test-Path -LiteralPath $eulaBackup) {
            Move-Item -LiteralPath $eulaBackup -Destination $eulaPath
        }
        if (Test-Path -LiteralPath $serverPropertiesPath) { Remove-Item -LiteralPath $serverPropertiesPath -Force }
        if (Test-Path -LiteralPath $serverPropertiesBackup) {
            Move-Item -LiteralPath $serverPropertiesBackup -Destination $serverPropertiesPath
        }

        $mixinFailure = $serverText -match 'Mixin apply failed|InvalidMixinException|InjectionError|Critical injection failure'
        $cleanStop = $serverText -match 'Stopping server'
        $status = if ($serverReady -and $packLoaded -and $fireOnePassed -and $fireTwoPassed -and
                $fireFortunePassed -and $enchantmentsRecorded -and $cleanStop -and -not $mixinFailure) {
            'passed'
        } else {
            'failed'
        }
        $results += [ordered]@{
            version = $version
            status = $status
            serverReady = $serverReady
            packLoaded = $packLoaded
            fireAspectOne = $fireOnePassed
            fireAspectTwo = $fireTwoPassed
            fireAspectFortune = $fireFortunePassed
            enchantmentsRecorded = $enchantmentsRecorded
            cleanStop = $cleanStop
            mixinFailure = $mixinFailure
            reason = $reason
            durationSeconds = [math]::Round(((Get-Date) - $startedAt).TotalSeconds, 1)
            serverLog = [IO.Path]::GetFileName($serverLog)
        }
        $results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $summaryPath -Encoding utf8
        Write-ProgressLine "RESULT $version $status reason=$reason"
    }
}

$passed = @($results | Where-Object { $_.status -eq 'passed' }).Count
Write-ProgressLine "COMPLETE passed=$passed total=$($results.Count)"
Get-Content -LiteralPath $summaryPath
if ($passed -ne $results.Count) { exit 1 }
