@echo off
REM AeroMQ 快速启动与测试脚本

setlocal EnableDelayedExpansion

echo ========================================
echo     AeroMQ 快速启动与测试工具
echo ========================================
echo.

REM 检查 Java 环境
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 Java，请先安装 Java 17+
    pause
    exit /b 1
)

echo [信息] 检查 Java 版本...
java -version

echo.
echo 请选择操作:
echo 1. 构建项目
echo 2. 启动 Broker
echo 3. 运行客户端测试
echo 4. 运行性能基准测试
echo 5. 运行完整演示 (推荐)
echo 6. 清理构建文件
echo.

set /p choice="请输入选择 (1-6): "

if "%choice%"=="1" goto build
if "%choice%"=="2" goto start_broker
if "%choice%"=="3" goto client_test
if "%choice%"=="4" goto benchmark
if "%choice%"=="5" goto full_demo
if "%choice%"=="6" goto clean

echo [错误] 无效选择
pause
exit /b 1

:build
echo [构建] 正在构建 AeroMQ 项目...
call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 构建失败
    pause
    exit /b 1
)
echo [成功] 项目构建完成
goto end

:start_broker
echo [启动] 正在启动 AeroMQ Broker...
if not exist "aeromq-core\target\aeromq-core-1.0.0-SNAPSHOT.jar" (
    echo [错误] 找不到构建文件，请先运行构建
    goto build
)

echo [信息] Broker 将在端口 9000 启动
echo [信息] 按 Ctrl+C 停止 Broker
java -Xmx1g -XX:+UseG1GC ^
     -Daeromq.config=aeromq-core/src/main/resources/aeromq.properties ^
     -cp "aeromq-core/target/aeromq-core-1.0.0-SNAPSHOT.jar;aeromq-core/target/lib/*;aeromq-protocol/target/aeromq-protocol-1.0.0-SNAPSHOT.jar" ^
     com.aeromq.broker.AeroBroker
goto end

:client_test
echo [测试] 正在运行客户端测试...
if not exist "aeromq-client\target\aeromq-client-1.0.0-SNAPSHOT.jar" (
    echo [错误] 找不到客户端构建文件，请先运行构建
    goto build
)

java -cp "aeromq-client/target/aeromq-client-1.0.0-SNAPSHOT.jar;aeromq-client/target/lib/*;aeromq-protocol/target/aeromq-protocol-1.0.0-SNAPSHOT.jar;aeromq-core/target/aeromq-core-1.0.0-SNAPSHOT.jar" ^
     com.aeromq.examples.SimpleExample
goto end

:benchmark
echo [基准测试] 正在运行性能基准测试...
if not exist "aeromq-benchmark\target\aeromq-benchmark-1.0.0-SNAPSHOT.jar" (
    echo [错误] 找不到基准测试构建文件，请先运行构建
    goto build
)

echo [信息] 基准测试将运行约 2 分钟...
java -Xmx2g -XX:+UseG1GC ^
     -Dresults.dir=benchmark-results ^
     -cp "aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar;aeromq-benchmark/target/lib/*" ^
     com.aeromq.benchmark.BenchmarkRunner

echo [信息] 正在生成性能图表...
if exist "scripts\visualize_benchmark.py" (
    python scripts\visualize_benchmark.py benchmark-results --output benchmark-charts
    echo [成功] 性能报告已生成到 benchmark-charts/ 目录
) else (
    echo [警告] 找不到可视化脚本，跳过图表生成
)
goto end

:full_demo
echo [完整演示] 正在运行 AeroMQ 完整演示...
echo.

echo 步骤 1/4: 构建项目
call mvn clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 构建失败
    pause
    exit /b 1
)
echo [完成] 项目构建

echo.
echo 步骤 2/4: 启动 Broker (后台模式)
start /b "AeroMQ-Broker" java -Xmx1g -XX:+UseG1GC ^
     -Daeromq.config=aeromq-core/src/main/resources/aeromq.properties ^
     -cp "aeromq-core/target/aeromq-core-1.0.0-SNAPSHOT.jar;aeromq-core/target/lib/*;aeromq-protocol/target/aeromq-protocol-1.0.0-SNAPSHOT.jar" ^
     com.aeromq.broker.AeroBroker

echo [等待] Broker 启动中...
timeout /t 5 /nobreak >nul

echo.
echo 步骤 3/4: 运行客户端测试
java -cp "aeromq-client/target/aeromq-client-1.0.0-SNAPSHOT.jar;aeromq-client/target/lib/*;aeromq-protocol/target/aeromq-protocol-1.0.0-SNAPSHOT.jar;aeromq-core/target/aeromq-core-1.0.0-SNAPSHOT.jar" ^
     com.aeromq.examples.SimpleExample

echo.
echo 步骤 4/4: 运行性能基准测试
java -Xmx2g -XX:+UseG1GC ^
     -Dresults.dir=benchmark-results ^
     -cp "aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar;aeromq-benchmark/target/lib/*" ^
     com.aeromq.benchmark.BenchmarkRunner

echo.
echo [完成] 完整演示完成！
echo [结果] 查看 benchmark-results/ 和 benchmark-charts/ 目录获取详细结果
taskkill /F /FI "WindowTitle eq AeroMQ-Broker*" 2>nul
goto end

:clean
echo [清理] 正在清理构建文件...
call mvn clean
if exist "benchmark-results" rmdir /s /q "benchmark-results"
if exist "benchmark-charts" rmdir /s /q "benchmark-charts"
if exist "logs" rmdir /s /q "logs"
echo [完成] 清理完成
goto end

:end
echo.
echo ========================================
echo 操作完成！按任意键退出...
pause >nul
