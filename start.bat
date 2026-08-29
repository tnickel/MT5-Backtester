@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "JAVA_EXE="
set "LOCAL_JDK=%USERPROFILE%\.jdk\jdk-25"
set "ADOPTIUM_JDK="
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*") do set "ADOPTIUM_JDK=%%D"

if exist "%LOCAL_JDK%\bin\java.exe" (
    call :select_java "%LOCAL_JDK%"
)

if not defined JAVA_EXE if defined ADOPTIUM_JDK if exist "%ADOPTIUM_JDK%\bin\java.exe" (
    call :select_java "%ADOPTIUM_JDK%"
)

if not defined JAVA_EXE if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    call :select_java "%JAVA_HOME%"
)

if not defined JAVA_EXE (
    echo Fehler: Zum Bauen und Starten wird ein JDK ab Version 25 benoetigt.
    echo Erwartet wurde "%LOCAL_JDK%" oder ein gueltiges JAVA_HOME.
    echo Installieren Sie JDK 25 oder setzen Sie JAVA_HOME auf dessen Verzeichnis.
    pause
    exit /b 1
)

set "JAVA_HOME=%SELECTED_JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Verwende Java %JAVA_MAJOR% aus: %JAVA_HOME%
echo Baue aktuelles JAR-Paket...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo Build fehlgeschlagen.
    pause
    exit /b 1
)

set "JAR="
for %%F in ("target\mt5-backtester-*.jar") do (
    if exist "%%~fF" (
        echo %%~nxF | %SystemRoot%\System32\findstr.exe /i /r /c:"-sources\.jar$" /c:"-javadoc\.jar$" /c:"-tests\.jar$" >nul
        if errorlevel 1 (
            if defined JAR (
                echo Fehler: Mehr als ein startbares JAR wurde erzeugt.
                echo Gefunden: %JAR%
                echo Zusaetzlich: %%~fF
                pause
                exit /b 1
            )
            set "JAR=%%~fF"
        )
    )
)

if not defined JAR (
    echo Fehler: Konnte das JAR File nach dem Build nicht finden.
    pause
    exit /b 1
)

echo Starte Backtester: %JAR%
rem Anker fuer config/, data/, exports/ am Installationsverzeichnis (statt aktuellem Arbeitsverzeichnis)
set "BACKTESTER_HOME=%~dp0"
"%JAVA_EXE%" -jar "%JAR%"

set "EXIT=%ERRORLEVEL%"
if not "%EXIT%"=="0" (
    echo.
    echo Anwendung wurde mit Code %EXIT% beendet.
    pause
)

exit /b %EXIT%

:select_java
set "JAVA_CANDIDATE_HOME=%~1"
set "JAVA_CANDIDATE_EXE=%~1\bin\java.exe"
set "JAVA_VERSION="
set "JAVA_CANDIDATE_MAJOR="

if not exist "%JAVA_CANDIDATE_HOME%\bin\javac.exe" exit /b 1
for /f "tokens=3" %%V in ('^""%JAVA_CANDIDATE_EXE%" -version 2^>^&1 ^| %SystemRoot%\System32\findstr.exe /i /c:"version"^"') do if not defined JAVA_VERSION set "JAVA_VERSION=%%~V"
for /f "tokens=1 delims=." %%M in ("%JAVA_VERSION%") do set "JAVA_CANDIDATE_MAJOR=%%M"

if not defined JAVA_CANDIDATE_MAJOR exit /b 1
for /f "delims=0123456789" %%N in ("%JAVA_CANDIDATE_MAJOR%") do exit /b 1
if %JAVA_CANDIDATE_MAJOR% LSS 25 exit /b 1

set "SELECTED_JAVA_HOME=%JAVA_CANDIDATE_HOME%"
set "JAVA_EXE=%JAVA_CANDIDATE_EXE%"
set "JAVA_MAJOR=%JAVA_CANDIDATE_MAJOR%"
exit /b 0
