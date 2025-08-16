package com.aeromq.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AeroClient
 */
public class AeroClientTest {
    
    private AeroClient client;
    
    @BeforeEach
    public void setUp() {
        client = new AeroClient("test-client", "localhost", 8888);
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        if (client != null && client.isConnected()) {
            client.disconnect().get();
        }
    }
    
    @Test
    public void testClientCreation() {
        assertNotNull(client);
        assertEquals("test-client", client.getClientId());
        assertEquals("localhost", client.getHost());
        assertEquals(8888, client.getPort());
        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
    }
    
    @Test
    public void testProducerCreation() {
        // This should fail when not connected
        assertThrows(IllegalStateException.class, () -> client.createProducer());
    }
    
    @Test
    public void testConsumerCreation() {
        // This should fail when not connected
        assertThrows(IllegalStateException.class, () -> client.createConsumer());
    }
    
    @Test
    public void testClientWithDefaults() {
        AeroClient defaultClient = new AeroClient("localhost");
        assertEquals(8888, defaultClient.getPort()); // Default port
        assertNotNull(defaultClient.getClientId()); // Auto-generated client ID
    }
}
