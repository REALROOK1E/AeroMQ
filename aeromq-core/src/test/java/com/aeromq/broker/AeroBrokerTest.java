package com.aeromq.broker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AeroBroker
 */
public class AeroBrokerTest {
    
    private AeroBroker broker;
    
    @BeforeEach
    public void setUp() throws Exception {
        broker = new AeroBroker();
    }
    
    @AfterEach
    public void tearDown() {
        if (broker != null) {
            broker.shutdown();
        }
    }
    
    @Test
    public void testBrokerInitialization() {
        assertNotNull(broker.getConfig());
        assertNotNull(broker.getMessageStore());
        assertNotNull(broker.getStateMachine());
        assertNotNull(broker.getIndexManager());
    }
    
    @Test
    public void testConfiguration() {
        String port = broker.getConfig().getProperty("aeromq.broker.port", "8888");
        assertEquals("8888", port);
    }
    
    @Test
    public void testStateMachine() throws Exception {
        broker.getStateMachine().initialize();
        assertEquals(StateMachine.State.FOLLOWER, broker.getStateMachine().getCurrentState());
    }
    
    @Test
    public void testMessageStore() throws Exception {
        MessageStore store = broker.getMessageStore();
        store.initialize();
        
        // Test queue creation
        store.createQueue("test-queue").get();
        
        // Test queue listing
        var queues = store.listQueues().get();
        assertTrue(queues.contains("test-queue"));
        
        // Test queue size
        long size = store.getQueueSize("test-queue").get();
        assertEquals(0, size);
    }
}
