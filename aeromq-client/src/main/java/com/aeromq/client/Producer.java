package com.aeromq.client;

import com.aeromq.protocol.AeroProtocol;
import com.aeromq.protocol.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Producer for sending messages to AeroMQ broker
 */
public class Producer {
    
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);
    
    private final AeroClient client;
    
    public Producer(AeroClient client) {
        this.client = client;
    }
    
    /**
     * Send a message to a queue
     */
    public CompletableFuture<String> send(String queueName, byte[] payload) {
        return send(queueName, payload, null);
    }
    
    /**
     * Send a message to a queue with headers
     */
    public CompletableFuture<String> send(String queueName, byte[] payload, Map<String, Object> headers) {
        return send(queueName, payload, headers, 0);
    }
    
    /**
     * Send a message to a queue with priority
     */
    public CompletableFuture<String> send(String queueName, byte[] payload, 
                                         Map<String, Object> headers, int priority) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        if (queueName == null || queueName.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Queue name cannot be null or empty"));
        }
        
        if (payload == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Payload cannot be null"));
        }
        
        Map<String, Object> messageHeaders = new HashMap<>();
        messageHeaders.put("queueName", queueName);
        messageHeaders.put("priority", priority);
        messageHeaders.put("messageType", "USER_MESSAGE");
        
        if (headers != null) {
            messageHeaders.putAll(headers);
        }
        
        logger.debug("Sending message to queue: {} with {} bytes", queueName, payload.length);
        
        return client.sendCommand(Commands.SEND, messageHeaders, payload)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        // 安全地处理可能为null的getData()
                        Map<String, Object> data = response.getData();
                        String messageId;
                        if (data != null && data.containsKey("messageId")) {
                            messageId = (String) data.get("messageId");
                        } else {
                            // 如果服务器没有返回messageId，使用requestId作为备用
                            messageId = String.valueOf(response.getRequestId());
                        }
                        logger.debug("Message sent successfully, messageId: {}", messageId);
                        return messageId;
                    } else {
                        throw new RuntimeException("Failed to send message: " + response.getStatusMessage());
                    }
                });
    }
    
    /**
     * Send a text message
     */
    public CompletableFuture<String> sendText(String queueName, String text) {
        return send(queueName, text.getBytes());
    }
    
    /**
     * Send a text message with headers
     */
    public CompletableFuture<String> sendText(String queueName, String text, Map<String, Object> headers) {
        return send(queueName, text.getBytes(), headers);
    }
    
    /**
     * Send multiple messages in batch
     */
    public CompletableFuture<String[]> sendBatch(String queueName, byte[][] payloads) {
        return sendBatch(queueName, payloads, null);
    }
    
    /**
     * Send multiple messages in batch with headers
     */
    public CompletableFuture<String[]> sendBatch(String queueName, byte[][] payloads, Map<String, Object>[] headers) {
        if (!client.isConnected()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        if (payloads == null || payloads.length == 0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Payloads cannot be null or empty"));
        }
        
        Map<String, Object> batchHeaders = new HashMap<>();
        batchHeaders.put("queueName", queueName);
        batchHeaders.put("batchSize", payloads.length);
        
        // TODO: Implement proper batch message serialization
        // For now, just send the first message as a placeholder
        byte[] batchPayload = payloads[0];
        
        logger.debug("Sending batch of {} messages to queue: {}", payloads.length, queueName);
        
        return client.sendCommand(Commands.SEND_BATCH, batchHeaders, batchPayload)
                .thenApply(response -> {
                    if (response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                        // TODO: Parse actual batch response
                        return new String[]{"batch-message-id"};
                    } else {
                        throw new RuntimeException("Failed to send batch: " + response.getStatusMessage());
                    }
                });
    }
    
    /**
     * Get producer statistics
     */
    public ProducerStats getStats() {
        // TODO: Implement producer statistics
        return new ProducerStats(0, 0, 0);
    }
    
    /**
     * Producer statistics holder
     */
    public static class ProducerStats {
        private final long messagesSent;
        private final long bytesProduced;
        private final long errors;
        
        public ProducerStats(long messagesSent, long bytesProduced, long errors) {
            this.messagesSent = messagesSent;
            this.bytesProduced = bytesProduced;
            this.errors = errors;
        }
        
        public long getMessagesSent() { return messagesSent; }
        public long getBytesProduced() { return bytesProduced; }
        public long getErrors() { return errors; }
        
        @Override
        public String toString() {
            return String.format("ProducerStats{messages=%d, bytes=%d, errors=%d}", 
                    messagesSent, bytesProduced, errors);
        }
    }
}
