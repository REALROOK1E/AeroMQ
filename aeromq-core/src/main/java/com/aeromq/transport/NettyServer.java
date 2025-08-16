package com.aeromq.transport;

import com.aeromq.broker.AeroBroker;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty-based network server for AeroMQ
 * Provides high-performance asynchronous I/O
 */
public class NettyServer {
    
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    
    private final AeroBroker broker;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;
    private ProtocolHandler protocolHandler;
    
    public NettyServer(AeroBroker broker) {
        this.broker = broker;
        this.protocolHandler = new ProtocolHandler(broker);
    }
    
    /**
     * Start the server on specified port
     */
    public void start(int port) throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_RCVBUF, 65536)
                    .childOption(ChannelOption.SO_SNDBUF, 65536)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            
                            // Frame decoder/encoder for message boundaries
                            pipeline.addLast("frameDecoder", 
                                    new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                            pipeline.addLast("frameEncoder", 
                                    new LengthFieldPrepender(4));
                            
                            // Protocol handler - 为每个连接创建新实例
                            pipeline.addLast("protocolHandler", new ProtocolHandler(broker));

                            // Connection handler
                            pipeline.addLast("connectionHandler", new ConnectionHandler());
                        }
                    });
            
            ChannelFuture future = bootstrap.bind(port).sync();
            serverChannel = future.channel();
            
            logger.info("AeroMQ Server started on port {}", port);
            
        } catch (Exception e) {
            logger.error("Failed to start server on port {}", port, e);
            shutdown();
            throw e;
        }
    }
    
    /**
     * Shutdown the server
     */
    public void shutdown() {
        logger.info("Shutting down AeroMQ Server...");
        
        try {
            if (serverChannel != null) {
                serverChannel.close().sync();
            }
        } catch (InterruptedException e) {
            logger.warn("Interrupted while closing server channel", e);
            Thread.currentThread().interrupt();
        } finally {
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
        }
        
        logger.info("AeroMQ Server shutdown complete");
    }
    
    /**
     * Connection handler for managing client connections
     */
    private static class ConnectionHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            String clientAddress = ctx.channel().remoteAddress().toString();
            logger.info("Client connected: {}", clientAddress);
            super.channelActive(ctx);
        }
        
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            String clientAddress = ctx.channel().remoteAddress().toString();
            logger.info("Client disconnected: {}", clientAddress);
            super.channelInactive(ctx);
        }
        
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            String clientAddress = ctx.channel().remoteAddress().toString();
            logger.error("Exception in connection with client {}", clientAddress, cause);
            ctx.close();
        }
    }
    
    /**
     * Get server statistics
     */
    public ServerStats getStats() {
        return new ServerStats(
                serverChannel != null && serverChannel.isActive(),
                bossGroup != null ? !bossGroup.isShutdown() : false,
                workerGroup != null ? !workerGroup.isShutdown() : false
        );
    }
    
    /**
     * Server statistics holder
     */
    public static class ServerStats {
        private final boolean serverActive;
        private final boolean bossGroupActive;
        private final boolean workerGroupActive;
        
        public ServerStats(boolean serverActive, boolean bossGroupActive, boolean workerGroupActive) {
            this.serverActive = serverActive;
            this.bossGroupActive = bossGroupActive;
            this.workerGroupActive = workerGroupActive;
        }
        
        public boolean isServerActive() { return serverActive; }
        public boolean isBossGroupActive() { return bossGroupActive; }
        public boolean isWorkerGroupActive() { return workerGroupActive; }
        
        @Override
        public String toString() {
            return String.format("ServerStats{server=%s, boss=%s, worker=%s}", 
                    serverActive, bossGroupActive, workerGroupActive);
        }
    }
}
