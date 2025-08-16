package com.aeromq.transport;

import io.netty.channel.Channel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client session management
 */
public class ClientSession {
    
    private final String clientId;
    private final String sessionId;
    private final Channel channel;
    private final long createdTime;
    private final ConcurrentMap<String, String> subscriptions;
    
    public ClientSession(String clientId, Channel channel) {
        this.clientId = clientId;
        this.sessionId = UUID.randomUUID().toString();
        this.channel = channel;
        this.createdTime = System.currentTimeMillis();
        this.subscriptions = new ConcurrentHashMap<>();
    }
    
    public String getClientId() { return clientId; }
    public String getSessionId() { return sessionId; }
    public Channel getChannel() { return channel; }
    public long getCreatedTime() { return createdTime; }
    public ConcurrentMap<String, String> getSubscriptions() { return subscriptions; }
    
    public void addSubscription(String queueName, String subscriptionId) {
        subscriptions.put(queueName, subscriptionId);
    }
    
    public void removeSubscription(String queueName) {
        subscriptions.remove(queueName);
    }
    
    public boolean isSubscribed(String queueName) {
        return subscriptions.containsKey(queueName);
    }
}
