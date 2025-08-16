package com.aeromq.client;

import com.aeromq.protocol.AeroProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for RequestManager functionality
 */
public class RequestManagerTest {
    
    private RequestManager requestManager;
    
    @BeforeEach
    void setUp() {
        requestManager = new RequestManager();
    }
    
    @AfterEach
    void tearDown() {
        if (requestManager != null) {
            requestManager.shutdown();
        }
    }
    
    @Test
    void testRequestIdGeneration() {
        // Test that request IDs are generated sequentially
        long id1 = requestManager.nextRequestId();
        long id2 = requestManager.nextRequestId();
        long id3 = requestManager.nextRequestId();
        
        assertEquals(1L, id1);
        assertEquals(2L, id2);
        assertEquals(3L, id3);
    }
    
    @Test
    void testRequestRegistrationAndCompletion() throws Exception {
        // Register a request
        long requestId = requestManager.nextRequestId();
        CompletableFuture<AeroProtocol.ProtocolResponse> future = 
                requestManager.registerRequest(requestId, 5, TimeUnit.SECONDS);
        
        // Verify request is pending
        assertFalse(future.isDone());
        
        // Create response
        AeroProtocol.ProtocolResponse response = AeroProtocol.success(requestId);
        
        // Complete the request
        assertTrue(requestManager.completeRequest(requestId, response));
        
        // Verify completion
        assertTrue(future.isDone());
        assertEquals(response, future.get());
        assertEquals(200, future.get().getStatusCode());
    }
    
    @Test
    void testRequestTimeout() throws Exception {
        // Register a request with short timeout
        long requestId = requestManager.nextRequestId();
        CompletableFuture<AeroProtocol.ProtocolResponse> future = 
                requestManager.registerRequest(requestId, 100, TimeUnit.MILLISECONDS);
        
        // Wait for timeout
        Thread.sleep(200);
        
        // Verify request timed out
        assertTrue(future.isDone());
        assertTrue(future.isCompletedExceptionally());
        
        assertThrows(TimeoutException.class, () -> {
            try {
                future.get();
            } catch (Exception e) {
                if (e.getCause() instanceof TimeoutException) {
                    throw (TimeoutException) e.getCause();
                }
                throw e;
            }
        });
    }
    
    @Test
    void testUnknownRequestCompletion() {
        // Try to complete a request that doesn't exist
        AeroProtocol.ProtocolResponse response = AeroProtocol.success(999L);
        assertFalse(requestManager.completeRequest(999L, response));
    }
    
    @Test
    void testConcurrentRequests() throws Exception {
        // Test multiple concurrent requests
        CompletableFuture<AeroProtocol.ProtocolResponse>[] futures = new CompletableFuture[10];
        long[] requestIds = new long[10];
        
        // Register multiple requests
        for (int i = 0; i < 10; i++) {
            requestIds[i] = requestManager.nextRequestId();
            futures[i] = requestManager.registerRequest(requestIds[i], 5, TimeUnit.SECONDS);
        }
        
        // Complete all requests
        for (int i = 0; i < 10; i++) {
            AeroProtocol.ProtocolResponse response = AeroProtocol.success(requestIds[i]);
            assertTrue(requestManager.completeRequest(requestIds[i], response));
        }
        
        // Verify all completed
        for (int i = 0; i < 10; i++) {
            assertTrue(futures[i].isDone());
            assertFalse(futures[i].isCompletedExceptionally());
            assertEquals(requestIds[i], futures[i].get().getRequestId());
        }
    }
    
    @Test
    void testRequestCapacityLimit() {
        // Test that capacity limits are enforced
        int maxCapacity = RequestManager.DEFAULT_MAX_REQUESTS;
        
        // Fill up to capacity
        for (int i = 0; i < maxCapacity; i++) {
            long requestId = requestManager.nextRequestId();
            assertNotNull(requestManager.registerRequest(requestId, 10, TimeUnit.SECONDS));
        }
        
        // Next request should fail
        long overflowRequestId = requestManager.nextRequestId();
        assertThrows(IllegalStateException.class, () -> {
            requestManager.registerRequest(overflowRequestId, 10, TimeUnit.SECONDS);
        });
    }
    
    @Test
    void testShutdown() throws Exception {
        // Register some requests
        long requestId1 = requestManager.nextRequestId();
        long requestId2 = requestManager.nextRequestId();
        
        CompletableFuture<AeroProtocol.ProtocolResponse> future1 = 
                requestManager.registerRequest(requestId1, 10, TimeUnit.SECONDS);
        CompletableFuture<AeroProtocol.ProtocolResponse> future2 = 
                requestManager.registerRequest(requestId2, 10, TimeUnit.SECONDS);
        
        // Shutdown
        requestManager.shutdown();
        
        // Verify all pending requests are cancelled
        assertTrue(future1.isDone());
        assertTrue(future2.isDone());
        assertTrue(future1.isCancelled());
        assertTrue(future2.isCancelled());
        
        // Verify no new requests can be registered
        long newRequestId = requestManager.nextRequestId();
        assertThrows(IllegalStateException.class, () -> {
            requestManager.registerRequest(newRequestId, 10, TimeUnit.SECONDS);
        });
    }
    
    @Test
    void testGetStats() throws Exception {
        // Register and complete some requests
        for (int i = 0; i < 5; i++) {
            long requestId = requestManager.nextRequestId();
            CompletableFuture<AeroProtocol.ProtocolResponse> future = 
                    requestManager.registerRequest(requestId, 5, TimeUnit.SECONDS);
            requestManager.completeRequest(requestId, AeroProtocol.success(requestId));
        }
        
        // Register some pending requests
        for (int i = 0; i < 3; i++) {
            long requestId = requestManager.nextRequestId();
            requestManager.registerRequest(requestId, 5, TimeUnit.SECONDS);
        }
        
        // Check stats
        RequestManager.RequestStats stats = requestManager.getStats();
        assertEquals(8L, stats.getTotalRequests());
        assertEquals(5L, stats.getCompletedRequests());
        assertEquals(3, stats.getPendingRequests());
        assertEquals(0L, stats.getTimedOutRequests());
        assertTrue(stats.getAverageResponseTime() >= 0);
    }
}
