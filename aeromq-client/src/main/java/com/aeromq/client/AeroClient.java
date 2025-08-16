package com.aeromq.client;

import com.aeromq.protocol.AeroProtocol;
import com.aeromq.protocol.Commands;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AeroMQ Client - Main client class for connecting to AeroMQ broker
 * Updated to use RequestManager for high-concurrency request handling
 */
public class AeroClient {
    
    private static final Logger logger = LoggerFactory.getLogger(AeroClient.class);
    
    private final String clientId;
    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private final RequestManager requestManager;
    
    private EventLoopGroup eventLoopGroup;
    private Channel channel;
    
    private volatile boolean connected = false;
    private volatile String sessionId;
    
    public AeroClient(String clientId, String host, int port) {
        this.clientId = clientId;
        this.host = host;
        this.port = port;
        this.objectMapper = new ObjectMapper();
        // 减少超时时间和最大并发数，提高响应性
        this.requestManager = new RequestManager(100, 15000); // max 100 pending requests, 15s default timeout
    }
    
    public AeroClient(String host, int port) {
        this(UUID.randomUUID().toString(), host, port);
    }
    
    public AeroClient(String host) {
        this(host, AeroProtocol.DEFAULT_PORT);
    }
    
    /**
     * Connect to the broker
     */
    public CompletableFuture<Void> connect() {
        if (connected) {
            return CompletableFuture.completedFuture(null);
        }
        
        CompletableFuture<Void> connectFuture = new CompletableFuture<>();
        
        eventLoopGroup = new NioEventLoopGroup();
        
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(eventLoopGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // Frame decoder/encoder
                            pipeline.addLast("frameDecoder", 
                                    new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                            pipeline.addLast("frameEncoder", 
                                    new LengthFieldPrepender(4));
                            
                            // Response handler
                            pipeline.addLast("responseHandler", new ResponseHandler());
                        }
                    });
            
            ChannelFuture channelFuture = bootstrap.connect(host, port);
            channelFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    channel = future.channel();
                    logger.info("Connected to AeroMQ broker at {}:{}", host, port);
                    
                    // Send connect command
                    sendConnect().whenComplete((response, throwable) -> {
                        if (throwable == null && response.getStatusCode() == AeroProtocol.StatusCodes.SUCCESS) {
                            connected = true;
                            sessionId = (String) response.getData().get("sessionId");
                            logger.info("Successfully authenticated with broker, sessionId: {}", sessionId);
                            connectFuture.complete(null);
                        } else {
                            logger.error("Failed to authenticate with broker", throwable);
                            connectFuture.completeExceptionally(
                                    throwable != null ? throwable : 
                                    new RuntimeException("Authentication failed: " + response.getStatusMessage()));
                        }
                    });
                } else {
                    logger.error("Failed to connect to broker at {}:{}", host, port, future.cause());
                    connectFuture.completeExceptionally(future.cause());
                }
            });
            
        } catch (Exception e) {
            logger.error("Error during connection", e);
            connectFuture.completeExceptionally(e);
        }
        
        return connectFuture;
    }
    
    /**
     * Disconnect from the broker
     */
    public CompletableFuture<Void> disconnect() {
        if (!connected) {
            return CompletableFuture.completedFuture(null);
        }
        
        CompletableFuture<Void> disconnectFuture = new CompletableFuture<>();
        
        try {
            // Send disconnect command
            sendDisconnect().whenComplete((response, throwable) -> {
                connected = false;
                sessionId = null;
                
                // Shutdown RequestManager and cancel pending requests
                requestManager.shutdown();
                
                if (channel != null) {
                    channel.close();
                }
                
                if (eventLoopGroup != null) {
                    eventLoopGroup.shutdownGracefully();
                }
                
                logger.info("Disconnected from AeroMQ broker");
                disconnectFuture.complete(null);
            });
            
        } catch (Exception e) {
            logger.error("Error during disconnection", e);
            disconnectFuture.completeExceptionally(e);
        }
        
        return disconnectFuture;
    }
    
    /**
     * Create a producer for sending messages
     */
    public Producer createProducer() {
        if (!connected) {
            throw new IllegalStateException("Client is not connected");
        }
        return new Producer(this);
    }
    
    /**
     * Create a consumer for receiving messages
     */
    public Consumer createConsumer() {
        if (!connected) {
            throw new IllegalStateException("Client is not connected");
        }
        return new Consumer(this);
    }
    
    /**
     * Send a ping to the broker
     */
    public CompletableFuture<AeroProtocol.ProtocolResponse> ping() {
        return sendCommand(Commands.PING, null, null);
    }
    
    /**
     * List available queues
     */
    public CompletableFuture<AeroProtocol.ProtocolResponse> listQueues() {
        return sendCommand(Commands.LIST_QUEUES, null, null);
    }
    
    /**
     * Create a queue
     */
    public CompletableFuture<AeroProtocol.ProtocolResponse> createQueue(String queueName) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("queueName", queueName);
        return sendCommand(Commands.CREATE_QUEUE, headers, null);
    }
    
    /**
     * Send connect command
     */
    private CompletableFuture<AeroProtocol.ProtocolResponse> sendConnect() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("clientVersion", "1.0");
        return sendCommand(Commands.CONNECT, headers, null);
    }
    
    /**
     * Send disconnect command
     */
    private CompletableFuture<AeroProtocol.ProtocolResponse> sendDisconnect() {
        return sendCommand(Commands.DISCONNECT, null, null);
    }
    
    /**
     * Send a command to the broker using RequestManager for concurrent requests
     */
    CompletableFuture<AeroProtocol.ProtocolResponse> sendCommand(String command, 
                                                                 Map<String, Object> headers, 
                                                                 byte[] payload) {
        if (!connected && !Commands.CONNECT.equals(command)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Client is not connected"));
        }
        
        try {
            // Use RequestManager to generate requestId and register request
            long requestId = requestManager.nextRequestId();
            // 使用更短的超时时间，与RequestManager的15秒配置保持一致
            CompletableFuture<AeroProtocol.ProtocolResponse> future =
                    requestManager.registerRequest(requestId, 10, TimeUnit.SECONDS);

            AeroProtocol.ProtocolMessage message = new AeroProtocol.ProtocolMessage(
                    AeroProtocol.PROTOCOL_VERSION,
                    command,
                    requestId,  // Now using long requestId
                    System.currentTimeMillis(),
                    clientId,
                    headers,
                    payload
            );
            
            byte[] messageBytes = objectMapper.writeValueAsBytes(message);
            
            ByteBuf buffer = channel.alloc().buffer(messageBytes.length);
            buffer.writeBytes(messageBytes);
            channel.writeAndFlush(buffer);
            
            logger.debug("Sent command: {} with requestId: {}", command, requestId);
            
            return future;
            
        } catch (Exception e) {
            logger.error("Failed to send command: " + command, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Response handler for processing server responses using RequestManager
     */
    private class ResponseHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof ByteBuf) {
                ByteBuf buffer = (ByteBuf) msg;
                try {
                    byte[] responseBytes = new byte[buffer.readableBytes()];
                    buffer.readBytes(responseBytes);
                    
                    AeroProtocol.ProtocolResponse response = objectMapper.readValue(
                            responseBytes, AeroProtocol.ProtocolResponse.class);
                    
                    long requestId = response.getRequestId();  // Now using long requestId
                    
                    if (requestManager.completeRequest(requestId, response)) {
                        logger.debug("Received response for requestId: {} with status: {}", 
                                requestId, response.getStatusCode());
                    } else {
                        logger.warn("Received response for unknown requestId: {}", requestId);
                    }
                    
                } finally {
                    buffer.release();
                }
            }
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("Exception in response handler", cause);
            ctx.close();
        }
    }
    
    // Getters
    public String getClientId() { return clientId; }
    public boolean isConnected() { return connected; }
    public String getSessionId() { return sessionId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
}
