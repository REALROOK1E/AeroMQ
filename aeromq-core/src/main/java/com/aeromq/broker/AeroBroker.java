package com.aeromq.broker;

import com.aeromq.transport.NettyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * AeroBroker - Main broker startup class
 * Manages the lifecycle of the AeroMQ broker
 */
public class AeroBroker {
    
    private static final Logger logger = LoggerFactory.getLogger(AeroBroker.class);
    
    private final Properties config;
    private final NettyServer server;
    private final MessageStore messageStore;
    private final StateMachine stateMachine;
    private final IndexManager indexManager;
    private final CountDownLatch shutdownLatch;
    
    public AeroBroker() throws IOException {
        this.config = loadConfiguration();
        
        // Use high-performance message store by default
        String storeType = config.getProperty("aeromq.storage.type", "high-performance");
        if ("high-performance".equals(storeType)) {
            this.messageStore = new HighPerformanceMessageStore();
        } else {
            this.messageStore = new InMemoryMessageStore();
        }
        
        this.stateMachine = new StateMachine();
        this.indexManager = new IndexManager();
        this.server = new NettyServer(this);
        this.shutdownLatch = new CountDownLatch(1);
    }
    
    /**
     * Start the broker
     */
    public void start() throws Exception {
        logger.info("Starting AeroMQ Broker...");
        
        // Initialize components
        messageStore.initialize();
        stateMachine.initialize();
        indexManager.initialize();
        
        // Start network server
        int port = Integer.parseInt(config.getProperty("aeromq.broker.port", "9000"));
        server.start(port);
        
        logger.info("AeroMQ Broker started successfully on port {}", port);
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        // Wait for shutdown signal
        shutdownLatch.await();
    }
    
    /**
     * Shutdown the broker
     */
    public void shutdown() {
        logger.info("Shutting down AeroMQ Broker...");
        
        try {
            server.shutdown();
            indexManager.shutdown();
            stateMachine.shutdown();
            messageStore.shutdown();
            
            logger.info("AeroMQ Broker shut down successfully");
        } catch (Exception e) {
            logger.error("Error during broker shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
    }
    
    /**
     * Load configuration from properties file
     */
    private Properties loadConfiguration() throws IOException {
        Properties props = new Properties();
        
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("aeromq.properties")) {
            if (input != null) {
                props.load(input);
            } else {
                logger.warn("aeromq.properties not found, using defaults");
            }
        }
        
        return props;
    }
    
    // Getters for components
    public Properties getConfig() { return config; }
    public MessageStore getMessageStore() { return messageStore; }
    public StateMachine getStateMachine() { return stateMachine; }
    public IndexManager getIndexManager() { return indexManager; }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        try {
            AeroBroker broker = new AeroBroker();
            broker.start();
        } catch (Exception e) {
            logger.error("Failed to start AeroMQ Broker", e);
            System.exit(1);
        }
    }
}
