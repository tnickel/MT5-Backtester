@echo off
setlocal EnableExtensions
cd /d "%~dp0"

:: Prüfe, ob eine JAR-Datei existiert
set "JAR="
for %%F in ("target\mt5-backtester-*.jar") do set "JAR=%%~fF"

:: Wenn keine JAR gefunden wurde, baue das Projekt
if not defined JAR (
    echo Kein JAR gefunden. Baue Projekt...
    call mvn clean package -DskipTests
    if errorlevel 1 (
        echo Build fehlgeschlagen.
        pause
        exit /b 1
    )
    :: Suche das JAR nach dem Build erneut
    for %%F in ("target\mt5-backtester-*.jar") do set "JAR=%%~fF"
)

:: Sicherheitsprüfung, falls das JAR immer noch nicht da ist
if not defined JAR (
    echo Fehler: Konnte das JAR File auch nach dem Build nicht finden.
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
