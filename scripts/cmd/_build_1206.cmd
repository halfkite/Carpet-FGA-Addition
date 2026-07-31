@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%
set GRADLE_USER_HOME=C:\Users\28656\.gradle
set TEMP=D:\ai\carpet-fga\.tmp
set TMP=D:\ai\carpet-fga\.tmp
call gradle-local.bat --no-daemon --configure-on-demand --max-workers=2 :1.20.6:build --stacktrace > build-1.20.6-port.log 2>&1
echo EXIT=%ERRORLEVEL%>> build-1.20.6-port.log
