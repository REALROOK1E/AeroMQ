package com.aeromq.client;

import com.aeromq.protocol.AeroProtocol;
import com.aeromq.protocol.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumer for receiving messages from AeroMQ broker
 */
public class Consumer {
    
    private static final Logger logger = LoggerFactory.getLogger(Consumer.class);
    
    private final AeroClient client;
    private final Map<String, String> subscriptions; // queueName -> subscriptionId
    private final Map<String, MessageHandler> messageHandlers;
    
    public Consumer(AeroClient client) {
        this.client = client;
        this.subscriptions = new ConcurrentHashMap<>();
        this.messageHandlers = new ConcurrentHashMap<>();
    }
    
    /**
     * Subscribe to a queue
     */
    public CompletableFuture<String> subscribe(String queueName, MessageHandler handler) {
        return subscribe(queueName, handler, null);
    }
    
    /**
     * Subscribe to a queue with options
     */
    public CompletableFuture<String> subscribe(String queueName, MessageHandler handler, 
                                             SubscriptionOptions options) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        if (queueName == null || queueName.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Queue name cannot be null or empty"));
        }
        
        if (handler == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message handler cannot be null"));
        }
        
        if (subscriptions.containsKey(queueName)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Already subscribed to queue: " + queueName));
        }
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("queueName", queueName);
        
        if (options != null) {
            headers.put("autoAck", options.isAutoAck());
            headers.put("prefetchCount", options.getPrefetchCount());
            headers.put("durableSubscription", options.isDurable());
        }
        
        logger.debug("Subscribing to queue: {}", queueName);
        
        return client.sendCommand(Commands.SUBSCRIBE, headers, null)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        String subscriptionId = (String) response.getData().get("subscriptionId");
                        subscriptions.put(queueName, subscriptionId);
                        messageHandlers.put(queueName, handler);
                        
                        logger.info("Successfully subscribed to queue: {} with subscriptionId: {}", 
                                queueName, subscriptionId);
                        return subscriptionId;
                    } else {
                        throw new RuntimeException("Failed to subscribe to queue: " + response.getStatusMessage());
                    }
                });
    }
    
    /**
     * Unsubscribe from a queue
     */
    public CompletableFuture<Void> unsubscribe(String queueName) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        String subscriptionId = subscriptions.get(queueName);
        if (subscriptionId == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not subscribed to queue: " + queueName));
        }
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("queueName", queueName);
        headers.put("subscriptionId", subscriptionId);
        
        logger.debug("Unsubscribing from queue: {}", queueName);
        
        return client.sendCommand(Commands.UNSUBSCRIBE, headers, null)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        subscriptions.remove(queueName);
                        messageHandlers.remove(queueName);
                        
                        logger.info("Successfully unsubscribed from queue: {}", queueName);
                        return null;
                    } else {
                        throw new RuntimeException("Failed to unsubscribe from queue: " + response.getStatusMessage());
                    }
                });
    }
    
    /**
     * Manually consume messages from a queue (pull mode)
     */
    public CompletableFuture<ReceivedMessage[]> consume(String queueName, int maxMessages) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("queueName", queueName);
        headers.put("maxMessages", maxMessages);
        
        logger.debug("Consuming up to {} messages from queue: {}", maxMessages, queueName);
        
        return client.sendCommand(Commands.CONSUME, headers, null)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        // 解析服务器返回的消息数据
                        Map<String, Object> data = response.getData();
                        if (data != null && data.containsKey("messages")) {
                            // 修复类型转换问题：服务器可能返回ArrayList而不是Object[]
                            Object messagesObj = data.get("messages");
                            ReceivedMessage[] messages;

                            if (messagesObj instanceof java.util.List) {
                                // 如果是List类型（ArrayList），转换为数组
                                java.util.List<?> messagesList = (java.util.List<?>) messagesObj;
                                messages = new ReceivedMessage[messagesList.size()];

                                for (int i = 0; i < messagesList.size(); i++) {
                                    Map<String, Object> messageData = (Map<String, Object>) messagesList.get(i);
                                    String messageId = (String) messageData.get("messageId");
                                    String queue = (String) messageData.get("queueName");
                                    String payload = (String) messageData.get("payload");
                                    Long timestamp = (Long) messageData.get("timestamp");
                                    Map<String, String> messageHeaders = (Map<String, String>) messageData.get("headers");

                                    messages[i] = new ReceivedMessage(
                                        messageId != null ? messageId : "unknown",
                                        queue != null ? queue : queueName,
                                        payload != null ? payload.getBytes() : new byte[0],
                                        messageHeaders != null ? messageHeaders : new HashMap<>(),
                                        timestamp != null ? timestamp : System.currentTimeMillis()
                                    );
                                }
                            } else if (messagesObj instanceof Object[]) {
                                // 如果是Object[]数组，按原来的方式处理
                                Object[] messagesArray = (Object[]) messagesObj;
                                messages = new ReceivedMessage[messagesArray.length];

                                for (int i = 0; i < messagesArray.length; i++) {
                                    Map<String, Object> messageData = (Map<String, Object>) messagesArray[i];
                                    String messageId = (String) messageData.get("messageId");
                                    String queue = (String) messageData.get("queueName");
                                    String payload = (String) messageData.get("payload");
                                    Long timestamp = (Long) messageData.get("timestamp");
                                    Map<String, String> messageHeaders = (Map<String, String>) messageData.get("headers");

                                    messages[i] = new ReceivedMessage(
                                        messageId != null ? messageId : "unknown",
                                        queue != null ? queue : queueName,
                                        payload != null ? payload.getBytes() : new byte[0],
                                        messageHeaders != null ? messageHeaders : new HashMap<>(),
                                        timestamp != null ? timestamp : System.currentTimeMillis()
                                    );
                                }
                            } else {
                                // 未知类型，返回空数组
                                messages = new ReceivedMessage[0];
                            }

                            logger.debug("Successfully parsed {} messages from server response", messages.length);
                            return messages;
                        }

                        // 如果没有消息数据，返回空数组
                        return new ReceivedMessage[0];
                    } else {
                        throw new RuntimeException("Failed to consume messages: " + response.getStatusMessage());
                    }
                });
    }
    
    /**
     * 顺序消费消息 (Sequential consumption with ordering guarantee)
     */
    public CompletableFuture<ReceivedMessage[]> consumeSequential(String queueName, int maxMessages) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put("queueName", queueName);
        headers.put("maxMessages", maxMessages);
        headers.put("consumeMode", "SEQUENTIAL"); // 标记为顺序消费模式
        headers.put("guaranteeOrder", true);      // 保证顺序

        logger.debug("Sequential consuming up to {} messages from queue: {}", maxMessages, queueName);

        return client.sendCommand(Commands.CONSUME, headers, null)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        // 解析服务器返回的消息数据
                        Map<String, Object> data = response.getData();
                        if (data != null && data.containsKey("messages")) {
                            Object messagesObj = data.get("messages");
                            ReceivedMessage[] messages;

                            if (messagesObj instanceof java.util.List) {
                                java.util.List<?> messagesList = (java.util.List<?>) messagesObj;
                                messages = new ReceivedMessage[messagesList.size()];

                                for (int i = 0; i < messagesList.size(); i++) {
                                    Map<String, Object> messageData = (Map<String, Object>) messagesList.get(i);
                                    String messageId = (String) messageData.get("messageId");
                                    String queue = (String) messageData.get("queueName");
                                    String payload = (String) messageData.get("payload");
                                    Long timestamp = (Long) messageData.get("timestamp");
                                    Long sequence = (Long) messageData.get("sequence"); // 顺序号
                                    Map<String, String> messageHeaders = (Map<String, String>) messageData.get("headers");

                                    messages[i] = new ReceivedMessage(
                                        messageId != null ? messageId : "unknown",
                                        queue != null ? queue : queueName,
                                        payload != null ? payload.getBytes() : new byte[0],
                                        messageHeaders != null ? messageHeaders : new HashMap<>(),
                                        timestamp != null ? timestamp : System.currentTimeMillis()
                                    );

                                    // 添加顺序信息到headers
                                    if (sequence != null) {
                                        messages[i].getHeaders().put("sequence", String.valueOf(sequence));
                                    }
                                }
                            } else if (messagesObj instanceof Object[]) {
                                Object[] messagesArray = (Object[]) messagesObj;
                                messages = new ReceivedMessage[messagesArray.length];

                                for (int i = 0; i < messagesArray.length; i++) {
                                    Map<String, Object> messageData = (Map<String, Object>) messagesArray[i];
                                    String messageId = (String) messageData.get("messageId");
                                    String queue = (String) messageData.get("queueName");
                                    String payload = (String) messageData.get("payload");
                                    Long timestamp = (Long) messageData.get("timestamp");
                                    Long sequence = (Long) messageData.get("sequence");
                                    Map<String, String> messageHeaders = (Map<String, String>) messageData.get("headers");

                                    messages[i] = new ReceivedMessage(
                                        messageId != null ? messageId : "unknown",
                                        queue != null ? queue : queueName,
                                        payload != null ? payload.getBytes() : new byte[0],
                                        messageHeaders != null ? messageHeaders : new HashMap<>(),
                                        timestamp != null ? timestamp : System.currentTimeMillis()
                                    );

                                    if (sequence != null) {
                                        messages[i].getHeaders().put("sequence", String.valueOf(sequence));
                                    }
                                }
                            } else {
                                messages = new ReceivedMessage[0];
                            }

                            logger.debug("Successfully parsed {} sequential messages from server response", messages.length);
                            return messages;
                        }

                        return new ReceivedMessage[0];
                    } else {
                        throw new RuntimeException("Failed to consume sequential messages: " + response.getStatusMessage());
                    }
                });
    }

    /**
     * 订阅队列进行顺序消费 (Sequential subscription with ordering guarantee)
     */
    public CompletableFuture<String> subscribeSequential(String queueName, MessageHandler handler) {
        return subscribeSequential(queueName, handler, null);
    }

    /**
     * 订阅队列进行顺序消费，带选项
     */
    public CompletableFuture<String> subscribeSequential(String queueName, MessageHandler handler,
                                                        SubscriptionOptions options) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }

        if (queueName == null || queueName.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Queue name cannot be null or empty"));
        }

        if (handler == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message handler cannot be null"));
        }

        if (subscriptions.containsKey(queueName)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Already subscribed to queue: " + queueName));
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put("queueName", queueName);
        headers.put("subscribeMode", "SEQUENTIAL"); // 标记为顺序订阅模式
        headers.put("guaranteeOrder", true);

        if (options != null) {
            headers.put("autoAck", options.isAutoAck());
            headers.put("prefetchCount", 1); // 顺序消费通常预取数量为1，确保顺序
            headers.put("durableSubscription", options.isDurable());
        } else {
            headers.put("prefetchCount", 1); // 默认预取1条
        }

        logger.debug("Sequential subscribing to queue: {}", queueName);

        return client.sendCommand(Commands.SUBSCRIBE, headers, null)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        String subscriptionId = (String) response.getData().get("subscriptionId");
                        subscriptions.put(queueName, subscriptionId);
                        messageHandlers.put(queueName, handler);

                        logger.info("Successfully subscribed to queue sequentially: {} with subscriptionId: {}",
                                queueName, subscriptionId);
                        return subscriptionId;
                    } else {
                        throw new RuntimeException("Failed to subscribe sequentially to queue: " + response.getStatusMessage());
                    }
                });
    }

    /**
     * Acknowledge message processing
     */
    public CompletableFuture<Void> ack(String messageId) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("messageId", messageId);
        headers.put("ackType", "ACK");
        
        logger.debug("Acknowledging message: {}", messageId);
        
        return client.sendCommand(Commands.ACK, headers, null)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        logger.debug("Successfully acknowledged message: {}", messageId);
                        return null;
                    } else {
                        throw new RuntimeException("Failed to acknowledge message: " + response.getStatusMessage());
                    }
                });
    }
    
    /**
     * Negatively acknowledge message (reject)
     */
    public CompletableFuture<Void> nack(String messageId, boolean requeue) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        Map<String, Object> headers = new HashMap<>();
        headers.put("messageId", messageId);
        headers.put("ackType", "NACK");
        headers.put("requeue", requeue);
        
        logger.debug("Negatively acknowledging message: {} (requeue: {})", messageId, requeue);
        
        return client.sendCommand(Commands.NACK, headers, null)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        logger.debug("Successfully nacked message: {}", messageId);
                        return null;
                    } else {
                        throw new RuntimeException("Failed to nack message: " + response.getStatusMessage());
                    }
                });
    }
    
    /**
     * Get consumer statistics
     */
    public ConsumerStats getStats() {
        // TODO: Implement consumer statistics
        return new ConsumerStats(subscriptions.size(), 0, 0, 0);
    }
    
    // Getters
    public Map<String, String> getSubscriptions() {
        return new HashMap<>(subscriptions);
    }
    
    /**
     * Message handler interface
     */
    @FunctionalInterface
    public interface MessageHandler {
        void handle(ReceivedMessage message);
    }
    
    /**
     * Subscription options
     */
    public static class SubscriptionOptions {
        private boolean autoAck = true;
        private int prefetchCount = 1;
        private boolean durable = false;
        
        public boolean isAutoAck() { return autoAck; }
        public void setAutoAck(boolean autoAck) { this.autoAck = autoAck; }
        
        public int getPrefetchCount() { return prefetchCount; }
        public void setPrefetchCount(int prefetchCount) { this.prefetchCount = prefetchCount; }
        
        public boolean isDurable() { return durable; }
        public void setDurable(boolean durable) { this.durable = durable; }
    }
    
    /**
     * Received message wrapper
     */
    public static class ReceivedMessage {
        private final String messageId;
        private final String queueName;
        private final byte[] payload;
        private final Map<String, String> headers;
        private final long timestamp;
        
        public ReceivedMessage(String messageId, String queueName, byte[] payload, 
                             Map<String, String> headers, long timestamp) {
            this.messageId = messageId;
            this.queueName = queueName;
            this.payload = payload;
            this.headers = headers;
            this.timestamp = timestamp;
        }
        
        public String getMessageId() { return messageId; }
        public String getQueueName() { return queueName; }
        public byte[] getPayload() { return payload; }
        public String getPayloadAsString() { return new String(payload); }
        public Map<String, String> getHeaders() { return headers; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Consumer statistics holder
     */
    public static class ConsumerStats {
        private final int activeSubscriptions;
        private final long messagesConsumed;
        private final long bytesConsumed;
        private final long errors;
        
        public ConsumerStats(int activeSubscriptions, long messagesConsumed, 
                           long bytesConsumed, long errors) {
            this.activeSubscriptions = activeSubscriptions;
            this.messagesConsumed = messagesConsumed;
            this.bytesConsumed = bytesConsumed;
            this.errors = errors;
        }
        
        public int getActiveSubscriptions() { return activeSubscriptions; }
        public long getMessagesConsumed() { return messagesConsumed; }
        public long getBytesConsumed() { return bytesConsumed; }
        public long getErrors() { return errors; }
        
        @Override
        public String toString() {
            return String.format("ConsumerStats{subscriptions=%d, messages=%d, bytes=%d, errors=%d}", 
                    activeSubscriptions, messagesConsumed, bytesConsumed, errors);
        }
    }
}
