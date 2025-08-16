package com.aeromq.protocol;

/**
 * AeroMQ Protocol Commands
 * Defines all available commands in the AeroMQ protocol
 */
public class Commands {
    
    // Connection commands
    public static final String CONNECT = "CONNECT";
    public static final String DISCONNECT = "DISCONNECT";
    public static final String PING = "PING";
    public static final String PONG = "PONG";
    
    // Producer commands
    public static final String SEND = "SEND";
    public static final String SEND_BATCH = "SEND_BATCH";
    public static final String SEND_ACK = "SEND_ACK";
    
    // Consumer commands
    public static final String SUBSCRIBE = "SUBSCRIBE";
    public static final String UNSUBSCRIBE = "UNSUBSCRIBE";
    public static final String CONSUME = "CONSUME";
    public static final String ACK = "ACK";
    public static final String NACK = "NACK";
    
    // Queue management commands
    public static final String CREATE_QUEUE = "CREATE_QUEUE";
    public static final String DELETE_QUEUE = "DELETE_QUEUE";
    public static final String LIST_QUEUES = "LIST_QUEUES";
    public static final String QUEUE_INFO = "QUEUE_INFO";
    
    // Administrative commands
    public static final String CLUSTER_STATUS = "CLUSTER_STATUS";
    public static final String NODE_STATUS = "NODE_STATUS";
    public static final String METRICS = "METRICS";
    
    // Error commands
    public static final String ERROR = "ERROR";
    
    /**
     * Command validation
     */
    public static boolean isValidCommand(String command) {
        if (command == null) {
            return false;
        }
        
        switch (command) {
            case CONNECT:
            case DISCONNECT:
            case PING:
            case PONG:
            case SEND:
            case SEND_BATCH:
            case SEND_ACK:
            case SUBSCRIBE:
            case UNSUBSCRIBE:
            case CONSUME:
            case ACK:
            case NACK:
            case CREATE_QUEUE:
            case DELETE_QUEUE:
            case LIST_QUEUES:
            case QUEUE_INFO:
            case CLUSTER_STATUS:
            case NODE_STATUS:
            case METRICS:
            case ERROR:
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Check if command requires authentication
     */
    public static boolean requiresAuth(String command) {
        switch (command) {
            case PING:
            case PONG:
            case CONNECT:
                return false;
            default:
                return true;
        }
    }
    
    /**
     * Check if command is administrative
     */
    public static boolean isAdminCommand(String command) {
        switch (command) {
            case CREATE_QUEUE:
            case DELETE_QUEUE:
            case CLUSTER_STATUS:
            case NODE_STATUS:
            case METRICS:
                return true;
            default:
                return false;
        }
    }
}
