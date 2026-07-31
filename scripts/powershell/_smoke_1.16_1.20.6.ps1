$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:GRADLE_USER_HOME = 'C:\Users\28656\.gradle'
$env:TEMP = 'D:\ai\carpet-fga\.tmp'
$env:TMP = 'D:\ai\carpet-fga\.tmp'
$root = 'D:\ai\carpet-fga'
$logDir = Join-Path $root 'build\smoke-logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$summary = Join-Path $logDir 'summary-1.16-1.20.6.txt'
$versions = @('1.16.5','1.17.1','1.18.2','1.19.2','1.19.4','1.20.1','1.20.4','1.20.6')
Set-Content -Path $summary -Value ("START " + (Get-Date -Format o)) -Encoding ascii

function Kill-CarpetJava {
  Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
    $_.CommandLine -and ($_.CommandLine -match 'carpet-fga|net\.fabricmc\.devlaunch|GradleWrapperMain|gradle-9\.5\.1')
  } | ForEach-Object {
    try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {}
  }
  Get-CimInstance Win32_Process -Filter "Name = 'cmd.exe'" | Where-Object {
    $_.CommandLine -and ($_.CommandLine -match 'gradle-local\.bat|runServer')
  } | ForEach-Object {
    try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {}
  }
}

foreach ($v in $versions) {
  Add-Content -Path $summary -Value ("==== SMOKE $v ====") -Encoding ascii
  Write-Host "==== SMOKE $v ===="
  $log = Join-Path $logDir ($v + '-smoke.log')
  if (Test-Path $log) { Remove-Item $log -Force -ErrorAction SilentlyContinue }
  $runDir = Join-Path $root ("versions\" + $v + '\run')
  New-Item -ItemType Directory -Force -Path $runDir | Out-Null
  Set-Content -Path (Join-Path $runDir 'eula.txt') -Value 'eula=true' -Encoding ascii
  Set-Content -Path (Join-Path $runDir 'server.properties') -Value @(
    'online-mode=false'
    'max-players=5'
    'spawn-protection=0'
    'view-distance=2'
    'simulation-distance=2'
    ('motd=FGA smoke ' + $v)
    'level-name=world'
    'gamemode=survival'
    'difficulty=peaceful'
    'enable-command-block=true'
  ) -Encoding ascii

  $arg = '/c ""' + $root + '\gradle-local.bat" --no-daemon --configure-on-demand --max-workers=1 :' + $v + ':runServer --args="--port 0" > "' + $log + '" 2>&1"'
  $p = Start-Process -FilePath 'cmd.exe' -ArgumentList $arg -WorkingDirectory $root -PassThru -WindowStyle Hidden
  $deadline = (Get-Date).AddMinutes(8)
  $status = 'TIMEOUT'
  while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    if (Test-Path $log) {
      $raw = Get-Content -Path $log -Raw -ErrorAction SilentlyContinue
      if ($raw -and ($raw -match 'Done \([0-9.]+s\)!')) { $status = 'OK'; break }
      if ($raw -and ($raw -match 'BUILD FAILED|FAILURE: Build failed|Critical injection failure|Mixin prepare failed|Could not resolve|Exception in thread "main"') -and ($raw -notmatch 'Done \([0-9.]+s\)!')) {
        $status = 'FAIL'; break
      }
    }
    if ($p.HasExited) {
      $raw = if (Test-Path $log) { Get-Content -Path $log -Raw -ErrorAction SilentlyContinue } else { '' }
      if ($raw -and ($raw -match 'Done \([0-9.]+s\)!')) { $status = 'OK' } else { $status = 'EXITED' }
      break
    }
  }

  try { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } catch {}
  try { & taskkill.exe /PID $p.Id /T /F 2>$null | Out-Null } catch {}
  Kill-CarpetJava
  Start-Sleep -Seconds 2

  if (Test-Path $log) {
    $raw = Get-Content -Path $log -Raw -ErrorAction SilentlyContinue
    $hasDone = [bool]($raw -match 'Done \([0-9.]+s\)!')
    $hasMixinFail = [bool]($raw -match 'Critical injection failure|Mixin prepare failed|Mixin apply for mod carpet-fga')
    $hasFga = [bool]($raw -match 'carpet-fga-addition')
    if ($hasDone -and -not $hasMixinFail -and $hasFga) { $status = 'OK' }
    elseif ($hasDone -and $hasMixinFail) { $status = 'FAIL-mixin' }
    elseif (-not $hasDone) { $status = 'FAIL-no-Done' }
    elseif ($hasDone -and -not $hasFga) { $status = 'FAIL-no-mod' }
    Add-Content -Path $summary -Value ("DETAIL $v done=$hasDone fga=$hasFga mixinFail=$hasMixinFail") -Encoding ascii
  } else {
    $status = 'FAIL-no-log'
  }
  Add-Content -Path $summary -Value ("RESULT=$v $status") -Encoding ascii
  Write-Host ("RESULT=$v $status")
}

Add-Content -Path $summary -Value ("ALL_DONE " + (Get-Date -Format o)) -Encoding ascii
Write-Host 'ALL_DONE'