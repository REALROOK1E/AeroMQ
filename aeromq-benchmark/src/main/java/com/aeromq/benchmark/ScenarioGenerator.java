package com.aeromq.benchmark;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generator for creating various benchmark scenarios and test data
 */
public class ScenarioGenerator {
    
    private static final String[] QUEUE_NAMES = {
        "orders", "payments", "notifications", "analytics", 
        "user-events", "system-logs", "monitoring", "alerts"
    };
    
    private static final String[] MESSAGE_TYPES = {
        "ORDER_CREATED", "PAYMENT_PROCESSED", "USER_REGISTERED", 
        "SYSTEM_ALERT", "DATA_EXPORT", "NOTIFICATION_SENT"
    };
    
    /**
     * Generate random message payload
     */
    public static byte[] generateRandomPayload(int size) {
        return RandomStringUtils.randomAlphanumeric(size).getBytes();
    }
    
    /**
     * Generate JSON-like message payload
     */
    public static byte[] generateJsonPayload(int minSize, int maxSize) {
        int size = ThreadLocalRandom.current().nextInt(minSize, maxSize + 1);
        StringBuilder json = new StringBuilder();
        
        json.append("{");
        json.append("\"id\":\"").append(RandomStringUtils.randomAlphanumeric(10)).append("\",");
        json.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
        json.append("\"type\":\"").append(getRandomMessageType()).append("\",");
        json.append("\"userId\":\"").append(RandomStringUtils.randomNumeric(8)).append("\",");
        json.append("\"data\":\"").append(RandomStringUtils.randomAlphanumeric(size - 100)).append("\"");
        json.append("}");
        
        return json.toString().getBytes();
    }
    
    /**
     * Generate message headers
     */
    public static Map<String, Object> generateHeaders() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("messageType", getRandomMessageType());
        headers.put("priority", RandomUtils.nextInt(0, 10));
        headers.put("source", "benchmark-client");
        headers.put("correlationId", RandomStringUtils.randomAlphanumeric(16));
        return headers;
    }
    
    /**
     * Get random queue name
     */
    public static String getRandomQueueName() {
        return QUEUE_NAMES[ThreadLocalRandom.current().nextInt(QUEUE_NAMES.length)];
    }
    
    /**
     * Get random message type
     */
    public static String getRandomMessageType() {
        return MESSAGE_TYPES[ThreadLocalRandom.current().nextInt(MESSAGE_TYPES.length)];
    }
    
    /**
     * Generate burst scenario - high throughput for short period
     */
    public static BenchmarkScenario createBurstScenario() {
        return new BenchmarkScenario.Builder()
                .name("Burst Test")
                .messageCount(50000)
                .messageSize(512)
                .producerCount(20)
                .consumerCount(5)
                .duration(60) // 60 seconds
                .build();
    }
    
    /**
     * Generate sustained load scenario
     */
    public static BenchmarkScenario createSustainedLoadScenario() {
        return new BenchmarkScenario.Builder()
                .name("Sustained Load")
                .messageCount(100000)
                .messageSize(1024)
                .producerCount(10)
                .consumerCount(10)
                .duration(300) // 5 minutes
                .build();
    }
    
    /**
     * Generate latency-focused scenario
     */
    public static BenchmarkScenario createLatencyScenario() {
        return new BenchmarkScenario.Builder()
                .name("Latency Test")
                .messageCount(10000)
                .messageSize(256)
                .producerCount(1)
                .consumerCount(1)
                .duration(120) // 2 minutes
                .build();
    }
    
    /**
     * Generate mixed workload scenario
     */
    public static BenchmarkScenario createMixedWorkloadScenario() {
        return new BenchmarkScenario.Builder()
                .name("Mixed Workload")
                .messageCount(75000)
                .messageSize(768)
                .producerCount(15)
                .consumerCount(8)
                .duration(240) // 4 minutes
                .variableMessageSize(true)
                .multipleQueues(true)
                .build();
    }
    
    /**
     * Benchmark scenario configuration
     */
    public static class BenchmarkScenario {
        private final String name;
        private final int messageCount;
        private final int messageSize;
        private final int producerCount;
        private final int consumerCount;
        private final int durationSeconds;
        private final boolean variableMessageSize;
        private final boolean multipleQueues;
        
        private BenchmarkScenario(Builder builder) {
            this.name = builder.name;
            this.messageCount = builder.messageCount;
            this.messageSize = builder.messageSize;
            this.producerCount = builder.producerCount;
            this.consumerCount = builder.consumerCount;
            this.durationSeconds = builder.durationSeconds;
            this.variableMessageSize = builder.variableMessageSize;
            this.multipleQueues = builder.multipleQueues;
        }
        
        // Getters
        public String getName() { return name; }
        public int getMessageCount() { return messageCount; }
        public int getMessageSize() { return messageSize; }
        public int getProducerCount() { return producerCount; }
        public int getConsumerCount() { return consumerCount; }
        public int getDurationSeconds() { return durationSeconds; }
        public boolean isVariableMessageSize() { return variableMessageSize; }
        public boolean isMultipleQueues() { return multipleQueues; }
        
        @Override
        public String toString() {
            return String.format("Scenario{name='%s', messages=%d, size=%d, producers=%d, consumers=%d, duration=%ds}",
                    name, messageCount, messageSize, producerCount, consumerCount, durationSeconds);
        }
        
        public static class Builder {
            private String name;
            private int messageCount = 10000;
            private int messageSize = 1024;
            private int producerCount = 1;
            private int consumerCount = 1;
            private int durationSeconds = 60;
            private boolean variableMessageSize = false;
            private boolean multipleQueues = false;
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder messageCount(int messageCount) {
                this.messageCount = messageCount;
                return this;
            }
            
            public Builder messageSize(int messageSize) {
                this.messageSize = messageSize;
                return this;
            }
            
            public Builder producerCount(int producerCount) {
                this.producerCount = producerCount;
                return this;
            }
            
            public Builder consumerCount(int consumerCount) {
                this.consumerCount = consumerCount;
                return this;
            }
            
            public Builder duration(int durationSeconds) {
                this.durationSeconds = durationSeconds;
                return this;
            }
            
            public Builder variableMessageSize(boolean variableMessageSize) {
                this.variableMessageSize = variableMessageSize;
                return this;
            }
            
            public Builder multipleQueues(boolean multipleQueues) {
                this.multipleQueues = multipleQueues;
                return this;
            }
            
            public BenchmarkScenario build() {
                return new BenchmarkScenario(this);
            }
        }
    }
    
    /**
     * Generate realistic e-commerce order payload
     */
    public static byte[] generateOrderPayload() {
        StringBuilder order = new StringBuilder();
        order.append("{");
        order.append("\"orderId\":\"ORD-").append(RandomStringUtils.randomNumeric(8)).append("\",");
        order.append("\"customerId\":\"CUST-").append(RandomStringUtils.randomNumeric(6)).append("\",");
        order.append("\"items\":[");
        
        int itemCount = ThreadLocalRandom.current().nextInt(1, 6);
        for (int i = 0; i < itemCount; i++) {
            if (i > 0) order.append(",");
            order.append("{");
            order.append("\"sku\":\"").append(RandomStringUtils.randomAlphanumeric(8)).append("\",");
            order.append("\"quantity\":").append(ThreadLocalRandom.current().nextInt(1, 10)).append(",");
            order.append("\"price\":").append(ThreadLocalRandom.current().nextDouble(10.0, 1000.0));
            order.append("}");
        }
        
        order.append("],");
        order.append("\"totalAmount\":").append(ThreadLocalRandom.current().nextDouble(50.0, 5000.0)).append(",");
        order.append("\"currency\":\"USD\",");
        order.append("\"orderDate\":\"").append(System.currentTimeMillis()).append("\"");
        order.append("}");
        
        return order.toString().getBytes();
    }
    
    /**
     * Generate realistic user event payload
     */
    public static byte[] generateUserEventPayload() {
        StringBuilder event = new StringBuilder();
        event.append("{");
        event.append("\"eventId\":\"").append(RandomStringUtils.randomAlphanumeric(12)).append("\",");
        event.append("\"userId\":\"").append(RandomStringUtils.randomNumeric(8)).append("\",");
        event.append("\"sessionId\":\"").append(RandomStringUtils.randomAlphanumeric(16)).append("\",");
        event.append("\"eventType\":\"").append(getRandomUserEventType()).append("\",");
        event.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
        event.append("\"properties\":{");
        event.append("\"page\":\"").append(getRandomPageName()).append("\",");
        event.append("\"userAgent\":\"").append(getRandomUserAgent()).append("\",");
        event.append("\"ip\":\"").append(generateRandomIP()).append("\"");
        event.append("}");
        event.append("}");
        
        return event.toString().getBytes();
    }
    
    private static String getRandomUserEventType() {
        String[] types = {"page_view", "click", "purchase", "signup", "login", "logout"};
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }
    
    private static String getRandomPageName() {
        String[] pages = {"/home", "/products", "/cart", "/checkout", "/profile", "/search"};
        return pages[ThreadLocalRandom.current().nextInt(pages.length)];
    }
    
    private static String getRandomUserAgent() {
        String[] agents = {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"
        };
        return agents[ThreadLocalRandom.current().nextInt(agents.length)];
    }
    
    private static String generateRandomIP() {
        return String.format("%d.%d.%d.%d",
                ThreadLocalRandom.current().nextInt(1, 256),
                ThreadLocalRandom.current().nextInt(0, 256),
                ThreadLocalRandom.current().nextInt(0, 256),
                ThreadLocalRandom.current().nextInt(1, 256));
    }
}
