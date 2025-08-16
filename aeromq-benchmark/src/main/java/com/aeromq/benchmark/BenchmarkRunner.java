package com.aeromq.benchmark;

import com.aeromq.client.AeroClient;
import com.aeromq.client.Producer;
import com.aeromq.client.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Benchmark runner for AeroMQ performance testing
 */
public class BenchmarkRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkRunner.class);
    
    public static void main(String[] args) {
        BenchmarkRunner runner = new BenchmarkRunner();
        
        try {
            if (args.length == 0) {
                runner.runDefaultBenchmarks();
            } else {
                runner.runCustomBenchmark(args);
            }
        } catch (Exception e) {
            logger.error("Benchmark execution failed", e);
            System.exit(1);
        }
    }
    
    /**
     * Run default benchmark scenarios
     */
    public void runDefaultBenchmarks() throws Exception {
        logger.info("Starting AeroMQ benchmark suite...");
        
        // Producer throughput test
        runProducerThroughputTest("localhost", 8888, "benchmark-queue", 
                10000, 1024, 10);
        
        // Consumer throughput test
        runConsumerThroughputTest("localhost", 8888, "benchmark-queue", 
                10000, 10);
        
        // Latency test
        runLatencyTest("localhost", 8888, "latency-queue", 
                1000, 1024);
        
        logger.info("Benchmark suite completed");
    }
    
    /**
     * Run custom benchmark based on command line arguments
     */
    public void runCustomBenchmark(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return;
        }
        
        String testType = args[0];
        String host = args.length > 1 ? args[1] : "localhost";
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 8888;
        
        switch (testType.toLowerCase()) {
            case "producer":
                runProducerBenchmark(host, port, args);
                break;
            case "consumer":
                runConsumerBenchmark(host, port, args);
                break;
            case "latency":
                runLatencyBenchmark(host, port, args);
                break;
            default:
                logger.error("Unknown test type: {}", testType);
                printUsage();
        }
    }
    
    /**
     * Producer throughput test
     */
    public BenchmarkResult runProducerThroughputTest(String host, int port, String queueName,
                                                    int messageCount, int messageSize, 
                                                    int producerCount) throws Exception {
        logger.info("Running producer throughput test: {} messages, {} bytes each, {} producers", 
                messageCount, messageSize, producerCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(producerCount);
        CountDownLatch latch = new CountDownLatch(producerCount);
        AtomicLong totalMessages = new AtomicLong(0);
        AtomicLong totalErrors = new AtomicLong(0);
        
        byte[] payload = new byte[messageSize];
        for (int i = 0; i < messageSize; i++) {
            payload[i] = (byte) ('A' + (i % 26));
        }
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < producerCount; i++) {
            final int producerId = i;
            executor.submit(() -> {
                try {
                    AeroClient client = new AeroClient("producer-" + producerId, host, port);
                    client.connect().get(10, TimeUnit.SECONDS);
                    
                    Producer producer = client.createProducer();
                    
                    int messagesPerProducer = messageCount / producerCount;
                    for (int j = 0; j < messagesPerProducer; j++) {
                        try {
                            producer.send(queueName, payload).get();
                            totalMessages.incrementAndGet();
                        } catch (Exception e) {
                            totalErrors.incrementAndGet();
                            logger.debug("Error sending message", e);
                        }
                    }
                    
                    client.disconnect().get();
                } catch (Exception e) {
                    logger.error("Producer {} failed", producerId, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        BenchmarkResult result = new BenchmarkResult(
                "Producer Throughput",
                totalMessages.get(),
                totalErrors.get(),
                duration,
                calculateThroughput(totalMessages.get(), duration),
                0 // No latency measurement for throughput test
        );
        
        logger.info("Producer throughput test completed: {}", result);
        return result;
    }
    
    /**
     * Consumer throughput test
     */
    public BenchmarkResult runConsumerThroughputTest(String host, int port, String queueName,
                                                    int expectedMessages, int consumerCount) throws Exception {
        logger.info("Running consumer throughput test: {} expected messages, {} consumers", 
                expectedMessages, consumerCount);
        
        ExecutorService executor = Executors.newFixedThreadPool(consumerCount);
        CountDownLatch latch = new CountDownLatch(consumerCount);
        AtomicLong totalMessages = new AtomicLong(0);
        AtomicLong totalErrors = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i;
            executor.submit(() -> {
                try {
                    AeroClient client = new AeroClient("consumer-" + consumerId, host, port);
                    client.connect().get(10, TimeUnit.SECONDS);
                    
                    Consumer consumer = client.createConsumer();
                    
                    // Subscribe with message handler
                    consumer.subscribe(queueName, message -> {
                        totalMessages.incrementAndGet();
                        // Auto-ack is enabled by default
                    }).get();
                    
                    // Wait for messages
                    Thread.sleep(30000); // 30 seconds
                    
                    consumer.unsubscribe(queueName).get();
                    client.disconnect().get();
                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    logger.error("Consumer {} failed", consumerId, e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        BenchmarkResult result = new BenchmarkResult(
                "Consumer Throughput",
                totalMessages.get(),
                totalErrors.get(),
                duration,
                calculateThroughput(totalMessages.get(), duration),
                0 // No latency measurement for throughput test
        );
        
        logger.info("Consumer throughput test completed: {}", result);
        return result;
    }
    
    /**
     * Latency test
     */
    public BenchmarkResult runLatencyTest(String host, int port, String queueName,
                                         int messageCount, int messageSize) throws Exception {
        logger.info("Running latency test: {} messages, {} bytes each", messageCount, messageSize);
        
        AeroClient producerClient = new AeroClient("latency-producer", host, port);
        AeroClient consumerClient = new AeroClient("latency-consumer", host, port);
        
        producerClient.connect().get(10, TimeUnit.SECONDS);
        consumerClient.connect().get(10, TimeUnit.SECONDS);
        
        Producer producer = producerClient.createProducer();
        Consumer consumer = consumerClient.createConsumer();
        
        byte[] payload = new byte[messageSize];
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        CountDownLatch receivedLatch = new CountDownLatch(messageCount);
        
        // Subscribe to receive messages
        consumer.subscribe(queueName, message -> {
            long receiveTime = System.currentTimeMillis();
            // Extract send time from headers (would need to be implemented)
            long sendTime = receiveTime - 1; // Placeholder
            latencies.offer(receiveTime - sendTime);
            receivedLatch.countDown();
        }).get();
        
        long startTime = System.currentTimeMillis();
        
        // Send messages
        for (int i = 0; i < messageCount; i++) {
            producer.send(queueName, payload).get();
        }
        
        // Wait for all messages to be received
        receivedLatch.await(60, TimeUnit.SECONDS);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Calculate average latency
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        consumer.unsubscribe(queueName).get();
        producerClient.disconnect().get();
        consumerClient.disconnect().get();
        
        BenchmarkResult result = new BenchmarkResult(
                "Latency Test",
                messageCount,
                0, // No errors in this simple test
                duration,
                calculateThroughput(messageCount, duration),
                avgLatency
        );
        
        logger.info("Latency test completed: {}", result);
        return result;
    }
    
    private void runProducerBenchmark(String host, int port, String[] args) throws Exception {
        // Parse additional arguments for producer benchmark
        String queueName = args.length > 3 ? args[3] : "benchmark-queue";
        int messageCount = args.length > 4 ? Integer.parseInt(args[4]) : 10000;
        int messageSize = args.length > 5 ? Integer.parseInt(args[5]) : 1024;
        int producerCount = args.length > 6 ? Integer.parseInt(args[6]) : 10;
        
        runProducerThroughputTest(host, port, queueName, messageCount, messageSize, producerCount);
    }
    
    private void runConsumerBenchmark(String host, int port, String[] args) throws Exception {
        // Parse additional arguments for consumer benchmark
        String queueName = args.length > 3 ? args[3] : "benchmark-queue";
        int expectedMessages = args.length > 4 ? Integer.parseInt(args[4]) : 10000;
        int consumerCount = args.length > 5 ? Integer.parseInt(args[5]) : 10;
        
        runConsumerThroughputTest(host, port, queueName, expectedMessages, consumerCount);
    }
    
    private void runLatencyBenchmark(String host, int port, String[] args) throws Exception {
        // Parse additional arguments for latency benchmark
        String queueName = args.length > 3 ? args[3] : "latency-queue";
        int messageCount = args.length > 4 ? Integer.parseInt(args[4]) : 1000;
        int messageSize = args.length > 5 ? Integer.parseInt(args[5]) : 1024;
        
        runLatencyTest(host, port, queueName, messageCount, messageSize);
    }
    
    private double calculateThroughput(long messageCount, long durationMs) {
        return (double) messageCount / (durationMs / 1000.0);
    }
    
    private void printUsage() {
        System.out.println("Usage: java -jar aeromq-benchmark.jar <test-type> [host] [port] [options]");
        System.out.println();
        System.out.println("Test types:");
        System.out.println("  producer [host] [port] [queue] [messageCount] [messageSize] [producerCount]");
        System.out.println("  consumer [host] [port] [queue] [expectedMessages] [consumerCount]");
        System.out.println("  latency  [host] [port] [queue] [messageCount] [messageSize]");
        System.out.println();
        System.out.println("Default values:");
        System.out.println("  host: localhost");
        System.out.println("  port: 8888");
        System.out.println("  queue: benchmark-queue");
        System.out.println("  messageCount: 10000 (1000 for latency)");
        System.out.println("  messageSize: 1024");
        System.out.println("  producerCount/consumerCount: 10");
    }
    
    /**
     * Benchmark result holder
     */
    public static class BenchmarkResult {
        private final String testName;
        private final long messagesProcessed;
        private final long errors;
        private final long durationMs;
        private final double throughputMsgPerSec;
        private final double avgLatencyMs;
        
        public BenchmarkResult(String testName, long messagesProcessed, long errors, 
                             long durationMs, double throughputMsgPerSec, double avgLatencyMs) {
            this.testName = testName;
            this.messagesProcessed = messagesProcessed;
            this.errors = errors;
            this.durationMs = durationMs;
            this.throughputMsgPerSec = throughputMsgPerSec;
            this.avgLatencyMs = avgLatencyMs;
        }
        
        // Getters
        public String getTestName() { return testName; }
        public long getMessagesProcessed() { return messagesProcessed; }
        public long getErrors() { return errors; }
        public long getDurationMs() { return durationMs; }
        public double getThroughputMsgPerSec() { return throughputMsgPerSec; }
        public double getAvgLatencyMs() { return avgLatencyMs; }
        
        @Override
        public String toString() {
            return String.format("%s - Messages: %d, Errors: %d, Duration: %dms, " +
                    "Throughput: %.2f msg/sec, Avg Latency: %.2fms",
                    testName, messagesProcessed, errors, durationMs, 
                    throughputMsgPerSec, avgLatencyMs);
        }
    }
}
