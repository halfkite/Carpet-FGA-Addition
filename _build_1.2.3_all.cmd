@echo off
setlocal EnableExtensions
cd /d D:\ai\carpet-fga
set "GRADLE_USER_HOME=C:\Users\28656\.gradle"
set "TEMP=D:\ai\carpet-fga\.tmp"
set "TMP=D:\ai\carpet-fga\.tmp"
if not exist "D:\ai\carpet-fga\.tmp" mkdir "D:\ai\carpet-fga\.tmp"
set "LOG=D:\ai\carpet-fga\build-1.2.3-all.log"
echo START %date% %time% > "%LOG%"

set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
for %%V in (1.21.1 1.21.3 1.21.4 1.21.5 1.21.8 1.21.10 1.21.11) do (
  echo ==== BUILD %%V ==== >> "%LOG%"
  call gradle-local.bat --no-daemon --configure-on-demand --max-workers=2 :%%V:clean :%%V:build --rerun-tasks >> "%LOG%" 2>&1
  if errorlevel 1 (echo FAIL %%V>>"%LOG%" & exit /b 1)
  echo OK %%V>>"%LOG%"
)

set "JAVA_HOME=D:\ai\carpet-fga\.jdk25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
for %%V in (26.1.2 26.2) do (
  echo ==== BUILD %%V ==== >> "%LOG%"
  call gradle-local.bat --no-daemon --configure-on-demand --max-workers=2 :%%V:clean :%%V:build --rerun-tasks >> "%LOG%" 2>&1
  if errorlevel 1 (echo FAIL %%V>>"%LOG%" & exit /b 1)
  echo OK %%V>>"%LOG%"
)
echo ALL_BUILDS_OK>>"%LOG%"
exit /b 0
