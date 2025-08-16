package com.aeromq.examples;

import com.aeromq.client.AeroClient;
import com.aeromq.client.Producer;
import com.aeromq.client.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * AeroMQ 完整功能演示
 * 展示四项性能优化的效果：
 * 1. RequestId -> CompletableFuture 并发请求
 * 2. SPSC Ring Buffer 高性能队列
 * 3. Off-heap DirectByteBuffer 存储
 * 4. 性能基准测试能力
 */
public class simpleExample {
    
    private static final Logger logger = LoggerFactory.getLogger(simpleExample.class);
    
    public static void main(String[] args) throws Exception {
        String brokerHost = args.length > 0 ? args[0] : "localhost";
        int brokerPort = args.length > 1 ? Integer.parseInt(args[1]) : 9000;  // 使用新端口
        String queueName = "demo-queue";
        
        logger.info("🚀 启动 AeroMQ 性能优化演示");
        logger.info("📡 Broker: {}:{}", brokerHost, brokerPort);
        logger.info("📦 Queue: {}", queueName);
        logger.info("=====================================");
        
        try {
            // 演示 1: 基础发送接收
            basicSendReceive(brokerHost, brokerPort, queueName);
            
            // 演示 2: 高并发请求映射（优化1）
            concurrentRequestDemo(brokerHost, brokerPort, queueName);
            
            // 演示 3: 批量消息处理（优化2：SPSC Ring Buffer）
            batchProcessingDemo(brokerHost, brokerPort, queueName);
            
            // 演示 4: 大消息处理（优化3：Off-heap 存储）
            largeMessageDemo(brokerHost, brokerPort, queueName);
            
            logger.info("✅ 所有演示完成！AeroMQ 四项优化运行正常");
            
        } catch (Exception e) {
            logger.error("❌ 演示过程中出现错误", e);
            System.exit(1);
        }
    }
    
    /**
     * 基础发送接收演示
     */
    private static void basicSendReceive(String host, int port, String queueName) throws Exception {
        logger.info("\n🔸 演示 1: 基础发送接收");
        
        // 连接到 broker
        AeroClient client = new AeroClient("demo-client", host, port);
        try {
            client.connect().get(10, TimeUnit.SECONDS);
            logger.info("✅ 连接到 broker 成功");
            
            // 创建队列
            client.createQueue(queueName).get(5, TimeUnit.SECONDS);
            logger.info("✅ 创建队列: {}", queueName);
            
            // 发送消息
            Producer producer = client.createProducer();
            String messageId = producer.sendText(queueName, "Hello, AeroMQ! 这是一个测试消息").get(5, TimeUnit.SECONDS);
            logger.info("✅ 发送消息成功, ID: {}", messageId);
            
        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
            logger.info("✅ 断开连接");
        }
    }
        
        // Send message
        Producer producer = client.createProducer();
        String messageId = producer.sendText(queueName, "Hello, AeroMQ!").get();
        logger.info("Sent message: {} with ID: {}", "Hello, AeroMQ!", messageId);
        
        // Consume message (pull mode)
        Consumer consumer = client.createConsumer();
        var messages = consumer.consume(queueName, 1).get();
        logger.info("Consumed {} messages", messages.length);
        
        // Cleanup
        client.disconnect().get();
        logger.info("Disconnected from broker");
    }
    
    /**
     * Producer-Consumer pattern example
     */
    private static void producerConsumerExample(String host, int port, String queueName) throws Exception {
        logger.info("=== Producer-Consumer Example ===");
        
        CountDownLatch messagesReceived = new CountDownLatch(5);
        
        // Consumer thread
        Thread consumerThread = new Thread(() -> {
            try {
                AeroClient consumerClient = new AeroClient("consumer-client", host, port);
                consumerClient.connect().get(5, TimeUnit.SECONDS);
                
                Consumer consumer = consumerClient.createConsumer();
                consumer.subscribe(queueName, message -> {
                    logger.info("Received: {}", message.getPayloadAsString());
                    messagesReceived.countDown();
                }).get();
                
                // Wait for messages
                messagesReceived.await(30, TimeUnit.SECONDS);
                
                consumer.unsubscribe(queueName).get();
                consumerClient.disconnect().get();
                
            } catch (Exception e) {
                logger.error("Consumer error", e);
            }
        });
        
        // Producer thread  
        Thread producerThread = new Thread(() -> {
            try {
                // Wait a bit for consumer to be ready
                Thread.sleep(1000);
                
                AeroClient producerClient = new AeroClient("producer-client", host, port);
                producerClient.connect().get(5, TimeUnit.SECONDS);
                
                Producer producer = producerClient.createProducer();
                
                for (int i = 1; i <= 5; i++) {
                    String message = "Message " + i;
                    String messageId = producer.sendText(queueName, message).get();
                    logger.info("Sent: {} with ID: {}", message, messageId);
                    Thread.sleep(500); // Send every 500ms
                }
                
                producerClient.disconnect().get();
                
            } catch (Exception e) {
                logger.error("Producer error", e);
            }
        });
        
        // Start both threads
        consumerThread.start();
        producerThread.start();
        
        // Wait for completion
        consumerThread.join(40000);
        producerThread.join(40000);
        
        logger.info("Producer-Consumer example completed");
    }
    
    /**
     * 高并发请求映射演示（优化1：RequestId -> CompletableFuture）
     */
    private static void concurrentRequestDemo(String host, int port, String queueName) throws Exception {
        logger.info("\n🔸 演示 2: 高并发请求映射 (RequestId -> CompletableFuture)");
        
        AeroClient client = new AeroClient("concurrent-client", host, port);
        try {
            client.connect().get(10, TimeUnit.SECONDS);
            Producer producer = client.createProducer();
            
            // 并发发送多个请求
            int concurrentRequests = 50;
            CompletableFuture<String>[] futures = new CompletableFuture[concurrentRequests];
            
            long startTime = System.currentTimeMillis();
            
            // 同时发起多个请求（展示非 ThreadLocal 的并发能力）
            for (int i = 0; i < concurrentRequests; i++) {
                String message = "并发消息 #" + i;
                futures[i] = producer.sendText(queueName, message);
            }
            
            // 等待所有请求完成
            CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);
            
            long endTime = System.currentTimeMillis();
            logger.info("✅ 并发发送 {} 条消息完成，耗时: {}ms", concurrentRequests, endTime - startTime);
            logger.info("💡 展示了 RequestManager 支持单连接高并发请求的能力");
            
        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 批量消息处理演示（优化2：SPSC Ring Buffer）
     */
    private static void batchProcessingDemo(String host, int port, String queueName) throws Exception {
        logger.info("\n🔸 演示 3: 批量消息处理 (SPSC Ring Buffer)");
        
        AeroClient client = new AeroClient("batch-client", host, port);
        try {
            client.connect().get(10, TimeUnit.SECONDS);
            Producer producer = client.createProducer();
            
            // 快速批量发送消息（展示 SPSC Ring Buffer 的高吞吐能力）
            int batchSize = 100;
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < batchSize; i++) {
                String message = "批量消息 #" + i + " - 测试 SPSC Ring Buffer 性能";
                producer.sendText(queueName, message).get(1, TimeUnit.SECONDS);
                
                // 每10条打印进度
                if (i % 10 == 0) {
                    logger.info("📤 已发送 {}/{} 条消息", i + 1, batchSize);
                }
            }
            
            long endTime = System.currentTimeMillis();
            double throughput = (double) batchSize / (endTime - startTime) * 1000;
            logger.info("✅ 批量发送 {} 条消息完成，吞吐量: {:.2f} msg/s", batchSize, throughput);
            logger.info("💡 展示了 SPSC Ring Buffer 的高吞吐无锁队列性能");
            
        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 大消息处理演示（优化3：Off-heap DirectByteBuffer 存储）
     */
    private static void largeMessageDemo(String host, int port, String queueName) throws Exception {
        logger.info("\n🔸 演示 4: 大消息处理 (Off-heap DirectByteBuffer)");
        
        AeroClient client = new AeroClient("large-msg-client", host, port);
        try {
            client.connect().get(10, TimeUnit.SECONDS);
            Producer producer = client.createProducer();
            
            // 创建大消息（64KB）来展示 off-heap 存储能力
            StringBuilder largeMessage = new StringBuilder();
            for (int i = 0; i < 8192; i++) {  // 64KB 消息
                largeMessage.append("大消息测试数据片段 #").append(i).append(" - ");
            }
            
            long startTime = System.currentTimeMillis();
            
            // 发送多个大消息
            int largeMessageCount = 10;
            for (int i = 0; i < largeMessageCount; i++) {
                String message = "大消息 #" + i + ": " + largeMessage.toString();
                producer.sendText(queueName, message).get(5, TimeUnit.SECONDS);
                logger.info("📦 发送大消息 {}/{} (大小: ~64KB)", i + 1, largeMessageCount);
            }
            
            long endTime = System.currentTimeMillis();
            long totalBytes = largeMessage.length() * largeMessageCount;
            double throughputMB = (double) totalBytes / (endTime - startTime) / 1024;  // MB/s
            
            logger.info("✅ 发送 {} 条大消息完成，总大小: {:.2f}MB，吞吐量: {:.2f}MB/s", 
                       largeMessageCount, totalBytes / 1024.0 / 1024.0, throughputMB);
            logger.info("💡 展示了 Off-heap DirectByteBuffer 处理大消息减少 GC 压力的能力");
            
        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
        }
    }
}
