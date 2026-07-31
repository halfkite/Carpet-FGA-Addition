$ErrorActionPreference = "Continue"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:GRADLE_USER_HOME = "C:\Users\28656\.gradle"
$root = "D:\ai\carpet-fga"
$logDir = Join-Path $root "build\smoke-logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$log = Join-Path $logDir "1.21.1-spectator-smoke.log"
if (Test-Path $log) { Remove-Item $log -Force }
$runDir = Join-Path $root "versions\1.21.1\run"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
Set-Content -Path (Join-Path $runDir "eula.txt") -Value "eula=true" -Encoding ascii
$props = @(
  "online-mode=false",
  "max-players=5",
  "spawn-protection=0",
  "view-distance=2",
  "simulation-distance=2",
  "motd=FGA spectator smoke 1.21.1",
  "level-name=world",
  "gamemode=survival",
  "difficulty=peaceful",
  "enable-command-block=true"
)
Set-Content -Path (Join-Path $runDir "server.properties") -Value $props -Encoding ascii
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
  $_.CommandLine -and ($_.CommandLine -match "carpet-fga|net\.fabricmc\.devlaunch")
} | ForEach-Object {
  try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {}
}
$gradle = Join-Path $root "gradle-local.bat"
$arg = "/c `"$gradle`" --no-daemon --configure-on-demand --max-workers=1 :1.21.1:runServer --args=`"--port 0`" > `"$log`" 2>&1"
$p = Start-Process -FilePath "cmd.exe" -ArgumentList $arg -WorkingDirectory $root -PassThru -WindowStyle Hidden
Set-Content -Path (Join-Path $logDir "1.21.1-spectator-smoke.pid") -Value $p.Id -Encoding ascii
Write-Output "STARTED pid=$($p.Id)"
$deadline = (Get-Date).AddMinutes(8)
$status = "TIMEOUT"
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 5
  if (Test-Path $log) {
    $raw = Get-Content -Path $log -Raw -ErrorAction SilentlyContinue
    if ($raw -and ($raw -match "Done \([0-9.]+s\)!")) { $status = "OK"; break }
    if ($raw -and ($raw -match "Critical injection failure|Mixin prepare failed|InvalidMixinException|BUILD FAILED|FAILURE: Build failed") -and ($raw -notmatch "Done \([0-9.]+s\)!")) {
      $status = "FAIL"
      break
    }
  }
  if ($p.HasExited) {
    $raw = if (Test-Path $log) { Get-Content -Path $log -Raw -ErrorAction SilentlyContinue } else { "" }
    if ($raw -and ($raw -match "Done \([0-9.]+s\)!")) { $status = "OK" } else { $status = "EXITED" }
    break
  }
}
try { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } } catch {}
try { & taskkill.exe /PID $p.Id /T /F 2>$null | Out-Null } catch {}
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
  $_.CommandLine -and ($_.CommandLine -match "carpet-fga|net\.fabricmc\.devlaunch")
} | ForEach-Object {
  try { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue } catch {}
}
Start-Sleep -Seconds 2
$raw = if (Test-Path $log) { Get-Content -Path $log -Raw -ErrorAction SilentlyContinue } else { "" }
$hasDone = [bool]($raw -match "Done \([0-9.]+s\)!")
$hasMixinFail = [bool]($raw -match "Critical injection failure|Mixin prepare failed|Mixin apply for mod carpet-fga|InvalidMixinException")
$hasFga = [bool]($raw -match "carpet-fga-addition")
$hasSpectator = [bool]($raw -match "spectatorFreeTeleport|TeleportCommandMixin|ServerPlayerSpectatorTeleportMixin")
if ($hasDone -and -not $hasMixinFail) { $status = "OK" }
$summary = Join-Path $logDir "1.21.1-spectator-smoke-summary.txt"
@(
  "STATUS=$status",
  "done=$hasDone",
  "fga=$hasFga",
  "mixinFail=$hasMixinFail",
  "spectatorMention=$hasSpectator",
  "log=$log"
) | Set-Content -Path $summary -Encoding ascii
Write-Output "STATUS=$status done=$hasDone mixinFail=$hasMixinFail fga=$hasFga"
if (Test-Path $log) {
  Select-String -Path $log -Pattern "Done \(|Mixin|ERROR|spectatorFreeTeleport|TeleportCommand|carpet-fga-addition" | Select-Object -Last 40
}
