package com.aeromq.broker;

import com.aeromq.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of MessageStore
 * Provides fast access but no persistence across restarts
 */
public class InMemoryMessageStore implements MessageStore {
    
    private static final Logger logger = LoggerFactory.getLogger(InMemoryMessageStore.class);
    
    // Message storage by ID
    private final Map<String, Message> messageById = new ConcurrentHashMap<>();
    
    // Queue storage
    private final Map<String, Queue<Message>> queueMessages = new ConcurrentHashMap<>();
    
    // Queue metadata
    private final Map<String, AtomicLong> queueSizes = new ConcurrentHashMap<>();
    
    @Override
    public void initialize() throws Exception {
        logger.info("Initializing InMemoryMessageStore");
        // No initialization needed for in-memory store
    }
    
    @Override
    public CompletableFuture<Void> store(Message message) {
        return CompletableFuture.runAsync(() -> {
            // Store message by ID
            messageById.put(message.getId(), message);
            
            // Add to queue
            String queueName = message.getQueueName();
            queueMessages.computeIfAbsent(queueName, k -> new ConcurrentLinkedQueue<>())
                    .offer(message);
            
            // Update queue size
            queueSizes.computeIfAbsent(queueName, k -> new AtomicLong(0))
                    .incrementAndGet();
            
            logger.debug("Stored message {} in queue {}", message.getId(), queueName);
        });
    }
    
    @Override
    public CompletableFuture<Message> getMessage(String messageId) {
        return CompletableFuture.supplyAsync(() -> {
            Message message = messageById.get(messageId);
            logger.debug("Retrieved message {}: {}", messageId, message != null ? "found" : "not found");
            return message;
        });
    }
    
    @Override
    public CompletableFuture<List<Message>> getMessages(String queueName, long offset, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Queue<Message> queue = queueMessages.get(queueName);
            if (queue == null) {
                return Collections.emptyList();
            }
            
            List<Message> messages = new ArrayList<>();
            List<Message> queueList = new ArrayList<>(queue);
            
            int start = (int) Math.min(offset, queueList.size());
            int end = Math.min(start + limit, queueList.size());
            
            for (int i = start; i < end; i++) {
                messages.add(queueList.get(i));
            }
            
            logger.debug("Retrieved {} messages from queue {} (offset: {}, limit: {})", 
                    messages.size(), queueName, offset, limit);
            return messages;
        });
    }
    
    @Override
    public CompletableFuture<Void> deleteMessage(String messageId) {
        return CompletableFuture.runAsync(() -> {
            Message message = messageById.remove(messageId);
            if (message != null) {
                // Remove from queue
                String queueName = message.getQueueName();
                Queue<Message> queue = queueMessages.get(queueName);
                if (queue != null) {
                    queue.remove(message);
                    queueSizes.get(queueName).decrementAndGet();
                }
                logger.debug("Deleted message {} from queue {}", messageId, queueName);
            }
        });
    }
    
    @Override
    public CompletableFuture<Long> getQueueSize(String queueName) {
        return CompletableFuture.supplyAsync(() -> {
            AtomicLong size = queueSizes.get(queueName);
            return size != null ? size.get() : 0L;
        });
    }
    
    @Override
    public CompletableFuture<Void> createQueue(String queueName) {
        return CompletableFuture.runAsync(() -> {
            queueMessages.putIfAbsent(queueName, new ConcurrentLinkedQueue<>());
            queueSizes.putIfAbsent(queueName, new AtomicLong(0));
            logger.info("Created queue: {}", queueName);
        });
    }
    
    @Override
    public CompletableFuture<Void> deleteQueue(String queueName) {
        return CompletableFuture.runAsync(() -> {
            Queue<Message> queue = queueMessages.remove(queueName);
            queueSizes.remove(queueName);
            
            if (queue != null) {
                // Remove all messages from the message store
                for (Message message : queue) {
                    messageById.remove(message.getId());
                }
                logger.info("Deleted queue: {} (removed {} messages)", queueName, queue.size());
            }
        });
    }
    
    @Override
    public CompletableFuture<List<String>> listQueues() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> queues = new ArrayList<>(queueMessages.keySet());
            logger.debug("Listed {} queues", queues.size());
            return queues;
        });
    }
    
    @Override
    public void shutdown() throws Exception {
        logger.info("Shutting down InMemoryMessageStore");
        messageById.clear();
        queueMessages.clear();
        queueSizes.clear();
    }
    
    /**
     * Get statistics for monitoring
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", messageById.size());
        stats.put("totalQueues", queueMessages.size());
        
        Map<String, Long> queueStats = new HashMap<>();
        for (Map.Entry<String, AtomicLong> entry : queueSizes.entrySet()) {
            queueStats.put(entry.getKey(), entry.getValue().get());
        }
        stats.put("queueSizes", queueStats);
        
        return stats;
    }
}
