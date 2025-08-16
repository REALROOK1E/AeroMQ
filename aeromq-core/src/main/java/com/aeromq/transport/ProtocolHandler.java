package com.aeromq.transport;

import com.aeromq.broker.AeroBroker;
import com.aeromq.protocol.AeroProtocol;
import com.aeromq.protocol.Commands;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol handler for processing AeroMQ messages
 * Handles the AeroMQ protocol communication
 */
public class ProtocolHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(ProtocolHandler.class);
    
    private final AeroBroker broker;
    private final ObjectMapper objectMapper;
    private final Map<String, ClientSession> clientSessions;
    
    public ProtocolHandler(AeroBroker broker) {
        this.broker = broker;
        this.objectMapper = new ObjectMapper();
        this.clientSessions = new ConcurrentHashMap<>();
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buffer = (ByteBuf) msg;
            try {
                processMessage(ctx, buffer);
            } finally {
                buffer.release();
            }
        }
    }
    
    /**
     * Process incoming message
     */
    private void processMessage(ChannelHandlerContext ctx, ByteBuf buffer) {
        try {
            // Read message bytes
            byte[] messageBytes = new byte[buffer.readableBytes()];
            buffer.readBytes(messageBytes);
            
            // Parse protocol message
            AeroProtocol.ProtocolMessage message = objectMapper.readValue(
                    messageBytes, AeroProtocol.ProtocolMessage.class);
            
            logger.debug("Received message: {} from client {}", 
                    message.getCommand(), message.getClientId());
            
            // Validate protocol version
            if (!AeroProtocol.PROTOCOL_VERSION.equals(message.getVersion())) {
                sendErrorResponse(ctx, message.getRequestId(), 
                        AeroProtocol.StatusCodes.BAD_REQUEST, 
                        "Unsupported protocol version: " + message.getVersion());
                return;
            }
            
            // Validate command
            if (!Commands.isValidCommand(message.getCommand())) {
                sendErrorResponse(ctx, message.getRequestId(), 
                        AeroProtocol.StatusCodes.BAD_REQUEST, 
                        "Unknown command: " + message.getCommand());
                return;
            }
            
            // Handle command
            handleCommand(ctx, message);
            
        } catch (Exception e) {
            logger.error("Error processing message", e);
            sendErrorResponse(ctx, (Long) null, 
                    AeroProtocol.StatusCodes.INTERNAL_ERROR, 
                    "Internal server error");
        }
    }
    
    /**
     * Handle specific command
     */
    private void handleCommand(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        String command = message.getCommand();
        String clientId = message.getClientId();
        
        try {
            switch (command) {
                case Commands.CONNECT:
                    handleConnect(ctx, message);
                    break;
                    
                case Commands.DISCONNECT:
                    handleDisconnect(ctx, message);
                    break;
                    
                case Commands.PING:
                    handlePing(ctx, message);
                    break;
                    
                case Commands.SEND:
                    handleSend(ctx, message);
                    break;
                    
                case Commands.SUBSCRIBE:
                    handleSubscribe(ctx, message);
                    break;
                    
                case Commands.UNSUBSCRIBE:
                    handleUnsubscribe(ctx, message);
                    break;
                    
                case Commands.ACK:
                    handleAck(ctx, message);
                    break;
                    
                case Commands.CONSUME:
                    handleConsume(ctx, message);
                    break;

                case Commands.CREATE_QUEUE:
                    handleCreateQueue(ctx, message);
                    break;
                    
                case Commands.LIST_QUEUES:
                    handleListQueues(ctx, message);
                    break;
                    
                default:
                    sendErrorResponse(ctx, message.getRequestId(), 
                            AeroProtocol.StatusCodes.BAD_REQUEST, 
                            "Command not implemented: " + command);
            }
        } catch (Exception e) {
            logger.error("Error handling command: {}", command, e);
            sendErrorResponse(ctx, message.getRequestId(), 
                    AeroProtocol.StatusCodes.INTERNAL_ERROR, 
                    "Error processing command");
        }
    }
    
    private void handleConnect(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        String clientId = message.getClientId();
        ClientSession session = new ClientSession(clientId, ctx.channel());
        clientSessions.put(clientId, session);
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("sessionId", session.getSessionId());
        responseData.put("serverVersion", AeroProtocol.PROTOCOL_VERSION);
        
        sendSuccessResponse(ctx, message.getRequestId(), "Connected successfully", responseData);
        logger.info("Client {} connected", clientId);
    }
    
    private void handleDisconnect(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        String clientId = message.getClientId();
        clientSessions.remove(clientId);
        
        sendSuccessResponse(ctx, message.getRequestId(), "Disconnected successfully", null);
        logger.info("Client {} disconnected", clientId);
        ctx.close();
    }
    
    private void handlePing(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // Send pong response
        sendSuccessResponse(ctx, message.getRequestId(), "pong", null);
    }
    
    private void handleSend(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // 生成一个唯一的消息ID
        String messageId = "msg-" + System.currentTimeMillis() + "-" + message.getRequestId();

        // 创建响应数据，包含messageId
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("messageId", messageId);

        // TODO: 实际的消息发送逻辑应该在这里处理
        // 这里只是模拟成功发送

        sendSuccessResponse(ctx, message.getRequestId(), "Message sent", responseData);
    }
    
    private void handleSubscribe(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // TODO: Implement subscription logic
        sendSuccessResponse(ctx, message.getRequestId(), "Subscribed", null);
    }
    
    private void handleUnsubscribe(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // TODO: Implement unsubscription logic
        sendSuccessResponse(ctx, message.getRequestId(), "Unsubscribed", null);
    }
    
    private void handleAck(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // TODO: Implement acknowledgment logic
        sendSuccessResponse(ctx, message.getRequestId(), "Acknowledged", null);
    }
    
    private void handleConsume(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // 从请求头中获取队列名称和最大消息数
        Map<String, Object> headers = message.getHeaders();
        String queueName = headers != null ? (String) headers.get("queueName") : "default-queue";
        Integer maxMessages = headers != null ? (Integer) headers.get("maxMessages") : 1;

        // 模拟返回消息数据
        Map<String, Object> responseData = new HashMap<>();

        // 创建模拟的消息数组
        Map<String, Object>[] messages = new Map[maxMessages];
        for (int i = 0; i < maxMessages; i++) {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messageId", "consumed-msg-" + System.currentTimeMillis() + "-" + i);
            messageData.put("queueName", queueName);
            messageData.put("payload", "Hello from " + queueName + " message " + i);
            messageData.put("timestamp", System.currentTimeMillis());
            messageData.put("headers", new HashMap<>());
            messages[i] = messageData;
        }

        responseData.put("messages", messages);
        responseData.put("messageCount", messages.length);

        sendSuccessResponse(ctx, message.getRequestId(), "Messages consumed successfully", responseData);
        logger.debug("Consumed {} messages from queue: {}", maxMessages, queueName);
    }

    private void handleCreateQueue(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // TODO: Implement queue creation logic
        sendSuccessResponse(ctx, message.getRequestId(), "Queue created", null);
    }
    
    private void handleListQueues(ChannelHandlerContext ctx, AeroProtocol.ProtocolMessage message) {
        // TODO: Implement queue listing logic
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("queues", new String[]{"test-queue"});
        sendSuccessResponse(ctx, message.getRequestId(), "Queues listed", responseData);
    }
    
    /**
     * Send success response
     */
    private void sendSuccessResponse(ChannelHandlerContext ctx, long requestId, 
                                   String statusMessage, Map<String, Object> data) {
        sendResponse(ctx, requestId, AeroProtocol.StatusCodes.SUCCESS, statusMessage, data);
    }
    
    /**
     * Send error response
     */
    private void sendErrorResponse(ChannelHandlerContext ctx, Long requestId, 
                                 int statusCode, String statusMessage) {
        sendResponse(ctx, requestId, statusCode, statusMessage, null);
    }
    
    /**
     * Send protocol response
     */
    private void sendResponse(ChannelHandlerContext ctx, Long requestId, 
                            int statusCode, String statusMessage, Map<String, Object> data) {
        try {
            // Handle null requestId (for general errors)
            long responseRequestId = requestId != null ? requestId : 0L;
            
            AeroProtocol.ProtocolResponse response = new AeroProtocol.ProtocolResponse(
                    responseRequestId, statusCode, statusMessage, data);
            
            byte[] responseBytes = objectMapper.writeValueAsBytes(response);
            
            ByteBuf buffer = ctx.alloc().buffer(responseBytes.length);
            buffer.writeBytes(responseBytes);
            ctx.writeAndFlush(buffer);
            
            logger.debug("Sent response: {} ({})", statusMessage, statusCode);
            
        } catch (Exception e) {
            logger.error("Error sending response", e);
        }
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Clean up client sessions for this channel
        clientSessions.entrySet().removeIf(entry -> 
                entry.getValue().getChannel().equals(ctx.channel()));
        super.channelInactive(ctx);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Protocol handler exception", cause);
        ctx.close();
    }
}
