# AeroMQ Setup Guide

## 快速开始指南

### 环境要求
- Java 17+ (必需)
- Maven 3.6+ (构建工具)

### 安装步骤

#### 1. 检查Java环境
```bash
java -version
```
如果没有安装Java，请访问 https://adoptium.net/ 下载安装Java 17+

#### 2. 检查Maven环境  
```bash
mvn -version
```
如果没有安装Maven，请访问 https://maven.apache.org/download.cgi 下载安装

#### Windows安装Maven:
1. 下载Maven二进制zip文件
2. 解压到 `C:\Program Files\Apache\maven`
3. 添加 `C:\Program Files\Apache\maven\bin` 到系统PATH环境变量
4. 重启命令提示符

#### 3. 构建项目
```bash
# Windows
build.bat

# Linux/macOS  
./build.sh
```

#### 4. 启动Broker
```bash
# Windows
start-broker.bat

# Linux/macOS
./start-broker.sh  
```

#### 5. 运行测试
```bash
java -jar aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar
```

### 环境检查
运行环境检查脚本：
```bash
# Windows
check-env.bat
```

### 故障排除

**问题**: mvn命令找不到
**解决**: 确保Maven已正确安装并添加到PATH环境变量

**问题**: Java版本过低  
**解决**: 安装Java 17或更高版本

**问题**: 构建失败
**解决**: 检查网络连接，确保可以下载Maven依赖

### 项目结构
- `aeromq-protocol/` - 协议定义
- `aeromq-core/` - 核心Broker实现  
- `aeromq-client/` - 客户端SDK
- `aeromq-benchmark/` - 性能测试工具

### 配置文件
主要配置文件位于: `aeromq-core/src/main/resources/aeromq.properties`

### 默认端口
- Broker服务端口: 8888
- 可在配置文件中修改

## 更多信息
详细文档请参考 README.md
