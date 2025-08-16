@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: AeroMQ 简化启动脚本 (无需 Maven)
:: 适用于已预编译或仅进行基础测试的情况

echo ============================================
echo          AeroMQ 简化启动脚本
echo ============================================
echo.

:: 检查 Java
java -version >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] Java 未安装或不在 PATH 中
    echo 请安装 Java 17+ 后重试
    pause
    exit /b 1
)

echo [成功] Java 环境检查通过
echo.

:menu
echo 可用选项:
echo 1. 查看项目结构
echo 2. 检查环境配置
echo 3. 手动编译指导
echo 4. Maven 安装指导
echo 5. 查看源代码文件
echo 0. 退出
echo.

set /p choice="请选择 (0-5): "

if "%choice%"=="1" goto :show_structure
if "%choice%"=="2" goto :check_env
if "%choice%"=="3" goto :compile_guide
if "%choice%"=="4" goto :maven_guide
if "%choice%"=="5" goto :show_source
if "%choice%"=="0" goto :exit

echo 无效选择，请重试
goto :menu

:show_structure
echo.
echo [项目结构]
echo AeroMQ/
echo ├── aeromq-protocol/     # 协议定义
echo ├── aeromq-core/         # 核心引擎
echo ├── aeromq-client/       # 客户端 SDK
echo ├── aeromq-benchmark/    # 性能测试
echo ├── scripts/             # Python 脚本
echo └── docs/               # 文档
echo.

if exist "aeromq-protocol" (
    echo ✅ aeromq-protocol 模块存在
) else (
    echo ❌ aeromq-protocol 模块缺失
)

if exist "aeromq-core" (
    echo ✅ aeromq-core 模块存在
) else (
    echo ❌ aeromq-core 模块缺失
)

if exist "aeromq-client" (
    echo ✅ aeromq-client 模块存在
) else (
    echo ❌ aeromq-client 模块缺失
)

echo.
pause
goto :menu

:check_env
echo.
echo [环境检查]
echo.

echo Java 版本:
java -version 2>&1

echo.
echo Maven 状态:
where mvn >nul 2>&1
if !errorlevel! equ 0 (
    echo ✅ Maven 已安装
    mvn -version
) else (
    echo ❌ Maven 未安装
)

echo.
echo Python 状态:
where python >nul 2>&1
if !errorlevel! equ 0 (
    echo ✅ Python 已安装
    python --version 2>&1
) else (
    echo ❌ Python 未安装
)

echo.
echo 当前目录文件:
dir /b

echo.
pause
goto :menu

:compile_guide
echo.
echo ======= 手动编译指导 =======
echo.
echo 如果没有 Maven，您可以手动编译：
echo.
echo 1. 下载依赖 JAR 文件:
echo    - Netty (网络通信)
echo    - Jackson (JSON 序列化)
echo    - SLF4J (日志)
echo.
echo 2. 创建 lib 目录并放入依赖:
echo    mkdir lib
echo    # 将 JAR 文件复制到 lib 目录
echo.
echo 3. 手动编译:
echo    javac -cp "lib/*" -d target/classes src/main/java/**/*.java
echo.
echo 4. 运行:
echo    java -cp "target/classes;lib/*" com.aeromq.broker.AeroBroker
echo.
echo [推荐] 安装 Maven 以获得完整的依赖管理和构建功能
echo.
pause
goto :menu

:maven_guide
echo.
echo ======= Maven 快速安装 =======
echo.
echo 1. 下载 Maven:
echo    https://maven.apache.org/download.cgi
echo    选择: Binary zip archive
echo.
echo 2. 解压到: C:\Apache\maven
echo.
echo 3. 设置环境变量 (系统属性 ^> 高级 ^> 环境变量):
echo    新建: MAVEN_HOME = C:\Apache\maven
echo    编辑 PATH: 添加 %%MAVEN_HOME%%\bin
echo.
echo 4. 重启命令提示符，验证:
echo    mvn -version
echo.
echo 5. 重新运行 quick-start.bat
echo.
pause
goto :menu

:show_source
echo.
echo [核心源代码文件]
echo.

echo Protocol 模块:
if exist "aeromq-protocol\src\main\java\com\aeromq\protocol\Commands.java" (
    echo ✅ Commands.java (协议定义)
) else (
    echo ❌ Commands.java 缺失
)

echo.
echo Core 模块:
if exist "aeromq-core\src\main\java\com\aeromq\core\util\SPSCRingBuffer.java" (
    echo ✅ SPSCRingBuffer.java (SPSC 环形缓冲区)
) else (
    echo ❌ SPSCRingBuffer.java 缺失
)

if exist "aeromq-core\src\main\java\com\aeromq\core\util\OffHeapMemoryManager.java" (
    echo ✅ OffHeapMemoryManager.java (Off-heap 内存管理)
) else (
    echo ❌ OffHeapMemoryManager.java 缺失
)

echo.
echo Client 模块:
if exist "aeromq-client\src\main\java\com\aeromq\client\RequestManager.java" (
    echo ✅ RequestManager.java (请求管理)
) else (
    echo ❌ RequestManager.java 缺失
)

if exist "aeromq-client\src\main\java\com\aeromq\examples\SimpleExample.java" (
    echo ✅ SimpleExample.java (示例程序)
) else (
    echo ❌ SimpleExample.java 缺失
)

echo.
echo Benchmark 模块:
if exist "aeromq-benchmark\src\main\java\com\aeromq\benchmark\BenchmarkRunner.java" (
    echo ✅ BenchmarkRunner.java (基准测试)
) else (
    echo ❌ BenchmarkRunner.java 缺失
)

echo.
pause
goto :menu

:exit
echo.
echo 感谢使用 AeroMQ 简化启动脚本！
echo.
echo 💡 提示: 安装 Maven 后使用 quick-start.bat 获得完整功能
echo.
pause
exit /b 0
