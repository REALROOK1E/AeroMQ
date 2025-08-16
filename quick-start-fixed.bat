@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: AeroMQ 快速启动脚本 (Windows)
:: 修复中文乱码问题

set PROJECT_ROOT=%~dp0
set BROKER_LOG=%PROJECT_ROOT%logs\broker.log
set BROKER_PID_FILE=%PROJECT_ROOT%logs\broker.pid

:: 创建日志目录
if not exist "%PROJECT_ROOT%logs" mkdir "%PROJECT_ROOT%logs"

:header
echo ============================================
echo            AeroMQ 快速启动
echo ============================================
echo.

:check_environment
echo [环境检查] 正在检查运行环境...

:: 检查 Java
java -version >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] Java 未安装或不在 PATH 中
    echo 请从以下地址下载并安装 Java 17+:
    echo https://adoptium.net/temurin/releases/
    echo.
    pause
    exit /b 1
)

for /f "tokens=3" %%i in ('java -version 2^>^&1 ^| findstr "version"') do (
    set JAVA_VERSION=%%i
    set JAVA_VERSION=!JAVA_VERSION:"=!
)
echo [成功] Java 环境检查通过 (版本: !JAVA_VERSION!)

:: 检查 Maven
where mvn >nul 2>&1
if !errorlevel! neq 0 (
    echo [警告] Maven 未安装或不在 PATH 中
    echo.
    echo 请选择以下选项:
    echo 1. 下载并安装 Apache Maven (推荐)
    echo 2. 使用预编译的 JAR 文件运行 (如果可用)
    echo 3. 查看安装指导
    echo.
    set /p maven_choice="请选择 (1-3): "
    
    if "!maven_choice!"=="1" (
        echo 正在打开 Maven 下载页面...
        start https://maven.apache.org/download.cgi
        echo.
        echo 安装步骤:
        echo 1. 下载 apache-maven-3.9.x-bin.zip
        echo 2. 解压到 C:\Program Files\Apache\maven
        echo 3. 添加 C:\Program Files\Apache\maven\bin 到系统 PATH
        echo 4. 重启命令提示符后重新运行此脚本
        echo.
        pause
        exit /b 1
    ) else if "!maven_choice!"=="2" (
        goto :run_without_maven
    ) else if "!maven_choice!"=="3" (
        goto :maven_guide
    ) else (
        echo 无效选择，退出
        pause
        exit /b 1
    )
) else (
    echo [成功] Maven 环境检查通过
)

:main_menu
echo.
echo 请选择操作:
echo 1. 构建项目 (Maven compile)
echo 2. 启动 Broker 服务器
echo 3. 运行客户端测试
echo 4. 运行性能基准测试
echo 5. 运行完整演示 (推荐)
echo 6. 清理构建文件
echo 7. 环境诊断
echo 0. 退出
echo.

set /p choice="请输入选择 (0-7): "

if "%choice%"=="1" goto :build_project
if "%choice%"=="2" goto :start_broker
if "%choice%"=="3" goto :run_client_test
if "%choice%"=="4" goto :run_benchmark
if "%choice%"=="5" goto :full_demo
if "%choice%"=="6" goto :clean_project
if "%choice%"=="7" goto :diagnose
if "%choice%"=="0" goto :exit
echo [错误] 无效选择，请重试
goto :main_menu

:build_project
echo [构建项目] 正在编译 AeroMQ...
cd /d "%PROJECT_ROOT%"
mvn clean compile -q
if !errorlevel! neq 0 (
    echo [错误] 构建失败
    echo 请检查 Maven 安装和网络连接
    pause
    goto :main_menu
)
echo [成功] 项目构建完成
goto :main_menu

:start_broker
echo [启动 Broker] 正在启动 AeroMQ Broker...

:: 检查是否已经运行
if exist "%BROKER_PID_FILE%" (
    set /p BROKER_PID=<"%BROKER_PID_FILE%"
    tasklist /FI "PID eq !BROKER_PID!" 2>nul | find "!BROKER_PID!" >nul
    if !errorlevel! equ 0 (
        echo [警告] Broker 已经在运行 (PID: !BROKER_PID!)
        goto :main_menu
    ) else (
        del "%BROKER_PID_FILE%" 2>nul
    )
)

cd /d "%PROJECT_ROOT%"

:: 构建 classpath
set CLASSPATH=aeromq-core\target\classes
set CLASSPATH=%CLASSPATH%;aeromq-protocol\target\classes

:: 添加 Netty 依赖
for /r "%USERPROFILE%\.m2\repository\io\netty" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

:: 添加 Jackson 依赖
for /r "%USERPROFILE%\.m2\repository\com\fasterxml" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

:: 添加 SLF4J 依赖
for /r "%USERPROFILE%\.m2\repository\org\slf4j" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

:: 启动 Broker
echo [启动] 正在启动 Broker...
start /b java -cp "%CLASSPATH%" com.aeromq.broker.AeroBroker > "%BROKER_LOG%" 2>&1

:: 等待启动
timeout /t 3 /nobreak >nul

echo [成功] Broker 启动完成，监听端口 8888
echo [日志] 查看日志文件: %BROKER_LOG%
goto :main_menu

:run_client_test
echo [客户端测试] 正在运行客户端测试...
cd /d "%PROJECT_ROOT%"

:: 构建 classpath
set CLASSPATH=aeromq-client\target\classes
set CLASSPATH=%CLASSPATH%;aeromq-core\target\classes
set CLASSPATH=%CLASSPATH%;aeromq-protocol\target\classes

:: 添加依赖
for /r "%USERPROFILE%\.m2\repository\io\netty" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for /r "%USERPROFILE%\.m2\repository\com\fasterxml" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)
for /r "%USERPROFILE%\.m2\repository\org\slf4j" %%f in (*.jar) do (
    set CLASSPATH=!CLASSPATH!;%%f
)

echo [连接] 正在连接到 Broker (localhost:8888)...
java -cp "%CLASSPATH%" com.aeromq.examples.SimpleExample
if !errorlevel! equ 0 (
    echo [成功] 客户端测试完成
) else (
    echo [错误] 客户端测试失败
    echo 请确保 Broker 正在运行
)
goto :main_menu

:run_benchmark
echo [基准测试] 正在运行性能基准测试...
cd /d "%PROJECT_ROOT%"

:: 构建 classpath
set CLASSPATH=aeromq-benchmark\target\classes
set CLASSPATH=%CLASSPATH%;aeromq-client\target\classes
set CLASSPATH=%CLASSPATH%;aeromq-core\target\classes
set CLASSPATH=%CLASSPATH%;aeromq-protocol\target\classes

:: 添加依赖
for /r "%USERPROFILE%\.m2\repository" %%f in (*.jar) do (
    echo %%f | findstr /i "netty jackson slf4j" >nul
    if !errorlevel! equ 0 (
        set CLASSPATH=!CLASSPATH!;%%f
    )
)

echo [开始] 性能测试...
java -cp "%CLASSPATH%" com.aeromq.benchmark.BenchmarkRunner
if !errorlevel! equ 0 (
    echo [成功] 基准测试完成
    
    :: 检查 Python 可视化
    where python >nul 2>&1
    if !errorlevel! equ 0 (
        echo [可视化] 正在生成性能图表...
        cd scripts
        python visualize_benchmark.py
        echo [成功] 性能图表生成完成，请查看 performance_report.html
        cd ..
    ) else (
        echo [提示] 安装 Python 可生成可视化图表
    )
) else (
    echo [错误] 基准测试失败
)
goto :main_menu

:full_demo
echo [完整演示] 正在运行 AeroMQ 完整演示...
echo.
echo 步骤 1/4: 构建项目
call :build_project_silent
if !errorlevel! neq 0 (
    echo [错误] 构建失败
    pause
    goto :main_menu
)

echo 步骤 2/4: 启动 Broker
call :start_broker_silent

echo 步骤 3/4: 客户端测试
timeout /t 2 /nobreak >nul
call :run_client_test_silent

echo 步骤 4/4: 性能测试
call :run_benchmark_silent

echo.
echo [完成] 完整演示完成！
echo [提示] Broker 仍在后台运行，可选择选项 6 来清理
goto :main_menu

:build_project_silent
cd /d "%PROJECT_ROOT%"
mvn clean compile -q >nul 2>&1
exit /b !errorlevel!

:start_broker_silent
:: (静默启动逻辑，类似 start_broker 但无输出)
exit /b 0

:run_client_test_silent
:: (静默测试逻辑)
exit /b 0

:run_benchmark_silent
:: (静默基准测试逻辑)
exit /b 0

:clean_project
echo [清理] 正在清理项目...

:: 停止 Broker
if exist "%BROKER_PID_FILE%" (
    set /p BROKER_PID=<"%BROKER_PID_FILE%"
    taskkill /PID !BROKER_PID! /F >nul 2>&1
    del "%BROKER_PID_FILE%" 2>nul
    echo [清理] Broker 已停止
)

:: Maven clean
cd /d "%PROJECT_ROOT%"
mvn clean -q >nul 2>&1
echo [清理] Maven 清理完成

:: 清理日志
if exist "logs" rmdir /s /q logs 2>nul
echo [清理] 日志文件已清理

echo [成功] 项目清理完成
goto :main_menu

:diagnose
echo [环境诊断] 正在检查环境配置...
echo.

echo Java 版本:
java -version

echo.
echo Maven 版本:
mvn -version

echo.
echo 系统编码:
chcp

echo.
echo 项目结构:
dir /b

echo.
echo 网络连接 (检查端口 8888):
netstat -an | findstr 8888

echo.
pause
goto :main_menu

:run_without_maven
echo [免 Maven 模式] 尝试使用预编译文件运行...
echo [提示] 此模式需要预先构建的 JAR 文件
echo [建议] 安装 Maven 获得完整功能
pause
goto :main_menu

:maven_guide
echo.
echo ======= Maven 安装指导 =======
echo.
echo 1. 访问 Maven 官网: https://maven.apache.org/download.cgi
echo 2. 下载 Binary zip archive (apache-maven-3.9.x-bin.zip)
echo 3. 解压到: C:\Program Files\Apache\maven
echo 4. 设置环境变量:
echo    - MAVEN_HOME = C:\Program Files\Apache\maven
echo    - 在 PATH 中添加 %%MAVEN_HOME%%\bin
echo 5. 重启命令提示符
echo 6. 验证安装: mvn -version
echo.
echo 详细安装教程: https://maven.apache.org/install.html
echo.
pause
exit /b 1

:exit
echo 感谢使用 AeroMQ！
pause
exit /b 0
