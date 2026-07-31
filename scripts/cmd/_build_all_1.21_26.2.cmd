@echo off
setlocal EnableExtensions
cd /d "D:\ai\carpet-fga"
set "GRADLE_USER_HOME=C:\Users\28656\.gradle"
set "TEMP=D:\ai\carpet-fga\.tmp"
set "TMP=D:\ai\carpet-fga\.tmp"
if not exist "D:\ai\carpet-fga\.tmp" mkdir "D:\ai\carpet-fga\.tmp"
set "LOG=D:\ai\carpet-fga\build-all-1.21-26.2.log"
echo START %date% %time% > "%LOG%"

set "JAVA21=C:\Program Files\Java\jdk-21.0.11"
set "JAVA25=D:\????\???\???\fabric?????26.1v0.2??\jdk-25"

set "JAVA_HOME=%JAVA21%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
for %%V in (1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11) do (
  echo ==== BUILD %%V ==== >> "%LOG%"
  call gradle-local.bat --no-daemon --configure-on-demand --max-workers=2 :%%V:clean :%%V:build --rerun-tasks >> "%LOG%" 2>&1
  if errorlevel 1 (
    echo FAIL %%V >> "%LOG%"
    echo FAIL_EXIT %%V
    exit /b 1
  )
  echo OK %%V >> "%LOG%"
)

set "JAVA_HOME=%JAVA25%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
for %%V in (26.1.2 26.2) do (
  echo ==== BUILD %%V ==== >> "%LOG%"
  call gradle-local.bat --no-daemon --configure-on-demand --max-workers=2 :%%V:clean :%%V:build --rerun-tasks >> "%LOG%" 2>&1
  if errorlevel 1 (
    echo FAIL %%V >> "%LOG%"
    echo FAIL_EXIT %%V
    exit /b 1
  )
  echo OK %%V >> "%LOG%"
)

echo ALL_BUILDS_OK >> "%LOG%"
exit /b 0
