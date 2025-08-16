package com.aeromq.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Off-heap memory manager for message payloads
 * Reduces GC pressure by storing large payloads in direct memory
 */
public class OffHeapMemoryManager {
    
    private static final Logger logger = LoggerFactory.getLogger(OffHeapMemoryManager.class);
    
    // Pool sizes for different buffer sizes
    private static final int SMALL_BUFFER_SIZE = 1024;      // 1KB
    private static final int MEDIUM_BUFFER_SIZE = 8192;     // 8KB
    private static final int LARGE_BUFFER_SIZE = 65536;     // 64KB
    
    private static final int POOL_SIZE = 1000;
    
    // Buffer pools
    private final ConcurrentLinkedQueue<ByteBuffer> smallBufferPool;
    private final ConcurrentLinkedQueue<ByteBuffer> mediumBufferPool;
    private final ConcurrentLinkedQueue<ByteBuffer> largeBufferPool;
    
    // Statistics
    private final AtomicLong allocatedBuffers = new AtomicLong(0);
    private final AtomicLong releasedBuffers = new AtomicLong(0);
    private final AtomicLong totalAllocatedMemory = new AtomicLong(0);
    
    private static final OffHeapMemoryManager INSTANCE = new OffHeapMemoryManager();
    
    private OffHeapMemoryManager() {
        this.smallBufferPool = new ConcurrentLinkedQueue<>();
        this.mediumBufferPool = new ConcurrentLinkedQueue<>();
        this.largeBufferPool = new ConcurrentLinkedQueue<>();
        
        // Pre-allocate some buffers
        preAllocateBuffers();
        
        logger.info("OffHeapMemoryManager initialized with buffer pools");
    }
    
    public static OffHeapMemoryManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Allocate a buffer for the given size
     */
    public ManagedBuffer allocate(int size) {
        ByteBuffer buffer = null;
        BufferType type;
        
        if (size <= SMALL_BUFFER_SIZE) {
            buffer = smallBufferPool.poll();
            type = BufferType.SMALL;
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(SMALL_BUFFER_SIZE);
            }
        } else if (size <= MEDIUM_BUFFER_SIZE) {
            buffer = mediumBufferPool.poll();
            type = BufferType.MEDIUM;
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(MEDIUM_BUFFER_SIZE);
            }
        } else if (size <= LARGE_BUFFER_SIZE) {
            buffer = largeBufferPool.poll();
            type = BufferType.LARGE;
            if (buffer == null) {
                buffer = ByteBuffer.allocateDirect(LARGE_BUFFER_SIZE);
            }
        } else {
            // For very large buffers, allocate directly without pooling
            buffer = ByteBuffer.allocateDirect(size);
            type = BufferType.HUGE;
        }
        
        buffer.clear();
        buffer.limit(size);
        
        allocatedBuffers.incrementAndGet();
        totalAllocatedMemory.addAndGet(buffer.capacity());
        
        return new ManagedBuffer(buffer, type, this);
    }
    
    /**
     * Release a buffer back to the pool
     */
    void release(ManagedBuffer managedBuffer) {
        ByteBuffer buffer = managedBuffer.getBuffer();
        BufferType type = managedBuffer.getType();
        
        buffer.clear();
        
        // Return to appropriate pool if not huge
        switch (type) {
            case SMALL:
                if (smallBufferPool.size() < POOL_SIZE) {
                    smallBufferPool.offer(buffer);
                }
                break;
            case MEDIUM:
                if (mediumBufferPool.size() < POOL_SIZE) {
                    mediumBufferPool.offer(buffer);
                }
                break;
            case LARGE:
                if (largeBufferPool.size() < POOL_SIZE) {
                    largeBufferPool.offer(buffer);
                }
                break;
            case HUGE:
                // Don't pool huge buffers, just let them be GC'd
                break;
        }
        
        releasedBuffers.incrementAndGet();
        totalAllocatedMemory.addAndGet(-buffer.capacity());
    }
    
    /**
     * Pre-allocate buffers for the pools
     */
    private void preAllocateBuffers() {
        for (int i = 0; i < POOL_SIZE / 4; i++) {
            smallBufferPool.offer(ByteBuffer.allocateDirect(SMALL_BUFFER_SIZE));
            mediumBufferPool.offer(ByteBuffer.allocateDirect(MEDIUM_BUFFER_SIZE));
            largeBufferPool.offer(ByteBuffer.allocateDirect(LARGE_BUFFER_SIZE));
        }
    }
    
    /**
     * Get memory statistics
     */
    public MemoryStats getStats() {
        return new MemoryStats(
                allocatedBuffers.get(),
                releasedBuffers.get(),
                totalAllocatedMemory.get(),
                smallBufferPool.size(),
                mediumBufferPool.size(),
                largeBufferPool.size()
        );
    }
    
    /**
     * Buffer type enumeration
     */
    enum BufferType {
        SMALL, MEDIUM, LARGE, HUGE
    }
    
    /**
     * Managed buffer wrapper with auto-release capability
     */
    public static class ManagedBuffer implements AutoCloseable {
        private final ByteBuffer buffer;
        private final BufferType type;
        private final OffHeapMemoryManager manager;
        private volatile boolean released = false;
        
        ManagedBuffer(ByteBuffer buffer, BufferType type, OffHeapMemoryManager manager) {
            this.buffer = buffer;
            this.type = type;
            this.manager = manager;
        }
        
        public ByteBuffer getBuffer() {
            if (released) {
                throw new IllegalStateException("Buffer has been released");
            }
            return buffer;
        }
        
        public BufferType getType() {
            return type;
        }
        
        public int capacity() {
            return buffer.capacity();
        }
        
        public int size() {
            return buffer.limit();
        }
        
        /**
         * Put data into the buffer
         */
        public void put(byte[] data) {
            if (data.length > buffer.capacity()) {
                throw new IllegalArgumentException("Data too large for buffer");
            }
            buffer.clear();
            buffer.put(data);
            buffer.flip();
        }
        
        /**
         * Get data from the buffer
         */
        public byte[] get() {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            buffer.rewind();
            return data;
        }
        
        @Override
        public void close() {
            if (!released) {
                released = true;
                manager.release(this);
            }
        }

    }
    
    /**
     * Memory statistics
     */
    public static class MemoryStats {
        private final long allocatedBuffers;
        private final long releasedBuffers;
        private final long totalAllocatedMemory;
        private final int smallPoolSize;
        private final int mediumPoolSize;
        private final int largePoolSize;
        
        public MemoryStats(long allocatedBuffers, long releasedBuffers, long totalAllocatedMemory,
                          int smallPoolSize, int mediumPoolSize, int largePoolSize) {
            this.allocatedBuffers = allocatedBuffers;
            this.releasedBuffers = releasedBuffers;
            this.totalAllocatedMemory = totalAllocatedMemory;
            this.smallPoolSize = smallPoolSize;
            this.mediumPoolSize = mediumPoolSize;
            this.largePoolSize = largePoolSize;
        }
        
        public long getAllocatedBuffers() { return allocatedBuffers; }
        public long getReleasedBuffers() { return releasedBuffers; }
        public long getActiveBuffers() { return allocatedBuffers - releasedBuffers; }
        public long getTotalAllocatedMemory() { return totalAllocatedMemory; }
        public int getSmallPoolSize() { return smallPoolSize; }
        public int getMediumPoolSize() { return mediumPoolSize; }
        public int getLargePoolSize() { return largePoolSize; }
        
        @Override
        public String toString() {
            return String.format("MemoryStats{active=%d, allocated=%d, released=%d, memory=%d bytes, pools=[%d,%d,%d]}",
                    getActiveBuffers(), allocatedBuffers, releasedBuffers, totalAllocatedMemory,
                    smallPoolSize, mediumPoolSize, largePoolSize);
        }
    }
}
