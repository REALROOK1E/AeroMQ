@echo off
REM AeroMQ Benchmark Suite Runner for Windows
REM Runs complete performance benchmarks and generates visualizations

setlocal enabledelayedexpansion

set SCRIPT_DIR=%~dp0
set PROJECT_ROOT=%SCRIPT_DIR%..
set RESULTS_DIR=%PROJECT_ROOT%\benchmark-results
set CHARTS_DIR=%PROJECT_ROOT%\benchmark-charts

echo AeroMQ Performance Benchmark Suite
echo ==================================

REM Create results directories
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"
if not exist "%CHARTS_DIR%" mkdir "%CHARTS_DIR%"

REM Function to run Java benchmark
:run_java_benchmark
echo Running Java benchmark...
cd /d "%PROJECT_ROOT%"

REM Check if maven is available
where mvn >nul 2>nul
if %errorlevel% equ 0 (
    echo Building project...
    mvn clean package -DskipTests -q
    
    echo Starting benchmark...
    java -cp "aeromq-benchmark\target\classes;aeromq-client\target\classes;aeromq-core\target\classes;aeromq-protocol\target\classes" ^
         -Xmx2g -XX:+UseG1GC ^
         -Dresults.dir="%RESULTS_DIR%" ^
         com.aeromq.benchmark.BenchmarkRunner
) else (
    echo Maven not found. Please compile manually or install Maven.
    exit /b 1
)
goto :eof

REM Function to install Python dependencies
:install_python_deps
echo Installing Python dependencies...
pip install -r "%SCRIPT_DIR%requirements.txt"
goto :eof

REM Function to generate visualizations
:generate_charts
echo Generating performance charts...
python "%SCRIPT_DIR%visualize_benchmark.py" "%RESULTS_DIR%" --output "%CHARTS_DIR%"
goto :eof

REM Main execution
set action=%1
if "%action%"=="" set action=all

if "%action%"=="benchmark" (
    call :run_java_benchmark
) else if "%action%"=="charts" (
    call :generate_charts
) else if "%action%"=="install-deps" (
    call :install_python_deps
) else if "%action%"=="all" (
    call :run_java_benchmark
    call :generate_charts
) else (
    echo Usage: %0 [benchmark^|charts^|install-deps^|all]
    echo   benchmark    - Run Java benchmark only
    echo   charts       - Generate charts from existing results
    echo   install-deps - Install Python dependencies
    echo   all          - Run benchmark and generate charts ^(default^)
    exit /b 1
)

REM Check Python availability
where python >nul 2>nul
if %errorlevel% neq 0 (
    echo Python is required for chart generation
    echo Please install Python and try again
    exit /b 1
)

echo.
echo Benchmark complete!
echo Results saved to: %RESULTS_DIR%
echo Charts saved to: %CHARTS_DIR%
echo Open %CHARTS_DIR%\performance_report.html to view the full report

endlocal
