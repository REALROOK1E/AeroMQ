package com.aeromq.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

/**
 * Message model representing a message in AeroMQ
 */
public class Message {
    
    private final String id;
    private final String queueName;
    private final byte[] payload;
    private final Map<String, String> headers;
    private final long timestamp;
    private final String type;
    private final int priority;
    private final MessageStatus status;
    private final long expiryTime;
    
    @JsonCreator
    public Message(
            @JsonProperty("id") String id,
            @JsonProperty("queueName") String queueName,
            @JsonProperty("payload") byte[] payload,
            @JsonProperty("headers") Map<String, String> headers,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("type") String type,
            @JsonProperty("priority") int priority,
            @JsonProperty("status") MessageStatus status,
            @JsonProperty("expiryTime") long expiryTime) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.queueName = queueName;
        this.payload = payload;
        this.headers = headers;
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
        this.type = type;
        this.priority = priority;
        this.status = status != null ? status : MessageStatus.PENDING;
        this.expiryTime = expiryTime;
    }
    
    /**
     * Builder for convenient message creation
     */
    public static class Builder {
        private String id;
        private String queueName;
        private byte[] payload;
        private Map<String, String> headers;
        private long timestamp;
        private String type;
        private int priority = 0;
        private MessageStatus status = MessageStatus.PENDING;
        private long expiryTime = 0;
        
        public Builder id(String id) {
            this.id = id;
            return this;
        }
        
        public Builder queueName(String queueName) {
            this.queueName = queueName;
            return this;
        }
        
        public Builder payload(byte[] payload) {
            this.payload = payload;
            return this;
        }
        
        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder type(String type) {
            this.type = type;
            return this;
        }
        
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }
        
        public Builder status(MessageStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder expiryTime(long expiryTime) {
            this.expiryTime = expiryTime;
            return this;
        }
        
        public Message build() {
            return new Message(id, queueName, payload, headers, timestamp, 
                    type, priority, status, expiryTime);
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Check if message has expired
     */
    public boolean isExpired() {
        return expiryTime > 0 && System.currentTimeMillis() > expiryTime;
    }
    
    /**
     * Get message size in bytes
     */
    public int getSize() {
        int size = payload != null ? payload.length : 0;
        
        // Add estimated header size
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                size += header.getKey().length() + header.getValue().length();
            }
        }
        
        return size;
    }
    
    /**
     * Create a copy of this message with updated status
     */
    public Message withStatus(MessageStatus newStatus) {
        return new Message(id, queueName, payload, headers, timestamp, 
                type, priority, newStatus, expiryTime);
    }
    
    // Getters
    public String getId() { return id; }
    public String getQueueName() { return queueName; }
    public byte[] getPayload() { return payload; }
    public Map<String, String> getHeaders() { return headers; }
    public long getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public int getPriority() { return priority; }
    public MessageStatus getStatus() { return status; }
    public long getExpiryTime() { return expiryTime; }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Message message = (Message) obj;
        return id.equals(message.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("Message{id='%s', queue='%s', type='%s', priority=%d, status=%s}", 
                id, queueName, type, priority, status);
    }
}
