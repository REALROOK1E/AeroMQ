# AeroMQ

![AeroMQ Logo](https://img.shields.io/badge/AeroMQ-v1.0.0-blue) ![License](https://img.shields.io/badge/license-MIT-green) ![Java](https://img.shields.io/badge/Java-17+-orange)

AeroMQ is a high-performance message queue system designed for modern distributed applications. Built with Java and Netty, it provides reliable message delivery, Raft consensus algorithm for distributed coordination, and multi-dimensional indexing for fast message lookups.

## 🚀 Features

- **High Performance**: Built on Netty for asynchronous I/O and optimal throughput
- **Distributed Consensus**: Raft algorithm implementation for cluster coordination
- **Multi-dimensional Indexing**: Fast message lookup by queue, timestamp, type, priority, and custom attributes
- **Multiple Storage Backends**: In-memory storage with planned support for persistent storage
- **Client SDK**: Easy-to-use Java client library for producers and consumers
- **Benchmarking Tools**: Built-in performance testing and monitoring capabilities
- **Scalable Architecture**: Modular design supporting horizontal scaling

## 📋 Prerequisites

- Java 17 or higher
- Maven 3.6+
- At least 2GB RAM (recommended for production)

## 🏗️ Project Structure

```
AeroMQ/
├── pom.xml                           # Root Maven configuration
├── README.md                         # Project documentation
├── LICENSE                           # MIT License
├── .gitignore                        # Git ignore rules
├── build.bat / build.sh              # Build scripts
├── start-broker.bat / start-broker.sh # Broker startup scripts
│
├── aeromq-protocol/                  # Protocol definitions
│   ├── pom.xml
│   └── src/main/java/com/aeromq/protocol/
│       ├── AeroProtocol.java         # Protocol message structures
│       └── Commands.java             # Command definitions
│
├── aeromq-core/                      # Core broker implementation
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/aeromq/
│       │   ├── broker/
│       │   │   ├── AeroBroker.java           # Main broker class
│       │   │   ├── MessageStore.java        # Message storage interface
│       │   │   ├── InMemoryMessageStore.java # In-memory storage impl
│       │   │   ├── StateMachine.java        # Raft state machine
│       │   │   └── IndexManager.java        # Multi-dimensional indexing
│       │   ├── model/
│       │   │   ├── Message.java              # Message model
│       │   │   └── MessageStatus.java       # Message status enum
│       │   └── transport/
│       │       ├── NettyServer.java          # Network server
│       │       ├── ProtocolHandler.java      # Protocol handler
│       │       └── ClientSession.java       # Client session management
│       ├── main/resources/
│       │   └── aeromq.properties            # Configuration file
│       └── test/java/                       # Unit tests
│
├── aeromq-client/                    # Client SDK
│   ├── pom.xml
│   └── src/main/java/com/aeromq/client/
│       ├── AeroClient.java           # Main client class
│       ├── Producer.java             # Message producer
│       └── Consumer.java             # Message consumer
│
└── aeromq-benchmark/                 # Performance testing
    ├── pom.xml
    └── src/main/java/com/aeromq/benchmark/
        ├── BenchmarkRunner.java      # Benchmark execution
        └── ScenarioGenerator.java    # Test scenario generation
```

## 🚀 Quick Start

### 1. Clone and Build

```bash
git clone https://github.com/REALROOK1E/AeroMQ.git
cd AeroMQ

# Windows
build.bat

# Linux/macOS
chmod +x build.sh
./build.sh
```

### 2. Start the Broker

```bash
# Windows
start-broker.bat

# Linux/macOS
chmod +x start-broker.sh
./start-broker.sh
```

The broker will start on port 8888 by default.

### 3. Use the Client SDK

#### Producer example

```java
import com.aeromq.client.AeroClient;
import com.aeromq.client.Producer;

// Connect to broker
AeroClient client = new AeroClient("localhost", 8888);
client.connect().get();

// Create producer
Producer producer = client.createProducer();

// Send message
String messageId = producer.sendText("my-queue", "Hello, AeroMQ!").get();
System.out.println("Message sent: " + messageId);

// Cleanup
client.disconnect().get();
```

#### Consumer example

```java
import com.aeromq.client.AeroClient;
import com.aeromq.client.Consumer;

// Connect to broker
AeroClient client = new AeroClient("localhost", 8888);
client.connect().get();

// Create consumer
Consumer consumer = client.createConsumer();

// Subscribe to queue
consumer.subscribe("my-queue", message -> {
    System.out.println("Received: " + message.getPayloadAsString());
    // Message is auto-acknowledged by default
}).get();

// Keep running to receive messages
Thread.sleep(60000);

// Cleanup
consumer.unsubscribe("my-queue").get();
client.disconnect().get();
```

## 📊 Running Benchmarks

AeroMQ includes comprehensive benchmarking tools for performance testing:

### Built-in Benchmark Scenarios

```bash
# Run default benchmark suite
java -jar aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar

# Producer throughput test
java -jar aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar producer localhost 8888 test-queue 10000 1024 10

# Consumer throughput test  
java -jar aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar consumer localhost 8888 test-queue 10000 5

# Latency test
java -jar aeromq-benchmark/target/aeromq-benchmark-1.0.0-SNAPSHOT.jar latency localhost 8888 latency-queue 1000 512
```

### Benchmark Parameters

- **messageCount**: Number of messages to send/receive
- **messageSize**: Size of each message in bytes  
- **producerCount**: Number of concurrent producers
- **consumerCount**: Number of concurrent consumers

## ⚙️ Configuration

Edit `aeromq-core/src/main/resources/aeromq.properties`:

```properties
# Server settings
aeromq.broker.port=8888
aeromq.broker.host=0.0.0.0
aeromq.broker.maxConnections=1000

# Performance settings
aeromq.broker.workerThreads=8
aeromq.broker.bossThreads=1

# Message settings
aeromq.message.maxSize=1048576
aeromq.message.defaultTtl=3600000
aeromq.message.maxRetries=3

# Storage settings
aeromq.storage.type=inmemory
aeromq.storage.dataDir=./data
aeromq.storage.syncInterval=1000

# Cluster settings (future feature)
aeromq.cluster.enabled=false
aeromq.cluster.nodeId=node1
```

## 🏛️ Architecture

### Core Components

1. **AeroBroker**: Main broker orchestrator
2. **NettyServer**: High-performance network layer
3. **MessageStore**: Pluggable storage interface
4. **StateMachine**: Raft consensus implementation
5. **IndexManager**: Multi-dimensional message indexing
6. **ProtocolHandler**: Message protocol processing

### Key Features

- **Asynchronous I/O**: Netty-based non-blocking network operations
- **Multi-dimensional Indexing**: Fast lookups by queue, timestamp, type, priority, custom attributes
- **State Machine**: Raft algorithm for distributed consensus
- **Modular Design**: Pluggable storage backends and transport layers
- **Type Safety**: Strong typing with comprehensive error handling

## 🧪 Development

### Running Tests

```bash
mvn test
```

### Code Coverage

```bash
mvn jacoco:report
```

### Debugging

Enable debug logging by setting log level to DEBUG in your logging configuration.

## 📈 Performance

Expected performance characteristics (single node, in-memory storage):

- **Throughput**: 100K+ messages/second
- **Latency**: Sub-millisecond for in-memory operations
- **Connections**: 10K+ concurrent connections
- **Memory**: Efficient memory usage with object pooling

## 🛣️ Roadmap

- [ ] Persistent storage backends (RocksDB, PostgreSQL)
- [ ] Multi-node cluster support
- [ ] Message replay and time-travel queries
- [ ] REST API and web management console
- [ ] Metrics and monitoring integration
- [ ] Message routing and filtering
- [ ] Dead letter queues
- [ ] Message transformation pipelines

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Support

- GitHub Issues: [Report bugs or request features](https://github.com/REALROOK1E/AeroMQ/issues)
- Documentation: [Wiki](https://github.com/REALROOK1E/AeroMQ/wiki)

## 🙏 Acknowledgments

- [Netty](https://netty.io/) - High-performance network framework
- [Apache Maven](https://maven.apache.org/) - Build automation
- [SLF4J](http://www.slf4j.org/) - Logging facade
- [Jackson](https://github.com/FasterXML/jackson) - JSON processing

---

Built with ❤️ for high-performance messaging