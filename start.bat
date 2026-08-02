@echo off
setlocal EnableExtensions
cd /d "%~dp0"

echo Baue aktuelles JAR-Paket...
call mvn package -DskipTests
if errorlevel 1 (
    echo Build fehlgeschlagen.
    pause
    exit /b 1
)

set "JAR="
for %%F in ("target\mt5-backtester-*.jar") do set "JAR=%%~fF"

if not defined JAR (
    echo Fehler: Konnte das JAR File nach dem Build nicht finden.
    pause
    exit /b 1
)

echo Starte Backtester: %JAR%
java -jar "%JAR%"

set "EXIT=%ERRORLEVEL%"
if not "%EXIT%"=="0" (
    echo.
    echo Anwendung wurde mit Code %EXIT% beendet.
    pause
)

exit /b %EXIT%
