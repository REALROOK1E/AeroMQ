package com.aeromq.util;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Single Producer Single Consumer Ring Buffer
 * High-performance lock-free circular buffer for message passing
 */
public class SPSCRingBuffer<T> {
    
    private final Object[] buffer;
    private final int capacity;
    private final int mask;
    
    // Padding to avoid false sharing
    private final PaddedAtomicLong head = new PaddedAtomicLong(0);
    private final PaddedAtomicLong tail = new PaddedAtomicLong(0);
    
    // Cache for reducing volatile reads
    private long cachedHead = 0;
    private long cachedTail = 0;
    
    public SPSCRingBuffer(int capacity) {
        if (capacity <= 0 || (capacity & (capacity - 1)) != 0) {
            throw new IllegalArgumentException("Capacity must be power of 2");
        }
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.buffer = new Object[capacity];
    }
    
    /**
     * Producer: offer element to the buffer
     * @param element element to add
     * @return true if successful, false if buffer is full
     */
    @SuppressWarnings("unchecked")
    public boolean offer(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Element cannot be null");
        }
        
        final long currentTail = tail.get();
        final long nextTail = currentTail + 1;
        
        // Check if buffer is full
        if (nextTail - cachedHead > capacity) {
            cachedHead = head.get();
            if (nextTail - cachedHead > capacity) {
                return false; // Buffer is full
            }
        }
        
        // Store element
        final int index = (int) (currentTail & mask);
        buffer[index] = element;
        
        // Update tail (release semantic)
        tail.lazySet(nextTail);
        
        return true;
    }
    
    /**
     * Consumer: poll element from the buffer
     * @return element or null if buffer is empty
     */
    @SuppressWarnings("unchecked")
    public T poll() {
        final long currentHead = head.get();
        
        // Check if buffer is empty
        if (currentHead >= cachedTail) {
            cachedTail = tail.get();
            if (currentHead >= cachedTail) {
                return null; // Buffer is empty
            }
        }
        
        // Load element
        final int index = (int) (currentHead & mask);
        T element = (T) buffer[index];
        buffer[index] = null; // Help GC
        
        // Update head (release semantic)
        head.lazySet(currentHead + 1);
        
        return element;
    }
    
    /**
     * Consumer: blocking poll with timeout
     * @param timeoutNanos timeout in nanoseconds
     * @return element or null if timeout
     */
    public T poll(long timeoutNanos) {
        final long deadline = System.nanoTime() + timeoutNanos;
        
        while (System.nanoTime() < deadline) {
            T element = poll();
            if (element != null) {
                return element;
            }
            
            // Use LockSupport for efficient waiting
            LockSupport.parkNanos(1000); // Park for 1 microsecond
        }
        
        return null;
    }
    
    /**
     * Consumer: drain multiple elements to a consumer function
     * @param consumer function to process each element
     * @param limit maximum number of elements to drain
     * @return actual number of elements drained
     */
    public int drainTo(java.util.function.Consumer<T> consumer, int limit) {
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer cannot be null");
        }
        
        int drained = 0;
        while (drained < limit) {
            T element = poll();
            if (element == null) {
                break; // Buffer is empty
            }
            
            consumer.accept(element);
            drained++;
        }
        
        return drained;
    }
    
    /**
     * Get current size (approximate)
     */
    public int size() {
        return (int) Math.max(0, tail.get() - head.get());
    }
    
    /**
     * Check if buffer is empty
     */
    public boolean isEmpty() {
        return head.get() >= tail.get();
    }
    
    /**
     * Check if buffer is full
     */
    public boolean isFull() {
        return tail.get() - head.get() >= capacity;
    }
    
    /**
     * Get buffer capacity
     */
    public int capacity() {
        return capacity;
    }
    
    /**
     * Padded AtomicLong to prevent false sharing
     */
    private static class PaddedAtomicLong extends AtomicLong {
        // Padding to prevent false sharing (cache line is typically 64 bytes)
        @SuppressWarnings("unused")
        private long p1, p2, p3, p4, p5, p6, p7;
        
        public PaddedAtomicLong(long initialValue) {
            super(initialValue);
        }
    }
}
