#!/bin/bash

# AeroMQ Quick Start Script (Linux/macOS)
# 支持构建、启动、测试和性能基准测试

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BROKER_LOG="$PROJECT_ROOT/logs/broker.log"
BROKER_PID_FILE="$PROJECT_ROOT/logs/broker.pid"

# 创建日志目录
mkdir -p "$PROJECT_ROOT/logs"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

print_header() {
    echo -e "${BLUE}============================================${NC}"
    echo -e "${BLUE}           AeroMQ Quick Start${NC}"
    echo -e "${BLUE}============================================${NC}"
}

print_menu() {
    echo ""
    echo -e "${YELLOW}选择操作:${NC}"
    echo "1. 构建项目 (Maven compile)"
    echo "2. 启动 Broker 服务器"
    echo "3. 运行客户端测试"
    echo "4. 运行性能基准测试"
    echo "5. 运行完整演示 (启动 Broker + 所有测试)"
    echo "6. 停止 Broker 服务器"
    echo "7. 清理项目"
    echo "0. 退出"
    echo ""
}

check_java() {
    if ! command -v java &> /dev/null; then
        echo -e "${RED}❌ Java 未安装或不在 PATH 中${NC}"
        echo "请安装 Java 17 或更高版本"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -lt 17 ]; then
        echo -e "${RED}❌ Java 版本过低，需要 Java 17+${NC}"
        exit 1
    fi
    
    echo -e "${GREEN}✅ Java 环境检查通过 (版本: $JAVA_VERSION)${NC}"
}

check_maven() {
    if ! command -v mvn &> /dev/null; then
        echo -e "${RED}❌ Maven 未安装或不在 PATH 中${NC}"
        echo "请安装 Apache Maven"
        exit 1
    fi
    echo -e "${GREEN}✅ Maven 环境检查通过${NC}"
}

build_project() {
    echo -e "${YELLOW}🔨 构建项目...${NC}"
    cd "$PROJECT_ROOT"
    
    if mvn clean compile -q; then
        echo -e "${GREEN}✅ 项目构建成功${NC}"
    else
        echo -e "${RED}❌ 项目构建失败${NC}"
        exit 1
    fi
}

start_broker() {
    echo -e "${YELLOW}🚀 启动 AeroMQ Broker...${NC}"
    
    # 检查是否已经运行
    if [ -f "$BROKER_PID_FILE" ]; then
        BROKER_PID=$(cat "$BROKER_PID_FILE")
        if kill -0 "$BROKER_PID" 2>/dev/null; then
            echo -e "${YELLOW}⚠️ Broker 已经在运行 (PID: $BROKER_PID)${NC}"
            return 0
        else
            rm -f "$BROKER_PID_FILE"
        fi
    fi
    
    cd "$PROJECT_ROOT"
    
    # 构建 classpath
    CLASSPATH="aeromq-core/target/classes"
    CLASSPATH="$CLASSPATH:aeromq-protocol/target/classes"
    
    # 添加依赖 JAR
    for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(netty|jackson|slf4j)" | head -20); do
        CLASSPATH="$CLASSPATH:$jar"
    done
    
    # 启动 Broker (后台运行)
    nohup java -cp "$CLASSPATH" com.aeromq.broker.AeroBroker > "$BROKER_LOG" 2>&1 &
    BROKER_PID=$!
    echo $BROKER_PID > "$BROKER_PID_FILE"
    
    echo -e "${GREEN}✅ Broker 启动成功 (PID: $BROKER_PID)${NC}"
    echo -e "${BLUE}📋 日志文件: $BROKER_LOG${NC}"
    
    # 等待 Broker 启动
    echo "等待 Broker 初始化..."
    sleep 3
    
    # 检查 Broker 是否正常运行
    if kill -0 "$BROKER_PID" 2>/dev/null; then
        echo -e "${GREEN}✅ Broker 运行正常，监听端口 8888${NC}"
    else
        echo -e "${RED}❌ Broker 启动失败，请查看日志: $BROKER_LOG${NC}"
        exit 1
    fi
}

stop_broker() {
    echo -e "${YELLOW}🛑 停止 AeroMQ Broker...${NC}"
    
    if [ -f "$BROKER_PID_FILE" ]; then
        BROKER_PID=$(cat "$BROKER_PID_FILE")
        if kill -0 "$BROKER_PID" 2>/dev/null; then
            kill "$BROKER_PID"
            rm -f "$BROKER_PID_FILE"
            echo -e "${GREEN}✅ Broker 已停止${NC}"
        else
            echo -e "${YELLOW}⚠️ Broker 未运行${NC}"
            rm -f "$BROKER_PID_FILE"
        fi
    else
        echo -e "${YELLOW}⚠️ 未找到 Broker PID 文件${NC}"
    fi
}

run_client_test() {
    echo -e "${YELLOW}🧪 运行客户端测试...${NC}"
    cd "$PROJECT_ROOT"
    
    # 构建 classpath
    CLASSPATH="aeromq-client/target/classes"
    CLASSPATH="$CLASSPATH:aeromq-core/target/classes"
    CLASSPATH="$CLASSPATH:aeromq-protocol/target/classes"
    
    # 添加依赖 JAR
    for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(netty|jackson|slf4j)" | head -20); do
        CLASSPATH="$CLASSPATH:$jar"
    done
    
    echo -e "${BLUE}📡 连接到 Broker (localhost:8888)...${NC}"
    
    if java -cp "$CLASSPATH" com.aeromq.examples.SimpleExample; then
        echo -e "${GREEN}✅ 客户端测试完成${NC}"
    else
        echo -e "${RED}❌ 客户端测试失败${NC}"
        echo "请确保 Broker 正在运行"
    fi
}

run_benchmark() {
    echo -e "${YELLOW}📊 运行性能基准测试...${NC}"
    cd "$PROJECT_ROOT"
    
    # 构建 classpath
    CLASSPATH="aeromq-benchmark/target/classes"
    CLASSPATH="$CLASSPATH:aeromq-client/target/classes"
    CLASSPATH="$CLASSPATH:aeromq-core/target/classes"
    CLASSPATH="$CLASSPATH:aeromq-protocol/target/classes"
    
    # 添加依赖 JAR
    for jar in $(find ~/.m2/repository -name "*.jar" | grep -E "(netty|jackson|slf4j)" | head -20); do
        CLASSPATH="$CLASSPATH:$jar"
    done
    
    echo -e "${BLUE}🚀 开始性能测试...${NC}"
    
    if java -cp "$CLASSPATH" com.aeromq.benchmark.BenchmarkRunner; then
        echo -e "${GREEN}✅ 基准测试完成${NC}"
        
        # 检查是否有 Python 来生成图表
        if command -v python3 &> /dev/null; then
            echo -e "${YELLOW}📈 生成性能图表...${NC}"
            if [ -f "scripts/visualize_benchmark.py" ]; then
                cd scripts
                python3 visualize_benchmark.py
                echo -e "${GREEN}✅ 性能图表生成完成，请查看 performance_report.html${NC}"
            fi
        else
            echo -e "${YELLOW}⚠️ 未安装 Python3，跳过图表生成${NC}"
        fi
    else
        echo -e "${RED}❌ 基准测试失败${NC}"
    fi
}

full_demo() {
    echo -e "${YELLOW}🎯 运行完整演示...${NC}"
    
    # 1. 构建项目
    build_project
    
    # 2. 启动 Broker
    start_broker
    
    # 3. 等待一下确保 Broker 完全启动
    sleep 2
    
    # 4. 运行客户端测试
    run_client_test
    
    # 5. 运行基准测试
    run_benchmark
    
    echo -e "${GREEN}🎉 完整演示完成！${NC}"
    echo -e "${BLUE}💡 Broker 仍在后台运行，使用选项 6 来停止${NC}"
}

clean_project() {
    echo -e "${YELLOW}🧹 清理项目...${NC}"
    cd "$PROJECT_ROOT"
    
    # 停止 Broker
    stop_broker
    
    # Maven clean
    if mvn clean -q; then
        echo -e "${GREEN}✅ Maven 清理完成${NC}"
    fi
    
    # 清理日志
    rm -rf logs/
    
    echo -e "${GREEN}✅ 项目清理完成${NC}"
}

# 主程序
main() {
    print_header
    
    # 检查环境
    check_java
    check_maven
    
    while true; do
        print_menu
        read -p "请选择 (0-7): " choice
        
        case $choice in
            1)
                build_project
                ;;
            2)
                start_broker
                ;;
            3)
                run_client_test
                ;;
            4)
                run_benchmark
                ;;
            5)
                full_demo
                ;;
            6)
                stop_broker
                ;;
            7)
                clean_project
                ;;
            0)
                echo -e "${GREEN}👋 再见！${NC}"
                exit 0
                ;;
            *)
                echo -e "${RED}❌ 无效选择，请重试${NC}"
                ;;
        esac
        
        echo ""
        read -p "按 Enter 继续..."
    done
}

# 支持命令行参数
if [ $# -gt 0 ]; then
    case $1 in
        "build")
            check_java && check_maven && build_project
            ;;
        "start")
            check_java && start_broker
            ;;
        "test")
            check_java && run_client_test
            ;;
        "benchmark")
            check_java && run_benchmark
            ;;
        "demo")
            check_java && check_maven && full_demo
            ;;
        "stop")
            stop_broker
            ;;
        "clean")
            clean_project
            ;;
        *)
            echo "用法: $0 [build|start|test|benchmark|demo|stop|clean]"
            exit 1
            ;;
    esac
else
    main
fi
