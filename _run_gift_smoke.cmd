set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11
set GRADLE_USER_HOME=D:\ai\carpet-fga\.gradle
set TEMP=D:\ai\carpet-fga\.tmp
set TMP=D:\ai\carpet-fga\.tmp
set Path=%JAVA_HOME%\bin;%Path%
cd /d D:\ai\carpet-fga
(
echo carpet villagerPerformanceOptimization true
echo villagerPerformance gift true
echo villagerPerformance gift block add minecraft:emerald_block
echo villagerPerformance help
echo stop
) | gradle-local.bat --no-daemon --offline --configure-on-demand --max-workers=1 :1.21.1:runServer "--args=--port 0" > build-1.21.1-gift-run.log 2>&1
