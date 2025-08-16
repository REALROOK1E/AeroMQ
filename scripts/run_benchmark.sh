#!/bin/bash
# AeroMQ Benchmark Suite Runner
# Runs complete performance benchmarks and generates visualizations

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
RESULTS_DIR="$PROJECT_ROOT/benchmark-results"
CHARTS_DIR="$PROJECT_ROOT/benchmark-charts"

echo "AeroMQ Performance Benchmark Suite"
echo "=================================="

# Create results directories
mkdir -p "$RESULTS_DIR"
mkdir -p "$CHARTS_DIR"

# Function to run Java benchmark
run_java_benchmark() {
    echo "Running Java benchmark..."
    cd "$PROJECT_ROOT"
    
    # Check if maven is available
    if command -v mvn &> /dev/null; then
        echo "Building project..."
        mvn clean package -DskipTests -q
        
        echo "Starting benchmark..."
        java -cp "aeromq-benchmark/target/classes:aeromq-client/target/classes:aeromq-core/target/classes:aeromq-protocol/target/classes" \
             -Xmx2g -XX:+UseG1GC \
             -Dresults.dir="$RESULTS_DIR" \
             com.aeromq.benchmark.BenchmarkRunner
    else
        echo "Maven not found. Please compile manually or install Maven."
        exit 1
    fi
}

# Function to install Python dependencies
install_python_deps() {
    echo "Installing Python dependencies..."
    pip install -r "$SCRIPT_DIR/requirements.txt"
}

# Function to generate visualizations
generate_charts() {
    echo "Generating performance charts..."
    python3 "$SCRIPT_DIR/visualize_benchmark.py" "$RESULTS_DIR" --output "$CHARTS_DIR"
}

# Main execution
main() {
    case "${1:-all}" in
        "benchmark")
            run_java_benchmark
            ;;
        "charts")
            generate_charts
            ;;
        "install-deps")
            install_python_deps
            ;;
        "all")
            run_java_benchmark
            generate_charts
            ;;
        *)
            echo "Usage: $0 [benchmark|charts|install-deps|all]"
            echo "  benchmark    - Run Java benchmark only"
            echo "  charts       - Generate charts from existing results"
            echo "  install-deps - Install Python dependencies"
            echo "  all          - Run benchmark and generate charts (default)"
            exit 1
            ;;
    esac
}

# Check Python availability
if ! command -v python3 &> /dev/null; then
    echo "Python 3 is required for chart generation"
    echo "Please install Python 3 and try again"
    exit 1
fi

main "$@"

echo ""
echo "Benchmark complete!"
echo "Results saved to: $RESULTS_DIR"
echo "Charts saved to: $CHARTS_DIR"
echo "Open $CHARTS_DIR/performance_report.html to view the full report"
