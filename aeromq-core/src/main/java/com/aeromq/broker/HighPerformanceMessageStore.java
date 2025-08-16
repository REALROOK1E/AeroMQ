package com.aeromq.broker;

import com.aeromq.model.Message;
import com.aeromq.util.SPSCRingBuffer;
import com.aeromq.util.OffHeapMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * High-performance message store with SPSC ring buffers and off-heap storage
 */
public class HighPerformanceMessageStore implements MessageStore {
    
    private static final Logger logger = LoggerFactory.getLogger(HighPerformanceMessageStore.class);
    
    private static final int RING_BUFFER_SIZE = 8192; // Must be power of 2
    private static final int SHARD_COUNT = Runtime.getRuntime().availableProcessors();
    
    // Message storage by ID (on-heap for metadata, off-heap for payload)
    private final ConcurrentHashMap<String, MessageMetadata> messageMetadata = new ConcurrentHashMap<>();
    
    // Sharded queue storage with SPSC ring buffers
    private final QueueShard[] queueShards;
    private final ExecutorService shardWorkers;
    
    // Off-heap memory manager
    private final OffHeapMemoryManager memoryManager;
    
    // Queue metadata
    private final ConcurrentHashMap<String, AtomicLong> queueSizes = new ConcurrentHashMap<>();
    
    private volatile boolean running = false;
    
    public HighPerformanceMessageStore() {
        this.memoryManager = OffHeapMemoryManager.getInstance();
        this.queueShards = new QueueShard[SHARD_COUNT];
        this.shardWorkers = Executors.newFixedThreadPool(SHARD_COUNT, 
                r -> new Thread(r, "MessageStore-Shard-Worker"));
        
        // Initialize shards
        for (int i = 0; i < SHARD_COUNT; i++) {
            queueShards[i] = new QueueShard(i);
        }
    }
    
    @Override
    public void initialize() throws Exception {
        logger.info("Initializing HighPerformanceMessageStore with {} shards", SHARD_COUNT);
        running = true;
        
        // Start shard workers
        for (QueueShard shard : queueShards) {
            shardWorkers.submit(shard);
        }
        
        logger.info("HighPerformanceMessageStore initialized successfully");
    }
    
    @Override
    public CompletableFuture<Void> store(Message message) {
        if (!running) {
            return CompletableFuture.failedFuture(new IllegalStateException("Store is not running"));
        }
        
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        try {
            // Store payload in off-heap memory
            OffHeapMemoryManager.ManagedBuffer buffer = null;
            if (message.getPayload() != null) {
                buffer = memoryManager.allocate(message.getPayload().length);
                buffer.put(message.getPayload());
            }
            
            // Create metadata
            MessageMetadata metadata = new MessageMetadata(message, buffer);
            messageMetadata.put(message.getId(), metadata);
            
            // Route to appropriate shard
            int shardIndex = getShardIndex(message.getQueueName());
            QueueShard shard = queueShards[shardIndex];
            
            StoreOperation operation = new StoreOperation(StoreOperationType.STORE, message, future);
            
            if (!shard.submitOperation(operation)) {
                // Ring buffer is full, complete with backpressure exception
                if (buffer != null) {
                    buffer.close();
                }
                messageMetadata.remove(message.getId());
                future.completeExceptionally(new IllegalStateException("Store backpressure - ring buffer full"));
            }
            
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    @Override
    public CompletableFuture<Message> getMessage(String messageId) {
        CompletableFuture<Message> future = new CompletableFuture<>();
        
        MessageMetadata metadata = messageMetadata.get(messageId);
        if (metadata == null) {
            future.complete(null);
            return future;
        }
        
        try {
            Message message = metadata.toMessage();
            future.complete(message);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    @Override
    public CompletableFuture<List<Message>> getMessages(String queueName, long offset, int limit) {
        CompletableFuture<List<Message>> future = new CompletableFuture<>();
        
        int shardIndex = getShardIndex(queueName);
        QueueShard shard = queueShards[shardIndex];
        
        GetMessagesOperation operation = new GetMessagesOperation(queueName, offset, limit, future);
        StoreOperation storeOp = new StoreOperation(StoreOperationType.GET_MESSAGES, null, null);
        storeOp.getMessagesOperation = operation;
        
        if (!shard.submitOperation(storeOp)) {
            future.completeExceptionally(new IllegalStateException("Store backpressure"));
        }
        
        return future;
    }
    
    @Override
    public CompletableFuture<Void> deleteMessage(String messageId) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        MessageMetadata metadata = messageMetadata.remove(messageId);
        if (metadata != null) {
            // Close off-heap buffer
            if (metadata.buffer != null) {
                metadata.buffer.close();
            }
            
            // Route to appropriate shard for queue cleanup
            int shardIndex = getShardIndex(metadata.queueName);
            QueueShard shard = queueShards[shardIndex];
            
            Message message = new Message.Builder()
                    .id(messageId)
                    .queueName(metadata.queueName)
                    .build();
                    
            StoreOperation operation = new StoreOperation(StoreOperationType.DELETE, message, future);
            
            if (!shard.submitOperation(operation)) {
                future.completeExceptionally(new IllegalStateException("Store backpressure"));
            }
        } else {
            future.complete(null);
        }
        
        return future;
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
            queueSizes.putIfAbsent(queueName, new AtomicLong(0));
            
            // Initialize queue in all shards
            for (QueueShard shard : queueShards) {
                shard.ensureQueueExists(queueName);
            }
            
            logger.info("Created queue: {}", queueName);
        });
    }
    
    @Override
    public CompletableFuture<Void> deleteQueue(String queueName) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        // Submit delete operations to all shards
        CompletableFuture<Void>[] futures = new CompletableFuture[SHARD_COUNT];
        
        for (int i = 0; i < SHARD_COUNT; i++) {
            futures[i] = new CompletableFuture<>();
            StoreOperation operation = new StoreOperation(StoreOperationType.DELETE_QUEUE, null, futures[i]);
            operation.queueName = queueName;
            
            if (!queueShards[i].submitOperation(operation)) {
                futures[i].completeExceptionally(new IllegalStateException("Store backpressure"));
            }
        }
        
        CompletableFuture.allOf(futures).whenComplete((result, throwable) -> {
            if (throwable == null) {
                queueSizes.remove(queueName);
                logger.info("Deleted queue: {}", queueName);
                future.complete(null);
            } else {
                future.completeExceptionally(throwable);
            }
        });
        
        return future;
    }
    
    @Override
    public CompletableFuture<List<String>> listQueues() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> queues = new ArrayList<>(queueSizes.keySet());
            logger.debug("Listed {} queues", queues.size());
            return queues;
        });
    }
    
    @Override
    public void shutdown() throws Exception {
        logger.info("Shutting down HighPerformanceMessageStore");
        running = false;
        
        // Stop shard workers
        for (QueueShard shard : queueShards) {
            shard.shutdown();
        }
        
        shardWorkers.shutdown();
        if (!shardWorkers.awaitTermination(10, TimeUnit.SECONDS)) {
            shardWorkers.shutdownNow();
        }
        
        // Clean up off-heap memory
        for (MessageMetadata metadata : messageMetadata.values()) {
            if (metadata.buffer != null) {
                metadata.buffer.close();
            }
        }
        messageMetadata.clear();
        queueSizes.clear();
        
        logger.info("HighPerformanceMessageStore shutdown complete");
    }
    
    /**
     * Get shard index for a queue name
     */
    private int getShardIndex(String queueName) {
        return Math.abs(queueName.hashCode()) % SHARD_COUNT;
    }
    
    /**
     * Get statistics including memory usage
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMessages", messageMetadata.size());
        stats.put("totalQueues", queueSizes.size());
        stats.put("shardCount", SHARD_COUNT);
        
        // Shard statistics
        Map<String, Object> shardStats = new HashMap<>();
        for (int i = 0; i < SHARD_COUNT; i++) {
            shardStats.put("shard-" + i, queueShards[i].getStats());
        }
        stats.put("shards", shardStats);
        
        // Memory statistics
        stats.put("memoryStats", memoryManager.getStats());
        
        return stats;
    }
    
    /**
     * Message metadata stored on-heap
     */
    private static class MessageMetadata {
        final String id;
        final String queueName;
        final Map<String, String> headers;
        final long timestamp;
        final String type;
        final int priority;
        final OffHeapMemoryManager.ManagedBuffer buffer; // Off-heap payload
        
        MessageMetadata(Message message, OffHeapMemoryManager.ManagedBuffer buffer) {
            this.id = message.getId();
            this.queueName = message.getQueueName();
            this.headers = message.getHeaders();
            this.timestamp = message.getTimestamp();
            this.type = message.getType();
            this.priority = message.getPriority();
            this.buffer = buffer;
        }
        
        Message toMessage() {
            byte[] payload = buffer != null ? buffer.get() : null;
            return new Message.Builder()
                    .id(id)
                    .queueName(queueName)
                    .payload(payload)
                    .headers(headers)
                    .timestamp(timestamp)
                    .type(type)
                    .priority(priority)
                    .build();
        }
    }
    
    /**
     * Queue shard with SPSC ring buffer and wait/notify
     */
    private class QueueShard implements Runnable {
        private final int shardId;
        private final SPSCRingBuffer<StoreOperation> operationBuffer;
        private final Map<String, LinkedList<String>> queueMessages; // queue -> messageIds
        
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition hasWork = lock.newCondition();
        
        private volatile boolean running = true;
        private final AtomicLong processedOperations = new AtomicLong(0);
        
        QueueShard(int shardId) {
            this.shardId = shardId;
            this.operationBuffer = new SPSCRingBuffer<>(RING_BUFFER_SIZE);
            this.queueMessages = new HashMap<>();
        }
        
        boolean submitOperation(StoreOperation operation) {
            boolean offered = operationBuffer.offer(operation);
            if (offered) {
                // Signal waiting worker
                lock.lock();
                try {
                    hasWork.signal();
                } finally {
                    lock.unlock();
                }
            }
            return offered;
        }
        
        void ensureQueueExists(String queueName) {
            // This will be handled by the worker thread
        }
        
        @Override
        public void run() {
            Thread.currentThread().setName("MessageStore-Shard-" + shardId);
            
            while (running) {
                StoreOperation operation = operationBuffer.poll();
                
                if (operation != null) {
                    processOperation(operation);
                    processedOperations.incrementAndGet();
                } else {
                    // Wait for work using condition variable
                    lock.lock();
                    try {
                        if (operationBuffer.isEmpty() && running) {
                            hasWork.await(1, TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }
        
        private void processOperation(StoreOperation operation) {
            try {
                switch (operation.type) {
                    case STORE:
                        handleStore(operation);
                        break;
                    case DELETE:
                        handleDelete(operation);
                        break;
                    case GET_MESSAGES:
                        handleGetMessages(operation);
                        break;
                    case DELETE_QUEUE:
                        handleDeleteQueue(operation);
                        break;
                }
            } catch (Exception e) {
                if (operation.future != null) {
                    operation.future.completeExceptionally(e);
                }
                logger.error("Error processing operation in shard {}", shardId, e);
            }
        }
        
        private void handleStore(StoreOperation operation) {
            Message message = operation.message;
            String queueName = message.getQueueName();
            
            // Add to queue
            queueMessages.computeIfAbsent(queueName, k -> new LinkedList<>())
                    .offer(message.getId());
            
            // Update queue size
            queueSizes.computeIfAbsent(queueName, k -> new AtomicLong(0))
                    .incrementAndGet();
            
            operation.future.complete(null);
        }
        
        private void handleDelete(StoreOperation operation) {
            Message message = operation.message;
            String queueName = message.getQueueName();
            
            LinkedList<String> queue = queueMessages.get(queueName);
            if (queue != null) {
                queue.remove(message.getId());
                queueSizes.get(queueName).decrementAndGet();
            }
            
            operation.future.complete(null);
        }
        
        private void handleGetMessages(StoreOperation operation) {
            GetMessagesOperation getOp = operation.getMessagesOperation;
            String queueName = getOp.queueName;
            
            LinkedList<String> queue = queueMessages.get(queueName);
            List<Message> result = new ArrayList<>();
            
            if (queue != null) {
                int skipped = 0;
                int collected = 0;
                
                for (String messageId : queue) {
                    if (skipped < getOp.offset) {
                        skipped++;
                        continue;
                    }
                    
                    if (collected >= getOp.limit) {
                        break;
                    }
                    
                    MessageMetadata metadata = messageMetadata.get(messageId);
                    if (metadata != null) {
                        result.add(metadata.toMessage());
                        collected++;
                    }
                }
            }
            
            getOp.future.complete(result);
        }
        
        private void handleDeleteQueue(StoreOperation operation) {
            String queueName = operation.queueName;
            
            LinkedList<String> queue = queueMessages.remove(queueName);
            if (queue != null) {
                // Clean up message metadata for this shard's messages
                for (String messageId : queue) {
                    MessageMetadata metadata = messageMetadata.remove(messageId);
                    if (metadata != null && metadata.buffer != null) {
                        metadata.buffer.close();
                    }
                }
            }
            
            operation.future.complete(null);
        }
        
        void shutdown() {
            running = false;
            lock.lock();
            try {
                hasWork.signalAll();
            } finally {
                lock.unlock();
            }
        }
        
        Map<String, Object> getStats() {
            Map<String, Object> stats = new HashMap<>();
            stats.put("processedOperations", processedOperations.get());
            stats.put("queueCount", queueMessages.size());
            stats.put("bufferSize", operationBuffer.size());
            return stats;
        }
    }
    
    /**
     * Store operation types
     */
    enum StoreOperationType {
        STORE, DELETE, GET_MESSAGES, DELETE_QUEUE
    }
    
    /**
     * Store operation wrapper
     */
    private static class StoreOperation {
        final StoreOperationType type;
        final Message message;
        final CompletableFuture<Void> future;
        
        // Additional fields for specific operations
        GetMessagesOperation getMessagesOperation;
        String queueName;
        
        StoreOperation(StoreOperationType type, Message message, CompletableFuture<Void> future) {
            this.type = type;
            this.message = message;
            this.future = future;
        }
    }
    
    /**
     * Get messages operation wrapper
     */
    private static class GetMessagesOperation {
        final String queueName;
        final long offset;
        final int limit;
        final CompletableFuture<List<Message>> future;
        
        GetMessagesOperation(String queueName, long offset, int limit, CompletableFuture<List<Message>> future) {
            this.queueName = queueName;
            this.offset = offset;
            this.limit = limit;
            this.future = future;
        }
    }
}
