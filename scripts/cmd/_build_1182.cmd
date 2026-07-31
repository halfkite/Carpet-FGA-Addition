@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_USER_HOME=C:\Users\28656\.gradle
set TEMP=D:\ai\carpet-fga\.tmp
set TMP=D:\ai\carpet-fga\.tmp
echo START %date% %time% > D:\ai\carpet-fga\build-1.18.2-port.log
call D:\ai\carpet-fga\gradle-local.bat --no-daemon --configure-on-demand --max-workers=1 :1.18.2:clean :1.18.2:build --rerun-tasks >> D:\ai\carpet-fga\build-1.18.2-port.log 2>&1
echo EXIT=%ERRORLEVEL%>> D:\ai\carpet-fga\build-1.18.2-port.log