@echo off
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_USER_HOME=C:\Users\28656\.gradle
set TEMP=D:\ai\carpet-fga\.tmp
set TMP=D:\ai\carpet-fga\.tmp
set LOG=D:\ai\carpet-fga\build-new-versions.log
echo START %date% %time% > "%LOG%"
for %%V in (1.19.4 1.19.2 1.18.2 1.16.5) do (
  echo ==== BUILD %%V ==== >> "%LOG%"
  call gradle-local.bat --no-daemon --configure-on-demand --max-workers=1 :%%V:clean :%%V:build --rerun-tasks >> "%LOG%" 2>&1
  if errorlevel 1 (
    echo FAIL %%V>>"%LOG%"
    exit /b 1
  )
  echo OK %%V>>"%LOG%"
)
echo ALL_NEW_OK>>"%LOG%"
exit /b 0
