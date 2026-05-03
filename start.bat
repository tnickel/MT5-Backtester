@echo off
title MT5 Backtester
echo =========================================
echo Building MT5 Backtester...
echo =========================================
call mvn package -DskipTests

echo =========================================
echo Starting MT5 Backtester...
echo =========================================
java -jar target/mt5-backtester-1.2.6.jar
pause
