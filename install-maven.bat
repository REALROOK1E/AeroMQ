@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion

:: Maven 快速安装脚本

echo ============================================
echo           Maven 快速安装工具
echo ============================================
echo.

echo [检查] 正在检查当前 Maven 状态...

:: 检查是否已安装 Maven
where mvn >nul 2>&1
if !errorlevel! equ 0 (
    echo ✅ Maven 已经安装！
    mvn -version
    echo.
    echo 可以直接运行 quick-start.bat 开始使用 AeroMQ
    pause
    exit /b 0
)

echo ❌ Maven 未安装
echo.

echo 请选择安装方式:
echo 1. 自动下载并安装 Maven (推荐)
echo 2. 手动安装指导
echo 3. 使用 Chocolatey 安装 (如果已安装 Chocolatey)
echo 0. 退出
echo.

set /p choice="请选择 (0-3): "

if "%choice%"=="1" goto :auto_install
if "%choice%"=="2" goto :manual_guide
if "%choice%"=="3" goto :chocolatey_install
if "%choice%"=="0" goto :exit

echo 无效选择，请重试
goto :menu

:auto_install
echo.
echo [自动安装] 正在下载并安装 Maven...
echo.

:: 创建临时目录
set TEMP_DIR=%TEMP%\maven-install
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"

:: 设置安装路径
set MAVEN_INSTALL_DIR=C:\Apache\maven

echo [下载] 正在下载 Maven 3.9.9...
echo 下载链接: https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip

:: 使用 PowerShell 下载
powershell -Command "& {
    $ProgressPreference = 'SilentlyContinue'
    try {
        Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip' -OutFile '%TEMP_DIR%\maven.zip'
        Write-Host '[成功] Maven 下载完成'
    } catch {
        Write-Host '[错误] 下载失败: ' $_.Exception.Message
        exit 1
    }
}"

if !errorlevel! neq 0 (
    echo [错误] Maven 下载失败，请检查网络连接
    pause
    goto :manual_guide
)

echo [解压] 正在解压 Maven...

:: 创建安装目录
if not exist "C:\Apache" mkdir "C:\Apache"

:: 使用 PowerShell 解压
powershell -Command "& {
    try {
        Expand-Archive -Path '%TEMP_DIR%\maven.zip' -DestinationPath '%TEMP_DIR%' -Force
        Move-Item '%TEMP_DIR%\apache-maven-3.9.9' '%MAVEN_INSTALL_DIR%' -Force
        Write-Host '[成功] Maven 解压完成'
    } catch {
        Write-Host '[错误] 解压失败: ' $_.Exception.Message
        exit 1
    }
}"

if !errorlevel! neq 0 (
    echo [错误] Maven 解压失败
    pause
    goto :manual_guide
)

echo [配置] 正在设置环境变量...

:: 设置环境变量
setx MAVEN_HOME "%MAVEN_INSTALL_DIR%" /M >nul 2>&1
setx PATH "%PATH%;%MAVEN_INSTALL_DIR%\bin" /M >nul 2>&1

echo [清理] 正在清理临时文件...
rmdir /s /q "%TEMP_DIR%" 2>nul

echo.
echo ✅ Maven 安装完成！
echo.
echo 安装位置: %MAVEN_INSTALL_DIR%
echo.
echo ⚠️  重要提示:
echo 1. 请重启命令提示符使环境变量生效
echo 2. 验证安装: mvn -version
echo 3. 然后运行 quick-start.bat 开始使用 AeroMQ
echo.
pause
exit /b 0

:chocolatey_install
echo.
echo [Chocolatey 安装] 使用 Chocolatey 安装 Maven...

where choco >nul 2>&1
if !errorlevel! neq 0 (
    echo [错误] Chocolatey 未安装
    echo 请先安装 Chocolatey: https://chocolatey.org/install
    pause
    goto :manual_guide
)

echo [安装] 正在通过 Chocolatey 安装 Maven...
choco install maven -y

if !errorlevel! equ 0 (
    echo ✅ Maven 通过 Chocolatey 安装成功！
    echo 请重启命令提示符后验证: mvn -version
) else (
    echo [错误] Chocolatey 安装失败
)

pause
exit /b 0

:manual_guide
echo.
echo ======= 手动安装指导 =======
echo.
echo 1. 下载 Maven:
echo    访问: https://maven.apache.org/download.cgi
echo    下载: Binary zip archive (apache-maven-3.9.x-bin.zip)
echo.
echo 2. 解压文件:
echo    解压到: C:\Apache\maven
echo    (或您喜欢的其他位置)
echo.
echo 3. 设置环境变量:
echo    - 打开"系统属性" ^> "高级" ^> "环境变量"
echo    - 新建系统变量:
echo      变量名: MAVEN_HOME
echo      变量值: C:\Apache\maven
echo    - 编辑系统变量 PATH:
echo      添加: %%MAVEN_HOME%%\bin
echo.
echo 4. 验证安装:
echo    - 重启命令提示符
echo    - 运行: mvn -version
echo    - 应该显示 Maven 版本信息
echo.
echo 5. 开始使用:
echo    - 运行: quick-start.bat
echo.
echo 💡 详细安装教程: https://maven.apache.org/install.html
echo.
pause
exit /b 0

:exit
echo.
echo 感谢使用 Maven 安装工具！
echo.
pause
exit /b 0
