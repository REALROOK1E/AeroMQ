# AeroMQ 启动和测试指南

本文档详细说明如何启动和测试 AeroMQ 消息队列系统。

## 🚀 快速开始

### Windows 用户

```powershell
# 运行快速启动脚本
.\quick-start.bat
```

### Linux/macOS 用户

```bash
# 给脚本执行权限
chmod +x quick-start.sh

# 运行快速启动脚本
./quick-start.sh
```

## 📋 环境要求

- **Java**: 17 或更高版本
- **Maven**: 3.6.0 或更高版本
- **Python** (可选): 用于生成性能图表
- **内存**: 建议 2GB 以上可用内存
- **端口**: 确保 8888 端口未被占用

## 🔧 手动启动步骤

### 1. 构建项目

```bash
cd AeroMQ
mvn clean compile
```

### 2. 启动 Broker

**Windows:**
```powershell
# 在项目根目录执行
java -cp "aeromq-core\target\classes;aeromq-protocol\target\classes;%USERPROFILE%\.m2\repository\io\netty\*\*.jar" com.aeromq.broker.AeroBroker
```

**Linux/macOS:**
```bash
# 构建 classpath
CLASSPATH="aeromq-core/target/classes:aeromq-protocol/target/classes"

# 添加 Netty 依赖
for jar in ~/.m2/repository/io/netty/*/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

# 启动 Broker
java -cp "$CLASSPATH" com.aeromq.broker.AeroBroker
```

### 3. 运行测试

在新的终端窗口中：

```bash
# 构建客户端 classpath
CLASSPATH="aeromq-client/target/classes:aeromq-core/target/classes:aeromq-protocol/target/classes"

# 添加依赖
for jar in ~/.m2/repository/io/netty/*/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

# 运行示例
java -cp "$CLASSPATH" com.aeromq.examples.simpleExample
```

## 🧪 测试场景详解

### 基础功能测试

**测试内容:**
- 客户端连接
- 生产者发送消息
- 消费者接收消息
- 生产者-消费者模式

**预期输出:**
```
=== AeroMQ Simple example ===
Connected to broker at localhost:8888
Sent message: Hello, AeroMQ! with ID: msg-1234567890
Consumed 1 messages
```

### 性能优化演示

#### 演示 1: 高并发请求映射
- **测试目标**: RequestId -> CompletableFuture 映射
- **测试场景**: 50个并发请求
- **预期结果**: 所有请求成功完成，显示总耗时

#### 演示 2: SPSC Ring Buffer
- **测试目标**: 无锁队列性能
- **测试场景**: 批量发送100条消息
- **预期结果**: 显示吞吐量 (msg/s)

#### 演示 3: Off-heap 存储
- **测试目标**: 大消息处理能力
- **测试场景**: 10条64KB大消息
- **预期结果**: 显示吞吐量 (MB/s)

#### 演示 4: 综合基准测试
- **测试目标**: 系统整体性能
- **测试场景**: 多场景组合测试
- **预期结果**: CSV报告 + HTML可视化图表

## 📊 性能基准测试

### 运行基准测试

```bash
java -cp "$CLASSPATH" com.aeromq.benchmark.BenchmarkRunner
```

### 生成可视化报告

```bash
cd scripts
python3 visualize_benchmark.py
```

### 基准测试指标

1. **吞吐量测试**
   - 小消息 (100 bytes): 目标 > 100,000 msg/s
   - 中等消息 (1KB): 目标 > 50,000 msg/s
   - 大消息 (10KB): 目标 > 10,000 msg/s

2. **延迟测试**
   - P50 延迟: < 1ms
   - P95 延迟: < 5ms
   - P99 延迟: < 10ms

3. **并发测试**
   - 并发连接: 1000+
   - 并发请求: 10,000+

## 🔍 故障排除

### 常见问题

#### 1. 端口占用
```bash
# 检查端口占用
netstat -an | grep 8888

# Windows 杀死进程
taskkill /PID <PID> /F

# Linux 杀死进程
kill -9 <PID>
```

#### 2. Java 版本问题
```bash
# 检查 Java 版本
java -version

# 应该显示 17 或更高版本
```

#### 3. Maven 依赖问题
```bash
# 重新下载依赖
mvn clean install -U
```

#### 4. 内存不足
```bash
# 增加 JVM 内存
java -Xmx2g -Xms1g -cp ... com.aeromq.broker.AeroBroker
```

### 日志查看

**Broker 日志位置:**
- Windows: `logs\broker.log`
- Linux/macOS: `logs/broker.log`

**关键日志信息:**
```
[INFO] AeroBroker started on port 8888  # Broker 启动成功
[INFO] Client connected: client-id      # 客户端连接
[ERROR] Failed to process message       # 消息处理错误
```

## 📈 性能优化验证

### 1. RequestManager 优化验证
```java
// 代码位置: aeromq-client/src/main/java/com/aeromq/client/RequestManager.java
// 验证指标: 支持高并发请求映射，非 ThreadLocal 限制
```

### 2. SPSC Ring Buffer 优化验证
```java
// 代码位置: aeromq-core/src/main/java/com/aeromq/core/util/SPSCRingBuffer.java
// 验证指标: 无锁队列，比 ConcurrentLinkedQueue 性能更好
```

### 3. Off-heap 存储优化验证
```java
// 代码位置: aeromq-core/src/main/java/com/aeromq/core/util/OffHeapMemoryManager.java
// 验证指标: DirectByteBuffer 池化，减少 GC 压力
```

### 4. 基准测试框架验证
```java
// 代码位置: aeromq-benchmark/src/main/java/com/aeromq/benchmark/BenchmarkRunner.java
// 验证指标: 完整的性能测试和可视化报告
```

## 🎯 测试检查清单

- [ ] ✅ Broker 成功启动并监听 8888 端口
- [ ] ✅ 客户端成功连接到 Broker
- [ ] ✅ 基础消息发送和接收功能正常
- [ ] ✅ 高并发请求映射演示成功
- [ ] ✅ SPSC Ring Buffer 批量处理演示成功
- [ ] ✅ Off-heap 大消息处理演示成功
- [ ] ✅ 综合基准测试完成并生成报告
- [ ] ✅ Python 可视化图表生成成功
- [ ] ✅ 所有性能指标符合预期

## 📞 技术支持

如遇到问题，请检查：

1. **环境配置**: Java 17+, Maven 3.6+
2. **网络连接**: 确保 localhost:8888 可访问
3. **资源限制**: 足够的内存和 CPU 资源
4. **日志信息**: 查看详细错误日志
5. **版本兼容**: 确保所有组件版本匹配

## 🚀 下一步

测试完成后，您可以：

1. **集成到现有系统**: 参考 `aeromq-client` 模块的 API
2. **自定义配置**: 修改 Broker 端口、缓冲区大小等
3. **扩展功能**: 添加持久化、集群支持等特性
4. **生产部署**: 配置监控、日志、安全等生产环境特性

---

🎉 **恭喜！您已成功完成 AeroMQ 的启动和测试！**
