from pathlib import Path
text = r"""$ErrorActionPreference = 'Continue'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
$env:GRADLE_USER_HOME = 'C:\Users\28656\.gradle'
$env:TEMP = 'D:\ai\carpet-fga\.tmp'
$env:TMP = 'D:\ai\carpet-fga\.tmp'
New-Item -ItemType Directory -Force -Path 'D:\ai\carpet-fga\build\smoke-logs','D:\ai\carpet-fga\versions\1.18.2\run','D:\ai\carpet-fga\.tmp' | Out-Null
Set-Content 'D:\ai\carpet-fga\versions\1.18.2\run\eula.txt' 'eula=true'
$log = 'D:\ai\carpet-fga\build\smoke-logs\1.18.2-canary.log'
if (Test-Path $log) { Remove-Item $log -Force }
$arg = '/c ""D:\ai\carpet-fga\gradle-local.bat" --no-daemon --configure-on-demand --max-workers=1 :1.18.2:compileJava :1.18.2:runServer --args="--port 0" > "D:\ai\carpet-fga\build\smoke-logs\1.18.2-canary.log" 2>&1"'
$p = Start-Process -FilePath 'cmd.exe' -ArgumentList $arg -WorkingDirectory 'D:\ai\carpet-fga' -PassThru -WindowStyle Hidden
Write-Host ('started pid=' + $p.Id)
$deadline = (Get-Date).AddMinutes(9)
$status = 'TIMEOUT'
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Seconds 10
  if (Test-Path $log) {
    $raw = Get-Content $log -Raw -ErrorAction SilentlyContinue
    if ($raw -and ($raw -match 'Done \([0-9.]+s\)!')) { $status = 'OK'; break }
    if ($raw -and ($raw -match 'Critical injection failure|InvalidMixinException|BUILD FAILED|Mixin apply for mod carpet-fga|What went wrong')) { $status = 'FAIL'; break }
  }
  if ($p.HasExited) { $status = 'EXITED-' + $p.ExitCode; break }
  $size = if (Test-Path $log) { (Get-Item $log).Length } else { 0 }
  Write-Host ('waiting size=' + $size)
}
Write-Host ('STATUS=' + $status)
try { taskkill /PID $p.Id /T /F 2>$null | Out-Null } catch {}
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object { $_.CommandLine -match 'carpet-fga|devlaunch|GradleWrapperMain|gradle-9.5.1' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
if (Test-Path $log) {
  Select-String -Path $log -Pattern 'Done \(|Critical injection|InvalidMixin|BUILD FAILED|Mixin apply|carpet-fga-addition|error:|What went wrong|FillCommand' | Select-Object -First 40 | ForEach-Object { $_.Line }
  $pp = 'D:\ai\carpet-fga\versions\1.18.2\build\preprocessed\main\java\carpet\fga\mixin\FillCommandLimitMixin.java'
  if (Test-Path $pp) { Write-Host '--- preprocessed fill ---'; Get-Content $pp }
}
"""
Path(r'D:/ai/carpet-fga/_run_canary_1182.ps1').write_text(text, encoding='utf-8')
print('ok', len(text))
