@echo off
REM AeroMQ Broker Startup Script for Windows

echo Starting AeroMQ Broker...

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed or not in PATH
    exit /b 1
)

REM Check if jar file exists
if not exist "aeromq-core\target\aeromq-core-1.0.0-SNAPSHOT.jar" (
    echo Error: aeromq-core jar file not found. Please run build.bat first.
    exit /b 1
)

REM Start the broker
echo Starting AeroMQ Broker on port 8888...
java -cp "aeromq-core\target\aeromq-core-1.0.0-SNAPSHOT.jar;aeromq-core\target\lib\*" com.aeromq.broker.AeroBroker

pause
