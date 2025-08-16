#!/bin/bash

# AeroMQ Build Script
echo "Building AeroMQ Project..."

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

# Clean and compile the project
echo "Cleaning previous builds..."
mvn clean

echo "Compiling and packaging..."
mvn compile package -DskipTests

# Check if build was successful
if [ $? -eq 0 ]; then
    echo "Build completed successfully!"
    echo ""
    echo "Generated artifacts:"
    echo "- aeromq-core/target/aeromq-core-1.0.0-SNAPSHOT.jar"
    echo "- aeromq-client/target/aeromq-client-1.0.0-SNAPSHOT.jar"
    echo "- aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar"
    echo ""
    echo "To run the broker: ./start-broker.sh"
    echo "To run benchmarks: java -jar aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar"
else
    echo "Build failed!"
    exit 1
fi
