package com.aeromq.broker;

import com.aeromq.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Multi-dimensional index manager for fast message lookups
 * Supports indexing by various message attributes
 */
public class IndexManager {
    
    private static final Logger logger = LoggerFactory.getLogger(IndexManager.class);
    
    // Primary index: messageId -> Message
    private final Map<String, Message> primaryIndex = new ConcurrentHashMap<>();
    
    // Secondary indexes
    private final Map<String, Set<String>> queueIndex = new ConcurrentHashMap<>(); // queue -> messageIds
    private final Map<Long, Set<String>> timestampIndex = new ConcurrentHashMap<>(); // timestamp -> messageIds
    private final Map<String, Set<String>> typeIndex = new ConcurrentHashMap<>(); // messageType -> messageIds
    private final Map<String, Set<String>> priorityIndex = new ConcurrentHashMap<>(); // priority -> messageIds
    
    // Custom attribute indexes
    private final Map<String, Map<String, Set<String>>> customIndexes = new ConcurrentHashMap<>();
    
    public void initialize() throws Exception {
        logger.info("Initializing IndexManager");
        // Initialize indexes if needed
    }
    
    /**
     * Add a message to all relevant indexes
     */
    public void addMessage(Message message) {
        String messageId = message.getId();
        
        // Add to primary index
        primaryIndex.put(messageId, message);
        
        // Add to queue index
        addToIndex(queueIndex, message.getQueueName(), messageId);
        
        // Add to timestamp index (rounded to seconds for grouping)
        long timestampSecond = message.getTimestamp() / 1000;
        addToIndex(timestampIndex, timestampSecond, messageId);
        
        // Add to type index
        if (message.getType() != null) {
            addToIndex(typeIndex, message.getType(), messageId);
        }
        
        // Add to priority index
        addToIndex(priorityIndex, String.valueOf(message.getPriority()), messageId);
        
        // Add to custom attribute indexes
        if (message.getHeaders() != null) {
            for (Map.Entry<String, String> header : message.getHeaders().entrySet()) {
                String attributeName = header.getKey();
                String attributeValue = header.getValue();
                
                customIndexes.computeIfAbsent(attributeName, k -> new ConcurrentHashMap<>());
                addToIndex(customIndexes.get(attributeName), attributeValue, messageId);
            }
        }
        
        logger.debug("Added message {} to indexes", messageId);
    }
    
    /**
     * Remove a message from all indexes
     */
    public void removeMessage(Message message) {
        String messageId = message.getId();
        
        // Remove from primary index
        primaryIndex.remove(messageId);
        
        // Remove from queue index
        removeFromIndex(queueIndex, message.getQueueName(), messageId);
        
        // Remove from timestamp index
        long timestampSecond = message.getTimestamp() / 1000;
        removeFromIndex(timestampIndex, timestampSecond, messageId);
        
        // Remove from type index
        if (message.getType() != null) {
            removeFromIndex(typeIndex, message.getType(), messageId);
        }
        
        // Remove from priority index
        removeFromIndex(priorityIndex, String.valueOf(message.getPriority()), messageId);
        
        // Remove from custom attribute indexes
        if (message.getHeaders() != null) {
            for (Map.Entry<String, String> header : message.getHeaders().entrySet()) {
                String attributeName = header.getKey();
                String attributeValue = header.getValue();
                
                Map<String, Set<String>> attributeIndex = customIndexes.get(attributeName);
                if (attributeIndex != null) {
                    removeFromIndex(attributeIndex, attributeValue, messageId);
                }
            }
        }
        
        logger.debug("Removed message {} from indexes", messageId);
    }
    
    /**
     * Find messages by queue name
     */
    public Set<String> findByQueue(String queueName) {
        Set<String> messageIds = queueIndex.get(queueName);
        return messageIds != null ? new HashSet<>(messageIds) : Collections.emptySet();
    }
    
    /**
     * Find messages by timestamp range
     */
    public Set<String> findByTimestampRange(long startTime, long endTime) {
        Set<String> result = new HashSet<>();
        
        long startSecond = startTime / 1000;
        long endSecond = endTime / 1000;
        
        for (long second = startSecond; second <= endSecond; second++) {
            Set<String> messageIds = timestampIndex.get(second);
            if (messageIds != null) {
                result.addAll(messageIds);
            }
        }
        
        return result;
    }
    
    /**
     * Find messages by type
     */
    public Set<String> findByType(String messageType) {
        Set<String> messageIds = typeIndex.get(messageType);
        return messageIds != null ? new HashSet<>(messageIds) : Collections.emptySet();
    }
    
    /**
     * Find messages by priority
     */
    public Set<String> findByPriority(int priority) {
        Set<String> messageIds = priorityIndex.get(String.valueOf(priority));
        return messageIds != null ? new HashSet<>(messageIds) : Collections.emptySet();
    }
    
    /**
     * Find messages by custom attribute
     */
    public Set<String> findByCustomAttribute(String attributeName, String attributeValue) {
        Map<String, Set<String>> attributeIndex = customIndexes.get(attributeName);
        if (attributeIndex != null) {
            Set<String> messageIds = attributeIndex.get(attributeValue);
            return messageIds != null ? new HashSet<>(messageIds) : Collections.emptySet();
        }
        return Collections.emptySet();
    }
    
    /**
     * Complex query with multiple criteria
     */
    public Set<String> findByCriteria(Map<String, Object> criteria) {
        Set<String> result = null;
        
        for (Map.Entry<String, Object> criterion : criteria.entrySet()) {
            String field = criterion.getKey();
            Object value = criterion.getValue();
            
            Set<String> currentResult = Collections.emptySet();
            
            switch (field) {
                case "queue":
                    currentResult = findByQueue(value.toString());
                    break;
                case "type":
                    currentResult = findByType(value.toString());
                    break;
                case "priority":
                    currentResult = findByPriority(Integer.parseInt(value.toString()));
                    break;
                default:
                    // Custom attribute
                    currentResult = findByCustomAttribute(field, value.toString());
                    break;
            }
            
            if (result == null) {
                result = new HashSet<>(currentResult);
            } else {
                result.retainAll(currentResult); // Intersection
            }
            
            if (result.isEmpty()) {
                break; // No point continuing if no matches
            }
        }
        
        return result != null ? result : Collections.emptySet();
    }
    
    /**
     * Get message by ID
     */
    public Message getMessage(String messageId) {
        return primaryIndex.get(messageId);
    }
    
    /**
     * Get index statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", primaryIndex.size());
        stats.put("queueCount", queueIndex.size());
        stats.put("typeCount", typeIndex.size());
        stats.put("customIndexCount", customIndexes.size());
        
        return stats;
    }
    
    /**
     * Helper method to add to index
     */
    private <K> void addToIndex(Map<K, Set<String>> index, K key, String messageId) {
        index.computeIfAbsent(key, k -> new ConcurrentSkipListSet<>()).add(messageId);
    }
    
    /**
     * Helper method to remove from index
     */
    private <K> void removeFromIndex(Map<K, Set<String>> index, K key, String messageId) {
        Set<String> messageIds = index.get(key);
        if (messageIds != null) {
            messageIds.remove(messageId);
            if (messageIds.isEmpty()) {
                index.remove(key);
            }
        }
    }
    
    public void shutdown() throws Exception {
        logger.info("Shutting down IndexManager");
        primaryIndex.clear();
        queueIndex.clear();
        timestampIndex.clear();
        typeIndex.clear();
        priorityIndex.clear();
        customIndexes.clear();
    }
}
