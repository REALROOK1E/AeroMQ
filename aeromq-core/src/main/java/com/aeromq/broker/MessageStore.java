package com.aeromq.broker;

import com.aeromq.model.Message;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Message storage interface
 * Defines the contract for message persistence
 */
public interface MessageStore {
    
    /**
     * Initialize the message store
     */
    void initialize() throws Exception;
    
    /**
     * Store a message
     * @param message the message to store
     * @return future that completes when message is stored
     */
    CompletableFuture<Void> store(Message message);
    
    /**
     * Retrieve a message by ID
     * @param messageId the message ID
     * @return the message, or null if not found
     */
    CompletableFuture<Message> getMessage(String messageId);
    
    /**
     * Get messages for a queue
     * @param queueName the queue name
     * @param offset starting offset
     * @param limit maximum number of messages
     * @return list of messages
     */
    CompletableFuture<List<Message>> getMessages(String queueName, long offset, int limit);
    
    /**
     * Delete a message
     * @param messageId the message ID
     * @return future that completes when message is deleted
     */
    CompletableFuture<Void> deleteMessage(String messageId);
    
    /**
     * Get queue size
     * @param queueName the queue name
     * @return number of messages in queue
     */
    CompletableFuture<Long> getQueueSize(String queueName);
    
    /**
     * Create a new queue
     * @param queueName the queue name
     * @return future that completes when queue is created
     */
    CompletableFuture<Void> createQueue(String queueName);
    
    /**
     * Delete a queue
     * @param queueName the queue name
     * @return future that completes when queue is deleted
     */
    CompletableFuture<Void> deleteQueue(String queueName);
    
    /**
     * List all queues
     * @return list of queue names
     */
    CompletableFuture<List<String>> listQueues();
    
    /**
     * Shutdown the message store
     */
    void shutdown() throws Exception;
}
