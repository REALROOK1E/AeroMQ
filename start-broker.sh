#!/bin/bash

# AeroMQ Broker Startup Script
echo "Starting AeroMQ Broker..."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    exit 1
fi

# Check if jar file exists
if [ ! -f "aeromq-core/target/aeromq-core-1.0.0-SNAPSHOT.jar" ]; then
    echo "Error: aeromq-core jar file not found. Please run build.sh first."
    exit 1
fi

# Start the broker
echo "Starting AeroMQ Broker on port 8888..."
java -cp "aeromq-core/target/aeromq-core-1.0.0-SNAPSHOT.jar:aeromq-core/target/lib/*" com.aeromq.broker.AeroBroker
