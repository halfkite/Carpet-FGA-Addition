@echo off
setlocal
cd /d D:\ai\carpet-fga
set "JAVA_HOME=D:\ai\carpet-fga\.jdk25"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=C:\Users\28656\.gradle"
set "TEMP=D:\ai\carpet-fga\.tmp"
set "TMP=D:\ai\carpet-fga\.tmp"
set "LOG=D:\ai\carpet-fga\build-26x-remaining.log"
echo START %date% %time% > "%LOG%"
java -version >> "%LOG%" 2>&1
call gradle-local.bat --no-daemon --configure-on-demand --max-workers=2 :26.1.2:clean :26.1.2:build --rerun-tasks >> "%LOG%" 2>&1
if errorlevel 1 (echo FAIL 26.1.2>>"%LOG%" & exit /b 1)
echo OK 26.1.2>>"%LOG%"
call gradle-local.bat --no-daemon --configure-on-demand --max-workers=2 :26.2:clean :26.2:build --rerun-tasks >> "%LOG%" 2>&1
if errorlevel 1 (echo FAIL 26.2>>"%LOG%" & exit /b 1)
echo OK 26.2>>"%LOG%"
echo ALL_26X_OK>>"%LOG%"
exit /b 0
