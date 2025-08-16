package com.aeromq.client;

import com.aeromq.protocol.AeroProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance request manager supporting concurrent requests
 * Replaces ThreadLocal with global requestId -> CompletableFuture mapping
 */
public class RequestManager {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestManager.class);
    
    // Global request ID generator (per client connection)
    private final AtomicLong requestIdGenerator;
    
    // Concurrent mapping for pending requests
    private final ConcurrentHashMap<Long, PendingRequest> pendingRequests;
    
    // Timeout scheduler for request cleanup
    private final ScheduledExecutorService timeoutScheduler;
    
    // Configuration
    private final int maxPendingRequests;
    private final long defaultTimeoutMs;
    
    public RequestManager(int maxPendingRequests, long defaultTimeoutMs) {
        this.requestIdGenerator = new AtomicLong(0);
        this.pendingRequests = new ConcurrentHashMap<>(maxPendingRequests);
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "RequestManager-Timeout"));
        this.maxPendingRequests = maxPendingRequests;
        this.defaultTimeoutMs = defaultTimeoutMs;
    }
    
    /**
     * Create a new request with unique ID and timeout handling
     * @param timeoutMs timeout in milliseconds, -1 for default
     * @return RequestContext containing requestId and future
     */
    public <T> RequestContext<T> createRequest(long timeoutMs) {
        // Check capacity to prevent memory explosion
        if (pendingRequests.size() >= maxPendingRequests) {
            throw new IllegalStateException("Too many pending requests: " + pendingRequests.size() + 
                    " >= " + maxPendingRequests);
        }
        
        long requestId = requestIdGenerator.incrementAndGet();
         long actualTimeoutMs = timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;

        CompletableFuture<T> future = new CompletableFuture<>();
        PendingRequest pendingRequest = new PendingRequest(future, actualTimeoutMs);

        pendingRequests.put(requestId, pendingRequest);

        // Schedule timeout cleanup
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            PendingRequest removed = pendingRequests.remove(requestId);
            if (removed != null && !removed.future.isDone()) {
                removed.future.completeExceptionally(
                        new TimeoutException("Request timed out after " + actualTimeoutMs + "ms"));
                logger.debug("Request {} timed out after {}ms", requestId, actualTimeoutMs);
            }
        }, actualTimeoutMs, TimeUnit.MILLISECONDS);

        pendingRequest.timeoutTask = timeoutTask;

        return new RequestContext<>(requestId, future);
    }

    /**
     * Generate next request ID
     */
    public long nextRequestId() {
        return requestIdGenerator.incrementAndGet();
    }

    /**
     * Register a request with timeout
     */
    public CompletableFuture<AeroProtocol.ProtocolResponse> registerRequest(long requestId, int timeout, TimeUnit timeUnit) {
        // Check capacity to prevent memory explosion
        if (pendingRequests.size() >= maxPendingRequests) {
            throw new IllegalStateException("Too many pending requests: " + pendingRequests.size() +
                    " >= " + maxPendingRequests);
        }

        long timeoutMs = timeUnit.toMillis(timeout);

        CompletableFuture<AeroProtocol.ProtocolResponse> future = new CompletableFuture<>();
        PendingRequest pendingRequest = new PendingRequest(future, timeoutMs);

        pendingRequests.put(requestId, pendingRequest);
        
        // Schedule timeout cleanup
        ScheduledFuture<?> timeoutTask = timeoutScheduler.schedule(() -> {
            PendingRequest removed = pendingRequests.remove(requestId);
            if (removed != null && !removed.future.isDone()) {
                removed.future.completeExceptionally(
                        new TimeoutException("Request timed out after " + timeoutMs + "ms"));
                logger.debug("Request {} timed out after {}ms", requestId, timeoutMs);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);

        pendingRequest.timeoutTask = timeoutTask;

        return future;
    }
    
    /**
     * Complete a request with response
     * @param requestId the request ID
     * @param response the response object
     * @return true if request was found and completed, false otherwise
     */
    @SuppressWarnings("unchecked")
    public <T> boolean completeRequest(long requestId, T response) {
        PendingRequest pendingRequest = pendingRequests.remove(requestId);
        if (pendingRequest != null) {
            // Cancel timeout task
            if (pendingRequest.timeoutTask != null) {
                pendingRequest.timeoutTask.cancel(false);
            }

            // Complete the future
            CompletableFuture<T> future = (CompletableFuture<T>) pendingRequest.future;
            future.complete(response);

            logger.debug("Completed request {} successfully", requestId);
            return true;
        }

        logger.debug("Request {} not found in pending requests", requestId);
        return false;
    }
    
    /**
     * Complete a request with exception
     * @param requestId the request ID
     * @param exception the exception
     * @return true if request was found and completed, false otherwise
     */
    public boolean completeRequestExceptionally(long requestId, Throwable exception) {
        PendingRequest pendingRequest = pendingRequests.remove(requestId);
        if (pendingRequest != null) {
            // Cancel timeout task
            if (pendingRequest.timeoutTask != null) {
                pendingRequest.timeoutTask.cancel(false);
            }

            // Complete the future exceptionally
            pendingRequest.future.completeExceptionally(exception);

            logger.debug("Completed request {} with exception: {}", requestId, exception.getMessage());
            return true;
        }

        logger.debug("Request {} not found in pending requests", requestId);
        return false;
    }

    /**
     * Get the number of pending requests
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }
    
    /**
     * Cleanup all pending requests and shutdown
     */
    public void shutdown() {
        logger.info("Shutting down RequestManager with {} pending requests", pendingRequests.size());

        // Complete all pending requests with exception
        pendingRequests.values().forEach(pendingRequest -> {
            if (pendingRequest.timeoutTask != null) {
                pendingRequest.timeoutTask.cancel(false);
            }
            if (!pendingRequest.future.isDone()) {
                pendingRequest.future.completeExceptionally(
                        new IllegalStateException("RequestManager is shutting down"));
            }
        });
        
        pendingRequests.clear();
        timeoutScheduler.shutdown();
        
        try {
            if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                timeoutScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            timeoutScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Internal class for tracking pending requests
     */
    private static class PendingRequest {
        final CompletableFuture<?> future;
        final long timeoutMs;
        volatile ScheduledFuture<?> timeoutTask;

        PendingRequest(CompletableFuture<?> future, long timeoutMs) {
            this.future = future;
            this.timeoutMs = timeoutMs;
        }
    }

    /**
     * Context for a request containing ID and future
     */
    public static class RequestContext<T> {
        private final long requestId;
        private final CompletableFuture<T> future;
        
        public RequestContext(long requestId, CompletableFuture<T> future) {
            this.requestId = requestId;
            this.future = future;
        }
        
        public long getRequestId() { return requestId; }
        public CompletableFuture<T> getFuture() { return future; }
    }
}
