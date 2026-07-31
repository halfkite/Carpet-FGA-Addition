@echo off
setlocal
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_USER_HOME=C:\Users\28656\.gradle
cd /d D:\ai\carpet-fga
if not exist build\smoke-logs mkdir build\smoke-logs
echo eula=true> versions\1.21.1\run\eula.txt
call gradle-local.bat --no-daemon --configure-on-demand --max-workers=1 :1.21.1:build :1.21.1:runServer --args="--port 0" > build\smoke-logs\1.21.1-spectator-smoke5.log 2>&1
echo EXIT:%ERRORLEVEL%> build\smoke-logs\1.21.1-spectator-smoke5.exit
