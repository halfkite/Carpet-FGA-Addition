param(
    [string] $CompatibilityModsDirectory = 'D:\我的世界\服务器\服务端\26.3空岛\mods',
    [switch] $PerformanceOnly,
    [switch] $ProfileCheck,
    [switch] $OnlineMode,
    [string] $BaselineJar = ''
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$reportDir = Join-Path $root "scripts\logs\fake-player-item-sort-smoke-26.3-$stamp"
$runDir = Join-Path $root "build\fake-player-item-sort-run-26.3-$stamp"
$gradleLog = Join-Path $reportDir 'gradle-console.log'
$serverLog = Join-Path $runDir 'logs\latest.log'
$summary = Join-Path $reportDir 'summary.txt'
$initScript = Join-Path $root 'scripts\gradle\fake-player-item-sort-smoke-isolated.init.gradle'
$jdk = Get-ChildItem -LiteralPath (Join-Path $env:ProgramFiles 'Java') -Directory |
    Where-Object { $_.Name -match '^jdk-25' -and (Test-Path -LiteralPath (Join-Path $_.FullName 'bin\javac.exe')) } |
    Sort-Object Name -Descending | Select-Object -First 1
if ($null -eq $jdk) { throw 'JDK 25 is required for the 26.3 smoke server' }
$toolchain21 = Join-Path $root 'build\toolchains\jdk-21.0.12.1+1'
$toolchain = "-Dorg.gradle.java.installations.paths=$toolchain21"
$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, 0)
$portProbe.Start()
$dashboardPort = [int] $portProbe.LocalEndpoint.Port
$portProbe.Stop()
$server = $null
$ready = $false
$helpPassed = $false
$quickopen = $false
$summon = $false
$mixedBox = $false
$fullBox = $false
$splitBox = $false
$settingsPassed = $false
$inventoryApiPassed = $false
$inventoryDiscoveryPassed = $false
$looseSpeedPassed = $false
$depotCleanupPassed = $false
$depotRoundPassed = $false
$depotBoxedPassed = $false
$dashboardPassed = $false
$dashboardStopped = $false
$stopped = $false
$failure = ''
$performancePassed = $false
$profileCheckPassed = $false
$profileControlPassed = $false
$profileSeedPassed = $false

function Wait-SortLog {
    param([string] $Pattern, [int] $TimeoutSeconds = 90, [int] $AfterChars = 0)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $content = if (Test-Path -LiteralPath $serverLog) { [string] (Get-Content -LiteralPath $serverLog -Raw -Encoding UTF8) } else { '' }
        if ($content -match 'FGA_SORT_PROBE_FAIL:[^\r\n]*') { throw $Matches[0] }
        $recent = $content.Substring([Math]::Min($AfterChars, $content.Length))
        if ($recent -match $Pattern) { return }
        if ($server.HasExited) { throw "Isolated server exited before $Pattern (code $($server.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for $Pattern"
}

function Send-SortCommand {
    param([string] $Command, [string] $Pattern, [int] $TimeoutSeconds = 90)
    $sortLogCursor = if (Test-Path -LiteralPath $serverLog) { (Get-Content -LiteralPath $serverLog -Raw -Encoding UTF8).Length } else { 0 }
    $server.StandardInput.WriteLine($Command)
    $server.StandardInput.Flush()
    Wait-SortLog -Pattern $Pattern -TimeoutSeconds $TimeoutSeconds -AfterChars $sortLogCursor
}

function Wait-DashboardResponse {
    param([string] $Uri, [int] $TimeoutSeconds = 30)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastFailure = ''
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 3 -ErrorAction Stop
            if ($response.StatusCode -ne 200) { throw "HTTP $($response.StatusCode) from $Uri" }
            return $response
        } catch {
            $lastFailure = $_.Exception.Message
            Start-Sleep -Milliseconds 500
        }
    }
    throw "Timed out waiting for dashboard response from ${Uri}: $lastFailure"
}

function Assert-DashboardStopped {
    param([string] $Uri, [int] $TimeoutSeconds = 10)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $null = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 2 -ErrorAction Stop
            Start-Sleep -Milliseconds 300
        } catch {
            return
        }
    }
    throw "Dashboard listener remained available at $Uri after it was disabled"
}

New-Item -ItemType Directory -Force -Path $reportDir, $runDir, (Join-Path $runDir 'mods') | Out-Null
$env:JAVA_HOME = $jdk.FullName
$env:Path = (Join-Path $jdk.FullName 'bin') + ';' + $env:Path
$env:FGA_SORT_SMOKE_RUN_DIR = $runDir
if ($BaselineJar) { $env:FGA_SORT_BASELINE_JAR = (Resolve-Path -LiteralPath $BaselineJar).Path }

try {
    $testBuildStart = [System.Diagnostics.ProcessStartInfo]::new()
    $testBuildStart.FileName = 'cmd.exe'
    $testBuildStart.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" ' + $toolchain +
        ' :26.3:testClasses --no-daemon --configure-on-demand --max-workers=1 > "' + $gradleLog + '" 2>&1"'
    $testBuildStart.WorkingDirectory = $root
    $testBuildStart.UseShellExecute = $false
    $testBuildStart.CreateNoWindow = $true
    $testBuild = [System.Diagnostics.Process]::new()
    $testBuild.StartInfo = $testBuildStart
    [void] $testBuild.Start()
    if (-not $testBuild.WaitForExit(300000)) {
        & taskkill.exe /PID $testBuild.Id /T /F 2>$null | Out-Null
        throw "26.3 testClasses timed out; see $gradleLog"
    }
    if ($testBuild.ExitCode -ne 0) { throw "26.3 testClasses failed; see $gradleLog" }

    $classes = Join-Path $root 'versions\26.3\build\classes\java\test\carpet\fga\smoke'
    $probeClasses = @(Get-ChildItem -LiteralPath $classes -File -Filter 'FakePlayerItemSortProbe*.class')
    if ($probeClasses.Count -eq 0) { throw 'Sort probe classes were not compiled' }
    $stage = Join-Path $runDir 'probe-staging'
    $package = Join-Path $stage 'carpet\fga\smoke'
    New-Item -ItemType Directory -Force -Path $package | Out-Null
    $probeClasses | Copy-Item -Destination $package
    Copy-Item -LiteralPath (Join-Path $root 'src\test\resources\fake-player-item-sort-probe\fabric.mod.json') `
        -Destination (Join-Path $stage 'fabric.mod.json')
    & (Join-Path $jdk.FullName 'bin\jar.exe') cf (Join-Path $runDir 'mods\fga-sort-probe.jar') -C $stage .
    if ($LASTEXITCODE -ne 0) { throw 'Could not package test-only sort probe' }

    Set-Content -LiteralPath (Join-Path $runDir 'eula.txt') -Encoding ascii -Value 'eula=true'
    $onlineModeSetting = if ($OnlineMode) { 'online-mode=true' } else { 'online-mode=false' }
    Set-Content -LiteralPath (Join-Path $runDir 'server.properties') -Encoding ascii -Value @(
        $onlineModeSetting, 'server-port=0', 'max-players=8', 'spawn-protection=0',
        'view-distance=2', 'simulation-distance=2', 'level-name=world', 'difficulty=peaceful'
    )

    $start = [System.Diagnostics.ProcessStartInfo]::new()
    $start.FileName = 'cmd.exe'
    $start.Arguments = '/d /s /c ""' + (Join-Path $root 'gradlew.bat') + '" -I "' + $initScript +
        '" ' + $toolchain + ' :26.3:runServer --no-daemon --configure-on-demand --max-workers=1 --args="--port 0" > "' +
        $gradleLog + '" 2>&1"'
    $start.WorkingDirectory = $root
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $start.CreateNoWindow = $true
    $server = [System.Diagnostics.Process]::new()
    $server.StartInfo = $start
    [void] $server.Start()

    Wait-SortLog -Pattern 'Done \([0-9.]+s\)!' -TimeoutSeconds 300
    $ready = $true
    if ($PerformanceOnly) {
        $server.StandardInput.WriteLine('setblock 0 79 0 minecraft:stone')
        $server.StandardInput.Flush()
        Send-SortCommand -Command 'player FgaSortPerf spawn at 0.5 80 0.5' `
            -Pattern 'FgaSortPerf\[local\] logged in with entity id' -TimeoutSeconds 120
        $server.StandardInput.WriteLine('tick rate 200')
        $server.StandardInput.Flush()
        $cases = if ($BaselineJar) { @('perf40', 'perf10', 'perf40warm') } else { @('perf40', 'perf10', 'perf40warm', 'perfquick40', 'perfdense40') }
        foreach ($case in $cases) {
            Send-SortCommand -Command "fgaSortProbe $case FgaSortPerf" `
                -Pattern "FGA_SORT_PERF_PASS: mode=$case " -TimeoutSeconds 240
        }
        if (-not $BaselineJar) {
            Send-SortCommand -Command 'player perfquick40_a spawn at 0.5 80 0.5' `
                -Pattern 'perfquick40_a\[local\] logged in with entity id' -TimeoutSeconds 120
            Send-SortCommand -Command 'fgaSortProbe verify-quick-login perfquick40_a' `
                -Pattern 'FGA_SORT_PROBE_PASS: quickopen-vanilla-login items=448' -TimeoutSeconds 30
        } else {
            Send-SortCommand -Command 'fgaSortProbe blocked-cost FgaSortPerf' `
                -Pattern 'FGA_SORT_BLOCKED_COST: calls=3 moved=0' -TimeoutSeconds 120
        }
        $performancePassed = $true
    } elseif ($ProfileCheck) {
        # The sorter must seed the target profile before carpet resolves the name, otherwise an
        # online-mode server blocks the tick thread on a Mojang profile lookup for every new target.
        $profileName = 'fgaProbeProfile'
        Send-SortCommand -Command "fgaSortProbe profile-control $profileName" `
            -Pattern 'FGA_SORT_PROBE_PASS: profile-control ' -TimeoutSeconds 90
        $profileControlPassed = $true
        Send-SortCommand -Command "fgaSortProbe profile-seed $profileName" `
            -Pattern 'FGA_SORT_PROBE_PASS: profile-seed ' -TimeoutSeconds 90
        $profileSeedPassed = $true
        $profileCheckPassed = $true
    } else {
    Send-SortCommand -Command 'fga playersort help' -Pattern 'Fake-player sorter setup' -TimeoutSeconds 30
    $helpPassed = $true
    Send-SortCommand -Command 'fgaSortProbe command-layout unused' `
        -Pattern 'FGA_SORT_PROBE_PASS: command-layout root-clean=true settings=11' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort language english' `
        -Pattern 'Sorter setting targetLanguage set to english' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set language chinese' `
        -Pattern 'Sorter setting targetLanguage set to chinese' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set language english' `
        -Pattern 'Sorter setting targetLanguage set to english' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set mode quickopen' `
        -Pattern 'Sorter mode set to quickopen' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set prefix off' `
        -Pattern 'Sorter name prefix updated' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set prefix default' `
        -Pattern 'Sorter name prefix updated' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set quickShulker false' `
        -Pattern 'Sorter setting quickShulker set to false' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set autoCraft false' `
        -Pattern 'Sorter setting shulkerRestock set to false' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set whitelistMode false' `
        -Pattern 'Sorter setting whitelistMode set to false' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set cleanOpenedTarget false' `
        -Pattern 'Sorter setting cleanOpenedTarget set to false' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set dashboard false' `
        -Pattern 'Sorter setting dashboard set to false' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set speed 6' -Pattern 'Sorter setting speed set to 6' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set cpu custom 3' -Pattern 'Sorter setting cpuThreads set to 3' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set summonNotices true' `
        -Pattern 'Sorter setting summonNotices set to true' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set summonNotices false' `
        -Pattern 'Sorter setting summonNotices set to false' -TimeoutSeconds 30
    $settingsPassed = $true
    Send-SortCommand -Command 'player FgaSortSource spawn at 0.5 80 0.5' `
        -Pattern 'FgaSortSource\[local\] logged in with entity id' -TimeoutSeconds 120
    Send-SortCommand -Command 'fgaSortProbe permission-layout FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: permission-layout legacy-grant=true revoke=true' -TimeoutSeconds 30
    Send-SortCommand -Command 'fgaSortProbe quickopen FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=quickopen' -TimeoutSeconds 120
    $quickopen = $true
    Send-SortCommand -Command 'fgaSortProbe summon FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=summon' -TimeoutSeconds 120
    $summon = $true
    Send-SortCommand -Command 'fgaSortProbe mixed-box FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=mixed-box boxes=1 stone=32 dirt=32' -TimeoutSeconds 120
    $mixedBox = $true
    Send-SortCommand -Command 'fgaSortProbe full-box FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=full-box boxes=1 stone=1728' -TimeoutSeconds 120
    $fullBox = $true
    Send-SortCommand -Command 'fgaSortProbe split-box FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=split-box boxes=1 diamond=32 dirt=32' -TimeoutSeconds 120
    $splitBox = $true
    Send-SortCommand -Command 'fgaSortProbe loose-speed FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: mode=loose-speed items=64 maxBatch=4 speed=16' -TimeoutSeconds 120
    $looseSpeedPassed = $true
    Send-SortCommand -Command 'fgaSortProbe depot-boxed FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: depot-boxed crafted=2 logsUsed=4 shellsUsed=4' -TimeoutSeconds 120
    $depotBoxedPassed = $true
    Send-SortCommand -Command 'fgaSortProbe depot-round FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: depot-round closed-after-round' -TimeoutSeconds 180
    $depotRoundPassed = $true
    Send-SortCommand -Command 'fgaSortProbe depot-restock FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: depot-restock-requested' -TimeoutSeconds 120
    Wait-SortLog -Pattern 'box_restock\[local\] logged in with entity id' -TimeoutSeconds 120
    Wait-SortLog -Pattern 'box_restock lost connection:' -TimeoutSeconds 120
    Send-SortCommand -Command 'fgaSortProbe depot-check box_restock' `
        -Pattern 'FGA_SORT_PROBE_PASS: depot-cleanup offline=true tracked=false' -TimeoutSeconds 30
    $depotCleanupPassed = $true
    Send-SortCommand -Command 'fgaSortProbe blocked-retry FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: blocked-retry attempts=3 interval=40 source=64 target=1728' -TimeoutSeconds 120
    $smokeText = Get-Content -LiteralPath $serverLog -Raw
    $targetLoginCount = [regex]::Matches($smokeText, 'bulk_cobblestone\[local\] logged in with entity id').Count
    if ($targetLoginCount -ne 1) {
        throw "Summon mode should log in exactly one target fake player, got $targetLoginCount"
    }
    if ($smokeText -match 'bulk_cobblestone_[0-9]+\[local\] logged in with entity id') {
        throw 'Summon mode spawned an unexpected numbered target fake player'
    }

    Send-SortCommand -Command 'fgaSortProbe inventory-api FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: inventory-api half64=32 half16=8 offline=true duplicate=true no-space=true stale=true permissions=true conserved=true' -TimeoutSeconds 180
    $inventoryApiPassed = $true

    Send-SortCommand -Command 'fgaSortProbe inventory-discovery FgaSortSource' `
        -Pattern 'FGA_SORT_PROBE_PASS: inventory-discovery chinese=32 remaining=1696 sparse-box=32 missing=true shortage=true conserved=true' -TimeoutSeconds 180
    $inventoryDiscoveryPassed = $true

    Send-SortCommand -Command 'fga playersort stock list all' `
        -Pattern 'Full stock list written to:' -TimeoutSeconds 30
    $exportDirectory = Join-Path $runDir 'world\config\carpetfgaaddition\exports'
    $exportFiles = @(Get-ChildItem -LiteralPath $exportDirectory -Filter 'playersort-stock-*.txt' -File)
    if ($exportFiles.Count -ne 1 -or $exportFiles[0].Length -eq 0) { throw 'Stock export did not produce one nonempty file' }

    Send-SortCommand -Command "fga playersort set dashboard port $dashboardPort" `
        -Pattern "Dashboard port set to $dashboardPort" -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set dashboard true' `
        -Pattern 'Sorter setting dashboard set to true' -TimeoutSeconds 30
    $dashboardRoot = "http://127.0.0.1:$dashboardPort/"
    $page = Wait-DashboardResponse -Uri $dashboardRoot
    if ($page.Content -notmatch '<title>FGA') { throw 'Dashboard page content did not match the retained web UI' }
    $legacyApi = Wait-DashboardResponse -Uri ($dashboardRoot + 'api/cache')
    $inventoryApi = Wait-DashboardResponse -Uri ($dashboardRoot + 'api/v1/inventory')
    $inventory = $inventoryApi.Content | ConvertFrom-Json
    if ($null -eq $inventory.items) { throw 'Dashboard inventory API did not return an items array' }
    # Windows PowerShell decodes both this script file and text/plain responses with the ANSI code
    # page unless UTF-8 is requested explicitly. Decode the body as UTF-8 and build the expected
    # header from code points (物品 TAB 数量 TAB 分类假人) so the check cannot depend on the
    # encoding this host happens to use for the script itself.
    $stockResponse = Wait-DashboardResponse -Uri ($dashboardRoot + 'api/v1/stock.txt')
    $stockBytes = $stockResponse.RawContentStream.ToArray()
    $stockText = [System.Text.Encoding]::UTF8.GetString($stockBytes)
    $stockHeader = [string]::Join('', [char] 0x7269, [char] 0x54C1, "`t", [char] 0x6570, [char] 0x91CF,
        "`t", [char] 0x5206, [char] 0x7C7B, [char] 0x5047, [char] 0x4EBA)
    if (-not $stockText.StartsWith($stockHeader)) {
        throw ('Dashboard stock text endpoint returned an unexpected header: bytes=' +
            (($stockBytes | Select-Object -First 24) -join ',') + ' text=' +
            (($stockText.ToCharArray() | Select-Object -First 16 | ForEach-Object { [int] $_ }) -join ','))
    }
    if ($legacyApi.StatusCode -ne 200) { throw 'Legacy dashboard cache endpoint did not remain available' }
    Send-SortCommand -Command 'fga playersort set dashboard password Fga-Smoke-Password-2026' `
        -Pattern 'Dashboard password saved' -TimeoutSeconds 30
    Send-SortCommand -Command 'fga playersort set dashboard login' `
        -Pattern 'Sorter setting dashboard set to login' -TimeoutSeconds 30
    foreach ($endpoint in @('', 'api/cache', 'api/v1/inventory', 'api/v1/stock.txt')) {
        foreach ($headers in @(@{}, @{Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('fga:wrong-password'))})) {
            $rejected = $false
            try { $null = Invoke-WebRequest -UseBasicParsing -Uri ($dashboardRoot + $endpoint) -Headers $headers -TimeoutSec 5 }
            catch { $rejected = [int]$_.Exception.Response.StatusCode -eq 401 }
            if (-not $rejected) { throw "Dashboard login did not reject unauthenticated/wrong-password request: $endpoint" }
        }
        $loginHeaders = @{Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('fga:Fga-Smoke-Password-2026'))}
        $authenticated = Invoke-WebRequest -UseBasicParsing -Uri ($dashboardRoot + $endpoint) -Headers $loginHeaders -TimeoutSec 5
        if ($authenticated.StatusCode -ne 200) { throw "Authenticated dashboard failed: $endpoint" }
    }
    Send-SortCommand -Command 'fga playersort name reload' `
        -Pattern 'Sorter configuration reloaded' -TimeoutSeconds 30
    $authenticated = Invoke-WebRequest -UseBasicParsing -Uri ($dashboardRoot + 'api/v1/inventory') -Headers $loginHeaders -TimeoutSec 5
    if ($authenticated.StatusCode -ne 200) { throw 'Dashboard credentials failed after config reload' }
    Send-SortCommand -Command 'fga playersort set dashboard true' `
        -Pattern 'Sorter setting dashboard set to true' -TimeoutSeconds 30
    $null = Wait-DashboardResponse -Uri ($dashboardRoot + 'api/v1/inventory')
    $dashboardPassed = $true
    Send-SortCommand -Command 'fga playersort set dashboard false' `
        -Pattern 'Sorter setting dashboard set to false' -TimeoutSeconds 30
    Assert-DashboardStopped -Uri $dashboardRoot
    $dashboardStopped = $true
    }

    $server.StandardInput.WriteLine('stop')
    $server.StandardInput.Flush()
    $stopped = $server.WaitForExit(90000)
    if (-not $stopped) { throw 'Isolated server did not stop cleanly' }
} catch {
    $failure = $_.Exception.Message
} finally {
    if ($null -ne $server -and -not $server.HasExited) {
        try {
            $server.StandardInput.WriteLine('stop')
            $server.StandardInput.Flush()
            $stopped = $server.WaitForExit(30000)
        } catch { }
    }
    if ($null -ne $server -and -not $server.HasExited) {
        & taskkill.exe /PID $server.Id /T /F 2>$null | Out-Null
    }
    @(
        'version=26.3', "serverReady=$ready", "helpPassed=$helpPassed", "quickopenPassed=$quickopen",
        "summonPassed=$summon", "mixedBoxPassed=$mixedBox", "fullBoxPassed=$fullBox",
        "splitBoxPassed=$splitBox", "settingsSyntaxPassed=$settingsPassed", "looseSpeedBatchingPassed=$looseSpeedPassed",
        "depotFakeCleanupPassed=$depotCleanupPassed", "depotRoundLifetimePassed=$depotRoundPassed", "depotBoxedMaterialPassed=$depotBoxedPassed", "dashboardPassed=$dashboardPassed", "dashboardStopped=$dashboardStopped",
        "inventoryApiPassed=$inventoryApiPassed", "inventoryDiscoveryPassed=$inventoryDiscoveryPassed", "cleanStop=$stopped",
        "performancePassed=$performancePassed",
        "profileControlPassed=$profileControlPassed",
        "profileSeedPassed=$profileSeedPassed",
        "status=$(if ($ready -and ($performancePassed -or $profileCheckPassed -or ($helpPassed -and $quickopen -and $summon -and $mixedBox -and $fullBox -and $splitBox -and $settingsPassed -and $looseSpeedPassed -and $depotCleanupPassed -and $depotRoundPassed -and $depotBoxedPassed -and $inventoryApiPassed -and $inventoryDiscoveryPassed -and $dashboardPassed -and $dashboardStopped)) -and $stopped -and -not $failure) { 'passed' } else { 'failed' })",
        "reason=$failure", "serverLog=$serverLog", "gradleLog=$gradleLog"
    ) | Set-Content -LiteralPath $summary -Encoding utf8
    Remove-Item Env:\FGA_SORT_SMOKE_RUN_DIR -ErrorAction SilentlyContinue
    Remove-Item Env:\FGA_SORT_BASELINE_JAR -ErrorAction SilentlyContinue
}

Get-Content -LiteralPath $summary
if (-not ($ready -and ($performancePassed -or $profileCheckPassed -or ($helpPassed -and $quickopen -and $summon -and $mixedBox -and $fullBox -and $splitBox -and $settingsPassed -and $looseSpeedPassed -and $depotCleanupPassed -and $depotRoundPassed -and $depotBoxedPassed -and $inventoryApiPassed -and $inventoryDiscoveryPassed -and $dashboardPassed -and $dashboardStopped)) -and $stopped -and -not $failure)) { exit 1 }
