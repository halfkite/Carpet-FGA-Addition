@echo off
setlocal
set "GRADLE_HOME=%~dp0tools\gradle\gradle-9.5.1"
if exist "%GRADLE_HOME%\bin\gradle.bat" (
    call "%GRADLE_HOME%\bin\gradle.bat" %*
) else (
    call "%~dp0gradlew.bat" %*
)
