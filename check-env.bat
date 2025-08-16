@echo off
REM AeroMQ Environment Check Script

echo Checking AeroMQ development environment...
echo.

REM Check Java
echo [1/2] Checking Java installation...
where java >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo ✓ Java is installed
    java -version 2>&1 | findstr "version"
) else (
    echo ✗ Java is NOT installed or not in PATH
    echo Please install Java 17 or higher from: https://adoptium.net/
    set MISSING_DEPS=1
)

echo.

REM Check Maven
echo [2/2] Checking Maven installation...
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo ✓ Maven is installed
    mvn -version 2>&1 | findstr "Apache Maven"
) else (
    echo ✗ Maven is NOT installed or not in PATH
    echo Please install Maven from: https://maven.apache.org/download.cgi
    echo.
    echo Quick installation steps:
    echo 1. Download Maven binary zip from the link above
    echo 2. Extract to C:\Program Files\Apache\maven
    echo 3. Add C:\Program Files\Apache\maven\bin to your PATH environment variable
    echo 4. Restart this command prompt
    set MISSING_DEPS=1
)

echo.

if defined MISSING_DEPS (
    echo ❌ Some dependencies are missing. Please install them and run this script again.
    echo.
    echo Alternative: You can use Maven Wrapper if available:
    echo   gradlew.bat build  ^(if Gradle Wrapper is present^)
    echo   mvnw.cmd clean compile  ^(if Maven Wrapper is present^)
    pause
    exit /b 1
) else (
    echo ✅ All dependencies are installed! You can now build AeroMQ.
    echo.
    echo Next steps:
    echo   1. Run: build.bat
    echo   2. Run: start-broker.bat
    echo.
    pause
)
