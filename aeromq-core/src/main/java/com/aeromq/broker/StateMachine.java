package com.aeromq.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * State Machine implementation for Raft consensus
 * Manages broker state transitions and leader election
 */
public class StateMachine {
    
    private static final Logger logger = LoggerFactory.getLogger(StateMachine.class);
    
    public enum State {
        FOLLOWER,
        CANDIDATE, 
        LEADER
    }
    
    private final AtomicReference<State> currentState = new AtomicReference<>(State.FOLLOWER);
    private final ConcurrentHashMap<String, Object> stateData = new ConcurrentHashMap<>();
    
    private volatile String currentLeader = null;
    private volatile long currentTerm = 0;
    private volatile String votedFor = null;
    
    public void initialize() throws Exception {
        logger.info("Initializing StateMachine");
        transitionTo(State.FOLLOWER);
    }
    
    /**
     * Transition to a new state
     */
    public synchronized void transitionTo(State newState) {
        State oldState = currentState.get();
        if (oldState != newState) {
            currentState.set(newState);
            onStateChanged(oldState, newState);
            logger.info("State transition: {} -> {}", oldState, newState);
        }
    }
    
    /**
     * Handle state change events
     */
    private void onStateChanged(State oldState, State newState) {
        switch (newState) {
            case FOLLOWER:
                onBecomeFollower();
                break;
            case CANDIDATE:
                onBecomeCandidate();
                break;
            case LEADER:
                onBecomeLeader();
                break;
        }
    }
    
    private void onBecomeFollower() {
        logger.debug("Became follower for term {}", currentTerm);
        currentLeader = null;
    }
    
    private void onBecomeCandidate() {
        logger.debug("Became candidate for term {}", currentTerm + 1);
        currentTerm++;
        votedFor = getNodeId(); // Vote for self
        currentLeader = null;
        // TODO: Start election process
    }
    
    private void onBecomeLeader() {
        logger.info("Became leader for term {}", currentTerm);
        currentLeader = getNodeId();
        // TODO: Start sending heartbeats
    }
    
    /**
     * Start an election
     */
    public synchronized void startElection() {
        if (currentState.get() != State.LEADER) {
            transitionTo(State.CANDIDATE);
            // TODO: Implement actual election logic
        }
    }
    
    /**
     * Receive a vote request
     */
    public synchronized boolean requestVote(String candidateId, long term) {
        if (term > currentTerm) {
            currentTerm = term;
            votedFor = null;
            transitionTo(State.FOLLOWER);
        }
        
        if (term == currentTerm && 
            (votedFor == null || votedFor.equals(candidateId))) {
            votedFor = candidateId;
            logger.debug("Voted for candidate {} in term {}", candidateId, term);
            return true;
        }
        
        return false;
    }
    
    /**
     * Receive a heartbeat from leader
     */
    public synchronized void receiveHeartbeat(String leaderId, long term) {
        if (term >= currentTerm) {
            currentTerm = term;
            currentLeader = leaderId;
            if (currentState.get() != State.FOLLOWER) {
                transitionTo(State.FOLLOWER);
            }
            logger.debug("Received heartbeat from leader {} in term {}", leaderId, term);
        }
    }
    
    /**
     * Apply a state change
     */
    public void applyStateChange(String key, Object value) {
        stateData.put(key, value);
        logger.debug("Applied state change: {} = {}", key, value);
    }
    
    /**
     * Get state value
     */
    public Object getStateValue(String key) {
        return stateData.get(key);
    }
    
    // Getters
    public State getCurrentState() {
        return currentState.get();
    }
    
    public String getCurrentLeader() {
        return currentLeader;
    }
    
    public long getCurrentTerm() {
        return currentTerm;
    }
    
    public boolean isLeader() {
        return currentState.get() == State.LEADER;
    }
    
    public boolean isFollower() {
        return currentState.get() == State.FOLLOWER;
    }
    
    public boolean isCandidate() {
        return currentState.get() == State.CANDIDATE;
    }
    
    /**
     * Get current node ID (simplified implementation)
     */
    private String getNodeId() {
        // TODO: Implement proper node ID generation
        return "node-" + System.getProperty("user.name", "unknown");
    }
    
    public void shutdown() throws Exception {
        logger.info("Shutting down StateMachine");
        stateData.clear();
    }
}
