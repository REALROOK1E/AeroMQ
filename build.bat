@echo off
REM AeroMQ Build Script for Windows

echo Building AeroMQ Project...

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Maven is not installed or not in PATH
    exit /b 1
)

REM Clean and compile the project
echo Cleaning previous builds...
call mvn clean

echo Compiling and packaging...
call mvn compile package -DskipTests

REM Check if build was successful
if %ERRORLEVEL% EQU 0 (
    echo Build completed successfully!
    echo.
    echo Generated artifacts:
    echo - aeromq-core\target\aeromq-core-1.0.0-SNAPSHOT.jar
    echo - aeromq-client\target\aeromq-client-1.0.0-SNAPSHOT.jar
    echo - aeromq-benchmark\target\aeromq-benchmark-1.0.0-SNAPSHOT.jar
    echo.
    echo To run the broker: start-broker.bat
    echo To run benchmarks: java -jar aeromq-benchmark\target\aeromq-benchmark-1.0.0-SNAPSHOT.jar
) else (
    echo Build failed!
    exit /b 1
)
