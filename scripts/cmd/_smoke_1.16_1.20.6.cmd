@echo off
setlocal EnableExtensions EnableDelayedExpansion
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_USER_HOME=C:\Users\28656\.gradle
set TEMP=D:\ai\carpet-fga\.tmp
set TMP=D:\ai\carpet-fga\.tmp
set ROOT=D:\ai\carpet-fga
set LOGDIR=%ROOT%\build\smoke-logs
if not exist "%LOGDIR%" mkdir "%LOGDIR%"
set SUMMARY=%LOGDIR%\summary-1.16-1.20.6.txt
echo START %date% %time%> "%SUMMARY%"

for %%V in (1.16.5 1.17.1 1.18.2 1.19.2 1.19.4 1.20.1 1.20.4 1.20.6) do (
  echo ==== SMOKE %%V ====
  echo ==== SMOKE %%V ====>> "%SUMMARY%"
  set LOG=%LOGDIR%\%%V-smoke.log
  if exist "!LOG!" del /f /q "!LOG!"
  if not exist "%ROOT%\versions\%%V\run" mkdir "%ROOT%\versions\%%V\run"
  > "%ROOT%\versions\%%V\run\eula.txt" echo eula=true
  > "%ROOT%\versions\%%V\run\server.properties" (
    echo online-mode=false
    echo max-players=5
    echo spawn-protection=0
    echo view-distance=2
    echo simulation-distance=2
    echo motd=FGA smoke %%V
    echo level-name=world
    echo gamemode=survival
    echo difficulty=peaceful
    echo enable-command-block=true
  )

  start "smoke-%%V" /b cmd /c "call \"%ROOT%\gradle-local.bat\" --no-daemon --configure-on-demand --max-workers=1 :%%V:runServer --args=\"--port 0\" > \"!LOG!\" 2>&1"

  set STATUS=TIMEOUT
  set /a WAIT=0
  :wait_%%V
  timeout /t 5 /nobreak >nul
  set /a WAIT+=5
  if exist "!LOG!" (
    findstr /C:"Done (" "!LOG!" >nul && set STATUS=OK&& goto endwait_%%V
    findstr /C:"BUILD FAILED" /C:"FAILURE: Build failed" /C:"Critical injection failure" /C:"Mixin prepare failed" "!LOG!" >nul && (
      findstr /C:"Done (" "!LOG!" >nul || (set STATUS=FAIL& goto endwait_%%V)
    )
  )
  if !WAIT! LSS 480 goto wait_%%V
  :endwait_%%V

  rem kill gradle/java children for this smoke window
  for /f "tokens=2 delims=," %%P in ('tasklist /v /fo csv ^| findstr /I "smoke-%%V"') do taskkill /PID %%~P /T /F >nul 2>&1
  rem broader cleanup of carpet-fga java
  for /f "tokens=2 delims=," %%P in ('tasklist /v /fo csv ^| findstr /I "carpet-fga"') do taskkill /PID %%~P /T /F >nul 2>&1
  rem kill remaining java launched recently via gradle daemon single-use may already exit
  wmic process where "name='java.exe' and CommandLine like '%%carpet-fga%%'" call terminate >nul 2>&1

  if exist "!LOG!" (
    findstr /C:"Done (" "!LOG!" >nul && set STATUS=OK
    findstr /C:"Done (" "!LOG!" >nul || set STATUS=FAIL-no-Done
    findstr /C:"Critical injection failure" /C:"Mixin prepare failed" "!LOG!" >nul && set STATUS=FAIL-mixin
    findstr /C:"carpet-fga-addition" "!LOG!" >nul || if "!STATUS!"=="OK" set STATUS=FAIL-no-mod
  ) else (
    set STATUS=FAIL-no-log
  )
  echo RESULT=%%V !STATUS!
  echo RESULT=%%V !STATUS!>> "%SUMMARY%"
  timeout /t 3 /nobreak >nul
)

echo ALL_DONE %date% %time%>> "%SUMMARY%"
echo ALL_DONE
exit /b 0