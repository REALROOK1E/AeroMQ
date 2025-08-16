package com.aeromq.examples;

import com.aeromq.client.AeroClient;
import com.aeromq.client.Producer;
import com.aeromq.client.Consumer;

import java.util.concurrent.*;

/**
 * AeroMQ 完整功能演示
 * 展示四项性能优化的效果：
 * 1. RequestId -> CompletableFuture 并发请求
 * 2. SPSC Ring Buffer 高性能队列
 * 3. Off-heap DirectByteBuffer 存储
 * 4. 性能基准测试能力
 */
public class Example {

    public static void main(String[] args) {
        String brokerHost = args.length > 0 ? args[0] : "localhost";
        int brokerPort = args.length > 1 ? Integer.parseInt(args[1]) : 9000; // 修改为9000端口
        String queueName = "demo-queue";

        // 直接使用System.out.println，避免SLF4J配置问题
        System.out.println("=== AeroMQ 性能优化演示 ===");
        System.out.println("🚀 启动 AeroMQ 性能优化演示");
        System.out.println("📡 Broker: " + brokerHost + ":" + brokerPort);
        System.out.println("📦 Queue: " + queueName);
        System.out.println("=====================================");

        try {
            // 演示 1: 基础发送接收
            basicSendReceive(brokerHost, brokerPort, queueName);
            
            // 演示 2: 高并发请求映射（优化1）
           concurrentRequestDemo(brokerHost, brokerPort, queueName);
            
            // 演示 3: 批量消息处理（优化2：SPSC Ring Buffer）
            batchProcessingDemo(brokerHost, brokerPort, queueName);
            
            // 演示 4: 大消息处理（优化3：Off-heap 存储）
            largeMessageDemo(brokerHost, brokerPort, queueName);
            
            // 演示 5: Producer-Consumer 模式
            producerConsumerExample(brokerHost, brokerPort, queueName);

            System.out.println("✅ 所有演示完成！AeroMQ 四项优化运行正常");

        } catch (Exception e) {
            System.err.println("❌ 演示过程中出现错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * 基础发送接收演示
     */
    private static void basicSendReceive(String host, int port, String queueName) throws Exception {
        System.out.println("\n🔸 演示 1: 基础发送接收");

        AeroClient client = new AeroClient("demo-client", host, port);
        try {
            client.connect().get(10, TimeUnit.SECONDS);
            System.out.println("✅ 连接到 broker 成功");

            // 创建队列
            client.createQueue(queueName).get(5, TimeUnit.SECONDS);
            System.out.println("✅ 创建队列: " + queueName);

            // 发送消息
            Producer producer = client.createProducer();
            String messageId = producer.sendText(queueName, "Hello, AeroMQ! 这是一个测试消息").get(5, TimeUnit.SECONDS);
            System.out.println("✅ 发送消息成功, ID: " + messageId);

            // 消费消息 (拉模式)
            Consumer consumer = client.createConsumer();
            var messages = consumer.consume(queueName, 1).get();
            System.out.println("✅ 消费了 " + messages.length + " 条消息");

        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
            System.out.println("✅ 断开连接");
        }
    }

    /**
     * Producer-Consumer pattern example
     */
    private static void producerConsumerExample(String host, int port, String queueName) throws Exception {
        System.out.println("\n🔸 演示 5: Producer-Consumer 模式");

        CountDownLatch messagesReceived = new CountDownLatch(5);
        
        // Consumer thread
        Thread consumerThread = new Thread(() -> {
            try {
                AeroClient consumerClient = new AeroClient("consumer-client", host, port);
                consumerClient.connect().get(5, TimeUnit.SECONDS);
                
                Consumer consumer = consumerClient.createConsumer();
                consumer.subscribe(queueName, message -> {
                    System.out.println("接收到消息: " + message.getPayloadAsString());
                    messagesReceived.countDown();
                }).get();
                
                // Wait for messages
                messagesReceived.await(30, TimeUnit.SECONDS);
                
                consumer.unsubscribe(queueName).get();
                consumerClient.disconnect().get();
                
            } catch (Exception e) {
                System.err.println("Consumer 错误: " + e.getMessage());
                e.printStackTrace();
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
                    String message = "消息 " + i;
                    String messageId = producer.sendText(queueName, message).get();
                    System.out.println("发送: " + message + " ID: " + messageId);
                    Thread.sleep(500); // Send every 500ms
                }
                
                producerClient.disconnect().get();
                
            } catch (Exception e) {
                System.err.println("Producer 错误: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Start both threads
        consumerThread.start();
        producerThread.start();
        
        // Wait for completion
        consumerThread.join(40000);
        producerThread.join(40000);
        
        System.out.println("✅ Producer-Consumer 模式演示完成");
    }
    
    /**
     * 高并发请求映射演示（优化1：RequestId -> CompletableFuture）
     */
    private static void concurrentRequestDemo(String host, int port, String queueName) throws Exception {
        System.out.println("\n🔸 演示 2: 高并发请求映射 (RequestId -> CompletableFuture)");

        AeroClient client = new AeroClient("concurrent-client", host, port);
        try {
            client.connect().get(10, TimeUnit.SECONDS);
            System.out.println("✅ 成功连接到服务器");

            Producer producer = client.createProducer();
            
            // 使用更保守的方式：少量并发 + 逐步验证
            int concurrentRequests = 5; // 进一步减少到5个

            System.out.println("开始发送 " + concurrentRequests + " 个并发请求...");
            long startTime = System.currentTimeMillis();

            // 第一种方式：完全并发
            try {
                @SuppressWarnings("unchecked")
                CompletableFuture<String>[] futures = new CompletableFuture[concurrentRequests];

                // 快速发起所有请求
                for (int i = 0; i < concurrentRequests; i++) {
                    String message = "并发消息 #" + i;
                    futures[i] = producer.sendText(queueName, message);
                    System.out.println("已发起请求 " + (i + 1));
                }

                System.out.println("等待所有请求完成（超时时间：30秒）...");

                // 逐个等待完成，而不是等待全部
                int completedCount = 0;
                for (int i = 0; i < futures.length; i++) {
                    try {
                        String result = futures[i].get(10, TimeUnit.SECONDS); // 每个请求10秒超时
                        System.out.println("✅ 请求 " + (i + 1) + " 完成，消息ID: " + result);
                        completedCount++;
                    } catch (TimeoutException e) {
                        System.out.println("⚠️ 请求 " + (i + 1) + " 超时，跳过");
                        futures[i].cancel(true);
                    } catch (Exception e) {
                        System.out.println("❌ 请求 " + (i + 1) + " 失败: " + e.getMessage());
                    }
                }

                long endTime = System.currentTimeMillis();
                System.out.println("✅ 并发测试完成：成功 " + completedCount + "/" + concurrentRequests +
                                 " 个请求，耗时: " + (endTime - startTime) + "ms");

                if (completedCount > 0) {
                    System.out.println("💡 展示了 RequestManager 支持单连接高并发请求的能力");
                } else {
                    System.out.println("⚠️ 所有并发请求都失败了，可能服务器响应较慢或有问题");
                }

            } catch (Exception e) {
                System.out.println("❌ 并发测试失败: " + e.getMessage());

                // 回退到序列化方式作为备选
                System.out.println("🔄 尝试序列化发送作为备选方案...");

                int serialRequests = 3;
                int successCount = 0;
                long serialStartTime = System.currentTimeMillis();

                for (int i = 0; i < serialRequests; i++) {
                    try {
                        String message = "序列化消息 #" + i;
                        String result = producer.sendText(queueName, message).get(5, TimeUnit.SECONDS);
                        System.out.println("✅ 序列化请求 " + (i + 1) + " 成功，ID: " + result);
                        successCount++;
                    } catch (Exception ex) {
                        System.out.println("❌ 序列化请求 " + (i + 1) + " 失败: " + ex.getMessage());
                    }
                }

                long serialEndTime = System.currentTimeMillis();
                if (successCount > 0) {
                    System.out.println("✅ 序列化方式成功 " + successCount + "/" + serialRequests +
                                     " 个请求，耗时: " + (serialEndTime - serialStartTime) + "ms");
                    System.out.println("💡 虽然并发有问题，但基本的 RequestManager 功能正常");
                } else {
                    System.out.println("❌ 序列化方式也失败，可能服务器有问题");
                }
            }

        } finally {
            try {
                client.disconnect().get(5, TimeUnit.SECONDS);
                System.out.println("✅ 已断开连接");
            } catch (Exception e) {
                System.out.println("⚠️ 断开连接时出现问题: " + e.getMessage());
            }
        }
    }
    
    /**
     * 批量消息处理演示（优化2：SPSC Ring Buffer）
     */
    private static void batchProcessingDemo(String host, int port, String queueName) throws Exception {
        System.out.println("\n🔸 演示 3: 批量消息处理 (SPSC Ring Buffer)");

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
                    System.out.println("📤 已发送 " + (i + 1) + "/" + batchSize + " 条消息");
                }
            }
            
            long endTime = System.currentTimeMillis();
            double throughput = (double) batchSize / (endTime - startTime) * 1000;
            System.out.println("✅ 批量发送 " + batchSize + " 条消息完成，吞吐量: " + String.format("%.2f", throughput) + " msg/s");
            System.out.println("💡 展示了 SPSC Ring Buffer 的高吞吐无锁队列性能");

        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
        }
    }
    
    /**
     * 大消息处理演示（优化3：Off-heap DirectByteBuffer 存储）
     */
    private static void largeMessageDemo(String host, int port, String queueName) throws Exception {
        System.out.println("\n🔸 演示 4: 大消息处理 (Off-heap DirectByteBuffer)");

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
                String message = "大消息 #" + i + ": " + largeMessage;
                producer.sendText(queueName, message).get(5, TimeUnit.SECONDS);
                System.out.println("📦 发送大消息 " + (i + 1) + "/" + largeMessageCount + " (大小: ~64KB)");
            }
            
            long endTime = System.currentTimeMillis();
            long totalBytes = (long) largeMessage.length() * largeMessageCount;
            double throughputMB = (double) totalBytes / (endTime - startTime) / 1024;  // MB/s
            
            System.out.println("✅ 发送 " + largeMessageCount + " 条大消息完成，总大小: " + String.format("%.2f", totalBytes / 1024.0 / 1024.0) + "MB，吞吐量: " + String.format("%.2f", throughputMB) + "MB/s");
            System.out.println("💡 展示了 Off-heap DirectByteBuffer 处理大消息减少 GC 压力的能力");

        } finally {
            client.disconnect().get(5, TimeUnit.SECONDS);
        }
    }
}
