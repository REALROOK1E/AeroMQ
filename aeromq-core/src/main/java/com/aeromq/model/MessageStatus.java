package com.aeromq.model;

/**
 * Message status enumeration
 * Represents the current state of a message in the system
 */
public enum MessageStatus {
    
    /**
     * Message is pending delivery
     */
    PENDING,
    
    /**
     * Message has been delivered to a consumer
     */
    DELIVERED,
    
    /**
     * Message delivery has been acknowledged by consumer
     */
    ACKNOWLEDGED,
    
    /**
     * Message delivery was rejected by consumer
     */
    REJECTED,
    
    /**
     * Message has expired and will not be delivered
     */
    EXPIRED,
    
    /**
     * Message delivery failed and is being retried
     */
    RETRY,
    
    /**
     * Message is in dead letter queue after max retries
     */
    DEAD_LETTER,
    
    /**
     * Message is being processed
     */
    PROCESSING;
    
    /**
     * Check if message is in a final state (no further processing)
     */
    public boolean isFinal() {
        return this == ACKNOWLEDGED || 
               this == EXPIRED || 
               this == DEAD_LETTER;
    }
    
    /**
     * Check if message can be delivered
     */
    public boolean canBeDelivered() {
        return this == PENDING || this == RETRY;
    }
    
    /**
     * Check if message is being actively processed
     */
    public boolean isActive() {
        return this == DELIVERED || this == PROCESSING;
    }
    
    /**
     * Get next status for retry scenario
     */
    public MessageStatus getRetryStatus() {
        switch (this) {
            case PENDING:
            case RETRY:
            case DELIVERED:
            case REJECTED:
                return RETRY;
            default:
                return this; // No change for final states
        }
    }
    
    /**
     * Get status description
     */
    public String getDescription() {
        switch (this) {
            case PENDING:
                return "Message is waiting to be delivered";
            case DELIVERED:
                return "Message has been delivered to consumer";
            case ACKNOWLEDGED:
                return "Message processing acknowledged by consumer";
            case REJECTED:
                return "Message was rejected by consumer";
            case EXPIRED:
                return "Message has expired";
            case RETRY:
                return "Message is being retried after failure";
            case DEAD_LETTER:
                return "Message moved to dead letter queue";
            case PROCESSING:
                return "Message is currently being processed";
            default:
                return "Unknown status";
        }
    }
}
